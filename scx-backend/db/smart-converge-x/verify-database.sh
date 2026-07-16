#!/usr/bin/env bash
set -euo pipefail

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

required_tables=(
  gen_data_source gen_table gen_table_column
  pay_config pay_notify_record pay_refund_order pay_trade_order
  sys_config sys_dept sys_dict sys_dict_value sys_log sys_login_log
  sys_material sys_material_group sys_menu sys_region sys_role sys_role_menu
  sys_sms_config sys_sms_send_record sys_social_account sys_storage_config
  sys_user sys_user_role
)

table_list="$(printf "'%s'," "${required_tables[@]}")"
table_list="${table_list%,}"

actual_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name IN (${table_list});")"

if [[ "$actual_count" -ne "${#required_tables[@]}" ]]; then
  echo "Database verification failed: expected ${#required_tables[@]} required tables, found $actual_count." >&2
  exit 1
fi

admin_count="$(mysql "${mysql_args[@]}" --batch --skip-column-names "$MYSQL_DATABASE" \
  --execute="SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='system' AND r.role_code='ROLE_ADMIN' AND u.del_flag='0';")"

if [[ "$admin_count" -ne 1 ]]; then
  echo "Database verification failed: default administrator role mapping is missing." >&2
  exit 1
fi

echo "Smart Converge X database verification passed: ${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
