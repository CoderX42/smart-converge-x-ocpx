#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

mysql_args=(
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

allowed_list="$(printf "'%s'," "${SCX_ALLOWED_TABLES[@]}")"
allowed_list="${allowed_list%,}"
expected_union="$(printf "SELECT '%s' AS table_name UNION ALL " "${SCX_ALLOWED_TABLES[@]}")"
expected_union="${expected_union% UNION ALL }"

missing="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT expected.table_name
FROM (${expected_union}) expected
LEFT JOIN information_schema.tables actual
  ON actual.table_schema='${MYSQL_DATABASE}'
 AND actual.table_type='BASE TABLE'
 AND actual.table_name=expected.table_name
WHERE actual.table_name IS NULL
ORDER BY expected.table_name;")"

unexpected="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT table_name
FROM information_schema.tables
WHERE table_schema='${MYSQL_DATABASE}'
  AND table_type='BASE TABLE'
  AND table_name NOT IN (${allowed_list})
ORDER BY table_name;")"

if [[ -n "$missing" ]]; then
  echo "Missing required tables:"
  printf '%s\n' "$missing"
fi

if [[ -n "$unexpected" ]]; then
  echo "Unexpected tables:"
  printf '%s\n' "$unexpected"
fi

if [[ -n "$missing" || -n "$unexpected" ]]; then
  exit 1
fi

echo "Database table inventory matches the current SCX manifest."
