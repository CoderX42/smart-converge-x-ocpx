CREATE TABLE IF NOT EXISTS scx_channel_client (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  client_id VARCHAR(64) NOT NULL,
  source_code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  max_qps INT NOT NULL DEFAULT 2000,
  dynamic_callback_enabled TINYINT(1) NOT NULL DEFAULT 0,
  callback_allowed_hosts VARCHAR(1000),
  current_key_version VARCHAR(32) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_channel_client_id (client_id),
  KEY idx_scx_channel_client_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX OCPX 渠道客户端';

CREATE TABLE IF NOT EXISTS scx_channel_credential (
  id VARCHAR(64) NOT NULL,
  channel_client_id VARCHAR(64) NOT NULL,
  key_version VARCHAR(32) NOT NULL,
  encrypted_secret VARCHAR(1024) NOT NULL,
  secret_iv VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  valid_from DATETIME(3) NOT NULL,
  valid_until DATETIME(3),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_channel_credential_version (channel_client_id, key_version),
  KEY idx_scx_channel_credential_active (channel_client_id, status, valid_from, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX OCPX 渠道密钥密文';

CREATE TABLE IF NOT EXISTS scx_click_callback_target (
  click_event_id VARCHAR(64) NOT NULL,
  target_ref VARCHAR(128),
  encrypted_url TEXT,
  url_iv VARCHAR(128),
  url_host VARCHAR(255),
  url_fingerprint VARCHAR(64),
  key_version VARCHAR(32),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (click_event_id),
  KEY idx_scx_click_callback_host (url_host)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 点击对应的安全回传目标';

CREATE TABLE IF NOT EXISTS scx_click_dedup_guard (
  guard_key VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  device_id_type VARCHAR(32) NOT NULL,
  device_key_hash VARCHAR(64) NOT NULL,
  rule_version VARCHAR(32) NOT NULL,
  canonical_click_event_id VARCHAR(64),
  window_started_at DATETIME(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (guard_key),
  KEY idx_scx_click_guard_task (task_id, rule_version, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 点击验重并发串行化守卫';

DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_channel_ingress()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='client_id') THEN
    ALTER TABLE scx_raw_event ADD COLUMN client_id VARCHAR(64) NULL AFTER source;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='receipt_id') THEN
    ALTER TABLE scx_raw_event ADD COLUMN receipt_id VARCHAR(64) NULL AFTER event_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='device_platform') THEN
    ALTER TABLE scx_raw_event ADD COLUMN device_platform VARCHAR(16) NULL AFTER received_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='device_id_type') THEN
    ALTER TABLE scx_raw_event ADD COLUMN device_id_type VARCHAR(32) NULL AFTER device_platform;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='device_id_version') THEN
    ALTER TABLE scx_raw_event ADD COLUMN device_id_version VARCHAR(32) NULL AFTER device_id_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='device_key_version') THEN
    ALTER TABLE scx_raw_event ADD COLUMN device_key_version VARCHAR(32) NULL AFTER device_id_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='processing_status') THEN
    ALTER TABLE scx_raw_event ADD COLUMN processing_status VARCHAR(16) NOT NULL DEFAULT 'PROCESSED' AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='processed_at') THEN
    ALTER TABLE scx_raw_event ADD COLUMN processed_at DATETIME(3) NULL AFTER processing_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='locked_by') THEN
    ALTER TABLE scx_raw_event ADD COLUMN locked_by VARCHAR(64) NULL AFTER processed_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND column_name='locked_until') THEN
    ALTER TABLE scx_raw_event ADD COLUMN locked_until DATETIME(3) NULL AFTER locked_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND index_name='uk_scx_raw_receipt') THEN
    ALTER TABLE scx_raw_event ADD UNIQUE KEY uk_scx_raw_receipt (receipt_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND index_name='uk_scx_raw_client_event') THEN
    ALTER TABLE scx_raw_event ADD UNIQUE KEY uk_scx_raw_client_event (client_id, event_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND index_name='idx_scx_raw_processing') THEN
    ALTER TABLE scx_raw_event ADD KEY idx_scx_raw_processing (processing_status, locked_until, received_at);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND column_name='locked_by') THEN
    ALTER TABLE scx_event_outbox ADD COLUMN locked_by VARCHAR(64) NULL AFTER next_retry_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND column_name='locked_until') THEN
    ALTER TABLE scx_event_outbox ADD COLUMN locked_until DATETIME(3) NULL AFTER locked_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND column_name='last_error_code') THEN
    ALTER TABLE scx_event_outbox ADD COLUMN last_error_code VARCHAR(64) NULL AFTER locked_until;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND column_name='last_error_message') THEN
    ALTER TABLE scx_event_outbox ADD COLUMN last_error_message VARCHAR(500) NULL AFTER last_error_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND index_name='uk_scx_outbox_aggregate_type') THEN
    ALTER TABLE scx_event_outbox ADD UNIQUE KEY uk_scx_outbox_aggregate_type (aggregate_id, event_type);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND index_name='idx_scx_outbox_lease') THEN
    ALTER TABLE scx_event_outbox ADD KEY idx_scx_outbox_lease (status, locked_until, next_retry_at, create_time);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name='adapter_code') THEN
    ALTER TABLE scx_callback_job ADD COLUMN adapter_code VARCHAR(64) NULL AFTER target_ref;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name='locked_by') THEN
    ALTER TABLE scx_callback_job ADD COLUMN locked_by VARCHAR(64) NULL AFTER next_retry_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name='locked_until') THEN
    ALTER TABLE scx_callback_job ADD COLUMN locked_until DATETIME(3) NULL AFTER locked_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND index_name='idx_scx_callback_lease') THEN
    ALTER TABLE scx_callback_job ADD KEY idx_scx_callback_lease (status, locked_until, next_retry_at, create_time);
  END IF;
END$$
DELIMITER ;

CALL scx_upgrade_ocpx_channel_ingress();
DROP PROCEDURE scx_upgrade_ocpx_channel_ingress;
