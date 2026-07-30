-- V65: consolidate sixteen historical leaf pages into eight workspaces.
-- Historical menu rows and role relations are retained for auditability; only
-- the old page rows are soft-hidden. Button permissions keep their identifiers.

UPDATE sys_menu SET name='商业主体',path='/commercial/entities',
  component='commercial/entities/index',sort=10,del_flag='0',update_time=NOW()
WHERE id='menu_com_org';
UPDATE sys_menu SET name='广告账户',path='/commercial/ad-accounts',
  component='commercial/ad-accounts/index',sort=20,del_flag='0',update_time=NOW()
WHERE id='menu_com_account';
UPDATE sys_menu SET name='合规中心',path='/commercial/compliance',
  component='commercial/compliance/index',sort=30,del_flag='0',update_time=NOW()
WHERE id='menu_com_qual';
UPDATE sys_menu SET name='合作方与产品',path='/commercial/catalog',
  parent_id='menu_biz_commercial',component='commercial/catalog/index',
  sort=40,del_flag='0',update_time=NOW()
WHERE id='menu_ad_partner';

UPDATE sys_menu SET name='媒体与权限',path='/inventory/media-access',
  parent_id='menu_biz_inventory',component='inventory/media-access/index',
  sort=10,del_flag='0',update_time=NOW()
WHERE id='menu_ad_master';
UPDATE sys_menu SET name='渠道接入',path='/inventory/channel-access',
  parent_id='menu_biz_inventory',component='inventory/channel-access/index',
  sort=20,del_flag='0',update_time=NOW()
WHERE id='menu_ad_channels';
UPDATE sys_menu SET name='媒体库存',path='/inventory/supply',
  parent_id='menu_biz_inventory',component='inventory/supply/index',
  sort=30,del_flag='0',update_time=NOW()
WHERE id='menu_inv_account';
UPDATE sys_menu SET name='推广计划',path='/campaign/plans',
  parent_id='menu_biz_campaign',component='campaign/index',
  sort=10,del_flag='0',update_time=NOW()
WHERE id='menu_camp_plan';

-- Move action permissions below the workspace that now owns the operation.
UPDATE sys_menu SET parent_id='menu_com_org',update_time=NOW()
WHERE id IN (
  'perm_com_org_read','perm_com_org_write','perm_com_adv_read','perm_com_adv_write',
  'perm_com_agency_read','perm_com_agency_write','perm_com_scope_read','perm_com_scope_write'
);
UPDATE sys_menu SET parent_id='menu_com_qual',update_time=NOW()
WHERE id IN (
  'perm_com_qual_read','perm_com_qual_write','perm_com_qual_review',
  'perm_com_agree_read','perm_com_agree_write',
  'perm_com_data_read','perm_com_data_write'
);
UPDATE sys_menu SET parent_id='menu_ad_partner',update_time=NOW()
WHERE id IN (
  'perm_ad_partner_read','perm_ad_partner_write',
  'perm_ad_product_page','perm_ad_product_write','perm_ad_master_read','perm_ad_master_write'
);
UPDATE sys_menu SET parent_id='menu_ad_channels',update_time=NOW()
WHERE id IN (
  'perm_ad_channel_page','perm_ad_channel_write','perm_ad_document_page',
  'perm_ad_document_write','perm_ad_channel_user_read',
  'perm_ad_channel_user_write','perm_ad_channel_user_sensitive_read'
);
UPDATE sys_menu SET parent_id='menu_inv_account',update_time=NOW()
WHERE id IN (
  'perm_inv_account_read','perm_inv_account_write','perm_inv_place_read',
  'perm_inv_place_write','perm_inv_cap_write'
);
UPDATE sys_menu SET parent_id='menu_camp_plan',update_time=NOW()
WHERE id IN ('perm_camp_read','perm_camp_write','perm_camp_review',
  'perm_cr_bind_read','perm_cr_bind_write');

-- Any role that could see a merged page receives only the new page-level
-- target. Existing action permissions are neither added nor removed here.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_com_org'),256),27)),
       role_id,'menu_com_org'
FROM sys_role_menu
WHERE menu_id IN ('menu_com_org','menu_com_adv','menu_com_agency');
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_com_qual'),256),27)),
       role_id,'menu_com_qual'
FROM sys_role_menu
WHERE menu_id IN ('menu_com_qual','menu_com_agreement');
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_ad_partner'),256),27)),
       role_id,'menu_ad_partner'
FROM sys_role_menu
WHERE menu_id IN ('menu_ad_partner','menu_ad_products');
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_ad_master'),256),27)),
       role_id,'menu_ad_master'
FROM sys_role_menu
WHERE menu_id='menu_ad_master';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_ad_channels'),256),27)),
       role_id,'menu_ad_channels'
FROM sys_role_menu
WHERE menu_id IN ('menu_ad_channels','menu_ad_documents','menu_ad_channel_user');
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_inv_account'),256),27)),
       role_id,'menu_inv_account'
FROM sys_role_menu
WHERE menu_id IN ('menu_inv_account','menu_inv_placement');
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v65_',LEFT(SHA2(CONCAT(role_id,'|menu_camp_plan'),256),27)),
       role_id,'menu_camp_plan'
FROM sys_role_menu
WHERE menu_id IN ('menu_camp_plan','menu_camp_review');

UPDATE sys_menu SET del_flag='1',update_time=NOW()
WHERE id IN (
  'menu_com_adv','menu_com_agency','menu_com_agreement','menu_ad_products',
  'menu_ad_documents','menu_ad_channel_user','menu_inv_placement','menu_camp_review'
);
