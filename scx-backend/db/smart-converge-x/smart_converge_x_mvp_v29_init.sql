-- Smart Converge X MVP one-file bootstrap (empty database only).
-- Run from this directory with the MySQL command-line client so `source` paths resolve.
-- This includes the sandbox/demo data in V21 and V25; do not use it to upgrade an existing database.

source 00schema.sql
source 10smart_converge_x_core.sql
source 20xxl_job_schema.sql
source ../../scx-boot/src/main/resources/db/migration/V1__scx_mvp_schema.sql
source ../../scx-boot/src/main/resources/db/migration/V2__upgrade_legacy_subject_tables.sql
source ../../scx-boot/src/main/resources/db/migration/V3__seed_mvp_access_and_sandbox_media.sql
source ../../scx-boot/src/main/resources/db/migration/V4__add_machine_nonce_replay_protection.sql
source ../../scx-boot/src/main/resources/db/migration/V5__add_restricted_report_export_tasks.sql
source ../../scx-boot/src/main/resources/db/migration/V6__add_rta_endpoints_and_task_delivery_options.sql
source ../../scx-boot/src/main/resources/db/migration/V7__add_attribution_review_and_over_limit.sql
source ../../scx-boot/src/main/resources/db/migration/V8__add_upstream_customer_model.sql
source ../../scx-boot/src/main/resources/db/migration/V9__add_sensitive_click_detail_permission.sql
source ../../scx-boot/src/main/resources/db/migration/V10__add_short_link_tool.sql
source ../../scx-boot/src/main/resources/db/migration/V11__add_ad_platform_config.sql
source ../../scx-boot/src/main/resources/db/migration/V12__add_impression_to_daily_report.sql
source ../../scx-boot/src/main/resources/db/migration/V13__add_media_channel_user.sql
source ../../scx-boot/src/main/resources/db/migration/V14__add_delivery_configuration_history.sql
source ../../scx-boot/src/main/resources/db/migration/V15__add_full_funnel_report_metrics.sql
source ../../scx-boot/src/main/resources/db/migration/V16__add_report_customer_document_channel_dimensions.sql
source ../../scx-boot/src/main/resources/db/migration/V17__add_ad_effect_dashboard_menu.sql
source ../../scx-boot/src/main/resources/db/migration/V18__add_user_task_scope.sql
source ../../scx-boot/src/main/resources/db/migration/V19__add_short_link_source_url.sql
source ../../scx-boot/src/main/resources/db/migration/V20__add_export_task_scope_snapshot.sql
source ../../scx-boot/src/main/resources/db/migration/V21__seed_sandbox_callback_endpoint.sql
source ../../scx-boot/src/main/resources/db/migration/V22__add_rta_decision_log.sql
source ../../scx-boot/src/main/resources/db/migration/V23__add_schema_migration_history.sql
source ../../scx-boot/src/main/resources/db/migration/V24__add_media_summary_menu.sql
source ../../scx-boot/src/main/resources/db/migration/V25__seed_sandbox_mvp_demo_chain.sql
source ../../scx-boot/src/main/resources/db/migration/V26__add_competitor_report_summary_menus.sql
source ../../scx-boot/src/main/resources/db/migration/V27__add_independent_master_data_menus.sql
source ../../scx-boot/src/main/resources/db/migration/V28__add_event_mapping_price_and_limit.sql
source ../../scx-boot/src/main/resources/db/migration/V29__reconcile_v28_schema_gap.sql
