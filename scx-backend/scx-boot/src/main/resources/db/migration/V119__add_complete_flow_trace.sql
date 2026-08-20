-- V119: complete OCPX request trace with seven-day restricted details and
-- ninety-day stage metadata. This forward migration intentionally does not use
-- the retired migration ledger.

ALTER TABLE scx_ocpx_flow_log
  ADD COLUMN trace_id CHAR(64) NULL AFTER id,
  ADD COLUMN stage_code VARCHAR(32) NOT NULL DEFAULT 'LEGACY' AFTER flow_type,
  ADD COLUMN request_method VARCHAR(8) NULL AFTER event_code,
  ADD COLUMN request_endpoint VARCHAR(512) NULL AFTER request_method,
  ADD COLUMN macro_audit JSON NULL AFTER request_endpoint,
  ADD KEY idx_ocpx_flow_log_trace (trace_id,event_time,stage_code),
  ADD KEY idx_ocpx_flow_log_stage_result (stage_code,result_status,event_time);

CREATE TABLE scx_ocpx_flow_log_detail (
  id VARCHAR(32) NOT NULL,
  flow_log_id VARCHAR(32) NOT NULL,
  request_params JSON NULL,
  response_preview VARCHAR(4096) NULL,
  request_truncated TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_flow_log_detail_flow (flow_log_id),
  KEY idx_ocpx_flow_log_detail_expiry (expires_at,id),
  CONSTRAINT fk_ocpx_flow_log_detail_flow FOREIGN KEY (flow_log_id)
    REFERENCES scx_ocpx_flow_log(id) ON DELETE CASCADE,
  CONSTRAINT ck_ocpx_flow_log_detail_truncated CHECK (request_truncated IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='RESTRICTED LOG: normalized request parameters and bounded response preview';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_flow_log_detail','查看链路请求原值','','menu_ocpx_flow_log',
   'ocpx:flow-log:detail',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v119_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),26)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:flow-log:detail' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id AND m.permission='ocpx:flow-log:detail'
LEFT JOIN sys_role r ON r.id=rm.role_id
WHERE r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0';
