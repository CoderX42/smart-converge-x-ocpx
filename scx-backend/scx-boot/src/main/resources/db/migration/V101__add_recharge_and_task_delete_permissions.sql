-- Forward-only additive migration: button permissions for advertiser recharge
-- and monitor task delete (SGYZ-aligned minimal port, no new tables).
-- Back up and preview the exact target before applying this file to an
-- existing database; initialization never auto-runs this directory.

USE `smart_converge_x`;
SET NAMES utf8mb4;

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_recharge','广告主充值','','menu_ocpx_advertiser','ocpx:advertiser:recharge',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_delete','删除监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:delete',50,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v101_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'perm_ocpx_advertiser_recharge','perm_ocpx_monitor_task_delete'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
