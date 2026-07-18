CREATE TABLE IF NOT EXISTS scx_user_task_scope (
  id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_user_task_scope (user_id, task_id),
  KEY idx_scx_task_scope_media_user (media_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 用户监测任务数据范围';

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_task_scope','任务授权','/operations/task-scopes','menu_ad_delivery','lucide:shield-check','operations/task-scopes/index',40,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_task_scope_read','查询任务授权','ad:task-scope:read','menu_ad_task_scope',0,'1','app_base',NOW(),'0'),
('perm_ad_task_scope_write','编辑任务授权','ad:task-scope:write','menu_ad_task_scope',0,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_',id),'1',id FROM sys_menu WHERE id IN ('menu_ad_task_scope','perm_ad_task_scope_read','perm_ad_task_scope_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
