CREATE TABLE IF NOT EXISTS scx_ad_platform_config (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, config_key VARCHAR(64) NOT NULL,
  config_value TEXT NOT NULL, description VARCHAR(500) NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ad_config_media_key (media_id,config_key), KEY idx_scx_ad_config_media_status (media_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 广告业务配置';
INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_platform_config','广告平台配置','/operations/platform-config','menu_ad_delivery','lucide:settings-2','operations/platform-config/index',30,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_config_read','查询广告配置','ad:config:read','menu_ad_platform_config',0,'1','app_base',NOW(),'0'),
('perm_ad_config_write','编辑广告配置','ad:config:write','menu_ad_platform_config',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('adm_',id),'1',id FROM sys_menu WHERE id IN ('menu_ad_platform_config','perm_ad_config_read','perm_ad_config_write') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('opr_',id),'role_operator',id FROM sys_menu WHERE id IN ('menu_ad_platform_config','perm_ad_config_read','perm_ad_config_write') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('ana_',id),'role_analyst',id FROM sys_menu WHERE id IN ('menu_ad_platform_config','perm_ad_config_read') ON DUPLICATE KEY UPDATE id=VALUES(id);
