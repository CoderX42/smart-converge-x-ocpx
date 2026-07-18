INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_media_summary','媒体数据汇总','/reporting/media-summary','menu_ad_data','lucide:radio-tower','reporting/media-summary/index',95,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id='menu_ad_media_summary'
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id='menu_ad_media_summary'
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id='menu_ad_media_summary'
ON DUPLICATE KEY UPDATE id=VALUES(id);
