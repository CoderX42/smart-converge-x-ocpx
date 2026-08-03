-- V70: add the independent OCPX forwarding data plane without reviving retired domains.
-- This is a forward-only migration. Existing V68/V69 migrations remain immutable.

DROP PROCEDURE IF EXISTS scx_upgrade_ocpx_forwarding_engine;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_forwarding_engine()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='channel_code') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN channel_code VARCHAR(64) NULL AFTER id;
    UPDATE scx_adx_channel SET channel_code=CONCAT('channel_',LEFT(id,16)) WHERE channel_code IS NULL;
    ALTER TABLE scx_adx_channel MODIFY channel_code VARCHAR(64) NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='adapter_code') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'STANDARD' AFTER channel_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='auth_type') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN auth_type VARCHAR(16) NOT NULL DEFAULT 'HMAC' AFTER adapter_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='key_version') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN key_version INT NOT NULL DEFAULT 1 AFTER auth_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='status') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='callback_host_allowlist') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN callback_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND index_name='uk_scx_adx_channel_code') THEN
    ALTER TABLE scx_adx_channel ADD UNIQUE KEY uk_scx_adx_channel_code (channel_code,active_marker);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='adapter_code') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'COCO' AFTER advertiser_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='status') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER balance;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='upstream_host_allowlist') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN upstream_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='downstream_callback_url') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN downstream_callback_url VARCHAR(1500) NULL AFTER click_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='route_status') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN route_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER downstream_callback_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER route_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='rta_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN rta_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER config_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='billing_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN billing_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER rta_mode;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_at') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_at DATETIME(3) NULL AFTER billing_mode;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_deduction_rule' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_deduction_rule ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER ratio;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='raw_conversion_count') THEN
    ALTER TABLE scx_adx_hourly_report
      ADD COLUMN raw_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER finish_order_count,
      ADD COLUMN forwarded_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER raw_conversion_count,
      ADD COLUMN deducted_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER forwarded_conversion_count,
      ADD COLUMN delivery_failure_count BIGINT NOT NULL DEFAULT 0 AFTER deducted_conversion_count;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_ocpx_forwarding_engine();
DROP PROCEDURE scx_upgrade_ocpx_forwarding_engine;

CREATE TABLE IF NOT EXISTS scx_ocpx_adapter (
  adapter_code VARCHAR(32) NOT NULL,
  adapter_name VARCHAR(100) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  capabilities_json JSON NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (adapter_code),
  KEY idx_scx_ocpx_adapter_direction (direction,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Published OCPX adapter registry';

INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('STANDARD','标准 FeedbackReq/CallbackReq','DOWNSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','POST','callbackMethod','POST','auth','HMAC','callback',TRUE),NOW(3),NOW(3)),
  ('COCO','Coco CPA','UPSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','GET','callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000),NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE adapter_name=VALUES(adapter_name),direction=VALUES(direction),status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),update_time=NOW(3);

CREATE TABLE IF NOT EXISTS scx_ocpx_event (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  parent_event_id VARCHAR(128) NULL,
  source_direction VARCHAR(32) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  device_key_hash CHAR(64) NULL,
  payload_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  callback_ref VARCHAR(32) NULL,
  amount DECIMAL(20,6) NULL,
  amount_raw VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_event_source (source_direction,event_id),
  KEY idx_scx_ocpx_event_parent (parent_event_id,event_type),
  KEY idx_scx_ocpx_event_route (monitor_id,occurred_at),
  KEY idx_scx_ocpx_event_received (received_at,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX immutable normalized event';

CREATE TABLE IF NOT EXISTS scx_ocpx_protocol_snapshot (
  feedback_event_id VARCHAR(128) NOT NULL,
  feedback_ciphertext MEDIUMTEXT NOT NULL,
  route_ciphertext TEXT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (feedback_event_id),
  KEY idx_scx_ocpx_snapshot_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Encrypted minimum protocol retry snapshot';

CREATE TABLE IF NOT EXISTS scx_ocpx_callback_ref (
  id VARCHAR(32) NOT NULL,
  feedback_event_id VARCHAR(128) NOT NULL,
  route_version BIGINT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_callback_feedback (feedback_event_id),
  KEY idx_scx_ocpx_callback_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Opaque upstream callback correlation';

CREATE TABLE IF NOT EXISTS scx_ocpx_delivery_job (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  adapter_code VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NOT NULL,
  lease_owner VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  callback_ref VARCHAR(32) NULL,
  last_error VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_delivery_event (direction,event_id),
  KEY idx_scx_ocpx_delivery_claim (status,next_retry_at,lease_until),
  KEY idx_scx_ocpx_delivery_source (source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reliable outbound delivery job';

CREATE TABLE IF NOT EXISTS scx_ocpx_delivery_attempt (
  id VARCHAR(32) NOT NULL,
  job_id VARCHAR(32) NOT NULL,
  attempt_no INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  http_status INT NULL,
  error_message VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_attempt_job (job_id,attempt_no),
  KEY idx_scx_ocpx_attempt_time (create_time,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbound delivery attempt audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_deduction_decision (
  id VARCHAR(32) NOT NULL,
  callback_event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  rule_version BIGINT NOT NULL,
  ratio DECIMAL(8,4) NOT NULL,
  forwarded TINYINT(1) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_deduction_event (callback_event_id),
  KEY idx_scx_ocpx_deduction_route (monitor_id,event_type,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable callback deduction decision';

CREATE TABLE IF NOT EXISTS scx_ocpx_ingress_dlq (
  id VARCHAR(32) NOT NULL,
  partition_no INT NOT NULL,
  stream_record_id VARCHAR(64) NOT NULL,
  event_id VARCHAR(128) NULL,
  envelope MEDIUMTEXT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  replayed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_ingress_dlq_record (partition_no,stream_record_id),
  KEY idx_scx_ocpx_ingress_dlq_status (status,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Feedback ingress dead-letter audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_config_outbox (
  id VARCHAR(32) NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(32) NOT NULL,
  config_version BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL,
  published_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_config_version (aggregate_type,aggregate_id,config_version),
  KEY idx_scx_ocpx_config_outbox (published,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional configuration publication outbox';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_decision_log (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(128) NULL,
  latency_ms INT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_rta_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reserved RTA decision audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_billing_entry (
  id VARCHAR(32) NOT NULL,
  charge_event_id VARCHAR(128) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  amount DECIMAL(20,6) NULL,
  currency CHAR(3) NULL,
  mode VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(128) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_billing_event (charge_event_id),
  KEY idx_scx_ocpx_billing_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reserved idempotent billing ledger';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_adx_runtime','转发运行中心','/adx/runtime','0','lucide:activity','adx/runtime/index',45,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_runtime_read','查看转发状态','adx:runtime:read','menu_adx_runtime',10,'1','app_base',NOW(),'0'),
  ('perm_adx_runtime_replay','重放失败任务','adx:runtime:replay','menu_adx_runtime',20,'1','app_base',NOW(),'0'),
  ('perm_adx_monitor_publish','发布路由配置','adx:monitor:publish','menu_adx_monitor',30,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_runtime','perm_adx_runtime_read','perm_adx_runtime_replay','perm_adx_monitor_publish')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_runtime','perm_adx_runtime_read')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
