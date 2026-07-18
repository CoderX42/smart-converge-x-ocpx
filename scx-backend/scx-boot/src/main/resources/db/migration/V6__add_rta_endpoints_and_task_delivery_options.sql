CREATE TABLE IF NOT EXISTS scx_rta_endpoint (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  rta_identifier VARCHAR(128) NULL,
  endpoint_url VARCHAR(1000) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  last_health_status VARCHAR(16) NULL,
  last_health_checked_at DATETIME(3) NULL,
  last_health_message VARCHAR(500) NULL,
  version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_rta_endpoint_media_code (media_id, code),
  KEY idx_scx_rta_endpoint_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SCX 沙箱 RTA 端点配置';

ALTER TABLE scx_tracking_task
  ADD COLUMN rta_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER impression_enabled,
  ADD COLUMN rta_endpoint_id VARCHAR(64) NULL AFTER rta_enabled,
  ADD COLUMN exposure_report_url VARCHAR(1000) NULL AFTER rta_endpoint_id,
  ADD COLUMN big_click_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER exposure_report_url;

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_rta','RTA 配置','/delivery/rta','menu_ad_delivery','lucide:waypoints','delivery/rta/index',60,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_rta_read','查询 RTA 配置','ad:rta:read','menu_ad_rta',0,'1','app_base',NOW(),'0'),
('perm_ad_rta_write','编辑 RTA 配置','ad:rta:write','menu_ad_rta',0,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id IN ('menu_ad_rta','perm_ad_rta_read','perm_ad_rta_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN ('menu_ad_rta','perm_ad_rta_read','perm_ad_rta_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN ('menu_ad_rta','perm_ad_rta_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
