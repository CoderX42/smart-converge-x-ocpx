-- V66: keep all commercial CRUD actions reachable from their page permissions.
-- V64/V65 may already exist in target migration ledgers; never reuse them.

UPDATE sys_menu SET parent_id='menu_com_org',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_org_read','perm_com_org_write','perm_com_scope_read','perm_com_scope_write');
UPDATE sys_menu SET parent_id='menu_com_adv',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_adv_read','perm_com_adv_write');
UPDATE sys_menu SET parent_id='menu_com_agency',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_agency_read','perm_com_agency_write');
UPDATE sys_menu SET parent_id='menu_com_account',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_account_read','perm_com_account_write');
UPDATE sys_menu SET parent_id='menu_com_qual',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_qual_read','perm_com_qual_write','perm_com_qual_review');
UPDATE sys_menu SET parent_id='menu_com_agreement',del_flag='0',update_time=NOW()
WHERE id IN ('perm_com_agree_read','perm_com_agree_write','perm_com_data_read','perm_com_data_write');

UPDATE sys_menu
SET parent_id='menu_biz_commercial',
    component='delivery/products/index',
    path='/delivery/products',
    sort=70,
    del_flag='0',
    update_time=NOW()
WHERE id='menu_ad_products';

UPDATE sys_menu SET parent_id='menu_ad_products',del_flag='0',update_time=NOW()
WHERE id IN ('perm_ad_product_page','perm_ad_product_write');

-- The stable business roles retain page visibility. Action grants stay role-specific:
-- administrators and operators may write; analysts are read-only.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v66_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_biz_commercial','menu_ad_products','perm_ad_product_page')
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR','ROLE_ANALYST')
  AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v66_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id='perm_ad_product_write'
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR')
  AND r.del_flag='0' AND m.del_flag='0';
