#!/usr/bin/env bash

# Canonical SCX application-table manifest shared by database verification and
# missing-table reconciliation. XXL-JOB tables are maintained separately by
# 20xxl_job_schema.sql and are not part of the scx-boot application gate.
SCX_REQUIRED_TABLES=(
  gen_data_source gen_table gen_table_column
  sys_config sys_dept sys_dict sys_dict_value sys_log sys_login_log
  sys_material sys_material_group sys_menu sys_region sys_role sys_role_menu
  sys_sms_config sys_sms_send_record sys_social_account sys_storage_config
  sys_user sys_user_role
  scx_adx_channel scx_adx_advertiser scx_adx_app_link
  scx_adx_monitor_link scx_adx_deduction_rule scx_adx_hourly_report
  scx_ocpx_adapter scx_ocpx_event scx_ocpx_protocol_snapshot scx_ocpx_callback_ref
  scx_ocpx_delivery_job scx_ocpx_delivery_attempt scx_ocpx_deduction_decision
  scx_ocpx_ingress_dlq scx_ocpx_config_outbox scx_ocpx_rta_decision_log
  scx_ocpx_billing_entry scx_ocpx_ingress_audit scx_schema_migration
  scx_ocpx_click_dedup_state scx_ocpx_click_dedup_decision
)
