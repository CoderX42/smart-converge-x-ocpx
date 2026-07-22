#!/usr/bin/env bash
set -euo pipefail

if [[ "${SCX_CAPACITY_ACK:-}" != "isolated-disposable-environment" ]]; then
  echo "Refusing to run: set SCX_CAPACITY_ACK=isolated-disposable-environment after verifying the target." >&2
  exit 2
fi

required=(SCX_BASE_URL SCX_CHANNEL_CLIENT_ID SCX_CHANNEL_KEY_VERSION SCX_CHANNEL_SECRET SCX_TASK_CODE SCX_DEVICE_SEED SCX_DEDUP_DEVICE_HASH)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 2
  fi
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
run_id="$(date -u +%Y%m%dT%H%M%SZ)"
export SCX_RUN_ID="${SCX_RUN_ID:-$run_id}"
report_dir="${repo_root}/artifacts/perf/${run_id}"
mkdir -p "$report_dir"

{
  echo "run_id=${run_id}"
  echo "started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "docker_image=grafana/k6:0.49.0"
  echo "os=$(uname -srvmo)"
  echo "cpu=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || true)"
  echo "logical_cpu=$(sysctl -n hw.logicalcpu 2>/dev/null || true)"
  echo "memory_bytes=$(sysctl -n hw.memsize 2>/dev/null || true)"
  echo "target=${SCX_BASE_URL}"
} > "${report_dir}/environment.txt"

set +e
docker run --rm --network host \
  -e SCX_BASE_URL -e SCX_CHANNEL_CLIENT_ID -e SCX_CHANNEL_KEY_VERSION \
  -e SCX_CHANNEL_SECRET -e SCX_TASK_CODE -e SCX_DEVICE_SEED -e SCX_DEDUP_DEVICE_HASH \
  -e SCX_RUN_ID \
  -v "${repo_root}/scripts/perf:/scripts:ro" -v "${report_dir}:/reports" \
  grafana/k6:0.49.0 run --summary-export=/reports/summary.json /scripts/ocpx-20k.js \
  2>&1 | tee "${report_dir}/k6.log"
k6_status=${PIPESTATUS[0]}
set -e

echo "Capacity evidence saved to ${report_dir}"
exit "$k6_status"
