#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
run_id="${BASHPID:-$$}-$(date +%s)"
container="scx-redis-recovery-${run_id}"
evidence_dir="${SCX_RECOVERY_EVIDENCE_DIR:-${repo_root}/artifacts/recovery}"
evidence_file="${evidence_dir}/redis-${run_id}.json"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }
command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 2; }

cleanup() {
  [[ "$container" == scx-redis-recovery-* ]] || return
  docker rm -f "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "$container" -p 127.0.0.1::6379 redis:7-alpine \
  redis-server --appendonly yes >/dev/null
for _ in {1..30}; do
  if docker exec "$container" redis-cli ping 2>/dev/null | grep -q PONG; then break; fi
  sleep 1
done
docker exec "$container" redis-cli ping 2>/dev/null | grep -q PONG \
  || { echo "Redis container did not become ready" >&2; exit 1; }
port="$(docker port "$container" 6379/tcp | awk -F: '{print $NF}')"
[[ "$port" =~ ^[0-9]+$ ]] || { echo "Failed to resolve mapped Redis port" >&2; exit 1; }

(
  cd "${repo_root}/scx-backend"
  SCX_REDIS_IT_PORT="$port" mvn -pl scx-tracking -am \
    -Dtest='RedisFeedbackBufferIntegrationTest,RedisRtaDecisionLogBufferIntegrationTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
)

docker exec "$container" redis-cli -n 13 XGROUP CREATE scx:recovery:stream scx-recovery 0 MKSTREAM >/dev/null
record_id="$(docker exec "$container" redis-cli -n 13 XADD scx:recovery:stream '*' payload durable-before-restart)"
docker exec "$container" redis-cli -n 13 XREADGROUP GROUP scx-recovery worker-before-restart \
  COUNT 1 STREAMS scx:recovery:stream '>' >/dev/null
before="$(docker exec "$container" redis-cli -n 13 XPENDING scx:recovery:stream scx-recovery | head -1)"
[[ "$before" == "1" ]] || { echo "expected one Pending entry before restart" >&2; exit 1; }

docker stop "$container" >/dev/null
docker start "$container" >/dev/null
for _ in {1..30}; do
  if docker exec "$container" redis-cli ping 2>/dev/null | grep -q PONG; then break; fi
  sleep 1
done
docker exec "$container" redis-cli ping 2>/dev/null | grep -q PONG \
  || { echo "Redis container did not recover after restart" >&2; exit 1; }
after="$(docker exec "$container" redis-cli -n 13 XPENDING scx:recovery:stream scx-recovery | head -1)"
[[ "$after" == "1" ]] || { echo "Pending entry was not preserved across Redis restart" >&2; exit 1; }
docker exec "$container" redis-cli -n 13 XAUTOCLAIM scx:recovery:stream scx-recovery \
  worker-after-restart 0 0-0 COUNT 1 >/dev/null
docker exec "$container" redis-cli -n 13 XACK scx:recovery:stream scx-recovery "$record_id" >/dev/null
remaining="$(docker exec "$container" redis-cli -n 13 XPENDING scx:recovery:stream scx-recovery | head -1)"
[[ "$remaining" == "0" ]] || { echo "Pending entry was not ACKed after recovery" >&2; exit 1; }

echo "Redis recovery verification passed: integration tests, persisted Pending, restart, reclaim and ACK."
mkdir -p "$evidence_dir"
jq -n \
  --arg runId "$run_id" \
  --arg container "$container" \
  '{runId:$runId,container:$container,checks:{pendingPersisted:true,xautoClaim:true,ack:true},
    secretsPersisted:false,result:"passed"}' \
  > "$evidence_file"
chmod 0640 "$evidence_file"
echo "Evidence metadata: $evidence_file"
