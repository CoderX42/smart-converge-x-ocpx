#!/usr/bin/env python3
import argparse
import hashlib
import hmac
import json
import socket
import time
import urllib.error
import urllib.request
import uuid


PATH = "/ocpx/open-api/ocpx/v1/channels/perf-channel/feedback"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--secret-file", required=True)
    parser.add_argument("--event-id", required=True)
    parser.add_argument("--device", default="synthetic-fault-device")
    parser.add_argument("--base-url", default="http://127.0.0.1:19999")
    args = parser.parse_args()
    with open(args.secret_file, "rb") as secret_input:
        secret = secret_input.read().strip()
    body = json.dumps({
        "schema_version": "1.0",
        "event_id": args.event_id,
        "event": "click",
        "monitor_id": "perf_monitor_id",
        "clickid": "click-" + args.event_id,
        "event_time": int(time.time() * 1000),
        "os": "android",
        "oaid": args.device,
    }, separators=(",", ":")).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    canonical = f"POST\n{PATH}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
    signature = hmac.new(secret, canonical, hashlib.sha256).hexdigest()
    request = urllib.request.Request(args.base_url + PATH, data=body, method="POST")
    for name, value in {
        "Content-Type": "application/json",
        "X-Client-Id": "perf-channel",
        "X-Key-Version": "1",
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
        "X-Sign": signature,
    }.items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            print(response.status)
    except urllib.error.HTTPError as error:
        print(error.code)
    except (urllib.error.URLError, TimeoutError, socket.timeout):
        print("CLIENT_ERROR")


if __name__ == "__main__":
    main()
