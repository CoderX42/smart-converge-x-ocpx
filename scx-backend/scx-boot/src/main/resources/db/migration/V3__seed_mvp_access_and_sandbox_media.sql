-- 给已经存在 M0 主体表的环境补齐 MVP 种子。不依赖 V1 重跑。
INSERT INTO sys_role (id,role_name,role_code,role_desc,type,del_flag,create_time,create_by)
SELECT 'role_operator','广告运营','ROLE_OPERATOR','授权媒体范围内配置与查询','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id='role_operator' OR role_code='ROLE_OPERATOR');
INSERT INTO sys_role (id,role_name,role_code,role_desc,type,del_flag,create_time,create_by)
SELECT 'role_analyst','只读分析员','ROLE_ANALYST','授权媒体范围内只读查询','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id='role_analyst' OR role_code='ROLE_ANALYST');

INSERT INTO scx_media (id,code,name,status,timezone,settlement_currency)
SELECT 'media_sandbox','sandbox-media','沙箱媒体','ACTIVE','Asia/Shanghai','CNY'
WHERE NOT EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox' OR code='sandbox-media');
INSERT INTO scx_user_subject_scope (id,user_id,subject_type,subject_id)
SELECT 'scope_system_sandbox','1','MEDIA','media_sandbox'
WHERE NOT EXISTS (SELECT 1 FROM scx_user_subject_scope WHERE user_id='1' AND subject_type='MEDIA' AND subject_id='media_sandbox');

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_delivery','投放配置','/delivery','0','lucide:send',NULL,85,'0','app_base',NOW(),'0'),
('menu_ad_master','主数据','/delivery/master-data','menu_ad_delivery','lucide:database','delivery/master-data/index',90,'0','app_base',NOW(),'0'),
('menu_ad_link','追踪链接','/delivery/tracking-link','menu_ad_delivery','lucide:link','delivery/tracking-link/index',80,'0','app_base',NOW(),'0'),
('menu_ad_task','监测任务','/delivery/tracking-task','menu_ad_delivery','lucide:list-checks','delivery/tracking-task/index',70,'0','app_base',NOW(),'0'),
('menu_ad_data','数据中心','/reporting','0','lucide:chart-no-axes-combined',NULL,75,'0','app_base',NOW(),'0'),
('menu_ad_daily','效果日报','/reporting/daily','menu_ad_data','lucide:chart-column','reporting/daily/index',90,'0','app_base',NOW(),'0'),
('menu_ad_events','事件归因','/reporting/event-detail','menu_ad_data','lucide:route','reporting/event-detail/index',80,'0','app_base',NOW(),'0'),
('menu_ad_jobs','回传任务','/operations/jobs','menu_ad_data','lucide:refresh-cw','operations/jobs/index',70,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_subject_media_read','查询媒体','subject:media:read','menu_ad_delivery',0,'1','app_base',NOW(),'0'),
('perm_ad_master_read','查询主数据','ad:master:read','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_master_write','编辑主数据','ad:master:write','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_master_status','变更主数据状态','ad:master:status','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_link_read','查询追踪链接','ad:link:read','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_link_write','编辑追踪链接','ad:link:write','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_link_status','变更链接状态','ad:link:status','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_task_read','查询监测任务','ad:task:read','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_task_write','编辑监测任务','ad:task:write','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_task_status','变更任务状态','ad:task:status','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_report_read','查询报表','ad:report:read','menu_ad_daily',0,'1','app_base',NOW(),'0'),
('perm_ad_event_read','查询事件明细','ad:event-detail:read','menu_ad_events',0,'1','app_base',NOW(),'0'),
('perm_ad_callback_replay','重放沙箱回传','ad:callback:replay','menu_ad_jobs',0,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id LIKE 'menu_ad_%' OR id LIKE 'perm_ad_%' OR id='perm_subject_media_read'
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN
('menu_ad_delivery','menu_ad_master','menu_ad_link','menu_ad_task','menu_ad_data','menu_ad_daily','menu_ad_events','menu_ad_jobs',
'perm_subject_media_read','perm_ad_master_read','perm_ad_master_write','perm_ad_master_status','perm_ad_link_read','perm_ad_link_write','perm_ad_link_status',
'perm_ad_task_read','perm_ad_task_write','perm_ad_task_status','perm_ad_report_read','perm_ad_event_read','perm_ad_callback_replay')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN
('menu_ad_data','menu_ad_daily','menu_ad_events','perm_subject_media_read','perm_ad_report_read','perm_ad_event_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
