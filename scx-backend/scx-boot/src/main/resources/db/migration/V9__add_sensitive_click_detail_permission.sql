INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_event_sensitive_read','查询脱敏点击字段','ad:event-sensitive:read','menu_ad_events',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id='perm_ad_event_sensitive_read'
ON DUPLICATE KEY UPDATE id=VALUES(id);
