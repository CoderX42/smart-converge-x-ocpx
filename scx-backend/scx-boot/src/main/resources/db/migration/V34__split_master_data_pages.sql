-- OCPX-003: split master-data into dedicated subpages.
INSERT INTO sys_menu (
  id,
  name,
  path,
  parent_id,
  icon,
  component,
  sort,
  type,
  application_key,
  create_time,
  del_flag
) VALUES
('menu_ad_products', '产品管理', '/delivery/products', 'menu_ocpx_media', 'lucide:package', 'delivery/products/index', 10, '0', 'app_base', NOW(), '0'),
('menu_ad_channels', '媒体渠道管理', '/delivery/media-channels', 'menu_ocpx_media', 'lucide:radio-tower', 'delivery/media-channels/index', 20, '0', 'app_base', NOW(), '0'),
('menu_ad_documents', '接入文档管理', '/delivery/documents', 'menu_ocpx_media', 'lucide:file-text', 'delivery/documents/index', 30, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  parent_id = VALUES(parent_id),
  icon = VALUES(icon),
  component = VALUES(component),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = VALUES(del_flag);

INSERT INTO sys_menu (
  id,
  name,
  permission,
  parent_id,
  sort,
  type,
  application_key,
  create_time,
  del_flag
) VALUES
('perm_ad_product_page', '查询产品', 'ad:product:read', 'menu_ad_products', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_product_write', '编辑产品', 'ad:product:write', 'menu_ad_products', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_channel_page', '查询媒体渠道', 'ad:media-channel:read', 'menu_ad_channels', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_channel_write', '编辑媒体渠道', 'ad:media-channel:write', 'menu_ad_channels', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_document_page', '查询接入文档', 'ad:integration-document:read', 'menu_ad_documents', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_document_write', '编辑接入文档', 'ad:integration-document:write', 'menu_ad_documents', 1, '1', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  permission = VALUES(permission),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = VALUES(del_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN (
  'menu_ad_products',
  'menu_ad_channels',
  'menu_ad_documents',
  'perm_ad_product_page',
  'perm_ad_product_write',
  'perm_ad_channel_page',
  'perm_ad_channel_write',
  'perm_ad_document_page',
  'perm_ad_document_write'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN (
  'menu_ad_products',
  'menu_ad_channels',
  'menu_ad_documents',
  'perm_ad_product_page',
  'perm_ad_product_write',
  'perm_ad_channel_page',
  'perm_ad_channel_write',
  'perm_ad_document_page',
  'perm_ad_document_write'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN (
  'menu_ad_products',
  'menu_ad_channels',
  'menu_ad_documents',
  'perm_ad_product_page',
  'perm_ad_channel_page',
  'perm_ad_document_page'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);
