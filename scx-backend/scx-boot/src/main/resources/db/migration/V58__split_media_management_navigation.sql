-- V58: split the former master-data aggregate into direct media-management pages.
-- V57 has already been applied and remains immutable. Historical menu rows and
-- legacy master-data permissions stay in place for API and bookmark compatibility.

UPDATE sys_menu
SET name = '媒体管理',
    path = '/inventory',
    parent_id = '0',
    icon = 'lucide:radio-tower',
    sort = 20,
    type = '0',
    application_key = 'app_base',
    del_flag = '0',
    update_time = NOW()
WHERE id = 'menu_biz_inventory';

UPDATE sys_menu
SET name = '媒体与授权',
    path = '/delivery/media-scopes',
    parent_id = 'menu_biz_inventory',
    icon = 'lucide:shield-check',
    component = 'delivery/media-scopes/index',
    sort = 10,
    type = '0',
    application_key = 'app_base',
    del_flag = '0',
    update_time = NOW()
WHERE id = 'menu_ad_master';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ad_partner','合作方','/delivery/partners','menu_biz_inventory',
   'lucide:handshake','delivery/partners/index',20,'0','app_base',NOW(),NOW(),'0')
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

UPDATE sys_menu
SET parent_id = 'menu_biz_inventory',
    name = CASE id
      WHEN 'menu_ad_customer' THEN '上游客户'
      WHEN 'menu_ad_channels' THEN '媒体渠道'
      WHEN 'menu_inv_account' THEN '媒体账户'
      WHEN 'menu_inv_placement' THEN '广告位与能力矩阵'
      WHEN 'menu_ad_documents' THEN '接入文档'
      WHEN 'menu_ad_channel_user' THEN '渠道用户'
      ELSE name
    END,
    sort = CASE id
      WHEN 'menu_ad_customer' THEN 30
      WHEN 'menu_ad_channels' THEN 40
      WHEN 'menu_inv_account' THEN 50
      WHEN 'menu_inv_placement' THEN 60
      WHEN 'menu_ad_documents' THEN 70
      WHEN 'menu_ad_channel_user' THEN 80
      ELSE sort
    END,
    update_time = NOW()
WHERE id IN (
  'menu_ad_customer',
  'menu_ad_channels',
  'menu_inv_account',
  'menu_inv_placement',
  'menu_ad_documents',
  'menu_ad_channel_user'
);

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

-- All three business roles can reach the root and the two new direct pages.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_inventory',
  'menu_ad_master',
  'menu_ad_partner'
)
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR','ROLE_ANALYST')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

-- Partner read is shared by the three roles; write stays with administrators
-- and operators. Legacy ad:master:* grants remain untouched for old clients.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
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
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 'perm_ad_partner_write'
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );

-- Operators can manage user assignments only inside media they already own.
-- Media creation, editing and status changes remain administrator-only.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
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

-- Reconcile administrator access without assuming a historical role id.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
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

-- Analysts only receive the existing media read capability. Operators retain
-- the same media-read permission while media mutations are not broadened.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v58_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id = 'perm_subject_media_read'
WHERE r.role_code IN ('ROLE_OPERATOR','ROLE_ANALYST')
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
