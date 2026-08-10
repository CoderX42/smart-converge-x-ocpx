#!/usr/bin/env bash

# Canonical SCX table manifest. New table deltas and the current-state schema
# must update this file in the same change.
SCX_REQUIRED_TABLES=(
  gen_data_source gen_table gen_table_column
  sys_ad_launch sys_agreement sys_article sys_article_category
  sys_config sys_dept sys_dict sys_dict_value sys_log sys_login_log
  sys_material sys_material_group sys_menu sys_region sys_role sys_role_menu
  sys_sms_config sys_sms_send_record sys_social_account sys_storage_config
  sys_user sys_user_role
  scx_adx_channel scx_adx_advertiser scx_adx_app_link
  scx_adx_monitor_link scx_adx_deduction_rule scx_adx_hourly_report
  scx_adx_event_definition scx_adx_channel_event_mapping
  scx_adx_monitor_event_binding scx_adx_route_event_snapshot
  scx_adx_user_scope scx_adx_user_scope_revision
  scx_api_debug_case scx_api_debug_execution scx_api_debug_execution_log
  scx_api_debug_log_access_audit
  scx_ocpx_adapter scx_ocpx_event scx_ocpx_protocol_snapshot scx_ocpx_callback_ref
  scx_ocpx_delivery_job scx_ocpx_delivery_attempt scx_ocpx_deduction_decision
  scx_ocpx_ingress_dlq scx_ocpx_config_outbox scx_ocpx_rta_decision_log
  scx_ocpx_billing_entry scx_ocpx_ingress_audit
  scx_ocpx_click_dedup_state scx_ocpx_click_dedup_decision
  scx_ocpx_event_daily_quota
  scx_schema_migration
)

SCX_XXL_JOB_TABLES=(
  xxl_job_group xxl_job_info xxl_job_lock xxl_job_log
  xxl_job_log_report xxl_job_logglue xxl_job_registry xxl_job_user
)

SCX_ALLOWED_TABLES=("${SCX_REQUIRED_TABLES[@]}" "${SCX_XXL_JOB_TABLES[@]}")
