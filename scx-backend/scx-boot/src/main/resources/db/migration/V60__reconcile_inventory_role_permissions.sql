-- V60: restore the inventory permission matrix for databases where the media
-- account and placement pages are visible but their action grants are absent.
--
-- V59 remains immutable. This migration is idempotent and does not remove
-- historical menu or role-menu records.

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_inv_account_read','查询媒体账户','inventory:media-account:read','menu_inv_account',1,'1','app_base',NOW(),NOW(),'0'),
  ('perm_inv_account_write','管理媒体账户','inventory:media-account:write','menu_inv_account',2,'1','app_base',NOW(),NOW(),'0'),
  ('perm_inv_place_read','查询广告位','inventory:placement:read','menu_inv_placement',1,'1','app_base',NOW(),NOW(),'0'),
  ('perm_inv_place_write','管理广告位','inventory:placement:write','menu_inv_placement',2,'1','app_base',NOW(),NOW(),'0'),
  ('perm_inv_cap_write','维护媒体能力','inventory:capability:write','menu_inv_placement',3,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  permission=VALUES(permission),
  parent_id=VALUES(parent_id),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0',
  update_time=NOW();

-- Administrators and operators retain the intended day-to-day inventory
-- management permissions.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v60_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_inventory',
  'menu_inv_account',
  'menu_inv_placement',
  'perm_inv_account_read',
  'perm_inv_account_write',
  'perm_inv_place_read',
  'perm_inv_place_write',
  'perm_inv_cap_write'
)
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

-- Analysts can load the account selector and placement list, but receive no
-- inventory mutation permission.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v60_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_inventory',
  'menu_inv_account',
  'menu_inv_placement',
  'perm_inv_account_read',
  'perm_inv_place_read'
)
WHERE r.role_code = 'ROLE_ANALYST'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
