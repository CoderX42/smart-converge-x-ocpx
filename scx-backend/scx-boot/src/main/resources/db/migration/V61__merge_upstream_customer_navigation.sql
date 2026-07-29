-- V61: merge the user-facing upstream-customer entry into Partner.
--
-- The customer API, table, permissions, role relations and historical
-- customer_id columns remain intact for compatibility. Only the page menu is
-- soft-deleted; no historical database record is physically removed.

UPDATE sys_menu
SET del_flag = '1',
    update_time = NOW()
WHERE id = 'menu_ad_customer';

-- Keep the media-management setup order compact after retiring the duplicate
-- customer page. All existing page ids and role grants remain stable.
UPDATE sys_menu
SET parent_id = 'menu_biz_inventory',
    sort = CASE id
      WHEN 'menu_ad_master' THEN 10
      WHEN 'menu_ad_partner' THEN 20
      WHEN 'menu_ad_channels' THEN 30
      WHEN 'menu_inv_account' THEN 40
      WHEN 'menu_inv_placement' THEN 50
      WHEN 'menu_ad_documents' THEN 60
      WHEN 'menu_ad_channel_user' THEN 70
      ELSE sort
    END,
    update_time = NOW()
WHERE id IN (
  'menu_ad_master',
  'menu_ad_partner',
  'menu_ad_channels',
  'menu_inv_account',
  'menu_inv_placement',
  'menu_ad_documents',
  'menu_ad_channel_user'
);
