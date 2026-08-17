-- Forward-only permission for real monitor-task click testing.
--
-- The action sends traffic to the configured Engine and may create a real billing
-- entry, so it is granted only to the active ROLE_ADMIN.

USE `smart_converge_x`;
SET NAMES utf8mb4;

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_monitor_task_test','执行监测任务点击测试','','menu_ocpx_monitor_task',
   'ocpx:monitor-task:test',50,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),
  update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v112_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),26)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:monitor-task:test' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
LEFT JOIN sys_role r ON r.id=rm.role_id
WHERE m.permission='ocpx:monitor-task:test'
  AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0');
