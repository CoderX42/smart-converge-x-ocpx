-- SCX V64/V65 -> V63 compatibility rollback
--
-- Scope:
--   1. Restore the menu tree, page routes and permission parents changed by V65.
--   2. Remove only role-menu rows created by V64/V65.
--   3. Remove the new channel-user sensitive-read permission.
--   4. Keep V64 qualification-document columns by default to avoid data loss.
--
-- Run this script against smart_converge_x only after taking a database backup.
-- The application code must be rolled back before, or at the same time as, this
-- database rollback.

SELECT DATABASE() AS rollback_database;

-- ---------------------------------------------------------------------------
-- 1. Recovery snapshots
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS scx_rb_20260730_v64_v65_sys_menu LIKE sys_menu;

INSERT IGNORE INTO scx_rb_20260730_v64_v65_sys_menu
SELECT *
FROM sys_menu
WHERE id IN (
  'menu_com_org', 'menu_com_adv', 'menu_com_agency', 'menu_com_account',
  'menu_com_qual', 'menu_com_agreement', 'menu_ad_partner',
  'menu_ad_products', 'menu_ad_master', 'menu_ad_channels',
  'menu_ad_documents', 'menu_ad_channel_user', 'menu_inv_account',
  'menu_inv_placement', 'menu_camp_plan', 'menu_camp_review',
  'perm_com_org_read', 'perm_com_org_write',
  'perm_com_adv_read', 'perm_com_adv_write',
  'perm_com_agency_read', 'perm_com_agency_write',
  'perm_com_scope_read', 'perm_com_scope_write',
  'perm_com_qual_read', 'perm_com_qual_write', 'perm_com_qual_review',
  'perm_com_agree_read', 'perm_com_agree_write',
  'perm_com_data_read', 'perm_com_data_write',
  'perm_ad_partner_read', 'perm_ad_partner_write',
  'perm_ad_product_page', 'perm_ad_product_write',
  'perm_ad_master_read', 'perm_ad_master_write',
  'perm_ad_channel_page', 'perm_ad_channel_write',
  'perm_ad_document_page', 'perm_ad_document_write',
  'perm_ad_channel_user_read', 'perm_ad_channel_user_write',
  'perm_ad_channel_user_sensitive_read',
  'perm_inv_account_read', 'perm_inv_account_write',
  'perm_inv_place_read', 'perm_inv_place_write', 'perm_inv_cap_write',
  'perm_camp_read', 'perm_camp_write', 'perm_camp_review',
  'perm_cr_bind_read', 'perm_cr_bind_write'
);

CREATE TABLE IF NOT EXISTS scx_rb_20260730_v64_v65_role_menu LIKE sys_role_menu;

INSERT IGNORE INTO scx_rb_20260730_v64_v65_role_menu
SELECT *
FROM sys_role_menu
WHERE LEFT(id, 4) IN ('v64_', 'v65_')
   OR menu_id = 'perm_ad_channel_user_sensitive_read';

-- Retain a full copy of qualification rows that use V64 managed-storage data.
CREATE TABLE IF NOT EXISTS scx_rb_20260730_v64_qualification_document
LIKE scx_qualification_document;

SET @scx_v64_backup_sql = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_object_key'
  ),
  'INSERT IGNORE INTO scx_rb_20260730_v64_qualification_document
   SELECT *
   FROM scx_qualification_document
   WHERE original_file_name IS NOT NULL
      OR mime_type IS NOT NULL
      OR file_size IS NOT NULL
      OR storage_config_id IS NOT NULL
      OR storage_provider IS NOT NULL
      OR storage_object_key IS NOT NULL
      OR content_hash IS NOT NULL',
  'SELECT 1'
);
PREPARE scx_v64_backup_statement FROM @scx_v64_backup_sql;
EXECUTE scx_v64_backup_statement;
DEALLOCATE PREPARE scx_v64_backup_statement;

-- ---------------------------------------------------------------------------
-- 2. Transactional menu and permission rollback
-- ---------------------------------------------------------------------------

START TRANSACTION;

-- Delete only page-visibility relationships inserted by V65. Relationships
-- that already existed before V65 have their original non-v65 identifiers.
DELETE FROM sys_role_menu
WHERE LEFT(id, 4) = 'v65_'
  AND menu_id IN (
    'menu_com_org', 'menu_com_qual', 'menu_ad_partner', 'menu_ad_master',
    'menu_ad_channels', 'menu_inv_account', 'menu_camp_plan'
  );

-- Remove all grants for the V64-only permission before deleting the node.
DELETE FROM sys_role_menu
WHERE menu_id = 'perm_ad_channel_user_sensitive_read';

DELETE FROM sys_menu
WHERE id = 'perm_ad_channel_user_sensitive_read'
  AND permission = 'ad:channel-user:sensitive:read';

-- Restore the eight V65 target rows to their V63 values.
INSERT INTO sys_menu
  (id, name, path, parent_id, icon, component, sort, type,
   application_key, create_time, update_time, del_flag)
VALUES
  ('menu_com_org', '组织与授权', '/commercial/organizations',
   'menu_biz_commercial', 'lucide:network', 'commercial/index',
   10, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_com_account', '广告账户', '/commercial/ad-accounts',
   'menu_biz_commercial', 'lucide:badge-cent', 'commercial/index',
   40, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_com_qual', '资质审核', '/commercial/qualifications',
   'menu_biz_commercial', 'lucide:badge-check', 'commercial/index',
   50, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_partner', '合作方', '/delivery/partners',
   'menu_biz_inventory', 'lucide:handshake', 'delivery/partners/index',
   20, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_master', '媒体与授权', '/delivery/media-scopes',
   'menu_biz_inventory', 'lucide:shield-check',
   'delivery/media-scopes/index', 10, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_channels', '投放渠道', '/delivery/media-channels',
   'menu_biz_inventory', 'lucide:radio-tower',
   'delivery/media-channels/index', 30, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_inv_account', '媒体投放账户', '/inventory/media-accounts',
   'menu_biz_inventory', 'lucide:server-cog', 'inventory/index',
   40, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_camp_plan', '推广计划', '/campaign/plans',
   'menu_biz_campaign', 'lucide:megaphone', 'campaign/index',
   10, '0', 'app_base', NOW(), NOW(), '0')
AS old
ON DUPLICATE KEY UPDATE
  name = old.name,
  path = old.path,
  parent_id = old.parent_id,
  icon = old.icon,
  component = old.component,
  sort = old.sort,
  type = old.type,
  application_key = old.application_key,
  del_flag = '0',
  update_time = NOW();

-- Restore the eight pages hidden by V65.
INSERT INTO sys_menu
  (id, name, path, parent_id, icon, component, sort, type,
   application_key, create_time, update_time, del_flag)
VALUES
  ('menu_com_adv', '广告主管理', '/commercial/advertisers',
   'menu_biz_commercial', 'lucide:building-2', 'commercial/index',
   20, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_com_agency', '代理关系', '/commercial/agencies',
   'menu_biz_commercial', 'lucide:handshake', 'commercial/index',
   30, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_com_agreement', '协议与数据授权', '/commercial/agreements',
   'menu_biz_commercial', 'lucide:file-signature', 'commercial/index',
   60, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_products', '产品管理', '/delivery/products',
   'menu_biz_commercial', 'lucide:package', 'delivery/products/index',
   70, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_documents', '接入文档', '/delivery/documents',
   'menu_biz_inventory', 'lucide:file-text', 'delivery/documents/index',
   60, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_ad_channel_user', '渠道用户', '/delivery/channel-users',
   'menu_biz_inventory', 'lucide:users', 'delivery/channel-users/index',
   70, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_inv_placement', '广告位与能力矩阵', '/inventory/placements',
   'menu_biz_inventory', 'lucide:panels-top-left', 'inventory/index',
   50, '0', 'app_base', NOW(), NOW(), '0'),
  ('menu_camp_review', '计划审核中心', '/campaign/reviews',
   'menu_biz_campaign', 'lucide:list-checks', 'campaign/index',
   20, '0', 'app_base', NOW(), NOW(), '0')
AS old
ON DUPLICATE KEY UPDATE
  name = old.name,
  path = old.path,
  parent_id = old.parent_id,
  icon = old.icon,
  component = old.component,
  sort = old.sort,
  type = old.type,
  application_key = old.application_key,
  del_flag = '0',
  update_time = NOW();

-- Restore permission ownership to the V63 page tree.
UPDATE sys_menu
SET parent_id = 'menu_com_org', update_time = NOW()
WHERE id IN (
  'perm_com_org_read', 'perm_com_org_write',
  'perm_com_scope_read', 'perm_com_scope_write'
);

UPDATE sys_menu
SET parent_id = 'menu_com_adv', update_time = NOW()
WHERE id IN ('perm_com_adv_read', 'perm_com_adv_write');

UPDATE sys_menu
SET parent_id = 'menu_com_agency', update_time = NOW()
WHERE id IN ('perm_com_agency_read', 'perm_com_agency_write');

UPDATE sys_menu
SET parent_id = 'menu_com_account', update_time = NOW()
WHERE id IN ('perm_com_account_read', 'perm_com_account_write');

UPDATE sys_menu
SET parent_id = 'menu_com_qual', update_time = NOW()
WHERE id IN (
  'perm_com_qual_read', 'perm_com_qual_write', 'perm_com_qual_review'
);

UPDATE sys_menu
SET parent_id = 'menu_com_agreement', update_time = NOW()
WHERE id IN (
  'perm_com_agree_read', 'perm_com_agree_write',
  'perm_com_data_read', 'perm_com_data_write'
);

UPDATE sys_menu
SET parent_id = 'menu_ad_partner', update_time = NOW()
WHERE id IN ('perm_ad_partner_read', 'perm_ad_partner_write');

UPDATE sys_menu
SET parent_id = 'menu_ad_products', update_time = NOW()
WHERE id IN ('perm_ad_product_page', 'perm_ad_product_write');

UPDATE sys_menu
SET parent_id = 'menu_ad_master', update_time = NOW()
WHERE id IN ('perm_ad_master_read', 'perm_ad_master_write');

UPDATE sys_menu
SET parent_id = 'menu_ad_channels', update_time = NOW()
WHERE id IN ('perm_ad_channel_page', 'perm_ad_channel_write');

UPDATE sys_menu
SET parent_id = 'menu_ad_documents', update_time = NOW()
WHERE id IN ('perm_ad_document_page', 'perm_ad_document_write');

UPDATE sys_menu
SET parent_id = 'menu_ad_channel_user', update_time = NOW()
WHERE id IN ('perm_ad_channel_user_read', 'perm_ad_channel_user_write');

UPDATE sys_menu
SET parent_id = 'menu_inv_account', update_time = NOW()
WHERE id IN ('perm_inv_account_read', 'perm_inv_account_write');

UPDATE sys_menu
SET parent_id = 'menu_inv_placement', update_time = NOW()
WHERE id IN (
  'perm_inv_place_read', 'perm_inv_place_write', 'perm_inv_cap_write'
);

UPDATE sys_menu
SET parent_id = 'menu_camp_plan', update_time = NOW()
WHERE id IN (
  'perm_camp_read', 'perm_camp_write',
  'perm_cr_bind_read', 'perm_cr_bind_write'
);

UPDATE sys_menu
SET parent_id = 'menu_camp_review', update_time = NOW()
WHERE id = 'perm_camp_review';

COMMIT;

-- ---------------------------------------------------------------------------
-- 3. Optional exact-schema rollback
-- ---------------------------------------------------------------------------
--
-- Default 0: keep the seven nullable V64 columns. The V63 application ignores
-- them, and keeping them prevents loss of managed-file metadata.
--
-- Change to 1 only when the final verification query reports
-- managed_qualification_rows = 0 and a full database backup exists.

SET @scx_drop_v64_qualification_columns = 0;

SET @scx_v64_has_storage_object_key = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'scx_qualification_document'
    AND column_name = 'storage_object_key'
);

SET @scx_v64_count_sql = IF(
  @scx_v64_has_storage_object_key = 1,
  'SELECT COUNT(*) INTO @scx_v64_managed_rows
     FROM scx_qualification_document
    WHERE original_file_name IS NOT NULL
       OR mime_type IS NOT NULL
       OR file_size IS NOT NULL
       OR storage_config_id IS NOT NULL
       OR storage_provider IS NOT NULL
       OR storage_object_key IS NOT NULL
       OR content_hash IS NOT NULL',
  'SET @scx_v64_managed_rows = 0'
);
PREPARE scx_v64_count_statement FROM @scx_v64_count_sql;
EXECUTE scx_v64_count_statement;
DEALLOCATE PREPARE scx_v64_count_statement;

SET @scx_v64_can_drop_columns =
  @scx_drop_v64_qualification_columns = 1
  AND @scx_v64_managed_rows = 0;

SET @scx_v64_drop_content_hash = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'content_hash'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN content_hash',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_content_hash;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_storage_object_key = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_object_key'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN storage_object_key',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_storage_object_key;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_storage_provider = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_provider'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN storage_provider',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_storage_provider;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_storage_config_id = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_config_id'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN storage_config_id',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_storage_config_id;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_file_size = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'file_size'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN file_size',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_file_size;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_mime_type = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'mime_type'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN mime_type',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_mime_type;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

SET @scx_v64_drop_original_file_name = IF(
  @scx_v64_can_drop_columns
  AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'original_file_name'
  ),
  'ALTER TABLE scx_qualification_document DROP COLUMN original_file_name',
  'SELECT 1'
);
PREPARE scx_v64_drop_statement FROM @scx_v64_drop_original_file_name;
EXECUTE scx_v64_drop_statement;
DEALLOCATE PREPARE scx_v64_drop_statement;

-- ---------------------------------------------------------------------------
-- 4. Verification
-- ---------------------------------------------------------------------------

SELECT id, name, path, parent_id, component, sort, del_flag
FROM sys_menu
WHERE id IN (
  'menu_com_org', 'menu_com_adv', 'menu_com_agency', 'menu_com_account',
  'menu_com_qual', 'menu_com_agreement', 'menu_ad_partner',
  'menu_ad_products', 'menu_ad_master', 'menu_ad_channels',
  'menu_ad_documents', 'menu_ad_channel_user', 'menu_inv_account',
  'menu_inv_placement', 'menu_camp_plan', 'menu_camp_review'
)
ORDER BY parent_id, sort, id;

SELECT COUNT(*) AS remaining_v64_v65_role_grants
FROM sys_role_menu
WHERE LEFT(id, 4) IN ('v64_', 'v65_')
   OR menu_id = 'perm_ad_channel_user_sensitive_read';

SELECT COUNT(*) AS remaining_sensitive_permission
FROM sys_menu
WHERE id = 'perm_ad_channel_user_sensitive_read'
   OR permission = 'ad:channel-user:sensitive:read';

SELECT COUNT(*) AS permission_parent_mismatches
FROM sys_menu
WHERE id IN (
  'perm_com_org_read', 'perm_com_org_write',
  'perm_com_scope_read', 'perm_com_scope_write',
  'perm_com_adv_read', 'perm_com_adv_write',
  'perm_com_agency_read', 'perm_com_agency_write',
  'perm_com_account_read', 'perm_com_account_write',
  'perm_com_qual_read', 'perm_com_qual_write', 'perm_com_qual_review',
  'perm_com_agree_read', 'perm_com_agree_write',
  'perm_com_data_read', 'perm_com_data_write',
  'perm_ad_partner_read', 'perm_ad_partner_write',
  'perm_ad_product_page', 'perm_ad_product_write',
  'perm_ad_master_read', 'perm_ad_master_write',
  'perm_ad_channel_page', 'perm_ad_channel_write',
  'perm_ad_document_page', 'perm_ad_document_write',
  'perm_ad_channel_user_read', 'perm_ad_channel_user_write',
  'perm_inv_account_read', 'perm_inv_account_write',
  'perm_inv_place_read', 'perm_inv_place_write', 'perm_inv_cap_write',
  'perm_camp_read', 'perm_camp_write', 'perm_camp_review',
  'perm_cr_bind_read', 'perm_cr_bind_write'
)
AND parent_id <> CASE
  WHEN id IN (
    'perm_com_org_read', 'perm_com_org_write',
    'perm_com_scope_read', 'perm_com_scope_write'
  ) THEN 'menu_com_org'
  WHEN id IN ('perm_com_adv_read', 'perm_com_adv_write')
    THEN 'menu_com_adv'
  WHEN id IN ('perm_com_agency_read', 'perm_com_agency_write')
    THEN 'menu_com_agency'
  WHEN id IN ('perm_com_account_read', 'perm_com_account_write')
    THEN 'menu_com_account'
  WHEN id IN (
    'perm_com_qual_read', 'perm_com_qual_write', 'perm_com_qual_review'
  ) THEN 'menu_com_qual'
  WHEN id IN (
    'perm_com_agree_read', 'perm_com_agree_write',
    'perm_com_data_read', 'perm_com_data_write'
  ) THEN 'menu_com_agreement'
  WHEN id IN ('perm_ad_partner_read', 'perm_ad_partner_write')
    THEN 'menu_ad_partner'
  WHEN id IN ('perm_ad_product_page', 'perm_ad_product_write')
    THEN 'menu_ad_products'
  WHEN id IN ('perm_ad_master_read', 'perm_ad_master_write')
    THEN 'menu_ad_master'
  WHEN id IN ('perm_ad_channel_page', 'perm_ad_channel_write')
    THEN 'menu_ad_channels'
  WHEN id IN ('perm_ad_document_page', 'perm_ad_document_write')
    THEN 'menu_ad_documents'
  WHEN id IN ('perm_ad_channel_user_read', 'perm_ad_channel_user_write')
    THEN 'menu_ad_channel_user'
  WHEN id IN ('perm_inv_account_read', 'perm_inv_account_write')
    THEN 'menu_inv_account'
  WHEN id IN (
    'perm_inv_place_read', 'perm_inv_place_write', 'perm_inv_cap_write'
  ) THEN 'menu_inv_placement'
  WHEN id IN (
    'perm_camp_read', 'perm_camp_write',
    'perm_cr_bind_read', 'perm_cr_bind_write'
  ) THEN 'menu_camp_plan'
  WHEN id = 'perm_camp_review' THEN 'menu_camp_review'
END;

SELECT @scx_v64_managed_rows AS managed_qualification_rows,
       @scx_drop_v64_qualification_columns AS requested_column_drop,
       @scx_v64_can_drop_columns AS columns_were_safe_to_drop;

SELECT COUNT(*) AS remaining_v64_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'scx_qualification_document'
  AND column_name IN (
    'original_file_name', 'mime_type', 'file_size', 'storage_config_id',
    'storage_provider', 'storage_object_key', 'content_hash'
  );
