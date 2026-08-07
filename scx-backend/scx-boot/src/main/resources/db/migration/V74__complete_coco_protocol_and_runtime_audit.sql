-- Coco v1.1 protocol completion and runtime request audit.
-- Forward-only migration: do not amend V70-V73 after they may have been applied.

ALTER TABLE scx_adx_advertiser
  MODIFY COLUMN bid_url VARCHAR(5000) NOT NULL;

ALTER TABLE scx_adx_monitor_link
  ADD COLUMN upstream_feedback_url VARCHAR(5000) NULL AFTER click_url,
  ADD COLUMN published_upstream_feedback_url VARCHAR(5000) NULL AFTER upstream_feedback_url;

ALTER TABLE scx_adx_hourly_report
  ADD COLUMN first_order_count BIGINT NOT NULL DEFAULT 0 AFTER order_count;

ALTER TABLE scx_ocpx_delivery_attempt
  ADD COLUMN request_method VARCHAR(16) NULL AFTER http_status,
  ADD COLUMN target_host VARCHAR(255) NULL AFTER request_method,
  ADD COLUMN target_path VARCHAR(1000) NULL AFTER target_host,
  ADD COLUMN request_summary VARCHAR(4000) NULL AFTER target_path,
  ADD COLUMN response_excerpt VARCHAR(2000) NULL AFTER request_summary,
  ADD COLUMN duration_ms BIGINT NULL AFTER response_excerpt;

CREATE TABLE IF NOT EXISTS scx_ocpx_ingress_audit (
  id VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NULL,
  channel_code VARCHAR(64) NULL,
  event_id VARCHAR(128) NULL,
  route_reference VARCHAR(100) NULL,
  result_status VARCHAR(32) NOT NULL,
  http_status INT NOT NULL,
  reason VARCHAR(1000) NULL,
  payload_hash CHAR(64) NULL,
  client_id VARCHAR(128) NULL,
  key_version VARCHAR(32) NULL,
  remote_ip_masked VARCHAR(64) NULL,
  duration_ms BIGINT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_ingress_audit_time (create_time,result_status),
  KEY idx_scx_ocpx_ingress_audit_event (event_id),
  KEY idx_scx_ocpx_ingress_audit_route (channel_code,route_reference,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Best-effort rejected and abnormal feedback ingress audit';
