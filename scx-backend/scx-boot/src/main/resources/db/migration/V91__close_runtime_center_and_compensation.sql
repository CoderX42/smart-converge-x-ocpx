-- S10 runtime centre and failure compensation closure.
--
-- Runtime facts/failures/attempts are append-only. Replay requests are the
-- recoverable command truth and never contain a raw payload, URL, header,
-- credential or third-party response body. Legacy OCPX rows are deliberately
-- not backfilled because their frozen Product/Task/MediaAccount dimensions
-- cannot be inferred safely.

CREATE TABLE IF NOT EXISTS scx_adx_runtime_fact (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  acceptance_status VARCHAR(24) NOT NULL,
  processing_status VARCHAR(24) NOT NULL,
  tenant_id VARCHAR(32) NULL,
  advertiser_id VARCHAR(32) NULL,
  product_id VARCHAR(32) NULL,
  app_link_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  channel_id VARCHAR(32) NULL,
  media_account_id VARCHAR(32) NULL,
  operator_user_id VARCHAR(32) NULL,
  route_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  event_definition_id VARCHAR(32) NULL,
  event_code VARCHAR(64) NULL,
  event_snapshot_schema_version INT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  request_id_hash CHAR(64) NULL,
  trace_id VARCHAR(128) NULL,
  correlation_id_hash CHAR(64) NULL,
  external_click_id_hash CHAR(64) NULL,
  media_task_id_hash CHAR(64) NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NULL,
  summary_code VARCHAR(64) NOT NULL,
  dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED',
  quality_status VARCHAR(24) NOT NULL DEFAULT 'UNMAPPED',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_adx_runtime_fact_id (id),
  UNIQUE KEY uk_scx_adx_runtime_fact_idempotency (idempotency_key_hash),
  KEY idx_scx_adx_runtime_fact_time_scope (advertiser_id,channel_id,media_account_id,received_time),
  KEY idx_scx_adx_runtime_fact_task_time (task_id,received_time,fact_type),
  KEY idx_scx_adx_runtime_fact_trace (trace_id,received_time),
  KEY idx_scx_adx_runtime_fact_route (route_version_id,received_time),
  CONSTRAINT ck_scx_adx_runtime_fact CHECK (
    source_type IN ('FEEDBACK','CALLBACK','DELIVERY','CONFIG_DISTRIBUTION','REPLAY','LEGACY') AND
    fact_type IN ('INTEGRATION','CLICK','CONVERSION','DELIVERY_ATTEMPT','CONFIG_APPLY','REPLAY_RESULT') AND
    direction IN ('INGRESS','UPSTREAM','DOWNSTREAM','INTERNAL') AND
    acceptance_status IN ('ACCEPTED','REJECTED','DUPLICATE','UNMAPPED') AND
    processing_status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','DLQ','ISOLATED') AND
    dimension_status IN ('FROZEN','UNMAPPED') AND quality_status IN ('COMPLETE','PARTIAL','UNMAPPED') AND
    CHAR_LENGTH(idempotency_key_hash)=64 AND
    (payload_hash IS NULL OR CHAR_LENGTH(payload_hash)=64) AND
    (request_id_hash IS NULL OR CHAR_LENGTH(request_id_hash)=64) AND
    (correlation_id_hash IS NULL OR CHAR_LENGTH(correlation_id_hash)=64) AND
    (external_click_id_hash IS NULL OR CHAR_LENGTH(external_click_id_hash)=64) AND
    (media_task_id_hash IS NULL OR CHAR_LENGTH(media_task_id_hash)=64) AND
    received_time>=event_time AND
    ((dimension_status='UNMAPPED') OR
     (tenant_id IS NOT NULL AND advertiser_id IS NOT NULL AND product_id IS NOT NULL AND
      app_link_id IS NOT NULL AND task_id IS NOT NULL AND channel_id IS NOT NULL AND
      media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND route_id IS NOT NULL AND
      route_version_id IS NOT NULL AND route_version>=1))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 sanitized runtime fact index';

CREATE TABLE IF NOT EXISTS scx_adx_runtime_failure (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_fact_id VARCHAR(32) NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  failure_class VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NOT NULL,
  replayable TINYINT(1) NOT NULL DEFAULT 0,
  failure_revision BIGINT NOT NULL DEFAULT 1,
  tenant_id VARCHAR(32) NULL,
  advertiser_id VARCHAR(32) NULL,
  product_id VARCHAR(32) NULL,
  app_link_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  channel_id VARCHAR(32) NULL,
  media_account_id VARCHAR(32) NULL,
  operator_user_id VARCHAR(32) NULL,
  route_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  event_definition_id VARCHAR(32) NULL,
  event_snapshot_schema_version INT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  trace_id VARCHAR(128) NULL,
  correlation_id_hash CHAR(64) NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NULL,
  summary_hash CHAR(64) NOT NULL,
  dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_adx_runtime_failure_id (id),
  UNIQUE KEY uk_scx_adx_runtime_failure_source (source_type,source_id,error_code),
  KEY idx_scx_adx_runtime_failure_dlq (failure_class,replayable,received_time),
  KEY idx_scx_adx_runtime_failure_scope (advertiser_id,channel_id,media_account_id,received_time),
  KEY idx_scx_adx_runtime_failure_task (task_id,received_time),
  CONSTRAINT fk_scx_adx_runtime_failure_fact FOREIGN KEY (source_fact_id)
    REFERENCES scx_adx_runtime_fact(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_runtime_failure CHECK (
    source_type IN ('FEEDBACK','CALLBACK','DELIVERY','CONFIG_DISTRIBUTION','LEGACY') AND
    failure_class IN ('RETRYABLE','NON_RETRYABLE','PROTOCOL_REJECTED','SIGNATURE_FAILED',
      'SCOPE_REJECTED','DUPLICATE','LATE_ARRIVAL','UNKNOWN') AND
    failure_revision>=1 AND replayable IN (0,1) AND
    CHAR_LENGTH(idempotency_key_hash)=64 AND CHAR_LENGTH(summary_hash)=64 AND
    (payload_hash IS NULL OR CHAR_LENGTH(payload_hash)=64) AND
    (correlation_id_hash IS NULL OR CHAR_LENGTH(correlation_id_hash)=64) AND
    received_time>=event_time AND dimension_status IN ('FROZEN','UNMAPPED') AND
    ((dimension_status='UNMAPPED') OR
     (tenant_id IS NOT NULL AND advertiser_id IS NOT NULL AND product_id IS NOT NULL AND
      app_link_id IS NOT NULL AND task_id IS NOT NULL AND channel_id IS NOT NULL AND
      media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND route_id IS NOT NULL AND
      route_version_id IS NOT NULL AND route_version>=1))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 failure envelope without raw payload';

CREATE TABLE IF NOT EXISTS scx_adx_runtime_replay_request (
  id VARCHAR(32) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  source_failure_id VARCHAR(32) NOT NULL,
  expected_failure_revision BIGINT NOT NULL,
  source_route_version_id VARCHAR(32) NOT NULL,
  source_route_version BIGINT NOT NULL,
  command_type VARCHAR(24) NOT NULL DEFAULT 'REPLAY',
  mode VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL_SNAPSHOT_ONLY',
  tenant_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(32) NOT NULL,
  requested_by VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  ticket VARCHAR(128) NULL,
  command_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  result_fact_id VARCHAR(32) NULL,
  result_contribution_id VARCHAR(32) NULL,
  last_error_class VARCHAR(32) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_runtime_replay_request (request_id),
  UNIQUE KEY uk_scx_adx_runtime_replay_fingerprint (command_fingerprint),
  KEY idx_scx_adx_runtime_replay_claim (status,next_attempt_at,lease_until,tenant_id),
  KEY idx_scx_adx_runtime_replay_scope (advertiser_id,channel_id,media_account_id,create_time),
  KEY idx_scx_adx_runtime_replay_task (task_id,create_time),
  CONSTRAINT fk_scx_adx_runtime_replay_failure FOREIGN KEY (source_failure_id)
    REFERENCES scx_adx_runtime_failure(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_runtime_replay_route_version FOREIGN KEY (source_route_version_id)
    REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_runtime_replay_result FOREIGN KEY (result_fact_id)
    REFERENCES scx_adx_runtime_fact(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_runtime_replay_request CHECK (
    request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_failure_revision>=1 AND
    source_route_version>=1 AND command_type='REPLAY' AND mode='ORIGINAL_SNAPSHOT_ONLY' AND
    CHAR_LENGTH(command_fingerprint)=64 AND CHAR_LENGTH(TRIM(reason)) BETWEEN 4 AND 500 AND
    status IN ('QUEUED','CLAIMED','RETRY_WAIT','SUCCEEDED','TERMINAL','CANCELLED') AND
    attempt_count BETWEEN 0 AND max_attempts AND max_attempts BETWEEN 1 AND 10 AND
    (lease_until IS NULL OR lease_owner IS NOT NULL) AND
    (status<>'SUCCEEDED' OR result_fact_id IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Recoverable S10 replay command truth';

CREATE TABLE IF NOT EXISTS scx_adx_runtime_replay_attempt (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  replay_request_id VARCHAR(32) NOT NULL,
  attempt_no INT NOT NULL,
  worker_id_hash CHAR(64) NOT NULL,
  source_route_version_id VARCHAR(32) NOT NULL,
  source_event_snapshot_schema_version INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  error_class VARCHAR(32) NULL,
  error_code VARCHAR(64) NULL,
  result_summary_hash CHAR(64) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  next_retry_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_adx_runtime_replay_attempt_id (id),
  UNIQUE KEY uk_scx_adx_runtime_replay_attempt_no (replay_request_id,attempt_no),
  KEY idx_scx_adx_runtime_replay_attempt_time (started_at,status),
  CONSTRAINT fk_scx_adx_runtime_replay_attempt_request FOREIGN KEY (replay_request_id)
    REFERENCES scx_adx_runtime_replay_request(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_adx_runtime_replay_attempt_version FOREIGN KEY (source_route_version_id)
    REFERENCES scx_adx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_adx_runtime_replay_attempt CHECK (
    attempt_no>=1 AND CHAR_LENGTH(worker_id_hash)=64 AND source_event_snapshot_schema_version>=1 AND
    status IN ('STARTED','SUCCEEDED','RETRY_WAIT','TERMINAL') AND
    (result_summary_hash IS NULL OR CHAR_LENGTH(result_summary_hash)=64) AND
    (finished_at IS NULL OR finished_at>=started_at)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 replay attempt timeline';

CREATE TABLE IF NOT EXISTS scx_adx_runtime_command_audit (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  command_type VARCHAR(32) NOT NULL,
  command_id VARCHAR(32) NULL,
  source_failure_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  action VARCHAR(32) NOT NULL,
  outcome VARCHAR(24) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  reason_hash CHAR(64) NULL,
  trace_id VARCHAR(128) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_adx_runtime_command_audit_id (id),
  KEY idx_scx_adx_runtime_command_audit_command (command_id,create_time),
  KEY idx_scx_adx_runtime_command_audit_task (task_id,create_time),
  CONSTRAINT ck_scx_adx_runtime_command_audit CHECK (
    command_type IN ('REPLAY','EMERGENCY_STOP') AND
    action IN ('PREVIEW','CONFIRM','CLAIM','ATTEMPT','COMPLETE','REJECT') AND
    outcome IN ('PASS','WARN','FAIL','QUEUED','SUCCEEDED','TERMINAL') AND
    (reason_hash IS NULL OR CHAR_LENGTH(reason_hash)=64)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 management command audit';

SET @s10_delivery_attempt_unique_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND index_name='uk_scx_ocpx_delivery_attempt_no'
);
SET @s10_delivery_attempt_unique_ddl := IF(
  @s10_delivery_attempt_unique_exists=0,
  'ALTER TABLE scx_ocpx_delivery_attempt ADD UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)',
  'SELECT 1'
);
PREPARE s10_delivery_attempt_unique_stmt FROM @s10_delivery_attempt_unique_ddl;
EXECUTE s10_delivery_attempt_unique_stmt;
DEALLOCATE PREPARE s10_delivery_attempt_unique_stmt;

DROP TRIGGER IF EXISTS trg_scx_adx_runtime_fact_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_fact_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_failure_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_failure_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_replay_attempt_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_replay_attempt_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_command_audit_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_command_audit_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_adx_runtime_fact_no_update BEFORE UPDATE ON scx_adx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_fact_no_delete BEFORE DELETE ON scx_adx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_failure_no_update BEFORE UPDATE ON scx_adx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_failure_no_delete BEFORE DELETE ON scx_adx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_replay_attempt_no_update BEFORE UPDATE ON scx_adx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_replay_attempt_no_delete BEFORE DELETE ON scx_adx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_command_audit_no_update BEFORE UPDATE ON scx_adx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
CREATE TRIGGER trg_scx_adx_runtime_command_audit_no_delete BEFORE DELETE ON scx_adx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
DELIMITER ;

-- Canonical S10 permissions. Existing legacy grants are copied once before
-- the old permissions are retired. A later accidental replay cannot restore a
-- revoked canonical grant because the old role-menu relation no longer exists.
INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_adx_runtime_view_v91','查看运行事实','','menu_adx_runtime',NULL,NULL,'adx:runtime:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_detail_v91','查看运行详情','','menu_adx_runtime',NULL,NULL,'adx:runtime:detail',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_body_v91','查看受控正文','','menu_adx_runtime',NULL,NULL,'adx:runtime:body',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_export_v91','导出运行事实','','menu_adx_runtime',NULL,NULL,'adx:runtime:export',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_dlq_view_v91','查看失败队列','','menu_adx_runtime',NULL,NULL,'adx:runtime:dlq:view',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_retry_v91','重试失败事实','','menu_adx_runtime',NULL,NULL,'adx:runtime:retry',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_replay_v91','补偿重放','','menu_adx_runtime',NULL,NULL,'adx:runtime:replay',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_debug_v91','受控运行调试','','menu_adx_runtime',NULL,NULL,'adx:runtime:debug',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_runtime_stop_v91','紧急停流','','menu_adx_runtime',NULL,NULL,'adx:runtime:emergency-stop',90,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_adx_runtime_view_v91'),256),55)),
       rm.role_id,'perm_adx_runtime_view_v91'
FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_adx_runtime_read';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_adx_runtime_replay_v91'),256),55)),
       rm.role_id,'perm_adx_runtime_replay_v91'
FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_adx_runtime_replay';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'perm_adx_runtime_view_v91','perm_adx_runtime_detail_v91','perm_adx_runtime_body_v91',
  'perm_adx_runtime_export_v91','perm_adx_runtime_dlq_view_v91','perm_adx_runtime_retry_v91',
  'perm_adx_runtime_replay_v91','perm_adx_runtime_debug_v91','perm_adx_runtime_stop_v91')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

DELETE FROM sys_role_menu WHERE menu_id IN ('perm_adx_runtime_read','perm_adx_runtime_replay');
UPDATE sys_menu SET del_flag='1',update_time=NOW()
WHERE id IN ('perm_adx_runtime_read','perm_adx_runtime_replay');
