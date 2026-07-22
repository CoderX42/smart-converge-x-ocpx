#!/usr/bin/env python3
import hashlib
import hmac
import os
import sys
import time
import uuid


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: sign_request.py METHOD RAW_PATH BODY_FILE")
    secret = os.environ.get("SCX_CHANNEL_SECRET")
    if not secret:
        raise SystemExit("SCX_CHANNEL_SECRET is required")
    method, path, body_file = sys.argv[1], sys.argv[2], sys.argv[3]
    body = open(body_file, "rb").read()
    timestamp = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    digest = hashlib.sha256(body).hexdigest()
    canonical = "\n".join([method.upper(), path, timestamp, nonce, digest])
    signature = hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    print(f"X-Timestamp: {timestamp}")
    print(f"X-Nonce: {nonce}")
    print(f"X-Sign: {signature}")


if __name__ == "__main__":
    main()
