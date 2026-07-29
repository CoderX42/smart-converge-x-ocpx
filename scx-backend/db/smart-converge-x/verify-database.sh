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
  scx_media scx_user_subject_scope scx_partner scx_product
  scx_organization scx_advertiser scx_agency_relation scx_ad_account
  scx_qualification_case scx_qualification_document scx_qualification_review_log
  scx_agreement_template scx_agreement_instance scx_data_authorization
  scx_fin_account scx_fin_journal scx_fin_entry scx_credit_line
  scx_budget_reservation scx_ledger_balance_snapshot scx_fin_idempotency
  scx_rebate_rule scx_rebate_rule_version scx_rebate_calculation
  scx_refund_case scx_invoice_application scx_invoice_item
  scx_reconciliation_run scx_reconciliation_item scx_closing_period
  scx_adjustment_journal
  scx_media_account scx_placement scx_media_capability
  scx_placement_quality_snapshot scx_media_adapter_config
  scx_creative_asset scx_creative_asset_revision scx_asset_binary
  scx_creative_review_case scx_creative_review_log
  scx_landing_page scx_landing_page_revision scx_creative_binding
  scx_campaign scx_campaign_revision scx_ad_group scx_ad_group_revision
  scx_ad scx_ad_revision scx_optimization_goal scx_budget_plan
  scx_targeting_revision scx_media_publishing_binding scx_publishing_operation
  scx_media_channel scx_integration_document scx_tracking_link
  scx_link_event_mapping scx_tracking_task scx_raw_event
  scx_conversion_identity scx_conversion_dedup_result scx_click_dedup_result
  scx_attribution_result scx_event_outbox scx_callback_job scx_machine_nonce
  scx_report_run scx_report_daily scx_export_task scx_rta_endpoint
  scx_upstream_customer scx_short_link scx_ad_platform_config
  scx_media_channel_user scx_delivery_config_revision scx_delivery_status_history
  scx_user_task_scope scx_rta_decision_log scx_schema_migration
  scx_channel_client scx_channel_credential scx_click_callback_target
  scx_click_dedup_guard
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
