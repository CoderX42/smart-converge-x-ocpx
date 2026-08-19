-- V118: add a dedicated read-only management page for authoritative upstream
-- Callback conversion facts. The service applies AdvertiserScope before query.

USE `smart_converge_x`;
SET NAMES utf8mb4;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_callback_record','上游转化记录','/ocpx/callback-record','0','lucide:webhook',
   'ocpx/callback-record/index','ocpx:callback-record:view',55,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),
  type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v118_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),26)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:callback-record:view' AND m.del_flag='0'
WHERE r.del_flag='0' AND r.role_code IN ('ROLE_ADMIN','ROLE_ADVERTISER');
