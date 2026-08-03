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

actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${table_list});")"

if [[ "$actual_count" -ne "${#SCX_REQUIRED_TABLES[@]}" ]]; then
  echo "Database verification failed: expected ${#SCX_REQUIRED_TABLES[@]} required tables, found $actual_count." >&2
  exit 1
fi

admin_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND r.role_code='ROLE_ADMIN' AND u.del_flag='0';")"

if [[ "$admin_count" -ne 1 ]]; then
  echo "Database verification failed: default administrator role mapping is missing." >&2
  exit 1
fi

system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_menu WHERE del_flag='0' AND ((id='menu_ocpx_system' AND name='系统管理') OR (parent_id='menu_ocpx_system' AND id IN ('menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log')));")"

if [[ "$system_menu_count" -ne 9 ]]; then
  echo "Database verification failed: original system-management menu tree is incomplete." >&2
  exit 1
fi

admin_system_menu_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.id) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.id IN ('menu_ocpx_system','menu_system_user','menu_role','menu_menu','menu_dept','menu_dict','menu_storage','menu_log','menu_login_log');")"

if [[ "$admin_system_menu_count" -ne 9 ]]; then
  echo "Database verification failed: administrator system-management menu grants are incomplete." >&2
  exit 1
fi

admin_system_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('system:sysuser:page','system:sysrole:page','system:sysmenu:page','system:sysdept:page','system:sysdict:page','system:storageconfig:page','system:syslog:page','system:sysloginlog:page');")"

if [[ "$admin_system_permission_count" -ne 8 ]]; then
  echo "Database verification failed: administrator system-management RBAC permissions are incomplete." >&2
  exit 1
fi

ocpx_column_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND ((table_name='scx_adx_channel' AND column_name IN ('channel_code','adapter_code','auth_type','config_version','callback_host_allowlist')) OR (table_name='scx_adx_advertiser' AND column_name='upstream_host_allowlist') OR (table_name='scx_adx_monitor_link' AND column_name IN ('route_status','config_version','downstream_callback_url')) OR (table_name='scx_adx_hourly_report' AND column_name IN ('raw_conversion_count','forwarded_conversion_count','deducted_conversion_count','delivery_failure_count')));")"

if [[ "$ocpx_column_count" -ne 13 ]]; then
  echo "Database verification failed: V70 OCPX routing/report columns are incomplete." >&2
  exit 1
fi

admin_ocpx_permission_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(DISTINCT m.permission) FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.id JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.permission IN ('adx:runtime:read','adx:runtime:replay','adx:monitor:publish');")"

if [[ "$admin_ocpx_permission_count" -ne 3 ]]; then
  echo "Database verification failed: administrator OCPX runtime permissions are incomplete." >&2
  exit 1
fi

echo "Smart Converge X database verification passed: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
