#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="${SCRIPT_DIR}/../../scx-boot/src/main/resources/db/migration"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="smart_converge_x"

mysql_args=(
  --commands
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

(
  cd "$SCRIPT_DIR"
  mysql "${mysql_args[@]}" < smart_converge_x_baseline.sql
)

mysql "${mysql_args[@]}" "$MYSQL_DATABASE" < "${MIGRATION_DIR}/V1__subject_advertiser_read_model.sql"
mysql "${mysql_args[@]}" "$MYSQL_DATABASE" < "${MIGRATION_DIR}/V2__subject_media_read_model.sql"

"${SCRIPT_DIR}/verify-database.sh"
