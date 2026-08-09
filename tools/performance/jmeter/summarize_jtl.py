#!/usr/bin/env python3
import argparse
import csv
import json
import math


def percentile(values, fraction):
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jtl")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    elapsed = []
    timestamps = []
    statuses = {}
    failures = 0
    with open(args.jtl, newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row.get("label") != "POST FeedbackReqV1":
                continue
            elapsed.append(int(row["elapsed"]))
            timestamps.append(int(row["timeStamp"]))
            code = row.get("responseCode", "CLIENT_ERROR")
            statuses[code] = statuses.get(code, 0) + 1
            if row.get("success", "false").lower() != "true":
                failures += 1
    duration = max(0.001, (max(timestamps) - min(timestamps)) / 1000) if len(timestamps) > 1 else 0.001
    summary = {
        "requests": len(elapsed),
        "statuses": statuses,
        "failures": failures,
        "measuredSeconds": round(duration, 3),
        "measuredQps": round(len(elapsed) / duration, 3),
        "p50Ms": percentile(elapsed, 0.50),
        "p95Ms": percentile(elapsed, 0.95),
        "p99Ms": percentile(elapsed, 0.99),
        "maxMs": max(elapsed) if elapsed else 0,
    }
    with open(args.output, "w", encoding="utf-8") as target:
        json.dump(summary, target, indent=2, sort_keys=True)
        target.write("\n")
    print(json.dumps(summary, sort_keys=True))
    if failures or any(code != "202" for code in statuses):
        raise SystemExit(1)


if __name__ == "__main__":
    main()

