#!/usr/bin/env python3
import argparse
import hashlib
import hmac
import json
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class State:
    def __init__(self, secret):
        self.secret = secret
        self.lock = threading.Lock()
        self.upstream_requests = 0
        self.callback_unique = set()
        self.callback_http_requests = 0
        self.callback_http_success = 0
        self.downstream_attempts = {}
        self.downstream_success = set()
        self.invalid_signatures = 0

    def stats(self):
        with self.lock:
            return {
                "upstreamRequests": self.upstream_requests,
                "callbackUnique": len(self.callback_unique),
                "callbackHttpRequests": self.callback_http_requests,
                "callbackHttpSuccess": self.callback_http_success,
                "downstreamUniqueKeys": len(self.downstream_attempts),
                "downstreamAttempts": sum(self.downstream_attempts.values()),
                "downstreamSuccessKeys": len(self.downstream_success),
                "invalidSignatures": self.invalid_signatures,
            }


def should_convert(event_id):
    try:
        return int(event_id.rsplit("-", 1)[1]) % 10 == 0
    except (ValueError, IndexError):
        return False


class Handler(BaseHTTPRequestHandler):
    server_version = "SCXMock/1.0"

    def log_message(self, _format, *_args):
        return

    def write_json(self, status, payload):
        encoded = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path == "/stats":
            self.write_json(200, self.server.state.stats())
            return
        if parsed.path != "/upstream":
            self.write_json(404, {"code": 404})
            return
        parameters = urllib.parse.parse_qs(parsed.query)
        event_id = parameters.get("event_id", [""])[0]
        callback = parameters.get("callback", [""])[0]
        with self.server.state.lock:
            self.server.state.upstream_requests += 1
        if event_id and callback and should_convert(event_id):
            with self.server.state.lock:
                first = event_id not in self.server.state.callback_unique
                self.server.state.callback_unique.add(event_id)
            if first:
                threading.Thread(target=self.fire_duplicate_callback, args=(callback, event_id), daemon=True).start()
        self.write_json(200, {"code": 0})

    def fire_duplicate_callback(self, callback, event_id):
        parsed = urllib.parse.urlsplit(callback)
        parameters = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
        parameters.extend([
            ("action_type", "activate"),
            ("conversion_id", "conversion-" + event_id),
            ("event_time", str(int(time.time() * 1000))),
        ])
        target = urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, urllib.parse.urlencode(parameters), ""))
        for _ in range(2):
            with self.server.state.lock:
                self.server.state.callback_http_requests += 1
            try:
                with urllib.request.urlopen(target, timeout=5) as response:
                    if response.status == 200:
                        with self.server.state.lock:
                            self.server.state.callback_http_success += 1
            except (urllib.error.URLError, TimeoutError):
                pass
            time.sleep(0.05)

    def do_POST(self):
        parsed = urllib.parse.urlsplit(self.path)
        if parsed.path != "/postback":
            self.write_json(404, {"code": 404})
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        timestamp = self.headers.get("X-Timestamp", "")
        nonce = self.headers.get("X-Nonce", "")
        supplied = self.headers.get("X-Sign", "")
        body_hash = hashlib.sha256(body).hexdigest()
        canonical = f"POST\n{parsed.path}\n{timestamp}\n{nonce}\n{body_hash}".encode()
        expected = hmac.new(self.server.state.secret, canonical, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, supplied):
            with self.server.state.lock:
                self.server.state.invalid_signatures += 1
            self.write_json(401, {"code": 401})
            return
        idempotency_key = self.headers.get("Idempotency-Key", "missing")
        with self.server.state.lock:
            attempt = self.server.state.downstream_attempts.get(idempotency_key, 0) + 1
            self.server.state.downstream_attempts[idempotency_key] = attempt
            if attempt >= 3:
                self.server.state.downstream_success.add(idempotency_key)
        if attempt < 3:
            self.write_json(500, {"code": 500})
        else:
            self.write_json(200, {"code": 0})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--secret-file", required=True)
    parser.add_argument("--port", type=int, default=18081)
    args = parser.parse_args()
    with open(args.secret_file, "rb") as secret_input:
        secret = secret_input.read().strip()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.state = State(secret)
    server.serve_forever()


if __name__ == "__main__":
    main()

