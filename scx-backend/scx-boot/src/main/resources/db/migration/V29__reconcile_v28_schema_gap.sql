-- Reconciles environments that were historically provisioned with a partial
-- MVP schema but whose migration history was later baselined at V28.
-- It is deliberately DDL-only: no sandbox endpoint or demo-chain data is seeded.

CREATE TABLE IF NOT EXISTS scx_upstream_customer (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_upstream_customer_media_code (media_id, code),
  KEY idx_scx_upstream_customer_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 上游客户';

CREATE TABLE IF NOT EXISTS scx_short_link (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  target_url VARCHAR(1000) NOT NULL,
  source_url VARCHAR(1000) NULL,
  link_type VARCHAR(16) NOT NULL DEFAULT 'GENERAL',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_short_link_code (code),
  KEY idx_scx_short_link_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 受控短链与DP转换';

CREATE TABLE IF NOT EXISTS scx_ad_platform_config (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  config_key VARCHAR(64) NOT NULL,
  config_value TEXT NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ad_config_media_key (media_id, config_key),
  KEY idx_scx_ad_config_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 广告业务配置';

CREATE TABLE IF NOT EXISTS scx_media_channel_user (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  account VARCHAR(128) NOT NULL,
  name VARCHAR(128) NOT NULL,
  phone VARCHAR(32) NULL,
  email VARCHAR(128) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_channel_user_account (channel_id, account),
  KEY idx_scx_channel_user_media_channel (media_id, channel_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 媒体渠道用户';

CREATE TABLE IF NOT EXISTS scx_delivery_config_revision (
  id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(16) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  config_version INT NOT NULL,
  snapshot_json JSON NOT NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_delivery_revision (resource_type, resource_id, config_version),
  KEY idx_scx_delivery_revision_lookup (resource_type, resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 投放配置版本快照';

CREATE TABLE IF NOT EXISTS scx_delivery_status_history (
  id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(16) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  previous_status VARCHAR(16) NOT NULL,
  next_status VARCHAR(16) NOT NULL,
  reason VARCHAR(500) NULL,
  operation_version INT NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_delivery_status_history (resource_type, resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 投放资源状态流转记录';

CREATE TABLE IF NOT EXISTS scx_user_task_scope (
  id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_user_task_scope (user_id, task_id),
  KEY idx_scx_task_scope_media_user (media_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 用户监测任务数据范围';

CREATE TABLE IF NOT EXISTS scx_rta_decision_log (
  id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  endpoint_id VARCHAR(64) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  decision VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64) NULL,
  latency_ms INT NULL,
  requested_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_rta_decision_task_request (task_id, request_id),
  KEY idx_scx_rta_decision_endpoint_time (endpoint_id, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RTA 决策运行记录';

DELIMITER $$
CREATE PROCEDURE scx_reconcile_v28_schema_gap()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_product' AND column_name='customer_id') THEN
    ALTER TABLE scx_product ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id, ADD KEY idx_scx_product_customer (customer_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_tracking_link' AND column_name='customer_id') THEN
    ALTER TABLE scx_tracking_link ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id, ADD KEY idx_scx_link_customer (customer_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_tracking_task' AND column_name='config_version') THEN
    ALTER TABLE scx_tracking_task ADD COLUMN config_version INT NOT NULL DEFAULT 1 AFTER callback_target_ref;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_report_daily' AND column_name='impression_count') THEN
    ALTER TABLE scx_report_daily ADD COLUMN impression_count BIGINT NOT NULL DEFAULT 0 AFTER task_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_report_daily' AND column_name='attributed_order_count') THEN
    ALTER TABLE scx_report_daily
      ADD COLUMN attributed_order_count BIGINT NOT NULL DEFAULT 0 AFTER attributed_pay_count,
      ADD COLUMN next_day_return_count BIGINT NOT NULL DEFAULT 0 AFTER attributed_order_count,
      ADD COLUMN retention_3d_count BIGINT NOT NULL DEFAULT 0 AFTER next_day_return_count,
      ADD COLUMN retention_7d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_3d_count,
      ADD COLUMN first_activate_count BIGINT NOT NULL DEFAULT 0 AFTER retention_7d_count,
      ADD COLUMN first_order_count BIGINT NOT NULL DEFAULT 0 AFTER first_activate_count,
      ADD COLUMN reactivate_count BIGINT NOT NULL DEFAULT 0 AFTER first_order_count,
      ADD COLUMN custom_count BIGINT NOT NULL DEFAULT 0 AFTER reactivate_count,
      ADD COLUMN credit_count BIGINT NOT NULL DEFAULT 0 AFTER custom_count;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_report_daily' AND column_name='customer_id') THEN
    ALTER TABLE scx_report_daily
      ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id,
      ADD COLUMN document_id VARCHAR(64) NULL AFTER link_id,
      ADD COLUMN channel_id VARCHAR(64) NULL AFTER task_id,
      ADD KEY idx_scx_report_daily_customer (report_date, media_id, customer_id),
      ADD KEY idx_scx_report_daily_channel (report_date, media_id, channel_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_export_task' AND column_name='task_scope_snapshot') THEN
    ALTER TABLE scx_export_task ADD COLUMN task_scope_snapshot TEXT NULL AFTER report_end_date;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_link_event_mapping' AND column_name='unit_price') THEN
    ALTER TABLE scx_link_event_mapping ADD COLUMN unit_price DECIMAL(18,6) NOT NULL DEFAULT 0 AFTER external_event;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_link_event_mapping' AND column_name='daily_limit') THEN
    ALTER TABLE scx_link_event_mapping ADD COLUMN daily_limit INT NULL AFTER unit_price;
  END IF;
END$$
DELIMITER ;

CALL scx_reconcile_v28_schema_gap();
DROP PROCEDURE scx_reconcile_v28_schema_gap;
