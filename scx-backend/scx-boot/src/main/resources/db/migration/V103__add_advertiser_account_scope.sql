-- Forward-only advertiser account and shared-role alignment.
--
-- This file is intentionally not auto-run. Back up and preview the target
-- database before applying it. Existing role-code collisions are blocked so
-- that a business account can never inherit an unrelated role by accident.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v103_advertiser_role$$
CREATE PROCEDURE preflight_v103_advertiser_role()
BEGIN
  DECLARE conflicting_roles TEXT;

  SELECT GROUP_CONCAT(CONCAT(id, ':', role_code) ORDER BY id SEPARATOR ',')
    INTO conflicting_roles
  FROM sys_role
  WHERE (id = 'role_advertiser' AND BINARY role_code <> BINARY 'ROLE_ADVERTISER')
     OR (role_code = 'ROLE_ADVERTISER' AND BINARY id <> BINARY 'role_advertiser')
     OR (id = 'role_advertiser' AND role_code = 'ROLE_ADVERTISER'
         AND (COALESCE(type, '') <> '0' OR shop_id IS NOT NULL));

  IF conflicting_roles IS NOT NULL THEN
    SELECT 'sys_role.ROLE_ADVERTISER' AS manual_mapping_required,
           conflicting_roles AS conflicting_roles;
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V103 blocked: ROLE_ADVERTISER conflicts with an existing role';
  END IF;
END$$
CALL preflight_v103_advertiser_role()$$
DROP PROCEDURE preflight_v103_advertiser_role$$
DELIMITER ;

ALTER TABLE scx_ocpx_advertiser
  ADD COLUMN account_user_id VARCHAR(64) NULL AFTER advertiser_name,
  ADD UNIQUE INDEX uk_ocpx_advertiser_account_user (account_user_id),
  ADD CONSTRAINT fk_ocpx_advertiser_account_user
    FOREIGN KEY (account_user_id) REFERENCES sys_user(id);

ALTER TABLE scx_ocpx_monitor_task
  MODIFY COLUMN show_url TEXT NULL,
  MODIFY COLUMN clk_url TEXT NULL;

INSERT INTO sys_role
  (id,role_name,role_code,role_desc,type,shop_id,create_time,update_time,del_flag)
VALUES
  ('role_advertiser','广告主','ROLE_ADVERTISER','广告主自助账号共享只读角色',
   '0',NULL,NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  role_name=VALUES(role_name),
  role_desc=VALUES(role_desc),
  type='0',
  shop_id=NULL,
  update_time=NOW(),
  del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_report_export','导出报表','','menu_ocpx_report',
   'ocpx:report:export',10,'1','app_base',NOW(),NOW(),'0')
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

DELETE FROM sys_role_menu
WHERE role_id = 'role_advertiser';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v103_',LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),54)),
       'role_advertiser',m.id
FROM sys_menu m
WHERE m.id IN (
  'menu_ocpx_home','menu_ocpx_advertiser','menu_ocpx_promotion_link',
  'menu_ocpx_monitor_task','menu_ocpx_report','menu_ocpx_flow_log',
  'perm_ocpx_report_export'
)
  AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v103_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),54)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id='perm_ocpx_report_export'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
