#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_DIR="${SCRIPT_DIR}/../../scx-boot/src/main/resources/db/migration"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="smart_converge_x"
SCX_SKIP_BASELINE="${SCX_SKIP_BASELINE:-false}"
SCX_MIGRATION_BASELINE_VERSION="${SCX_MIGRATION_BASELINE_VERSION:-0}"
SCX_MIGRATION_MAX_VERSION="${SCX_MIGRATION_MAX_VERSION:-999999}"
SCX_SKIP_VERIFY="${SCX_SKIP_VERIFY:-false}"

mysql_args=(
  --commands
  # Keep CHAR(9) delimiters intact for migration history parsing below.
  --raw
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

if [[ "$SCX_SKIP_BASELINE" != "true" ]]; then
  (
    cd "$SCRIPT_DIR"
    mysql "${mysql_args[@]}" < smart_converge_x_baseline.sql
  )
fi

mysql "${mysql_args[@]}" "$MYSQL_DATABASE" <<'SQL'
CREATE TABLE IF NOT EXISTS scx_schema_migration (
  version VARCHAR(32) NOT NULL,
  script_name VARCHAR(255) NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 本地数据库迁移历史';
SQL

if [[ "$SCX_MIGRATION_BASELINE_VERSION" == "auto" ]]; then
  migration_history_count="$(mysql "${mysql_args[@]}" "$MYSQL_DATABASE" --batch --skip-column-names \
    --execute="SELECT COUNT(*) FROM scx_schema_migration;")"
  if [[ "$migration_history_count" -eq 0 ]]; then
    full_schema_present="$(mysql "${mysql_args[@]}" "$MYSQL_DATABASE" --batch --skip-column-names \
      --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_rta_decision_log';")"
    v4_schema_present="$(mysql "${mysql_args[@]}" "$MYSQL_DATABASE" --batch --skip-column-names \
      --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_machine_nonce';")"
    if [[ "$full_schema_present" -gt 0 ]]; then
      SCX_MIGRATION_BASELINE_VERSION="$(find "${SCHEMA_DIR}" -maxdepth 1 -name 'V*.sql' -exec basename {} \; | sort -t_ -k1.2n | tail -n 1 | sed -E 's/^V([0-9]+)__.*/\1/')"
    elif [[ "$v4_schema_present" -gt 0 ]]; then
      SCX_MIGRATION_BASELINE_VERSION=4
    else
      SCX_MIGRATION_BASELINE_VERSION=0
    fi
    echo "Detected existing local schema; migration baseline is V${SCX_MIGRATION_BASELINE_VERSION}."
  else
    SCX_MIGRATION_BASELINE_VERSION=0
  fi
fi

if ! [[ "$SCX_MIGRATION_BASELINE_VERSION" =~ ^[0-9]+$ ]]; then
  echo "SCX_MIGRATION_BASELINE_VERSION must be a non-negative integer or auto." >&2
  exit 1
fi

if ! [[ "$SCX_MIGRATION_MAX_VERSION" =~ ^[0-9]+$ ]]; then
  echo "SCX_MIGRATION_MAX_VERSION must be a non-negative integer." >&2
  exit 1
fi

while IFS= read -r script; do
  [[ -e "$script" ]] || continue
  script_name="$(basename "$script")"
  version="${script_name%%__*}"
  version_number="${version#V}"
  if [[ "$version_number" -gt "$SCX_MIGRATION_MAX_VERSION" ]]; then
    continue
  fi
  checksum="$(shasum -a 256 "$script" | awk '{print $1}')"
  applied_record="$(mysql "${mysql_args[@]}" "$MYSQL_DATABASE" --batch --skip-column-names \
    --execute="SELECT CONCAT(script_name, CHAR(9), checksum) FROM scx_schema_migration WHERE version='${version}';")"

  if [[ -n "$applied_record" ]]; then
    IFS=$'\t' read -r applied_script_name applied_checksum <<< "$applied_record"
    if [[ "$applied_script_name" != "$script_name" || "$applied_checksum" != "$checksum" ]]; then
      echo "Migration history mismatch for ${version}; stop and investigate before continuing." >&2
      exit 1
    fi
    continue
  fi

  if [[ "$version_number" -le "$SCX_MIGRATION_BASELINE_VERSION" ]]; then
    echo "Baselining existing schema script: $script_name"
  else
    echo "Applying schema script: $script_name"
    mysql "${mysql_args[@]}" "$MYSQL_DATABASE" < "$script"
  fi

  mysql "${mysql_args[@]}" "$MYSQL_DATABASE" --execute="INSERT INTO scx_schema_migration (version, script_name, checksum) VALUES ('${version}', '${script_name}', '${checksum}')"
done < <(find "${SCHEMA_DIR}" -maxdepth 1 -type f -name 'V*.sql' -print | awk -F/ '{file=$NF; sub(/^V/, "", file); sub(/__.*/, "", file); print file "\t" $0}' | sort -n -k1,1 | cut -f2-)

if [[ "$SCX_SKIP_VERIFY" != "true" ]]; then
  "${SCRIPT_DIR}/verify-database.sh"
fi
