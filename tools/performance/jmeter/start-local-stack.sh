#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
SECRET_FILE="${1:?usage: start-local-stack.sh CHANNEL_SECRET_FILE}"
COMPOSE=(docker-compose -p scx-ocpx-jmeter -f "${SCRIPT_DIR}/docker-compose.yml")

if [[ ! -f "$SECRET_FILE" ]]; then
  echo "Channel secret file does not exist." >&2
  exit 1
fi

existing="$(${COMPOSE[@]} ps -q 2>/dev/null || true)"
if [[ -n "$existing" ]]; then
  echo "The dedicated scx-ocpx-jmeter stack already exists; inspect it or run stop-local-stack.sh first." >&2
  exit 1
fi

for port in 13316 16389 16390 18081 19999; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Local isolation port ${port} is already in use; refusing to reuse an unknown service." >&2
    exit 1
  fi
done

"${COMPOSE[@]}" up -d

for _ in $(seq 1 90); do
  mysql_health="$(${COMPOSE[@]} ps --format json mysql 2>/dev/null | sed -n 's/.*"Health":"\([^"]*\)".*/\1/p')"
  primary_health="$(${COMPOSE[@]} ps --format json redis-primary 2>/dev/null | sed -n 's/.*"Health":"\([^"]*\)".*/\1/p')"
  replica_health="$(${COMPOSE[@]} ps --format json redis-replica 2>/dev/null | sed -n 's/.*"Health":"\([^"]*\)".*/\1/p')"
  if [[ "$mysql_health" == "healthy" && "$primary_health" == "healthy" && "$replica_health" == "healthy" ]]; then
    break
  fi
  sleep 2
done

if [[ "$mysql_health" != "healthy" || "$primary_health" != "healthy" || "$replica_health" != "healthy" ]]; then
  "${COMPOSE[@]}" ps >&2
  echo "The isolated dependency stack did not become healthy." >&2
  exit 1
fi

MYSQL_HOST=127.0.0.1 \
MYSQL_PORT=13316 \
MYSQL_USERNAME=root \
MYSQL_PASSWORD=local-jmeter-root-only \
  bash "${REPO_ROOT}/scx-backend/db/smart-converge-x/init-database.sh"

channel_secret="$(tr -d '\r\n' < "$SECRET_FILE")"
if [[ ! "$channel_secret" =~ ^[a-f0-9]{64}$ ]]; then
  echo "The generated channel secret must be 64 lowercase hexadecimal characters." >&2
  exit 1
fi
{
  printf "SET @scx_perf_channel_secret='%s';\n" "$channel_secret"
  sed -n '1,$p' "${SCRIPT_DIR}/fixture.sql"
} | MYSQL_PWD=local-jmeter-root-only mysql --host=127.0.0.1 --port=13316 --user=root smart_converge_x

echo "Isolated MySQL/Redis primary/replica stack and synthetic route are ready."
