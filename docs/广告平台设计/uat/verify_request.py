#!/usr/bin/env python3
"""Verify an SCX OCPX HMAC request captured as raw bytes."""

import hashlib
import hmac
import os
import sys


def main():
    if len(sys.argv) != 7:
        raise SystemExit(
            "usage: verify_request.py METHOD RAW_PATH BODY_FILE "
            "X_TIMESTAMP X_NONCE X_SIGN"
        )
    secret = os.environ.get("SCX_CHANNEL_SECRET")
    if not secret:
        raise SystemExit("SCX_CHANNEL_SECRET is required")
    method, path, body_file, timestamp, nonce, actual = sys.argv[1:]
    with open(body_file, "rb") as stream:
        body = stream.read()
    digest = hashlib.sha256(body).hexdigest()
    canonical = "\n".join([method.upper(), path, timestamp, nonce, digest])
    expected = hmac.new(
        secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    if not hmac.compare_digest(expected, actual.lower()):
        raise SystemExit("signature mismatch")
    print("signature valid")


if __name__ == "__main__":
    main()
