-- V76: built-in API debugger, execution audit and OCPX debug correlation.
-- Forward-only migration. Debug facts remain immutable; scheduled retention only removes debugger-owned rows.

CREATE TABLE IF NOT EXISTS scx_api_debug_case (
  id VARCHAR(32) NOT NULL,
  case_name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  mode VARCHAR(32) NOT NULL,
  scenario VARCHAR(40) NULL,
  monitor_id VARCHAR(32) NULL,
  request_json MEDIUMTEXT NOT NULL,
  created_by VARCHAR(32) NOT NULL,
  updated_by VARCHAR(32) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_scx_api_debug_case_updated (deleted,update_time),
  KEY idx_scx_api_debug_case_name (case_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Shared administrator API debugger cases';

CREATE TABLE IF NOT EXISTS scx_api_debug_execution (
  id VARCHAR(32) NOT NULL,
  draft_id VARCHAR(32) NOT NULL,
  mode VARCHAR(32) NOT NULL,
  scenario VARCHAR(40) NULL,
  status VARCHAR(24) NOT NULL,
  monitor_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  request_method VARCHAR(16) NOT NULL,
  request_url VARCHAR(5000) NOT NULL,
  request_headers MEDIUMTEXT NULL,
  body_type VARCHAR(24) NOT NULL,
  request_body MEDIUMTEXT NULL,
  timeout_seconds INT NOT NULL DEFAULT 5,
  response_status INT NULL,
  response_headers MEDIUMTEXT NULL,
  response_body MEDIUMTEXT NULL,
  response_mime VARCHAR(255) NULL,
  response_size BIGINT NULL,
  response_sha256 CHAR(64) NULL,
  duration_ms BIGINT NULL,
  risk_summary VARCHAR(2000) NULL,
  side_effect_summary VARCHAR(4000) NULL,
  error_message VARCHAR(1000) NULL,
  created_by VARCHAR(32) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_api_debug_draft (draft_id),
  KEY idx_scx_api_debug_history (expires_at,create_time),
  KEY idx_scx_api_debug_status (status,update_time),
  KEY idx_scx_api_debug_monitor (monitor_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API debugger execution and seven-day history';

CREATE TABLE IF NOT EXISTS scx_api_debug_execution_log (
  id VARCHAR(32) NOT NULL,
  execution_id VARCHAR(32) NOT NULL,
  sequence_no BIGINT NOT NULL,
  source VARCHAR(16) NOT NULL,
  level VARCHAR(8) NOT NULL,
  phase VARCHAR(40) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  detail_json VARCHAR(4000) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_api_debug_log_sequence (execution_id,sequence_no),
  KEY idx_scx_api_debug_log_time (execution_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sanitized management and engine debugger timeline';

CREATE TABLE IF NOT EXISTS scx_api_debug_log_access_audit (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,
  level_filter VARCHAR(16) NULL,
  logger_filter VARCHAR(255) NULL,
  keyword_sha256 CHAR(64) NULL,
  range_start DATETIME(3) NOT NULL,
  range_end DATETIME(3) NOT NULL,
  returned_count INT NOT NULL,
  client_ip VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_api_debug_log_audit_expiry (expires_at),
  KEY idx_scx_api_debug_log_audit_user (user_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Raw debugger log access audit';

DROP PROCEDURE IF EXISTS scx_upgrade_api_debug_workbench;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_api_debug_workbench()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_ref,
      ADD COLUMN is_debug TINYINT(1) NOT NULL DEFAULT 0 AFTER debug_execution_id,
      ADD KEY idx_scx_ocpx_event_debug (debug_execution_id,received_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_callback_ref' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_callback_ref
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER route_version,
      ADD KEY idx_scx_ocpx_callback_debug (debug_execution_id,expires_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_job' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_delivery_job
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_ref,
      ADD KEY idx_scx_ocpx_delivery_debug (debug_execution_id,status);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_delivery_attempt
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER job_id,
      ADD KEY idx_scx_ocpx_attempt_debug (debug_execution_id,create_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_billing_entry
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER charge_event_id,
      ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'BUSINESS' AFTER debug_execution_id,
      ADD KEY idx_scx_ocpx_billing_debug (debug_execution_id,create_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_decision' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_deduction_decision
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_event_id,
      ADD KEY idx_scx_ocpx_deduction_debug (debug_execution_id,create_time);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_api_debug_workbench();
DROP PROCEDURE scx_upgrade_api_debug_workbench;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_adx_api_debug','接口调试','/adx/api-debugger','0','lucide:terminal-square','adx/api-debugger/index',46,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_api_debug_read','查看接口调试','adx:api-debug:read','menu_adx_api_debug',10,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_case_write','维护调试用例','adx:api-debug:case:write','menu_adx_api_debug',20,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_execute','执行接口调试','adx:api-debug:execute','menu_adx_api_debug',30,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_execute_billable','执行真实扣费调试','adx:api-debug:execute-billable','menu_adx_api_debug',40,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_logs','查看调试日志','adx:api-debug:logs','menu_adx_api_debug',50,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_logs_raw','查看近期原始日志','adx:api-debug:logs-raw','menu_adx_api_debug',60,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_api_debug','perm_adx_api_debug_read','perm_adx_api_debug_case_write',
   'perm_adx_api_debug_execute','perm_adx_api_debug_execute_billable',
   'perm_adx_api_debug_logs','perm_adx_api_debug_logs_raw')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
