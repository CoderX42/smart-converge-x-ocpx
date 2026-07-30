#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="${SCRIPT_DIR}/../../scx-boot/src/main/resources/db/migration"
source "${SCRIPT_DIR}/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"

apply_mode=false
assume_yes=false
prompt_password=false

usage() {
  cat <<'EOF'
Usage:
  ./reconcile-missing-tables.sh [options]

Default behavior is read-only: connect to MySQL 8, scan the canonical SCX
application-table manifest, and print every missing table plus its DDL source.

Options:
  --apply             Create missing tables only. Does not replay ALTER/DML or
                      modify scx_schema_migration.
  --yes               Skip the interactive APPLY confirmation (requires --apply).
  --host HOST         MySQL host (default: MYSQL_HOST or 127.0.0.1).
  --port PORT         MySQL port (default: MYSQL_PORT or 3306).
  --user USER         MySQL user (default: MYSQL_USERNAME or root).
  --database NAME     Database (default: MYSQL_DATABASE or smart_converge_x).
  --prompt-password   Read the MySQL password without echoing it.
  -h, --help          Show this help.

Password:
  Prefer MYSQL_PASSWORD in the environment, or use --prompt-password. The
  password is passed through MYSQL_PWD and is never printed or added to the
  command line.

Examples:
  MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD='***' \
    ./reconcile-missing-tables.sh

  MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD='***' \
    ./reconcile-missing-tables.sh --apply
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      apply_mode=true
      shift
      ;;
    --yes)
      assume_yes=true
      shift
      ;;
    --host)
      MYSQL_HOST="${2:?--host requires a value}"
      shift 2
      ;;
    --port)
      MYSQL_PORT="${2:?--port requires a value}"
      shift 2
      ;;
    --user)
      MYSQL_USERNAME="${2:?--user requires a value}"
      shift 2
      ;;
    --database)
      MYSQL_DATABASE="${2:?--database requires a value}"
      shift 2
      ;;
    --prompt-password)
      prompt_password=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if $assume_yes && ! $apply_mode; then
  echo "--yes requires --apply." >&2
  exit 2
fi

if ! [[ "$MYSQL_PORT" =~ ^[0-9]+$ ]] || [[ "$MYSQL_PORT" -lt 1 ]] || [[ "$MYSQL_PORT" -gt 65535 ]]; then
  echo "Invalid MySQL port: $MYSQL_PORT" >&2
  exit 2
fi

if ! [[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "Invalid MySQL database name: $MYSQL_DATABASE" >&2
  exit 2
fi

if $prompt_password; then
  read -r -s -p "MySQL password for ${MYSQL_USERNAME}@${MYSQL_HOST}: " MYSQL_PASSWORD
  printf '\n'
fi

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

mysql_args=(
  --commands
  --raw
  --default-character-set=utf8mb4
  --connect-timeout=10
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

for required_command in mysql awk grep find sort mktemp; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "Required command is unavailable: $required_command" >&2
    exit 1
  fi
done

for required_path in \
  "${SCRIPT_DIR}/10smart_converge_x_core.sql" \
  "${SCRIPT_DIR}/schema-required-tables.sh" \
  "$MIGRATION_DIR"; do
  if [[ ! -e "$required_path" ]]; then
    echo "Required schema source is unavailable: $required_path" >&2
    exit 1
  fi
done

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/scx-table-reconcile.XXXXXX")"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

existing_tables_file="${work_dir}/existing-tables.txt"
missing_tables_file="${work_dir}/missing-tables.txt"
repair_plan_file="${work_dir}/repair-missing-tables.sql"

schema_files=("${SCRIPT_DIR}/10smart_converge_x_core.sql")
while IFS= read -r migration_file; do
  schema_files+=("$migration_file")
done < <(
  find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' -print \
    | awk -F/ '{
        file=$NF;
        version=file;
        sub(/^V/, "", version);
        sub(/__.*/, "", version);
        print version "\t" $0;
      }' \
    | sort -n -k1,1 \
    | cut -f2-
)

contains_standard_create() {
  local sql_file="$1"
  local table_name="$2"
  awk -v target="$table_name" '
    {
      normalized=tolower($0);
      gsub(/`/, "", normalized);
      pattern="^[[:space:]]*create[[:space:]]+table[[:space:]]+(if[[:space:]]+not[[:space:]]+exists[[:space:]]+)?" target "([[:space:](]|$)";
      if (normalized ~ pattern) {
        found=1;
        exit;
      }
    }
    END { exit(found ? 0 : 1); }
  ' "$sql_file"
}

find_latest_source() {
  local table_name="$1"
  local sql_file
  local source_file=""

  for sql_file in "${schema_files[@]}"; do
    if contains_standard_create "$sql_file" "$table_name"; then
      source_file="$sql_file"
    fi
  done

  printf '%s' "$source_file"
}

extract_create_statement() {
  local sql_file="$1"
  local table_name="$2"
  awk -v target="$table_name" '
    BEGIN {
      capture=0;
      complete=0;
    }
    {
      if (!capture) {
        normalized=tolower($0);
        gsub(/`/, "", normalized);
        pattern="^[[:space:]]*create[[:space:]]+table[[:space:]]+(if[[:space:]]+not[[:space:]]+exists[[:space:]]+)?" target "([[:space:](]|$)";
        if (normalized ~ pattern) {
          capture=1;
        }
      }
      if (capture) {
        print;
        if ($0 ~ /;[[:space:]]*$/) {
          complete=1;
          exit;
        }
      }
    }
    END {
      if (!complete) {
        exit 1;
      }
    }
  ' "$sql_file"
}

emit_dynamic_create_statement() {
  local table_name="$1"
  case "$table_name" in
    scx_media)
      cat <<'SQL'
CREATE TABLE IF NOT EXISTS `scx_media` (
  `id` VARCHAR(64) NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  `settlement_currency` VARCHAR(8) NOT NULL DEFAULT 'CNY',
  `version` INT NOT NULL DEFAULT 0,
  `create_by` VARCHAR(64) NULL,
  `update_by` VARCHAR(64) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scx_media_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'SCX 媒体安全主体';
SQL
      ;;
    scx_user_subject_scope)
      cat <<'SQL'
CREATE TABLE IF NOT EXISTS `scx_user_subject_scope` (
  `id` VARCHAR(64) NOT NULL,
  `user_id` VARCHAR(64) NOT NULL,
  `subject_type` VARCHAR(32) NOT NULL,
  `subject_id` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scx_user_subject` (`user_id`, `subject_type`, `subject_id`),
  KEY `idx_scx_scope_subject` (`subject_type`, `subject_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'SCX 用户对象授权';
SQL
      ;;
    *)
      return 1
      ;;
  esac
}

source_label_for_table() {
  local table_name="$1"
  local source_file
  if [[ "$table_name" == "scx_media" || "$table_name" == "scx_user_subject_scope" ]]; then
    printf '%s' "V1__scx_mvp_schema.sql (normalized direct DDL)"
    return
  fi
  source_file="$(find_latest_source "$table_name")"
  if [[ -z "$source_file" ]]; then
    return 1
  fi
  basename "$source_file"
}

verify_all_sources() {
  local table_name
  local missing_source=false
  for table_name in "${SCX_REQUIRED_TABLES[@]}"; do
    if ! source_label_for_table "$table_name" >/dev/null; then
      echo "No CREATE TABLE source found for required table: $table_name" >&2
      missing_source=true
    fi
  done
  if $missing_source; then
    exit 1
  fi
}

scan_missing_tables() {
  local table_list
  local table_name

  table_list="$(printf "'%s'," "${SCX_REQUIRED_TABLES[@]}")"
  table_list="${table_list%,}"

  mysql "${mysql_args[@]}" --batch --skip-column-names \
    --execute="SELECT table_name FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${table_list}) ORDER BY table_name;" \
    > "$existing_tables_file"

  : > "$missing_tables_file"
  for table_name in "${SCX_REQUIRED_TABLES[@]}"; do
    if ! grep -Fxq "$table_name" "$existing_tables_file"; then
      printf '%s\n' "$table_name" >> "$missing_tables_file"
    fi
  done
}

build_repair_plan() {
  local table_name
  local source_file

  {
    echo "SET NAMES utf8mb4;"
    echo "USE \`${MYSQL_DATABASE}\`;"
    echo
  } > "$repair_plan_file"

  while IFS= read -r table_name; do
    [[ -n "$table_name" ]] || continue
    printf -- '-- Repair missing table: %s\n' "$table_name" >> "$repair_plan_file"
    if emit_dynamic_create_statement "$table_name" >> "$repair_plan_file"; then
      :
    else
      source_file="$(find_latest_source "$table_name")"
      if [[ -z "$source_file" ]]; then
        echo "No CREATE TABLE source found for missing table: $table_name" >&2
        exit 1
      fi
      extract_create_statement "$source_file" "$table_name" >> "$repair_plan_file"
    fi
    printf '\n' >> "$repair_plan_file"
  done < "$missing_tables_file"
}

verify_all_sources

server_version="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT VERSION();")"
if [[ "$server_version" != 8.* ]]; then
  echo "Refusing to continue: MySQL 8 is required, server reports ${server_version}." >&2
  exit 1
fi

selected_database="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT DATABASE();")"
if [[ "$selected_database" != "$MYSQL_DATABASE" ]]; then
  echo "Database selection mismatch: expected ${MYSQL_DATABASE}, got ${selected_database:-<empty>}." >&2
  exit 1
fi

echo "Target: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE} (MySQL ${server_version})"
echo "Canonical required tables: ${#SCX_REQUIRED_TABLES[@]}"

scan_missing_tables
missing_count="$(wc -l < "$missing_tables_file" | tr -d '[:space:]')"

if [[ "$missing_count" -eq 0 ]]; then
  echo "No required SCX application tables are missing."
  exit 0
fi

echo "Missing required tables: ${missing_count}"
while IFS= read -r missing_table; do
  [[ -n "$missing_table" ]] || continue
  printf '  - %s <- %s\n' "$missing_table" "$(source_label_for_table "$missing_table")"
done < "$missing_tables_file"

build_repair_plan

if ! $apply_mode; then
  echo "Audit only; no database changes were made."
  echo "Re-run with --apply after taking a production backup and reviewing the list."
  exit 3
fi

if ! $assume_yes; then
  if [[ ! -t 0 ]]; then
    echo "Interactive confirmation is unavailable; add --yes to the explicit --apply command." >&2
    exit 2
  fi
  read -r -p "Type APPLY to create the ${missing_count} missing tables: " confirmation
  if [[ "$confirmation" != "APPLY" ]]; then
    echo "Cancelled; no database changes were made."
    exit 2
  fi
fi

echo "Applying missing-table DDL only..."
if ! mysql "${mysql_args[@]}" "$MYSQL_DATABASE" < "$repair_plan_file"; then
  echo "The MySQL connection failed while applying table DDL." >&2
  echo "Each CREATE TABLE statement is idempotent; rerun this script after the connection is stable." >&2
  exit 1
fi

scan_missing_tables
remaining_count="$(wc -l < "$missing_tables_file" | tr -d '[:space:]')"
if [[ "$remaining_count" -ne 0 ]]; then
  echo "Repair incomplete; ${remaining_count} required tables are still missing:" >&2
  sed 's/^/  - /' "$missing_tables_file" >&2
  echo "The script is safe to rerun after the connection is stable." >&2
  exit 1
fi

echo "All ${#SCX_REQUIRED_TABLES[@]} required SCX application tables now exist."
echo "Running the existing database verification gate..."
"${SCRIPT_DIR}/verify-database.sh"
echo "Missing-table reconciliation completed."
