#!/usr/bin/env python3
import hashlib
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


CLIENT_ID = os.environ.get("SCX_CALLBACK_CLIENT_ID", "")
KEY_VERSION = os.environ.get("SCX_CALLBACK_KEY_VERSION", "")
SECRET = os.environ.get("SCX_CALLBACK_SECRET", "")
SEEN = set()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        timestamp = self.headers.get("X-Timestamp", "")
        nonce = self.headers.get("X-Nonce", "")
        canonical = "\n".join([
            "POST", self.path, timestamp, nonce, hashlib.sha256(body).hexdigest()
        ])
        expected = hmac.new(SECRET.encode(), canonical.encode(), hashlib.sha256).hexdigest()
        valid = (
            self.path == "/postback"
            and self.headers.get("X-Client-Id") == CLIENT_ID
            and self.headers.get("X-Key-Version") == KEY_VERSION
            and hmac.compare_digest(self.headers.get("X-Sign", ""), expected)
            and bool(self.headers.get("Idempotency-Key"))
        )
        if not valid:
            self.respond(401, {"code": 401, "message": "signature rejected"})
            return
        SEEN.add(self.headers["Idempotency-Key"])
        self.respond(200, {"code": 0, "message": "accepted"})

    def respond(self, status, value):
        payload = json.dumps(value, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        print("callback", self.command, self.path, self.response_code if hasattr(self, "response_code") else "")


if __name__ == "__main__":
    if not all([CLIENT_ID, KEY_VERSION, SECRET]):
        raise SystemExit("SCX_CALLBACK_CLIENT_ID, SCX_CALLBACK_KEY_VERSION and SCX_CALLBACK_SECRET are required")
    port = int(os.environ.get("SCX_CALLBACK_PORT", "18080"))
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
