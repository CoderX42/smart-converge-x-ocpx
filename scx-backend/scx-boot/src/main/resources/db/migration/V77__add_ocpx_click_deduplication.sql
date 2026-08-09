-- Forward-only OCPX click-deduplication foundation.
-- Keep accepted click facts immutable; the decision table controls billing and forwarding.

DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_click_deduplication()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link'
      AND column_name='click_dedup_window_seconds'
  ) THEN
    ALTER TABLE scx_adx_monitor_link
      ADD COLUMN click_dedup_window_seconds INT NOT NULL DEFAULT 3600 AFTER billing_mode;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_platform'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_platform VARCHAR(16) NULL AFTER received_at;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_id_type'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_id_type VARCHAR(32) NULL AFTER device_platform;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_key_version'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_key_version INT NULL AFTER device_id_type;
  END IF;
END$$
DELIMITER ;

CALL scx_upgrade_ocpx_click_deduplication();
DROP PROCEDURE scx_upgrade_ocpx_click_deduplication;

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_state (
  monitor_id VARCHAR(32) NOT NULL,
  device_key_hash CHAR(64) NOT NULL,
  canonical_event_id VARCHAR(128) NOT NULL,
  canonical_received_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (monitor_id, device_key_hash),
  KEY idx_scx_ocpx_click_state_time (canonical_received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Latest eligible click per route and device';

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_decision (
  event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  device_key_hash CHAR(64) NULL,
  window_seconds INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  canonical_event_id VARCHAR(128) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY idx_scx_ocpx_click_decision_route (monitor_id, received_at, status),
  KEY idx_scx_ocpx_click_decision_device (monitor_id, device_key_hash, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable OCPX click deduplication decision';
