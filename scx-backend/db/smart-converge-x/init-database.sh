#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"
SCX_SKIP_VERIFY="${SCX_SKIP_VERIFY:-false}"

if [[ "$MYSQL_DATABASE" != "smart_converge_x" ]]; then
  echo "Initialization SQL targets smart_converge_x; refusing database '$MYSQL_DATABASE'." >&2
  exit 1
fi

mysql_args=(
  --commands
  --raw
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client is required." >&2
  exit 1
fi

existing_table_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_type='BASE TABLE';")"

if [[ "$existing_table_count" -ne 0 ]]; then
  echo "Refusing initialization: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE} already contains ${existing_table_count} tables." >&2
  echo "Apply a reviewed forward-only delta explicitly; db/migration is never auto-executed." >&2
  exit 1
fi

(
  cd "$SCRIPT_DIR"
  mysql "${mysql_args[@]}" < smart_converge_x_baseline.sql
)

if [[ "$SCX_SKIP_VERIFY" != "true" ]]; then
  "$SCRIPT_DIR/verify-database.sh"
fi
