#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/schema-required-tables.sh"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USERNAME="${MYSQL_USERNAME:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-smart_converge_x}"

mysql_args=(
  --default-character-set=utf8mb4
  --host="$MYSQL_HOST"
  --port="$MYSQL_PORT"
  --user="$MYSQL_USERNAME"
)

if [[ -n "${MYSQL_PASSWORD:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
fi

table_list="$(printf "'%s'," "${SCX_REQUIRED_TABLES[@]}")"
table_list="${table_list%,}"
xxl_table_list="$(printf "'%s'," "${SCX_XXL_JOB_TABLES[@]}")"
xxl_table_list="${xxl_table_list%,}"
allowed_table_list="$(printf "'%s'," "${SCX_ALLOWED_TABLES[@]}")"
allowed_table_list="${allowed_table_list%,}"

actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${table_list});")"

if [[ "$actual_count" -ne "${#SCX_REQUIRED_TABLES[@]}" ]]; then
  echo "Database verification failed: expected ${#SCX_REQUIRED_TABLES[@]} required tables, found $actual_count." >&2
  exit 1
fi

xxl_actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${xxl_table_list});")"

if [[ "$xxl_actual_count" -ne "${#SCX_XXL_JOB_TABLES[@]}" ]]; then
  echo "Database verification failed: expected ${#SCX_XXL_JOB_TABLES[@]} XXL-JOB tables, found $xxl_actual_count." >&2
  exit 1
fi

unexpected_tables="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COALESCE(GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ','),'') FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_type='BASE TABLE' AND table_name NOT IN (${allowed_table_list});")"

if [[ -n "$unexpected_tables" ]]; then
  echo "Database verification failed: unexpected tables: $unexpected_tables" >&2
  exit 1
fi

admin_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND r.role_code='ROLE_ADMIN' AND u.del_flag='0';")"

if [[ "$admin_count" -ne 1 ]]; then
  echo "Database verification failed: default administrator role mapping is missing." >&2
  exit 1
fi

system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND ((id='menu_ocpx_system' AND name='系统管理') OR (parent_id='menu_ocpx_system' AND id IN ('menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log','menu_adx_event')));")"

if [[ "$system_menu_count" -ne 10 ]]; then
  echo "Database verification failed: original system-management menu tree is incomplete." >&2
  exit 1
fi

admin_system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.id) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.id IN ('menu_ocpx_system','menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log','menu_adx_event');")"

if [[ "$admin_system_menu_count" -ne 10 ]]; then
  echo "Database verification failed: administrator system-management menu grants are incomplete." >&2
  exit 1
fi

admin_system_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('system:sysuser:page','system:sysrole:page','system:sysmenu:page','system:sysdept:page','system:sysdict:page','system:storageconfig:page','system:syslog:page','system:sysloginlog:page','adx:event:read');")"

if [[ "$admin_system_permission_count" -ne 9 ]]; then
  echo "Database verification failed: administrator system-management RBAC permissions are incomplete." >&2
  exit 1
fi

adx_scope_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_adx_user_scope' AND column_name IN ('user_id','scope_type','scope_id','permission_level','valid_from','valid_to','status','version','deleted','active_scope_key')) OR (table_name='scx_adx_user_scope_revision' AND column_name IN ('user_id','version','update_by','update_time')));")"

if [[ "$adx_scope_column_count" -ne 14 ]]; then
  echo "Database verification failed: ADX object-scope schema is incomplete." >&2
  exit 1
fi

admin_scope_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('adx:scope:view','adx:scope:edit');")"

if [[ "$admin_scope_permission_count" -ne 2 ]]; then
  echo "Database verification failed: administrator ADX object-scope permissions are incomplete." >&2
  exit 1
fi

active_scope_administrator_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT s.user_id) FROM scx_adx_user_scope s JOIN sys_user u ON u.id=s.user_id WHERE u.del_flag='0' AND s.deleted=0 AND s.status='ACTIVE' AND s.scope_type='ALL' AND s.scope_id IS NULL AND s.permission_level='MANAGE' AND (s.valid_from IS NULL OR s.valid_from<=NOW(3)) AND (s.valid_to IS NULL OR s.valid_to>NOW(3));")"

if [[ "$active_scope_administrator_count" -lt 1 ]]; then
  echo "Database verification failed: at least one active ADX scope administrator is required." >&2
  exit 1
fi

invalid_scope_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_adx_user_scope WHERE deleted=0 AND (scope_type NOT IN ('ALL','ADVERTISER','CHANNEL','MEDIA_ACCOUNT') OR permission_level NOT IN ('VIEW','OPERATE','MANAGE') OR (scope_type='ALL' AND scope_id IS NOT NULL) OR (scope_type<>'ALL' AND scope_id IS NULL) OR (valid_from IS NOT NULL AND valid_to IS NOT NULL AND valid_from>=valid_to));")"

if [[ "$invalid_scope_count" -ne 0 ]]; then
  echo "Database verification failed: invalid ADX object-scope rows found." >&2
  exit 1
fi

ocpx_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_adx_channel' AND column_name IN ('channel_code','adapter_code','auth_type','config_version','callback_host_allowlist')) OR (table_name='scx_adx_advertiser' AND column_name IN ('upstream_host_allowlist','bid_url')) OR (table_name='scx_adx_monitor_link' AND column_name IN ('route_status','config_version','downstream_callback_url','upstream_feedback_url','published_upstream_feedback_url','click_dedup_window_seconds','event_snapshot_version')) OR (table_name='scx_adx_hourly_report' AND column_name IN ('raw_conversion_count','forwarded_conversion_count','deducted_conversion_count','delivery_failure_count','first_order_count','eligible_click_count','deduplicated_click_count','skipped_click_count','classified_click_count','first_activate_count','reactivation_count','retention_3d_count','retention_4d_count','retention_5d_count','retention_6d_count','retention_7d_count','create_time','update_time')) OR (table_name='scx_ocpx_event' AND column_name IN ('device_platform','device_id_type','device_key_version','source_event_code','conversion_event_code','media_event_code','cpa_charge_amount','daily_limit_result')) OR (table_name='scx_ocpx_delivery_attempt' AND column_name IN ('request_method','target_host','target_path','request_summary','response_excerpt','duration_ms')) OR (table_name='scx_ocpx_billing_entry' AND column_name IN ('balance_before','balance_after','operator_id','remark','channel_id','app_link_id','monitor_id','source_event_code','conversion_event_code','media_event_code')));")"

if [[ "$ocpx_column_count" -ne 56 ]]; then
  echo "Database verification failed: OCPX routing/report/accounting columns are incomplete." >&2
  exit 1
fi

bid_url_length="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='scx_adx_advertiser' AND column_name='bid_url';")"

if [[ "$bid_url_length" -ne 5000 ]]; then
  echo "Database verification failed: advertiser bid_url is not VARCHAR(5000)." >&2
  exit 1
fi

ocpx_audit_index_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND index_name IN ('idx_scx_ocpx_attempt_job','idx_scx_ocpx_attempt_time','idx_scx_ocpx_ingress_audit_time','idx_scx_ocpx_ingress_audit_event','idx_scx_ocpx_ingress_audit_route');")"

if [[ "$ocpx_audit_index_count" -ne 5 ]]; then
  echo "Database verification failed: OCPX attempt/ingress audit indexes are incomplete." >&2
  exit 1
fi

ocpx_adapter_contract_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_ocpx_adapter WHERE adapter_code IN ('COCO','TEMPLATE_GET','STANDARD','TEMPLATE_GET_DOWNSTREAM') AND status='ACTIVE' AND JSON_UNQUOTE(JSON_EXTRACT(capabilities_json,'$.contractVersion'))='1.0' AND JSON_CONTAINS_PATH(capabilities_json,'all','$.supportedMacros','$.requiredUrlMacros','$.requiredAnyUrlMacros');")"

if [[ "$ocpx_adapter_contract_count" -ne 4 ]]; then
  echo "Database verification failed: built-in OCPX adapter capability contracts are incomplete." >&2
  exit 1
fi

admin_ocpx_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('adx:runtime:read','adx:runtime:replay','adx:monitor:publish','adx:account:read','adx:account:recharge','adx:event:write');")"

if [[ "$admin_ocpx_permission_count" -ne 6 ]]; then
  echo "Database verification failed: administrator OCPX runtime/account permissions are incomplete." >&2
  exit 1
fi

event_seed_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_adx_event_definition WHERE deleted=0 AND built_in=1;")"
channel_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_adx_channel WHERE deleted=0;")"
expected_event_seed_count="$((22 + channel_count * 44))"
if [[ "$event_seed_count" -lt "$expected_event_seed_count" ]]; then
  echo "Database verification failed: default event dictionaries are incomplete." >&2
  exit 1
fi

default_mapping_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM scx_adx_channel_event_mapping WHERE deleted=0 AND status='ACTIVE';")"
if [[ "$default_mapping_count" -lt "$((channel_count * 4))" ]]; then
  echo "Database verification failed: four default channel event mappings are incomplete." >&2
  exit 1
fi

echo "Smart Converge X database verification passed: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
