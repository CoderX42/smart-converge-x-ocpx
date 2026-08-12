#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"
mysql_args=(--default-character-set=utf8mb4 --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME")
if [[ -n "${MYSQL_PASSWORD:-}" ]]; then export MYSQL_PWD="$MYSQL_PASSWORD"; fi

scalar() { mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" --execute="$1"; }
fail() { echo "Database verification failed: $1" >&2; exit 1; }

table_list="$(printf "'%s'," "${SCX_REQUIRED_TABLES[@]}")"; table_list="${table_list%,}"
actual_count="$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (${table_list})")"
[[ "$actual_count" -eq "${#SCX_REQUIRED_TABLES[@]}" ]] || fail "required table manifest is incomplete"

business_tables="$(scalar "SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name),'') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_comment LIKE 'BUSINESS:%'")"
expected_business_tables='scx_ocpx_advertiser,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link'
[[ "$business_tables" == "$expected_business_tables" ]] || fail "the four business main tables are not exact"

ocpx_tables="$(scalar "SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name),'') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_ocpx\\_%'")"
expected_ocpx_tables='scx_ocpx_advertiser,scx_ocpx_flow_log,scx_ocpx_log_callback,scx_ocpx_log_feedback,scx_ocpx_log_feedback_day,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link,scx_ocpx_report_mat,scx_ocpx_report_mat_temp'
[[ "$ocpx_tables" == "$expected_ocpx_tables" ]] || fail "OCPX must contain four business tables and the legacy-compatible data layer"

debug_tables="$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_api_debug\\_%'")"
[[ "$debug_tables" -eq 0 ]] || fail "retired API debugger tables remain"

secret_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_advertiser','scx_ocpx_promotion_link','scx_ocpx_media','scx_ocpx_monitor_task','scx_ocpx_flow_log','scx_ocpx_log_feedback','scx_ocpx_log_feedback_day','scx_ocpx_log_callback')
 AND column_name REGEXP '(^|_)(account|password|secret)(_|$)'")"
[[ "$secret_columns" -eq 0 ]] || fail "OCPX tables expose account/password/secret columns"

invalid_link_owner="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task t JOIN scx_ocpx_promotion_link l ON l.id=t.promotion_link_id WHERE BINARY l.advertiser_id<>BINARY t.advertiser_id")"
[[ "$invalid_link_owner" -eq 0 ]] || fail "monitor task references a promotion link owned by another advertiser"

composite_fk="$(scalar "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_task' AND constraint_name='fk_ocpx_monitor_task_link_owner'")"
[[ "$composite_fk" -eq 1 ]] || fail "advertiser/promotion-link composite foreign key is missing"

retired_permissions="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND
 (permission LIKE 'adx:%' OR permission REGEXP '^ocpx:(channel|product|app-link|media-account|task|monitor|deduction|event|runtime|api-debug|scope|account):'
  OR permission IN ('ocpx:monitor-task:publish','ocpx:report:export','ocpx:advertiser:grant'))")"
[[ "$retired_permissions" -eq 0 ]] || fail "retired OCPX permissions remain active"

system_admin="$(scalar "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND u.del_flag='0' AND r.role_code='ROLE_ADMIN' AND r.del_flag='0'")"
[[ "$system_admin" -eq 1 ]] || fail "system administrator role mapping changed"

dangling_role_menu="$(scalar "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE m.id IS NULL")"
[[ "$dangling_role_menu" -eq 0 ]] || fail "dangling role-menu assignments found"

echo "Database verification passed: four OCPX business tables, legacy-compatible data layer and the system framework are valid."
