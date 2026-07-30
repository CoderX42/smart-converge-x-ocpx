-- V67: complete the reporting pipeline with persistent media facts, hourly
-- materialization, automatic/backfill runs and explicit campaign-to-task binding.
-- This migration is forward-only. Existing facts and sandbox evidence are retained.

ALTER TABLE scx_media_publishing_binding
  ADD COLUMN tracking_task_id VARCHAR(32) NULL COMMENT '明确绑定的监测任务',
  ADD COLUMN ad_group_id VARCHAR(32) NULL COMMENT '用于确定优化目标和CPA分母',
  ADD COLUMN effective_from DATETIME(3) NULL COMMENT '绑定生效时间（UTC）',
  ADD COLUMN effective_to DATETIME(3) NULL COMMENT '绑定失效时间（UTC）',
  ADD COLUMN binding_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|INACTIVE',
  ADD COLUMN version INT NOT NULL DEFAULT 0,
  ADD COLUMN active_task_key VARCHAR(32)
    GENERATED ALWAYS AS (CASE WHEN binding_status='ACTIVE' THEN tracking_task_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_scx_binding_active_task (active_task_key),
  ADD KEY idx_scx_binding_external (media_account_id, external_campaign_id),
  ADD KEY idx_scx_binding_task_period (tracking_task_id, effective_from, effective_to);

ALTER TABLE scx_raw_event
  ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'LIVE' COMMENT 'LIVE|SANDBOX_SEED';

ALTER TABLE scx_callback_job
  ADD COLUMN completed_at DATETIME(3) NULL COMMENT '成功或终止时间（UTC）';

UPDATE scx_raw_event
SET data_origin='SANDBOX_SEED'
WHERE source='sandbox-seed' OR id IN ('raw_demo_mvp_click','raw_demo_mvp_activate');

ALTER TABLE scx_report_run
  ADD COLUMN media_id VARCHAR(32) NULL,
  ADD COLUMN granularity VARCHAR(16) NOT NULL DEFAULT 'DAILY' COMMENT 'HOURLY|DAILY',
  ADD COLUMN run_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO|MANUAL|BACKFILL',
  ADD COLUMN revision INT NOT NULL DEFAULT 1,
  ADD COLUMN data_status VARCHAR(24) NOT NULL DEFAULT 'PRELIMINARY'
    COMMENT 'PRELIMINARY|FINAL|INCOMPLETE|SUCCEEDED_EMPTY',
  ADD COLUMN row_count INT NOT NULL DEFAULT 0,
  ADD COLUMN source_watermark DATETIME(3) NULL,
  ADD COLUMN failure_reason VARCHAR(512) NULL,
  ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'LIVE',
  ADD KEY idx_scx_report_run_scope (media_id, report_date, granularity, status, finished_at);

UPDATE scx_report_run
SET data_origin='SANDBOX_SEED'
WHERE id='report_run_demo_mvp' OR input_snapshot_hash LIKE 'sandbox-demo%';

ALTER TABLE scx_report_daily
  ADD COLUMN media_account_id VARCHAR(32) NULL,
  ADD COLUMN campaign_id VARCHAR(32) NULL,
  ADD COLUMN publishing_binding_id VARCHAR(32) NULL,
  ADD COLUMN media_impression_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN media_click_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN media_conversion_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  ADD COLUMN currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  ADD COLUMN callback_succeeded_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN callback_failed_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN data_status VARCHAR(24) NOT NULL DEFAULT 'PRELIMINARY',
  ADD COLUMN quality_code VARCHAR(64) NULL,
  ADD COLUMN source_watermark DATETIME(3) NULL,
  ADD COLUMN data_origin VARCHAR(24) NOT NULL DEFAULT 'LIVE',
  DROP INDEX uk_scx_report_daily_grain,
  ADD UNIQUE KEY uk_scx_report_daily_grain
    (report_run_id,media_id,partner_id,product_id,link_id,task_id,currency,revision),
  ADD KEY idx_scx_report_daily_campaign (report_date, media_id, campaign_id),
  ADD KEY idx_scx_report_daily_account (report_date, media_account_id);

UPDATE scx_report_daily
SET data_origin='SANDBOX_SEED', quality_code='SANDBOX_SEED'
WHERE id='report_daily_demo_mvp' OR report_run_id='report_run_demo_mvp';

CREATE TABLE scx_media_report_sync_run (
  id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  sync_type VARCHAR(16) NOT NULL COMMENT 'PERFORMANCE|BILLING',
  window_start DATETIME(3) NOT NULL,
  window_end DATETIME(3) NOT NULL,
  cursor_value VARCHAR(512) NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL COMMENT 'RUNNING|SUCCEEDED|SUCCEEDED_EMPTY|FAILED',
  fetched_rows INT NOT NULL DEFAULT 0,
  persisted_rows INT NOT NULL DEFAULT 0,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,
  locked_by VARCHAR(128) NULL,
  locked_until DATETIME(3) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_media_sync_request (media_account_id, sync_type, request_hash),
  KEY idx_scx_media_sync_status (status, locked_until, started_at),
  KEY idx_scx_media_sync_account (media_account_id, window_start, window_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体报表同步运行';

CREATE TABLE scx_media_report_hourly_fact (
  id VARCHAR(32) NOT NULL,
  sync_run_id VARCHAR(32) NOT NULL,
  sync_type VARCHAR(16) NOT NULL COMMENT 'PERFORMANCE|BILLING',
  media_account_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NULL,
  publishing_binding_id VARCHAR(32) NULL,
  campaign_id VARCHAR(32) NULL,
  tracking_task_id VARCHAR(32) NULL,
  external_campaign_id VARCHAR(128) NOT NULL,
  stat_hour_utc DATETIME(3) NOT NULL,
  source_timezone VARCHAR(64) NOT NULL,
  local_report_date DATE NOT NULL,
  local_hour TINYINT UNSIGNED NOT NULL,
  impression_count BIGINT NOT NULL DEFAULT 0,
  click_count BIGINT NOT NULL DEFAULT 0,
  conversion_count BIGINT NOT NULL DEFAULT 0,
  spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  currency VARCHAR(8) NOT NULL,
  source_version INT NOT NULL DEFAULT 1,
  source_hash CHAR(64) NOT NULL,
  quality_code VARCHAR(64) NULL,
  pulled_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_media_hour_fact
    (media_account_id, external_campaign_id, stat_hour_utc, sync_type, source_version),
  KEY idx_scx_media_hour_date (media_id, local_report_date, local_hour),
  KEY idx_scx_media_hour_task (tracking_task_id, stat_hour_utc),
  KEY idx_scx_media_hour_binding (publishing_binding_id, stat_hour_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体小时表现与消耗事实';

CREATE TABLE scx_report_hourly (
  id VARCHAR(32) NOT NULL,
  report_run_id VARCHAR(32) NOT NULL,
  stat_hour_utc DATETIME(3) NOT NULL,
  report_date DATE NOT NULL,
  local_hour TINYINT UNSIGNED NOT NULL,
  timezone VARCHAR(64) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NULL,
  campaign_id VARCHAR(32) NULL,
  publishing_binding_id VARCHAR(32) NULL,
  partner_id VARCHAR(32) NULL,
  customer_id VARCHAR(32) NULL,
  product_id VARCHAR(32) NULL,
  link_id VARCHAR(32) NULL,
  document_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NULL,
  media_impression_count BIGINT NOT NULL DEFAULT 0,
  media_click_count BIGINT NOT NULL DEFAULT 0,
  media_conversion_count BIGINT NOT NULL DEFAULT 0,
  unique_click_count BIGINT NOT NULL DEFAULT 0,
  eligible_click_count BIGINT NOT NULL DEFAULT 0,
  deduplicated_click_count BIGINT NOT NULL DEFAULT 0,
  attributed_activate_count BIGINT NOT NULL DEFAULT 0,
  attributed_register_count BIGINT NOT NULL DEFAULT 0,
  attributed_pay_count BIGINT NOT NULL DEFAULT 0,
  attributed_order_count BIGINT NOT NULL DEFAULT 0,
  next_day_return_count BIGINT NOT NULL DEFAULT 0,
  retention_3d_count BIGINT NOT NULL DEFAULT 0,
  retention_7d_count BIGINT NOT NULL DEFAULT 0,
  first_activate_count BIGINT NOT NULL DEFAULT 0,
  first_order_count BIGINT NOT NULL DEFAULT 0,
  reactivate_count BIGINT NOT NULL DEFAULT 0,
  custom_count BIGINT NOT NULL DEFAULT 0,
  credit_count BIGINT NOT NULL DEFAULT 0,
  callback_succeeded_count BIGINT NOT NULL DEFAULT 0,
  callback_failed_count BIGINT NOT NULL DEFAULT 0,
  spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  revision INT NOT NULL DEFAULT 1,
  data_status VARCHAR(24) NOT NULL DEFAULT 'PRELIMINARY',
  quality_code VARCHAR(64) NULL,
  source_watermark DATETIME(3) NULL,
  calculated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_report_hour_grain
    (report_run_id, stat_hour_utc, media_id, task_id, currency),
  KEY idx_scx_report_hour_query (report_date, media_id, local_hour, task_id),
  KEY idx_scx_report_hour_campaign (campaign_id, stat_hour_utc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='双事实源小时报表';

CREATE TABLE scx_report_backfill (
  id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  dry_run TINYINT(1) NOT NULL DEFAULT 1,
  status VARCHAR(24) NOT NULL COMMENT 'PENDING|RUNNING|SUCCEEDED|FAILED',
  processed_dates INT NOT NULL DEFAULT 0,
  old_row_count BIGINT NOT NULL DEFAULT 0,
  new_row_count BIGINT NOT NULL DEFAULT 0,
  difference_json JSON NULL,
  requested_by VARCHAR(64) NULL,
  started_at DATETIME(3) NULL,
  finished_at DATETIME(3) NULL,
  error_message VARCHAR(512) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_report_backfill_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表历史补算任务';

CREATE TABLE scx_report_reconciliation_issue (
  id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  report_date DATE NOT NULL,
  issue_type VARCHAR(32) NOT NULL COMMENT 'LATE_AFTER_FINAL|UNBOUND|CURRENCY_MISMATCH|MISSING_GOAL',
  resource_id VARCHAR(128) NULL,
  detail_json JSON NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  detected_at DATETIME(3) NOT NULL,
  resolved_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_scx_report_issue_scope (media_id, report_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表对账异常';

CREATE TABLE scx_report_lease (
  lease_key VARCHAR(190) NOT NULL,
  locked_by VARCHAR(128) NOT NULL,
  locked_until DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (lease_key),
  KEY idx_scx_report_lease_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表调度数据库租约';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_ad_report_sync','同步媒体报表','ad:report:sync','menu_ocpx_data',91,'1','app_base',NOW(),'0'),
  ('perm_ad_report_backfill','补算历史报表','ad:report:backfill','menu_ocpx_data',92,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v67a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id),3,'0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ad_report_sync','perm_ad_report_backfill')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v67o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id),3,'0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id='perm_ad_report_sync'
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';
