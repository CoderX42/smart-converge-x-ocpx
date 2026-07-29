-- V57: reorder media configuration menus by the normal setup workflow.
-- Keep menu ids, routes, permissions and role grants unchanged.
--
-- The migration supports both menu trees:
--
-- Legacy Media Management (before V55):
--   1. Master Data (currently contains media and media-scope management)
--   2. Upstream Customers
--   3. Media Channels
--   4. Integration Documents
--   5. Channel Users
--
-- Media Resources (V55 and later):
--   1. Master Data (currently contains media and media-scope management)
--   2. Media Accounts
--   3. Placements and Capability Matrix
--   4. Media Channels
--   5. Integration Documents
--   6. Channel Users

UPDATE sys_menu
SET sort = CASE
  WHEN parent_id = 'menu_ocpx_media' AND id = 'menu_ad_master' THEN 10
  WHEN parent_id = 'menu_ocpx_media' AND id = 'menu_ad_customer' THEN 20
  WHEN parent_id = 'menu_ocpx_media' AND id = 'menu_ad_channels' THEN 30
  WHEN parent_id = 'menu_ocpx_media' AND id = 'menu_ad_documents' THEN 40
  WHEN parent_id = 'menu_ocpx_media' AND id = 'menu_ad_channel_user' THEN 50
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_ad_master' THEN 10
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_inv_account' THEN 20
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_inv_placement' THEN 30
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_ad_channels' THEN 40
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_ad_documents' THEN 50
  WHEN parent_id = 'menu_biz_inventory' AND id = 'menu_ad_channel_user' THEN 60
  ELSE sort
END
WHERE del_flag = '0'
  AND parent_id IN ('menu_ocpx_media', 'menu_biz_inventory')
  AND id IN (
    'menu_ad_master',
    'menu_ad_customer',
    'menu_ad_channels',
    'menu_ad_documents',
    'menu_ad_channel_user',
    'menu_inv_account',
    'menu_inv_placement'
  );
