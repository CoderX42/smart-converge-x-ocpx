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
  scx_ocpx_advertiser scx_ocpx_protocol_allowed_host scx_ocpx_promotion_link scx_ocpx_creative
  scx_ocpx_creative_promotion_link scx_ocpx_media scx_ocpx_monitor_task
  scx_ocpx_billing_config_audit
  scx_ocpx_deduction_rule
  scx_ocpx_advertiser_account scx_ocpx_event_fact scx_ocpx_billing_entry
  scx_ocpx_hourly_metric
  scx_ocpx_flow_log scx_ocpx_log_feedback scx_ocpx_log_feedback_day
  scx_ocpx_log_callback scx_ocpx_report_mat_temp scx_ocpx_report_mat
)

SCX_XXL_JOB_TABLES=(
  xxl_job_group xxl_job_info xxl_job_lock xxl_job_log
  xxl_job_log_report xxl_job_logglue xxl_job_registry xxl_job_user
)

# Retired tables may remain in an upgraded database until a separately
# authorized cleanup. They are neither required nor queried by SCX.
SCX_OPTIONAL_RETIRED_TABLES=(
  scx_schema_migration
)

# OCPX Feedback/Callback route dimensions. Java and Query use the matching
# lower-camel names mId/tId/aId/lId; adv_id remains the advertiser-side ad ID.
SCX_OCPX_ROUTE_TABLES=(
  scx_ocpx_log_feedback scx_ocpx_log_feedback_day scx_ocpx_log_callback
  scx_ocpx_report_mat_temp scx_ocpx_report_mat
)
SCX_OCPX_ROUTE_COLUMNS=(m_id t_id a_id l_id)
SCX_OCPX_RETIRED_ROUTE_COLUMNS=(channel_id monitor_id dsp_id link_id)

SCX_EXPECTED_TABLES=(
  "${SCX_REQUIRED_TABLES[@]}"
  "${SCX_XXL_JOB_TABLES[@]}"
)

SCX_ALLOWED_TABLES=(
  "${SCX_EXPECTED_TABLES[@]}"
  "${SCX_OPTIONAL_RETIRED_TABLES[@]}"
)
