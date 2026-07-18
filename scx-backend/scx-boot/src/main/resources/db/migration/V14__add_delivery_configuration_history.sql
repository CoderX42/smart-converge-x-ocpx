-- 链接/任务配置与状态均需要可追溯，不能只依赖当前行和系统日志。
ALTER TABLE scx_tracking_task
  ADD COLUMN config_version INT NOT NULL DEFAULT 1 AFTER callback_target_ref;

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
