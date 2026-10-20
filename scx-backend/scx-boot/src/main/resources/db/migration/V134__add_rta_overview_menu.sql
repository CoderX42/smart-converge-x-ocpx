-- V133 -> V134. Explicit one-time forward change; back up and approve target before execution.
-- RTA overview page reuses the task binding view permission; grant it wherever that permission is already granted.
INSERT INTO sys_menu(id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag) VALUES
('menu_ocpx_rta_overview','接入总览','/ocpx/rta/overview','menu_ocpx_rta','lucide:activity','ocpx/rta/overview/index','ocpx:rta:bindings:view',0,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),del_flag='0';
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id) SELECT CONCAT('rta134_',LEFT(SHA2(CONCAT(rm.role_id,'|menu_ocpx_rta_overview'),256),55)),rm.role_id,'menu_ocpx_rta_overview' FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0' WHERE rm.menu_id='menu_ocpx_rta_bindings';
INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id) SELECT CONCAT('rta134_',LEFT(SHA2(CONCAT(r.id,'|menu_ocpx_rta_overview'),256),55)),r.id,'menu_ocpx_rta_overview' FROM sys_role r WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';
