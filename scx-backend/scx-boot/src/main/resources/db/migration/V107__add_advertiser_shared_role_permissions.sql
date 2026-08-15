-- Forward-only shared advertiser role permission management.
--
-- This file is intentionally not auto-run. Back up and preview the target
-- database before applying it. The initial grant intentionally gives every
-- advertiser account all current OCPX business permissions and full OCPX data
-- scope; the dedicated role-management permission remains administrator-only.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v107_advertiser_role$$
CREATE PROCEDURE preflight_v107_advertiser_role()
BEGIN
  DECLARE fixed_role_count INT DEFAULT 0;
  DECLARE admin_role_count INT DEFAULT 0;

  SELECT COUNT(*) INTO fixed_role_count
  FROM sys_role
  WHERE BINARY id=BINARY 'role_advertiser'
    AND BINARY role_code=BINARY 'ROLE_ADVERTISER'
    AND type='0' AND shop_id IS NULL AND del_flag='0';

  IF fixed_role_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V107 blocked: fixed ROLE_ADVERTISER is missing or invalid';
  END IF;

  SELECT COUNT(*) INTO admin_role_count
  FROM sys_role
  WHERE BINARY role_code=BINARY 'ROLE_ADMIN' AND del_flag='0';

  IF admin_role_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V107 blocked: exactly one active ROLE_ADMIN is required';
  END IF;
END$$
CALL preflight_v107_advertiser_role()$$
DROP PROCEDURE preflight_v107_advertiser_role$$
DELIMITER ;

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_adv_role_manage','管理广告主账号角色权限','','menu_ocpx_advertiser',
   'ocpx:advertiser:role:manage',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_adv_scope_all','全量 OCPX 数据范围','','menu_ocpx_advertiser',
   'ocpx:advertiser:scope:all',90,'1','app_base',NOW(),NOW(),'0')
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

UPDATE sys_role
SET role_desc='广告主自助账号共享可配置角色',update_time=NOW()
WHERE id='role_advertiser' AND role_code='ROLE_ADVERTISER' AND del_flag='0';

-- Only ROLE_ADMIN receives the permission-management action during migration.
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
LEFT JOIN sys_role r ON r.id=rm.role_id
WHERE m.permission='ocpx:advertiser:role:manage'
  AND (r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0');

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v107_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:advertiser:role:manage' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

-- The selected rollout default is all current OCPX permissions plus full data
-- scope. Future OCPX permissions are not granted automatically.
DELETE FROM sys_role_menu
WHERE role_id='role_advertiser';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v107_',LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),27)),
       'role_advertiser',m.id
FROM sys_menu m
WHERE m.del_flag='0'
  AND (m.permission LIKE 'ocpx:%'
       OR (COALESCE(m.permission,'')='' AND m.path LIKE '/ocpx%'))
  AND COALESCE(m.permission,'') <> 'ocpx:advertiser:role:manage';
