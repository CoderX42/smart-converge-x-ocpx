-- Forward-only ROLE_ADMIN CRUD permission alignment for the five OCPX resources.
--
-- This file is intentionally not auto-run. Back up and preview the target
-- database before applying it. No business rows are changed.

USE `smart_converge_x`;
SET NAMES utf8mb4;

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_delete','删除广告主','','menu_ocpx_advertiser',
   'ocpx:advertiser:delete',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_delete','删除媒体','','menu_ocpx_media',
   'ocpx:media:delete',30,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  permission=VALUES(permission),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  update_time=NOW(),
  del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v104_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),54)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ocpx_advertiser_delete','perm_ocpx_media_delete')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
