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
expected_business_tables='scx_ocpx_advertiser,scx_ocpx_deduction_rule,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link'
[[ "$business_tables" == "$expected_business_tables" ]] || fail "the five business main tables are not exact"

ocpx_tables="$(scalar "SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name),'') FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_ocpx\\_%'")"
expected_ocpx_tables='scx_ocpx_advertiser,scx_ocpx_deduction_rule,scx_ocpx_flow_log,scx_ocpx_log_callback,scx_ocpx_log_feedback,scx_ocpx_log_feedback_day,scx_ocpx_media,scx_ocpx_monitor_task,scx_ocpx_promotion_link,scx_ocpx_report_mat,scx_ocpx_report_mat_temp'
[[ "$ocpx_tables" == "$expected_ocpx_tables" ]] || fail "OCPX must contain five business tables and the legacy-compatible data layer"

assert_exact_columns() {
  local table="$1" expected="$2" actual
  actual="$(scalar "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='${table}'")"
  [[ "$actual" == "$expected" ]] || fail "${table} columns do not match the current management model"
}

assert_exact_columns scx_ocpx_advertiser 'id,advertiser_name,account_user_id,credential_config_key,bid_url,macro_params,create_time,update_time'
assert_exact_columns scx_ocpx_promotion_link 'id,advertiser_id,link_name,platform,link_channel,link_url,deeplink,link_ext,remark,is_delete,create_time,update_time'
assert_exact_columns scx_ocpx_media 'id,media_name,credential_config_key,uri,macro_params,create_time,update_time'
assert_exact_columns scx_ocpx_monitor_task 'id,monitor_name,advertiser_id,promotion_link_id,media_id,unit_price,biz_id,show_url,clk_url,remark,is_delete,create_time,update_time'
assert_exact_columns scx_ocpx_deduction_rule 'id,promotion_link_id,media_id,ratio,is_delete,create_time,update_time'

advertiser_account_column="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND column_name='account_user_id' AND data_type='varchar'
 AND character_maximum_length=64 AND is_nullable='YES'")"
[[ "$advertiser_account_column" -eq 1 ]] || fail "advertiser account_user_id must be nullable VARCHAR(64)"

advertiser_account_unique="$(scalar "SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND index_name='uk_ocpx_advertiser_account_user' AND non_unique=0
 AND column_name='account_user_id'")"
[[ "$advertiser_account_unique" -eq 1 ]] || fail "advertiser account_user_id unique index is missing"

advertiser_account_fk="$(scalar "SELECT COUNT(*) FROM information_schema.key_column_usage
 WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_advertiser'
 AND constraint_name='fk_ocpx_advertiser_account_user'
 AND column_name='account_user_id' AND referenced_table_name='sys_user'
 AND referenced_column_name='id'")"
[[ "$advertiser_account_fk" -eq 1 ]] || fail "advertiser account_user_id foreign key is missing"

monitor_text_urls="$(scalar "SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_task'
 AND column_name IN ('show_url','clk_url') AND data_type='text'")"
[[ "$monitor_text_urls" -eq 2 ]] || fail "monitor show_url and clk_url must both be TEXT"

invalid_platforms="$(scalar "SELECT COUNT(*) FROM scx_ocpx_promotion_link WHERE platform NOT IN (1,2,3,4)")"
[[ "$invalid_platforms" -eq 0 ]] || fail "promotion-link platform must be one of 1 Android, 2 iOS, 3 Android+iOS, 4 quick app"

deduction_unique_parts="$(scalar "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule' AND index_name='uk_ocpx_deduction_rule_route'")"
deduction_function_part="$(scalar "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule' AND index_name='uk_ocpx_deduction_rule_route' AND expression IS NOT NULL")"
[[ "$deduction_unique_parts" -eq 3 && "$deduction_function_part" -eq 1 ]] || fail "deduction route functional unique index is missing"

route_table_list="$(printf "'%s'," "${SCX_OCPX_ROUTE_TABLES[@]}")"; route_table_list="${route_table_list%,}"
route_column_list="$(printf "'%s'," "${SCX_OCPX_ROUTE_COLUMNS[@]}")"; route_column_list="${route_column_list%,}"
retired_route_column_list="$(printf "'%s'," "${SCX_OCPX_RETIRED_ROUTE_COLUMNS[@]}")"; retired_route_column_list="${retired_route_column_list%,}"
route_column_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${route_column_list})")"
expected_route_column_count=$(( ${#SCX_OCPX_ROUTE_TABLES[@]} * ${#SCX_OCPX_ROUTE_COLUMNS[@]} ))
[[ "$route_column_count" -eq "$expected_route_column_count" ]] || fail "OCPX route ID columns m_id/t_id/a_id/l_id are incomplete"
retired_route_column_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${retired_route_column_list})")"
[[ "$retired_route_column_count" -eq 0 ]] || fail "retired OCPX route ID columns remain"
route_comment_count="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN (${route_table_list}) AND column_name IN (${route_column_list}) AND column_comment<>''")"
[[ "$route_comment_count" -eq "$expected_route_column_count" ]] || fail "OCPX route ID column comments are incomplete"

debug_tables="$(scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'scx_api_debug\\_%'")"
[[ "$debug_tables" -eq 0 ]] || fail "retired API debugger tables remain"

secret_columns="$(scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name IN ('scx_ocpx_advertiser','scx_ocpx_promotion_link','scx_ocpx_media','scx_ocpx_monitor_task','scx_ocpx_deduction_rule','scx_ocpx_flow_log','scx_ocpx_log_feedback','scx_ocpx_log_feedback_day','scx_ocpx_log_callback')
 AND column_name REGEXP '(^|_)(account|password|secret)(_|$)'
 AND NOT (table_name='scx_ocpx_advertiser' AND column_name='account_user_id')")"
[[ "$secret_columns" -eq 0 ]] || fail "OCPX tables expose account/password/secret columns"

invalid_advertiser_accounts="$(scalar "SELECT COUNT(*)
 FROM scx_ocpx_advertiser a
 LEFT JOIN sys_user u ON u.id=a.account_user_id
 WHERE a.account_user_id IS NOT NULL
 AND (u.id IS NULL OR u.type<>'2' OR u.status<>'0' OR u.del_flag<>'0'
      OR u.shop_id IS NULL OR BINARY u.shop_id<>BINARY a.id)")"
[[ "$invalid_advertiser_accounts" -eq 0 ]] || fail "advertiser account bindings must reference active type-2 users scoped to the advertiser"

invalid_link_owner="$(scalar "SELECT COUNT(*) FROM scx_ocpx_monitor_task t JOIN scx_ocpx_promotion_link l ON l.id=t.promotion_link_id WHERE BINARY l.advertiser_id<>BINARY t.advertiser_id")"
[[ "$invalid_link_owner" -eq 0 ]] || fail "monitor task references a promotion link owned by another advertiser"

composite_fk="$(scalar "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_task' AND constraint_name='fk_ocpx_monitor_task_link_owner'")"
[[ "$composite_fk" -eq 1 ]] || fail "advertiser/promotion-link composite foreign key is missing"

retired_permissions="$(scalar "SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND
 (permission LIKE CONCAT('ad','x:%') OR permission REGEXP '^ocpx:(channel|product|app-link|media-account|task|monitor|deduction|event|runtime|api-debug|scope|account):'
  OR permission IN (
    'ocpx:monitor-task:publish','ocpx:advertiser:grant',
    'ocpx:advertiser:status','ocpx:advertiser:archive',
    'ocpx:promotion-link:status','ocpx:promotion-link:archive','ocpx:promotion-link:validate',
    'ocpx:media:status','ocpx:media:archive','ocpx:media:preflight','ocpx:media:connectivity','ocpx:media:export',
    'ocpx:monitor-task:status','ocpx:monitor-task:archive'
  ))")"
[[ "$retired_permissions" -eq 0 ]] || fail "retired OCPX permissions remain active"

required_permissions="$(scalar "SELECT COUNT(DISTINCT permission) FROM sys_menu WHERE del_flag='0' AND permission IN ('ocpx:advertiser:recharge','ocpx:promotion-link:delete','ocpx:monitor-task:delete','ocpx:report:export')")"
[[ "$required_permissions" -eq 4 ]] || fail "required recharge, delete and report-export permissions are incomplete"

advertiser_role="$(scalar "SELECT COUNT(*) FROM sys_role
 WHERE BINARY id=BINARY 'role_advertiser'
 AND BINARY role_code=BINARY 'ROLE_ADVERTISER'
 AND type='0' AND shop_id IS NULL AND del_flag='0'")"
[[ "$advertiser_role" -eq 1 ]] || fail "the protected shared ROLE_ADVERTISER role is missing or invalid"

advertiser_role_menus="$(scalar "SELECT COALESCE(GROUP_CONCAT(menu_id ORDER BY menu_id),'')
 FROM sys_role_menu WHERE role_id='role_advertiser'")"
expected_advertiser_role_menus='menu_ocpx_advertiser,menu_ocpx_flow_log,menu_ocpx_home,menu_ocpx_monitor_task,menu_ocpx_promotion_link,menu_ocpx_report,perm_ocpx_report_export'
[[ "$advertiser_role_menus" == "$expected_advertiser_role_menus" ]] || fail "ROLE_ADVERTISER must have exactly six read-only pages and report export"

admin_report_export="$(scalar "SELECT COUNT(*)
 FROM sys_role r
 JOIN sys_role_menu rm ON rm.role_id=r.id
 JOIN sys_menu m ON m.id=rm.menu_id
 WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0'
 AND m.permission='ocpx:report:export' AND m.del_flag='0'")"
[[ "$admin_report_export" -eq 1 ]] || fail "system administrator report-export grant is missing"

system_admin="$(scalar "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND u.del_flag='0' AND r.role_code='ROLE_ADMIN' AND r.del_flag='0'")"
[[ "$system_admin" -eq 1 ]] || fail "system administrator role mapping changed"

dangling_role_menu="$(scalar "SELECT COUNT(*) FROM sys_role_menu rm LEFT JOIN sys_menu m ON m.id=rm.menu_id WHERE m.id IS NULL")"
[[ "$dangling_role_menu" -eq 0 ]] || fail "dangling role-menu assignments found"

echo "Database verification passed: five OCPX business tables, legacy-compatible data layer and the system framework are valid."
