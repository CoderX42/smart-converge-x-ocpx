-- V121: encrypted application-layer request/response audit for all OCPX stages.
-- Raw payloads expire after 30 days and metadata after 180 days. The tables have
-- no business foreign keys because malformed requests must be captured before
-- route validation. This migration is forward-only and never uses the retired
-- migration ledger.

CREATE TABLE scx_ocpx_wire_audit (
  id VARCHAR(32) NOT NULL,
  trace_id CHAR(64) NULL,
  flow_log_id VARCHAR(32) NULL,
  stage_code VARCHAR(32) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  traffic_class VARCHAR(16) NOT NULL DEFAULT 'FORMAL',
  protocol VARCHAR(32) NOT NULL,
  request_method VARCHAR(16) NULL,
  request_path VARCHAR(512) NULL,
  wire_sent TINYINT(1) NOT NULL DEFAULT 0,
  result_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  partner_http_status INT NULL,
  error_code VARCHAR(128) NULL,
  request_id VARCHAR(128) NULL,
  advertiser_id VARCHAR(32) NULL,
  promotion_link_id VARCHAR(32) NULL,
  media_id VARCHAR(32) NULL,
  monitor_task_id VARCHAR(32) NULL,
  request_bytes BIGINT NOT NULL DEFAULT 0,
  response_bytes BIGINT NOT NULL DEFAULT 0,
  request_sha256 CHAR(64) NULL,
  response_sha256 CHAR(64) NULL,
  raw_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  key_id VARCHAR(32) NOT NULL,
  crypto_version SMALLINT NOT NULL,
  capture_source VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
  captured_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  raw_expires_at DATETIME(3) NOT NULL,
  metadata_expires_at DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ocpx_wire_audit_time (captured_at,id),
  KEY idx_ocpx_wire_audit_trace (trace_id,captured_at,stage_code),
  KEY idx_ocpx_wire_audit_request (request_id,captured_at),
  KEY idx_ocpx_wire_audit_filter (stage_code,direction,result_status,captured_at),
  KEY idx_ocpx_wire_audit_raw_expiry (raw_expires_at,raw_state,id),
  KEY idx_ocpx_wire_audit_metadata_expiry (metadata_expires_at,id),
  CONSTRAINT ck_ocpx_wire_audit_direction CHECK (direction IN ('INBOUND','OUTBOUND','INTERNAL')),
  CONSTRAINT ck_ocpx_wire_audit_traffic CHECK (traffic_class IN ('FORMAL','TEST','INTEGRATION')),
  CONSTRAINT ck_ocpx_wire_audit_sent CHECK (wire_sent IN (0,1)),
  CONSTRAINT ck_ocpx_wire_audit_result CHECK (result_status IN ('PENDING','SUCCESS','FAILED','INTERRUPTED')),
  CONSTRAINT ck_ocpx_wire_audit_raw CHECK (raw_state IN ('PENDING','COMPLETE','INTERRUPTED','EXPIRED')),
  CONSTRAINT ck_ocpx_wire_audit_lengths CHECK (request_bytes>=0 AND response_bytes>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='SECURITY AUDIT: encrypted OCPX wire metadata retained 180 days';

CREATE TABLE scx_ocpx_wire_audit_payload (
  audit_id VARCHAR(32) NOT NULL,
  key_id VARCHAR(32) NOT NULL,
  crypto_version SMALLINT NOT NULL,
  payload_aad VARCHAR(256) NOT NULL,
  payload_nonce VARBINARY(12) NOT NULL,
  payload_ciphertext LONGBLOB NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (audit_id),
  KEY idx_ocpx_wire_payload_key (key_id,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='RESTRICTED SECURITY AUDIT: AES-GCM raw payload retained 30 days';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_flow_log_raw','查看原始请求审计','','menu_ocpx_flow_log',
   'ocpx:flow-log:raw',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_flow_log_raw_export','导出原始请求审计','','menu_ocpx_flow_log',
   'ocpx:flow-log:raw:export',30,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v121_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),25)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission IN ('ocpx:flow-log:raw','ocpx:flow-log:raw:export') AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
  AND m.permission IN ('ocpx:flow-log:raw','ocpx:flow-log:raw:export')
LEFT JOIN sys_role r ON r.id=rm.role_id
WHERE r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0';
