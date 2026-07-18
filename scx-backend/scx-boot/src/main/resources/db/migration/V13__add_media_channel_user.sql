CREATE TABLE IF NOT EXISTS scx_media_channel_user (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, channel_id VARCHAR(64) NOT NULL,
  account VARCHAR(128) NOT NULL, name VARCHAR(128) NOT NULL, phone VARCHAR(32) NULL, email VARCHAR(128) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_channel_user_account (channel_id,account), KEY idx_scx_channel_user_media_channel (media_id,channel_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 媒体渠道用户';
INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_channel_user','渠道用户','/delivery/channel-users','menu_ad_delivery','lucide:users','delivery/channel-users/index',20,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_channel_user_read','查询渠道用户','ad:channel-user:read','menu_ad_channel_user',0,'1','app_base',NOW(),'0'),
('perm_ad_channel_user_write','编辑渠道用户','ad:channel-user:write','menu_ad_channel_user',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('adm_',id),'1',id FROM sys_menu WHERE id IN ('menu_ad_channel_user','perm_ad_channel_user_read','perm_ad_channel_user_write') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('opr_',id),'role_operator',id FROM sys_menu WHERE id IN ('menu_ad_channel_user','perm_ad_channel_user_read','perm_ad_channel_user_write') ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id) SELECT CONCAT('ana_',id),'role_analyst',id FROM sys_menu WHERE id IN ('menu_ad_channel_user','perm_ad_channel_user_read') ON DUPLICATE KEY UPDATE id=VALUES(id);
