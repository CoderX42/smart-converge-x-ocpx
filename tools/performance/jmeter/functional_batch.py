#!/usr/bin/env python3
import argparse
import concurrent.futures
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.request
import uuid


PATH = "/ocpx/open-api/ocpx/v1/channels/perf-channel/feedback"


def send(base_url, secret, body):
    raw = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    body_hash = hashlib.sha256(raw).hexdigest()
    canonical = f"POST\n{PATH}\n{timestamp}\n{nonce}\n{body_hash}".encode()
    signature = hmac.new(secret, canonical, hashlib.sha256).hexdigest()
    request = urllib.request.Request(base_url + PATH, data=raw, method="POST")
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
            return response.status
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:500]
        print(f"functional request rejected: http={error.code} body={detail}")
        return error.code


def click(prefix, name, **device):
    body = {
        "schema_version": "1.0",
        "event_id": f"{prefix}-{name}",
        "event": "click",
        "monitor_id": "perf_monitor_id",
        "clickid": f"click-{prefix}-{name}",
        "event_time": int(time.time() * 1000),
        "ip": "192.0.2.20",
        "ua": "SCX-Functional/1.0",
        "creative_id": "synthetic-creative",
    }
    body.update(device)
    return body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:19999")
    parser.add_argument("--secret-file", required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    with open(args.secret_file, "rb") as secret_input:
        secret = secret_input.read().strip()

    results = []

    def expect(case, body, expected):
        actual = send(args.base_url, secret, body)
        results.append({"case": case, "expected": expected, "actual": actual})
        if actual != expected:
            raise RuntimeError(f"{case}: expected HTTP {expected}, got {actual}")

    android_fields = ["oaid", "oaid_md5", "gaid", "aaid", "android_id", "android_idmd5", "imei", "imei_md5"]
    ios_fields = ["idfa", "idfa_md5", "caid", "idfv"]
    for index, field in enumerate(android_fields):
        expect(f"android-{field}", click(args.prefix, f"android-{index}", os="android", **{field: f"synthetic-{field}-{index}"}), 202)
    for index, field in enumerate(ios_fields):
        expect(f"ios-{field}", click(args.prefix, f"ios-{index}", os="ios", **{field: f"synthetic-{field}-{index}"}), 202)

    replay = click(args.prefix, "event-replay", os="android", oaid="synthetic-replay-device")
    expect("event-id-first", replay, 202)
    expect("event-id-same-payload", replay, 200)
    conflict = dict(replay)
    conflict["creative_id"] = "synthetic-conflict"
    expect("event-id-conflict", conflict, 409)

    race = [click(args.prefix, f"race-{index}", os="android", oaid="synthetic-race-device") for index in range(12)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
        statuses = list(executor.map(lambda body: send(args.base_url, secret, body), race))
    for index, status in enumerate(statuses):
        results.append({"case": f"device-window-race-{index}", "expected": 202, "actual": status})
    if any(status != 202 for status in statuses):
        raise RuntimeError(f"device window race returned {statuses}")

    expect("window-first", click(args.prefix, "window-first", os="ios", idfa="synthetic-expiry-device"), 202)
    time.sleep(3.2)
    expect("window-expired", click(args.prefix, "window-expired", os="ios", idfa="synthetic-expiry-device"), 202)
    expect("no-device", click(args.prefix, "no-device", os="android"), 202)

    with open(args.output, "w", encoding="utf-8") as output:
        json.dump({"prefix": args.prefix, "uniqueAccepted": 28, "results": results}, output, ensure_ascii=False, indent=2)
        output.write("\n")


if __name__ == "__main__":
    main()
