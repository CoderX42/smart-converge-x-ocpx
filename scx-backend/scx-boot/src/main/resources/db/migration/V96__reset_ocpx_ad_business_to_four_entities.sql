-- MANUAL / DESTRUCTIVE / FORWARD-ONLY
-- Required invocation from this migration directory:
--   mysql ... --init-command="SET @SCX_CONFIRM_RESET_OCPX='RESET_OCPX_TO_FOUR_ENTITIES'" smart_converge_x \
--     < V96__reset_ocpx_ad_business_to_four_entities.sql
-- The source command below is resolved by the mysql client. Do not run this file
-- through an application migration scanner. Back up and record this file's SHA-256.

SET NAMES utf8mb4;

SET @v96_guard_sql := IF(
  DATABASE()='smart_converge_x' AND COALESCE(@SCX_CONFIRM_RESET_OCPX,'')='RESET_OCPX_TO_FOUR_ENTITIES',
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''V96 refused: target database or explicit confirmation is invalid'''
);
PREPARE v96_guard_stmt FROM @v96_guard_sql;
EXECUTE v96_guard_stmt;
DEALLOCATE PREPARE v96_guard_stmt;

SET @v96_old_table_count := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_name IN (
    'scx_ocpx_channel','scx_ocpx_integration_document','scx_ocpx_advertiser_credential_revision',
    'scx_ocpx_advertiser_change_audit','scx_ocpx_app_link','scx_ocpx_app_link_change_audit',
    'scx_ocpx_monitor_link','scx_ocpx_deduction_rule','scx_ocpx_hourly_report','scx_ocpx_adapter',
    'scx_ocpx_rta_decision_log','scx_ocpx_monitor_event_binding','scx_ocpx_product_logo_object',
    'scx_ocpx_product','scx_ocpx_product_event_policy','scx_ocpx_product_change_audit',
    'scx_ocpx_media_account','scx_ocpx_media_credential','scx_ocpx_media_credential_revision',
    'scx_ocpx_media_account_owner','scx_ocpx_media_account_change_audit','scx_ocpx_task',
    'scx_ocpx_task_change_audit','scx_ocpx_task_event_binding','scx_ocpx_event_change_audit'
  )
);
SET @v96_new_table_count := (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_name IN (
    'scx_ocpx_promotion_link','scx_ocpx_media','scx_ocpx_monitor_task','scx_ocpx_flow_log'
  )
);
SET @v96_repeat_guard_sql := IF(
  @v96_old_table_count=0 AND @v96_new_table_count=4,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''V96 refused: four-entity reset is already applied''',
  'SELECT 1'
);
PREPARE v96_repeat_guard_stmt FROM @v96_repeat_guard_sql;
EXECUTE v96_repeat_guard_stmt;
DEALLOCATE PREPARE v96_repeat_guard_stmt;

SET @v96_sys_user_count := (SELECT COUNT(*) FROM sys_user);
SET @v96_sys_role_count := (SELECT COUNT(*) FROM sys_role);
SET @v96_sys_dept_count := (SELECT COUNT(*) FROM sys_dept);
SET @v96_sys_dict_count := (SELECT COUNT(*) FROM sys_dict);
SET @v96_sys_dict_value_count := (SELECT COUNT(*) FROM sys_dict_value);
SET @v96_sys_log_count := (SELECT COUNT(*) FROM sys_log);
SET @v96_sys_login_log_count := (SELECT COUNT(*) FROM sys_login_log);
SET @v96_sys_storage_config_count := (SELECT COUNT(*) FROM sys_storage_config);
SET @v96_sys_user_role_count := (SELECT COUNT(*) FROM sys_user_role);
SET @v96_system_menu_count := (
  SELECT COUNT(*) FROM sys_menu WHERE id='menu_ocpx_system' OR permission LIKE 'system:%'
);
SET @v96_system_role_menu_count := (
  SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id
  WHERE m.id='menu_ocpx_system' OR m.permission LIKE 'system:%'
);

SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS
	 scx_ocpx_flow_log,
	 scx_api_debug_log_access_audit,
	 scx_api_debug_execution_log,
	 scx_api_debug_execution,
	 scx_api_debug_case,
	 scx_ocpx_runtime_command_audit,
  scx_ocpx_runtime_replay_attempt,
  scx_ocpx_runtime_replay_request,
  scx_ocpx_runtime_failure,
  scx_ocpx_runtime_fact,
  scx_ocpx_report_export_audit,
  scx_ocpx_report_export_task,
  scx_ocpx_hourly_adjustment,
  scx_ocpx_hourly_aggregation_cursor,
  scx_ocpx_hourly_fact_contribution,
  scx_ocpx_config_outbox,
  scx_ocpx_ingress_dlq,
  scx_ocpx_ingress_audit,
  scx_ocpx_event_daily_quota,
  scx_ocpx_click_dedup_decision,
  scx_ocpx_click_dedup_state,
  scx_ocpx_billing_entry,
  scx_ocpx_deduction_decision,
  scx_ocpx_delivery_attempt,
  scx_ocpx_delivery_job,
  scx_ocpx_callback_ref,
  scx_ocpx_protocol_snapshot,
  scx_ocpx_event,
  scx_ocpx_route_pause_command,
  scx_ocpx_route_instance_ack,
  scx_ocpx_route_distribution,
  scx_ocpx_route_publish_record,
  scx_ocpx_route_runtime_state,
  scx_ocpx_route_event_snapshot,
  scx_ocpx_route_version,
  scx_ocpx_monitor_task_event_binding,
  scx_ocpx_media_event_mapping,
  scx_ocpx_event_definition,
  scx_ocpx_advertiser_login_binding,
  scx_ocpx_user_scope,
  scx_ocpx_user_scope_revision,
  scx_ocpx_business_audit,
  scx_ocpx_monitor_task,
  scx_ocpx_media,
  scx_ocpx_promotion_link,
  scx_ocpx_advertiser,
  scx_ocpx_event_change_audit,
  scx_ocpx_task_event_binding,
  scx_ocpx_task_change_audit,
  scx_ocpx_task,
  scx_ocpx_media_account_change_audit,
  scx_ocpx_media_account_owner,
  scx_ocpx_media_credential_revision,
  scx_ocpx_media_credential,
  scx_ocpx_media_account,
  scx_ocpx_product_change_audit,
  scx_ocpx_product_event_policy,
  scx_ocpx_product_logo_object,
  scx_ocpx_product,
  scx_ocpx_monitor_event_binding,
  scx_ocpx_rta_decision_log,
  scx_ocpx_adapter,
  scx_ocpx_hourly_report,
  scx_ocpx_deduction_rule,
  scx_ocpx_monitor_link,
  scx_ocpx_app_link_change_audit,
  scx_ocpx_app_link,
  scx_ocpx_advertiser_change_audit,
  scx_ocpx_advertiser_credential_revision,
  scx_ocpx_integration_document,
  scx_ocpx_channel_event_mapping,
  scx_ocpx_channel;
SET FOREIGN_KEY_CHECKS=1;

-- Remove explicitly enumerated pre-V92 ADX advertising menus if an upgraded
-- database stopped in a partial namespace-repair state. Core system menus are
-- intentionally absent from this list.
DELETE FROM sys_role_menu WHERE menu_id IN (
  'menu_adx_advertiser','menu_adx_advertiser_list','menu_adx_app_link','menu_adx_channel',
  'menu_adx_deduction','menu_adx_event','menu_adx_home','menu_adx_media_account','menu_adx_monitor',
  'menu_adx_product','menu_adx_report','menu_adx_report_link_v89','menu_adx_report_media_v89',
  'menu_adx_report_product_v89','menu_adx_report_task_v89','menu_adx_report_time','menu_adx_report_time_v89',
  'menu_adx_runtime','menu_adx_task','perm_adx_advertiser_add','perm_adx_advertiser_credential',
  'perm_adx_advertiser_document_edit','perm_adx_advertiser_document_view','perm_adx_advertiser_edit',
  'perm_adx_advertiser_status','perm_adx_advertiser_view','perm_adx_app_link_add_v85',
  'perm_adx_app_link_archive_v85','perm_adx_app_link_edit_v85','perm_adx_app_link_status_v85',
  'perm_adx_app_link_validate_v85','perm_adx_app_link_view_v85','perm_adx_dashboard_read',
  'perm_adx_dashboard_view_v90','perm_adx_event_add_v87','perm_adx_event_edit_v87',
  'perm_adx_event_mapping_add_v87','perm_adx_event_mapping_edit_v87','perm_adx_event_mapping_status_v87',
  'perm_adx_event_mapping_view_v87','perm_adx_event_status_v87','perm_adx_event_view_v87',
  'perm_adx_event_write','perm_adx_media_account_add','perm_adx_media_account_archive',
  'perm_adx_media_account_edit','perm_adx_media_account_status','perm_adx_media_account_view',
  'perm_adx_media_credential_add','perm_adx_media_credential_edit','perm_adx_media_credential_rotate',
  'perm_adx_media_credential_status','perm_adx_media_credential_view','perm_adx_media_debug',
  'perm_adx_media_owner_edit','perm_adx_media_owner_view','perm_adx_monitor_copy_v88',
  'perm_adx_monitor_publish_v88','perm_adx_monitor_status_v88','perm_adx_monitor_view_v88',
  'perm_adx_product_add','perm_adx_product_archive','perm_adx_product_edit','perm_adx_product_view',
  'perm_adx_report_deduction_export_v89','perm_adx_report_export_v89','perm_adx_report_read',
  'perm_adx_report_sensitive_v89','perm_adx_report_view_v89','perm_adx_runtime_body_v91',
  'perm_adx_runtime_debug_v91','perm_adx_runtime_detail_v91','perm_adx_runtime_dlq_view_v91',
  'perm_adx_runtime_export_v91','perm_adx_runtime_read','perm_adx_runtime_replay',
  'perm_adx_runtime_replay_v91','perm_adx_runtime_retry_v91','perm_adx_runtime_stop_v91',
  'perm_adx_runtime_view_v91','perm_adx_scope_edit','perm_adx_scope_view','perm_adx_task_add_v86',
  'perm_adx_task_archive_v86','perm_adx_task_edit_v86','perm_adx_task_event_edit_v87',
  'perm_adx_task_publish_v88','perm_adx_task_status_v86','perm_adx_task_view_v86'
);
DELETE FROM sys_menu WHERE id IN (
  'menu_adx_advertiser','menu_adx_advertiser_list','menu_adx_app_link','menu_adx_channel',
  'menu_adx_deduction','menu_adx_event','menu_adx_home','menu_adx_media_account','menu_adx_monitor',
  'menu_adx_product','menu_adx_report','menu_adx_report_link_v89','menu_adx_report_media_v89',
  'menu_adx_report_product_v89','menu_adx_report_task_v89','menu_adx_report_time','menu_adx_report_time_v89',
  'menu_adx_runtime','menu_adx_task','perm_adx_advertiser_add','perm_adx_advertiser_credential',
  'perm_adx_advertiser_document_edit','perm_adx_advertiser_document_view','perm_adx_advertiser_edit',
  'perm_adx_advertiser_status','perm_adx_advertiser_view','perm_adx_app_link_add_v85',
  'perm_adx_app_link_archive_v85','perm_adx_app_link_edit_v85','perm_adx_app_link_status_v85',
  'perm_adx_app_link_validate_v85','perm_adx_app_link_view_v85','perm_adx_dashboard_read',
  'perm_adx_dashboard_view_v90','perm_adx_event_add_v87','perm_adx_event_edit_v87',
  'perm_adx_event_mapping_add_v87','perm_adx_event_mapping_edit_v87','perm_adx_event_mapping_status_v87',
  'perm_adx_event_mapping_view_v87','perm_adx_event_status_v87','perm_adx_event_view_v87',
  'perm_adx_event_write','perm_adx_media_account_add','perm_adx_media_account_archive',
  'perm_adx_media_account_edit','perm_adx_media_account_status','perm_adx_media_account_view',
  'perm_adx_media_credential_add','perm_adx_media_credential_edit','perm_adx_media_credential_rotate',
  'perm_adx_media_credential_status','perm_adx_media_credential_view','perm_adx_media_debug',
  'perm_adx_media_owner_edit','perm_adx_media_owner_view','perm_adx_monitor_copy_v88',
  'perm_adx_monitor_publish_v88','perm_adx_monitor_status_v88','perm_adx_monitor_view_v88',
  'perm_adx_product_add','perm_adx_product_archive','perm_adx_product_edit','perm_adx_product_view',
  'perm_adx_report_deduction_export_v89','perm_adx_report_export_v89','perm_adx_report_read',
  'perm_adx_report_sensitive_v89','perm_adx_report_view_v89','perm_adx_runtime_body_v91',
  'perm_adx_runtime_debug_v91','perm_adx_runtime_detail_v91','perm_adx_runtime_dlq_view_v91',
  'perm_adx_runtime_export_v91','perm_adx_runtime_read','perm_adx_runtime_replay',
  'perm_adx_runtime_replay_v91','perm_adx_runtime_retry_v91','perm_adx_runtime_stop_v91',
  'perm_adx_runtime_view_v91','perm_adx_scope_edit','perm_adx_scope_view','perm_adx_task_add_v86',
  'perm_adx_task_archive_v86','perm_adx_task_edit_v86','perm_adx_task_event_edit_v87',
  'perm_adx_task_publish_v88','perm_adx_task_status_v86','perm_adx_task_view_v86'
);

DELETE FROM sys_role_menu WHERE menu_id IN (
  'menu_ocpx_channel','menu_ocpx_channel_list','menu_ocpx_advertiser_list','menu_ocpx_product','menu_ocpx_app_link',
  'menu_ocpx_media_account','menu_ocpx_task','menu_ocpx_monitor','menu_ocpx_deduction',
  'menu_ocpx_report_time','menu_ocpx_report_time_v89','menu_ocpx_report_link_v89',
  'menu_ocpx_report_media_v89','menu_ocpx_report_product_v89','menu_ocpx_report_task_v89',
  'perm_ocpx_channel_read','perm_ocpx_channel_write','perm_ocpx_product_view','perm_ocpx_product_add',
  'perm_ocpx_product_edit','perm_ocpx_product_archive','perm_ocpx_app_link_read','perm_ocpx_app_link_write',
  'perm_ocpx_app_link_view_v85','perm_ocpx_app_link_add_v85','perm_ocpx_app_link_edit_v85',
  'perm_ocpx_app_link_status_v85','perm_ocpx_app_link_archive_v85','perm_ocpx_app_link_validate_v85',
  'perm_ocpx_media_account_view','perm_ocpx_media_account_add','perm_ocpx_media_account_edit',
  'perm_ocpx_media_account_status','perm_ocpx_media_account_archive','perm_ocpx_media_credential_view',
  'perm_ocpx_media_credential_add','perm_ocpx_media_credential_edit','perm_ocpx_media_credential_rotate',
  'perm_ocpx_media_credential_status','perm_ocpx_media_owner_view','perm_ocpx_media_owner_edit',
  'perm_ocpx_media_debug','perm_ocpx_task_view_v86','perm_ocpx_task_add_v86','perm_ocpx_task_edit_v86',
  'perm_ocpx_task_status_v86','perm_ocpx_task_archive_v86','perm_ocpx_task_event_edit_v87',
  'perm_ocpx_task_publish_v88','perm_ocpx_monitor_view_v88','perm_ocpx_monitor_publish_v88',
  'perm_ocpx_monitor_copy_v88','perm_ocpx_monitor_status_v88','perm_ocpx_deduction_read',
  'perm_ocpx_deduction_write','perm_ocpx_advertiser_credential','perm_ocpx_advertiser_document_view',
  'perm_ocpx_advertiser_document_edit','perm_ocpx_advertiser_view','perm_ocpx_advertiser_grant_v95',
  'perm_ocpx_dashboard_read','perm_ocpx_dashboard_view_v90','perm_ocpx_event_write',
  'perm_ocpx_event_view_v87','perm_ocpx_event_add_v87','perm_ocpx_event_edit_v87',
  'perm_ocpx_event_status_v87','perm_ocpx_event_mapping_view_v87','perm_ocpx_event_mapping_add_v87',
  'perm_ocpx_event_mapping_edit_v87','perm_ocpx_event_mapping_status_v87','perm_ocpx_report_read',
  'perm_ocpx_report_view_v89','perm_ocpx_report_export_v89','perm_ocpx_report_deduction_export_v89',
  'perm_ocpx_report_sensitive_v89','perm_ocpx_runtime_read','perm_ocpx_runtime_view_v91',
  'perm_ocpx_runtime_dlq_view_v91','perm_ocpx_runtime_detail_v91','perm_ocpx_runtime_replay_v91',
  'perm_ocpx_runtime_retry_v91','perm_ocpx_runtime_stop_v91','perm_ocpx_runtime_export_v91',
  'perm_ocpx_runtime_body_v91','perm_ocpx_runtime_debug_v91'
);
DELETE FROM sys_menu WHERE id IN (
  'menu_ocpx_channel','menu_ocpx_channel_list','menu_ocpx_advertiser_list','menu_ocpx_product','menu_ocpx_app_link',
  'menu_ocpx_media_account','menu_ocpx_task','menu_ocpx_monitor','menu_ocpx_deduction',
  'menu_ocpx_report_time','menu_ocpx_report_time_v89','menu_ocpx_report_link_v89',
  'menu_ocpx_report_media_v89','menu_ocpx_report_product_v89','menu_ocpx_report_task_v89',
  'perm_ocpx_channel_read','perm_ocpx_channel_write','perm_ocpx_product_view','perm_ocpx_product_add',
  'perm_ocpx_product_edit','perm_ocpx_product_archive','perm_ocpx_app_link_read','perm_ocpx_app_link_write',
  'perm_ocpx_app_link_view_v85','perm_ocpx_app_link_add_v85','perm_ocpx_app_link_edit_v85',
  'perm_ocpx_app_link_status_v85','perm_ocpx_app_link_archive_v85','perm_ocpx_app_link_validate_v85',
  'perm_ocpx_media_account_view','perm_ocpx_media_account_add','perm_ocpx_media_account_edit',
  'perm_ocpx_media_account_status','perm_ocpx_media_account_archive','perm_ocpx_media_credential_view',
  'perm_ocpx_media_credential_add','perm_ocpx_media_credential_edit','perm_ocpx_media_credential_rotate',
  'perm_ocpx_media_credential_status','perm_ocpx_media_owner_view','perm_ocpx_media_owner_edit',
  'perm_ocpx_media_debug','perm_ocpx_task_view_v86','perm_ocpx_task_add_v86','perm_ocpx_task_edit_v86',
  'perm_ocpx_task_status_v86','perm_ocpx_task_archive_v86','perm_ocpx_task_event_edit_v87',
  'perm_ocpx_task_publish_v88','perm_ocpx_monitor_view_v88','perm_ocpx_monitor_publish_v88',
  'perm_ocpx_monitor_copy_v88','perm_ocpx_monitor_status_v88','perm_ocpx_deduction_read',
  'perm_ocpx_deduction_write','perm_ocpx_advertiser_credential','perm_ocpx_advertiser_document_view',
  'perm_ocpx_advertiser_document_edit','perm_ocpx_advertiser_view','perm_ocpx_advertiser_grant_v95',
  'perm_ocpx_dashboard_read','perm_ocpx_dashboard_view_v90','perm_ocpx_event_write',
  'perm_ocpx_event_view_v87','perm_ocpx_event_add_v87','perm_ocpx_event_edit_v87',
  'perm_ocpx_event_status_v87','perm_ocpx_event_mapping_view_v87','perm_ocpx_event_mapping_add_v87',
  'perm_ocpx_event_mapping_edit_v87','perm_ocpx_event_mapping_status_v87','perm_ocpx_report_read',
  'perm_ocpx_report_view_v89','perm_ocpx_report_export_v89','perm_ocpx_report_deduction_export_v89',
  'perm_ocpx_report_sensitive_v89','perm_ocpx_runtime_read','perm_ocpx_runtime_view_v91',
  'perm_ocpx_runtime_dlq_view_v91','perm_ocpx_runtime_detail_v91','perm_ocpx_runtime_replay_v91',
  'perm_ocpx_runtime_retry_v91','perm_ocpx_runtime_stop_v91','perm_ocpx_runtime_export_v91',
  'perm_ocpx_runtime_body_v91','perm_ocpx_runtime_debug_v91'
);

-- Remove the technical OCPX menus that were briefly introduced by the first
-- four-entity draft. The protected system-management tree is deliberately not
-- part of this list.
DELETE FROM sys_role_menu WHERE menu_id IN (
  'menu_ocpx_event','menu_ocpx_runtime','menu_ocpx_api_debug',
  'perm_ocpx_advertiser_grant','perm_ocpx_account_read','perm_ocpx_account_recharge',
  'perm_ocpx_monitor_task_publish','perm_ocpx_report_export',
  'perm_ocpx_event_add','perm_ocpx_event_edit','perm_ocpx_event_status',
  'perm_ocpx_event_mapping_view','perm_ocpx_event_mapping_add','perm_ocpx_event_mapping_edit',
  'perm_ocpx_runtime_dlq_view','perm_ocpx_runtime_detail','perm_ocpx_runtime_replay',
  'perm_ocpx_api_debug_execute','perm_ocpx_api_debug_execute_billable','perm_ocpx_api_debug_logs',
  'perm_ocpx_api_debug_logs_raw','perm_ocpx_api_debug_case_write',
  'perm_ocpx_scope_view','perm_ocpx_scope_edit'
);
DELETE FROM sys_menu WHERE id IN (
  'menu_ocpx_event','menu_ocpx_runtime','menu_ocpx_api_debug',
  'perm_ocpx_advertiser_grant','perm_ocpx_account_read','perm_ocpx_account_recharge',
  'perm_ocpx_monitor_task_publish','perm_ocpx_report_export',
  'perm_ocpx_event_add','perm_ocpx_event_edit','perm_ocpx_event_status',
  'perm_ocpx_event_mapping_view','perm_ocpx_event_mapping_add','perm_ocpx_event_mapping_edit',
  'perm_ocpx_runtime_dlq_view','perm_ocpx_runtime_detail','perm_ocpx_runtime_replay',
  'perm_ocpx_api_debug_execute','perm_ocpx_api_debug_execute_billable','perm_ocpx_api_debug_logs',
  'perm_ocpx_api_debug_logs_raw','perm_ocpx_api_debug_case_write',
  'perm_ocpx_scope_view','perm_ocpx_scope_edit'
);

source ../schema/scx_current_schema.sql

SET @v96_system_guard_sql := IF(
  @v96_sys_user_count=(SELECT COUNT(*) FROM sys_user) AND
  @v96_sys_role_count=(SELECT COUNT(*) FROM sys_role) AND
  @v96_sys_dept_count=(SELECT COUNT(*) FROM sys_dept) AND
  @v96_sys_dict_count=(SELECT COUNT(*) FROM sys_dict) AND
  @v96_sys_dict_value_count=(SELECT COUNT(*) FROM sys_dict_value) AND
  @v96_sys_log_count=(SELECT COUNT(*) FROM sys_log) AND
  @v96_sys_login_log_count=(SELECT COUNT(*) FROM sys_login_log) AND
  @v96_sys_storage_config_count=(SELECT COUNT(*) FROM sys_storage_config) AND
  @v96_sys_user_role_count=(SELECT COUNT(*) FROM sys_user_role) AND
  @v96_system_menu_count=(
    SELECT COUNT(*) FROM sys_menu WHERE id='menu_ocpx_system' OR permission LIKE 'system:%'
  ) AND
  @v96_system_role_menu_count=(
    SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id
    WHERE m.id='menu_ocpx_system' OR m.permission LIKE 'system:%'
  ),
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT=''V96 failed: protected system-management row counts changed'''
);
PREPARE v96_system_guard_stmt FROM @v96_system_guard_sql;
EXECUTE v96_system_guard_stmt;
DEALLOCATE PREPARE v96_system_guard_stmt;
