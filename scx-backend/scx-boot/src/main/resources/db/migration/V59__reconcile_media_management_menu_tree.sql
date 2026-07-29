-- V59: reconcile the media-management tree for databases whose historical
-- menu seeds or role relations are incomplete.
--
-- V57 and V58 remain immutable. Historical menu rows are retained; the legacy
-- media root is only soft-deleted.

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_biz_inventory','媒体管理','/inventory','0',
   'lucide:radio-tower',NULL,20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_master','媒体与授权','/delivery/media-scopes','menu_biz_inventory',
   'lucide:shield-check','delivery/media-scopes/index',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_partner','合作方','/delivery/partners','menu_biz_inventory',
   'lucide:handshake','delivery/partners/index',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_customer','上游客户','/delivery/customers','menu_biz_inventory',
   'lucide:building-2','delivery/customers/index',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_channels','媒体渠道','/delivery/media-channels','menu_biz_inventory',
   'lucide:radio-tower','delivery/media-channels/index',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_inv_account','媒体账户','/inventory/media-accounts','menu_biz_inventory',
   'lucide:server-cog','inventory/index',50,'0','app_base',NOW(),NOW(),'0'),
  ('menu_inv_placement','广告位与能力矩阵','/inventory/placements','menu_biz_inventory',
   'lucide:panels-top-left','inventory/index',60,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_documents','接入文档','/delivery/documents','menu_biz_inventory',
   'lucide:file-text','delivery/documents/index',70,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ad_channel_user','渠道用户','/delivery/channel-users','menu_biz_inventory',
   'lucide:users','delivery/channel-users/index',80,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  icon=VALUES(icon),
  component=VALUES(component),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0',
  update_time=NOW();

-- Product management belongs to the commercial domain, not media management.
UPDATE sys_menu
SET parent_id = 'menu_ocpx_product',
    sort = 70,
    update_time = NOW()
WHERE id = 'menu_ad_products';

UPDATE sys_menu product_menu
JOIN sys_menu commercial_root
  ON commercial_root.id = 'menu_biz_commercial'
 AND commercial_root.del_flag = '0'
SET product_menu.parent_id = commercial_root.id,
    product_menu.sort = 70,
    product_menu.update_time = NOW()
WHERE product_menu.id = 'menu_ad_products';

-- Keep the historical row for old references, but never expose it as a second
-- media-management root.
UPDATE sys_menu
SET del_flag = '1',
    update_time = NOW()
WHERE id = 'menu_ocpx_media';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ad_partner_read','查询合作方','ad:partner:read','menu_ad_partner',1,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ad_partner_write','管理合作方','ad:partner:write','menu_ad_partner',2,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  permission=VALUES(permission),
  parent_id=VALUES(parent_id),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0',
  update_time=NOW();

UPDATE sys_menu
SET parent_id = 'menu_ad_master',
    update_time = NOW()
WHERE id IN (
  'perm_subject_media_read',
  'perm_subject_media_write',
  'perm_subject_media_scope_read',
  'perm_subject_media_scope_write'
);

-- Reconcile page visibility for the three stable business roles. Page grants
-- do not broaden the existing action-permission matrix.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_inventory',
  'menu_ad_master',
  'menu_ad_partner',
  'menu_ad_customer',
  'menu_ad_channels',
  'menu_inv_account',
  'menu_inv_placement',
  'menu_ad_documents',
  'menu_ad_channel_user'
)
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR','ROLE_ANALYST')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

-- Partner read is available to all three roles; partner write remains limited
-- to administrators and operators.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 'perm_ad_partner_read'
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR','ROLE_ANALYST')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 'perm_ad_partner_write'
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

-- Media mutations remain administrator-only. Operators can manage assignments
-- inside their own writable media; analysts only receive media read.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_subject_media_read',
  'perm_subject_media_write',
  'perm_subject_media_scope_read',
  'perm_subject_media_scope_write'
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_subject_media_read',
  'perm_subject_media_scope_read',
  'perm_subject_media_scope_write'
)
WHERE r.role_code = 'ROLE_OPERATOR'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v59_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 'perm_subject_media_read'
WHERE r.role_code = 'ROLE_ANALYST'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
