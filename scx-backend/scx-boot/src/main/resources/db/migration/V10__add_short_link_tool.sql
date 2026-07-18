CREATE TABLE IF NOT EXISTS scx_short_link (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  target_url VARCHAR(1000) NOT NULL,
  link_type VARCHAR(16) NOT NULL DEFAULT 'GENERAL',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_short_link_code (code), KEY idx_scx_short_link_media_status (media_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 受控短链与DP转换';

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_short_link','短链与DP工具','/operations/short-links','menu_ad_delivery','lucide:link-2','operations/short-links/index',40,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_short_link_read','查询短链','ad:short-link:read','menu_ad_short_link',0,'1','app_base',NOW(),'0'),
('perm_ad_short_link_write','编辑短链','ad:short-link:write','menu_ad_short_link',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id IN ('menu_ad_short_link','perm_ad_short_link_read','perm_ad_short_link_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN ('menu_ad_short_link','perm_ad_short_link_read','perm_ad_short_link_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN ('menu_ad_short_link','perm_ad_short_link_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
