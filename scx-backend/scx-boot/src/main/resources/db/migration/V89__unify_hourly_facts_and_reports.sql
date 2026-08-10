-- S8 unified hourly facts and reports. Forward-only and replay-safe on MySQL 8.x.
-- Existing report/event rows are explicitly UNMAPPED. This migration never infers
-- Product, Task, MediaAccount, operator or RouteVersion from mutable names or URLs.

DROP PROCEDURE IF EXISTS scx_upgrade_v89_hourly_fact;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v89_hourly_fact()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='scx_adx_hourly_report' AND index_name='uk_scx_adx_report_grain') THEN
    ALTER TABLE scx_adx_hourly_report DROP INDEX uk_scx_adx_report_grain;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='scx_adx_hourly_report' AND column_name='tenant_id') THEN
    ALTER TABLE scx_adx_hourly_report
      ADD COLUMN tenant_id VARCHAR(32) NULL AFTER time_hour,
      ADD COLUMN stat_date DATE GENERATED ALWAYS AS (DATE(time_hour)) STORED AFTER tenant_id,
      ADD COLUMN stat_hour TINYINT GENERATED ALWAYS AS (HOUR(time_hour)) STORED AFTER stat_date,
      ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER stat_hour,
      ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER app_link_id,
      ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN operator_user_id VARCHAR(32) NULL AFTER media_account_id,
      ADD COLUMN route_version_id VARCHAR(32) NULL AFTER monitor_id,
      ADD COLUMN route_version BIGINT NULL AFTER route_version_id,
      ADD COLUMN dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED' AFTER route_version,
      ADD COLUMN raw_clicks BIGINT NOT NULL DEFAULT 0 AFTER clicks,
      ADD COLUMN valid_clicks BIGINT NOT NULL DEFAULT 0 AFTER raw_clicks,
      ADD COLUMN duplicate_clicks BIGINT NOT NULL DEFAULT 0 AFTER valid_clicks,
      ADD COLUMN adjustment_count BIGINT NOT NULL DEFAULT 0 AFTER duplicate_clicks,
      ADD COLUMN deduction_count BIGINT NOT NULL DEFAULT 0 AFTER adjustment_count,
      ADD COLUMN adjustment_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 AFTER deduction_count,
      ADD COLUMN deduction_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 AFTER adjustment_amount,
      ADD COLUMN source_watermark DATETIME(3) NULL AFTER deduction_amount,
      ADD COLUMN as_of DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER source_watermark,
      ADD COLUMN finality VARCHAR(16) NOT NULL DEFAULT 'PROVISIONAL' AFTER as_of,
      ADD COLUMN quality_status VARCHAR(24) NOT NULL DEFAULT 'UNMAPPED' AFTER finality,
      ADD COLUMN metric_definitions_version VARCHAR(32) NOT NULL DEFAULT 'S8_V1' AFTER quality_status,
      ADD COLUMN dimension_key CHAR(64) GENERATED ALWAYS AS
        (SHA2(CONCAT_WS('|',COALESCE(tenant_id,'~'),COALESCE(advertiser_id,'~'),
          COALESCE(product_id,'~'),COALESCE(app_link_id,'~'),COALESCE(task_id,'~'),
          COALESCE(channel_id,'~'),COALESCE(media_account_id,'~'),COALESCE(operator_user_id,'~'),
          COALESCE(monitor_id,'~'),COALESCE(route_version_id,'~'),COALESCE(route_version,-1)),256)) STORED;
  END IF;

  UPDATE scx_adx_hourly_report
  SET raw_clicks=clicks,valid_clicks=eligible_click_count,
      duplicate_clicks=deduplicated_click_count,dimension_status='UNMAPPED',
      quality_status='UNMAPPED',finality='PROVISIONAL',timezone='Asia/Shanghai',
      metric_definitions_version='S8_V1',as_of=COALESCE(as_of,update_time,NOW(3))
  WHERE dimension_status='UNMAPPED';

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='scx_adx_hourly_report' AND index_name='uk_scx_adx_hourly_frozen_grain') THEN
    ALTER TABLE scx_adx_hourly_report
      ADD UNIQUE KEY uk_scx_adx_hourly_frozen_grain (time_hour,dimension_key),
      ADD KEY idx_scx_adx_hourly_time_task (time_hour,task_id,app_link_id),
      ADD KEY idx_scx_adx_hourly_scope (time_hour,advertiser_id,channel_id,media_account_id),
      ADD KEY idx_scx_adx_hourly_product (time_hour,product_id,app_link_id),
      ADD KEY idx_scx_adx_hourly_media (time_hour,media_account_id,channel_id),
      ADD KEY idx_scx_adx_hourly_freshness (finality,source_watermark,as_of);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='scx_adx_hourly_report' AND constraint_name='ck_scx_adx_hourly_v89_dimensions') THEN
    ALTER TABLE scx_adx_hourly_report
      ADD CONSTRAINT ck_scx_adx_hourly_v89_dimensions CHECK (
        timezone='Asia/Shanghai' AND stat_hour BETWEEN 0 AND 23 AND
        dimension_status IN ('UNMAPPED','FROZEN') AND
        ((dimension_status='UNMAPPED') OR
         (tenant_id IS NOT NULL AND product_id IS NOT NULL AND task_id IS NOT NULL AND
          media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND
          route_version_id IS NOT NULL AND route_version>=1)) AND
        raw_clicks>=0 AND valid_clicks>=0 AND duplicate_clicks>=0 AND
        adjustment_count>=0 AND deduction_count>=0 AND
        finality IN ('DELAYED','PROVISIONAL','FINAL') AND
        quality_status IN ('COMPLETE','PARTIAL','UNMAPPED') AND
        metric_definitions_version='S8_V1'
      );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='scx_ocpx_event' AND column_name='tenant_id') THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN tenant_id VARCHAR(32) NULL AFTER event_type,
      ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER app_link_id,
      ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN operator_user_id VARCHAR(32) NULL AFTER media_account_id,
      ADD COLUMN route_version_id VARCHAR(32) NULL AFTER monitor_id,
      ADD COLUMN dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED' AFTER route_version,
      ADD KEY idx_scx_ocpx_event_frozen_hour (occurred_at,task_id,route_version_id),
      ADD KEY idx_scx_ocpx_event_frozen_scope (advertiser_id,channel_id,media_account_id,occurred_at);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v89_hourly_fact();
DROP PROCEDURE scx_upgrade_v89_hourly_fact;

CREATE TABLE IF NOT EXISTS scx_adx_hourly_fact_contribution (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  contribution_kind VARCHAR(32) NOT NULL,
  tenant_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(32) NOT NULL,
  monitor_route_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  stat_hour DATETIME NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  impressions BIGINT NOT NULL DEFAULT 0,
  raw_clicks BIGINT NOT NULL DEFAULT 0,
  valid_clicks BIGINT NOT NULL DEFAULT 0,
  duplicate_clicks BIGINT NOT NULL DEFAULT 0,
  skipped_clicks BIGINT NOT NULL DEFAULT 0,
  activation_count BIGINT NOT NULL DEFAULT 0,
  registration_count BIGINT NOT NULL DEFAULT 0,
  payment_count BIGINT NOT NULL DEFAULT 0,
  order_count BIGINT NOT NULL DEFAULT 0,
  first_activation_count BIGINT NOT NULL DEFAULT 0,
  first_order_count BIGINT NOT NULL DEFAULT 0,
  reactivation_count BIGINT NOT NULL DEFAULT 0,
  retention_d2_count BIGINT NOT NULL DEFAULT 0,
  retention_d3_count BIGINT NOT NULL DEFAULT 0,
  retention_d7_count BIGINT NOT NULL DEFAULT 0,
  raw_conversion_count BIGINT NOT NULL DEFAULT 0,
  forwarded_conversion_count BIGINT NOT NULL DEFAULT 0,
  delivery_failure_count BIGINT NOT NULL DEFAULT 0,
  adjustment_count BIGINT NOT NULL DEFAULT 0,
  deduction_count BIGINT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  event_counts_json JSON NULL,
  aggregated_at DATETIME(3) NULL,
  aggregate_run_id VARCHAR(32) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_adx_fact_contribution_id (id),
  UNIQUE KEY uk_scx_adx_fact_contribution_source (source_event_id,contribution_kind),
  KEY idx_scx_adx_fact_contribution_claim (aggregated_at,sequence_no),
  KEY idx_scx_adx_fact_contribution_hour (stat_hour,task_id,app_link_id),
  KEY idx_scx_adx_fact_contribution_scope (advertiser_id,channel_id,media_account_id,stat_hour),
  CONSTRAINT ck_scx_adx_fact_contribution CHECK (
    contribution_kind IN ('FEEDBACK','CALLBACK','DELIVERY_SUCCESS','DELIVERY_FAILURE') AND
    route_version>=1 AND timezone='Asia/Shanghai' AND stat_hour=DATE_FORMAT(event_time,'%Y-%m-%d %H:00:00') AND
    impressions>=0 AND raw_clicks>=0 AND valid_clicks>=0 AND duplicate_clicks>=0 AND skipped_clicks>=0 AND
    activation_count>=0 AND registration_count>=0 AND payment_count>=0 AND order_count>=0 AND
    first_activation_count>=0 AND first_order_count>=0 AND reactivation_count>=0 AND
    retention_d2_count>=0 AND retention_d3_count>=0 AND retention_d7_count>=0 AND
    raw_conversion_count>=0 AND forwarded_conversion_count>=0 AND delivery_failure_count>=0 AND
    adjustment_count>=0 AND deduction_count>=0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Immutable idempotent S8 source contributions';

CREATE TABLE IF NOT EXISTS scx_adx_hourly_aggregation_cursor (
  source_name VARCHAR(64) NOT NULL,
  partition_no INT NOT NULL DEFAULT 0,
  last_sequence_no BIGINT NOT NULL DEFAULT 0,
  source_watermark DATETIME(3) NULL,
  last_run_id VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'IDLE',
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (source_name,partition_no),
  KEY idx_scx_adx_aggregate_cursor_lease (status,lease_until),
  CONSTRAINT ck_scx_adx_aggregate_cursor CHECK (
    partition_no>=0 AND last_sequence_no>=0 AND status IN ('IDLE','RUNNING','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S8 incremental aggregation cursor';

CREATE TABLE IF NOT EXISTS scx_adx_hourly_adjustment (
  id VARCHAR(32) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  source_hourly_fact_id VARCHAR(32) NULL,
  adjustment_type VARCHAR(16) NOT NULL,
  metric_code VARCHAR(64) NOT NULL,
  delta_count BIGINT NOT NULL DEFAULT 0,
  delta_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  reason_code VARCHAR(64) NOT NULL,
  rule_version BIGINT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  monitor_route_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  stat_hour DATETIME NOT NULL,
  actor VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_hourly_adjustment_source (source_event_id,adjustment_type,metric_code,reason_code),
  KEY idx_scx_adx_hourly_adjustment_fact (source_hourly_fact_id,create_time),
  KEY idx_scx_adx_hourly_adjustment_scope (stat_hour,advertiser_id,channel_id,media_account_id),
  CONSTRAINT fk_scx_adx_hourly_adjustment_fact FOREIGN KEY (source_hourly_fact_id)
    REFERENCES scx_adx_hourly_report(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_hourly_adjustment CHECK (
    adjustment_type IN ('ADJUSTMENT','DEDUCTION') AND metric_code REGEXP '^[a-z][A-Za-z0-9]{1,63}$' AND
    route_version_id<>'' AND reason_code<>''
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable S8 adjustment and deduction facts';

CREATE TABLE IF NOT EXISTS scx_adx_report_export_task (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  export_type VARCHAR(24) NOT NULL,
  query_json JSON NOT NULL,
  query_hash CHAR(64) NOT NULL,
  scope_json JSON NOT NULL,
  scope_hash CHAR(64) NOT NULL,
  idempotency_bucket DATETIME NOT NULL,
  metric_definitions_version VARCHAR(32) NOT NULL DEFAULT 'S8_V1',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  storage_config_id VARCHAR(32) NULL,
  storage_provider VARCHAR(32) NULL,
  storage_object_key VARCHAR(1024) NULL,
  storage_object_url VARCHAR(2048) NULL,
  file_size BIGINT NULL,
  row_count BIGINT NULL,
  expires_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_report_export_idempotency
    (user_id,scope_hash,query_hash,export_type,idempotency_bucket),
  KEY idx_scx_adx_report_export_claim (status,next_attempt_at,lease_until),
  KEY idx_scx_adx_report_export_expiry (status,expires_at),
  CONSTRAINT ck_scx_adx_report_export CHECK (
    export_type IN ('STANDARD','DEDUCTION') AND metric_definitions_version='S8_V1' AND
    status IN ('PENDING','PROCESSING','SUCCEEDED','RETRY_WAIT','FAILED','CANCELLED','EXPIRED') AND
    attempt_count>=0 AND (file_size IS NULL OR file_size>=0) AND (row_count IS NULL OR row_count>=0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S8 asynchronous managed-storage export task';

CREATE TABLE IF NOT EXISTS scx_adx_report_export_audit (
  id VARCHAR(32) NOT NULL,
  export_task_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  result VARCHAR(16) NOT NULL,
  query_hash CHAR(64) NOT NULL,
  scope_hash CHAR(64) NOT NULL,
  row_count BIGINT NULL,
  detail_summary VARCHAR(500) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_report_export_audit_task (export_task_id,create_time),
  KEY idx_scx_adx_report_export_audit_user (user_id,create_time),
  CONSTRAINT fk_scx_adx_report_export_audit_task FOREIGN KEY (export_task_id)
    REFERENCES scx_adx_report_export_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_report_export_audit CHECK (
    action IN ('CREATE','CLAIM','SUCCEED','FAIL','RETRY','DOWNLOAD','CANCEL','EXPIRE','CLEANUP') AND
    result IN ('SUCCESS','DENIED','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payload-free S8 export audit';

DROP TRIGGER IF EXISTS trg_scx_adx_hourly_adjustment_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_hourly_adjustment_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_adx_hourly_adjustment_no_update BEFORE UPDATE ON scx_adx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts are immutable'$$
CREATE TRIGGER trg_scx_adx_hourly_adjustment_no_delete BEFORE DELETE ON scx_adx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts cannot be deleted'$$
DELIMITER ;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_report_time_v89','数据汇总','/adx/report/time','menu_adx_report','lucide:clock-3','adx/report/index','adx:report:view',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_adx_report_product_v89','产品汇总','/adx/report/product','menu_adx_report','lucide:package','adx/report/index','adx:report:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_adx_report_link_v89','链接汇总','/adx/report/link','menu_adx_report','lucide:link-2','adx/report/index','adx:report:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_adx_report_task_v89','任务汇总','/adx/report/task','menu_adx_report','lucide:list-checks','adx/report/index','adx:report:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_adx_report_media_v89','媒体汇总','/adx/report/media','menu_adx_report','lucide:radio-tower','adx/report/index','adx:report:view',50,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_report_view_v89','查看统一报表','','menu_adx_report',NULL,NULL,'adx:report:view',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_report_export_v89','创建普通导出','','menu_adx_report',NULL,NULL,'adx:report:export',110,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_report_deduction_export_v89','创建审减导出','','menu_adx_report',NULL,NULL,'adx:report:deduction:export',120,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_report_sensitive_v89','查看敏感指标','','menu_adx_report',NULL,NULL,'adx:report:metric:sensitive',130,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id IN ('menu_adx_report_time','perm_adx_report_read');

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_report','menu_adx_report_time_v89','menu_adx_report_product_v89',
  'menu_adx_report_link_v89','menu_adx_report_task_v89','menu_adx_report_media_v89','perm_adx_report_view_v89',
  'perm_adx_report_export_v89','perm_adx_report_deduction_export_v89','perm_adx_report_sensitive_v89')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_report','menu_adx_report_time_v89','menu_adx_report_product_v89',
  'menu_adx_report_link_v89','menu_adx_report_task_v89','menu_adx_report_media_v89','perm_adx_report_view_v89',
  'perm_adx_report_export_v89')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_report','menu_adx_report_time_v89','menu_adx_report_product_v89',
  'menu_adx_report_link_v89','menu_adx_report_task_v89','menu_adx_report_media_v89','perm_adx_report_view_v89')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
