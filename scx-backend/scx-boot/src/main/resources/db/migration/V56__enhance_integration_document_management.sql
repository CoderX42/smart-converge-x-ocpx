-- Complete integration-document metadata, managed storage lifecycle and delete RBAC.
ALTER TABLE scx_integration_document
  ADD COLUMN description VARCHAR(500) NULL AFTER name,
  ADD COLUMN original_file_name VARCHAR(255) NULL AFTER external_url,
  ADD COLUMN mime_type VARCHAR(128) NULL AFTER original_file_name,
  ADD COLUMN file_size BIGINT NULL AFTER mime_type,
  ADD COLUMN storage_config_id VARCHAR(64) NULL AFTER file_size,
  ADD COLUMN storage_provider VARCHAR(32) NULL AFTER storage_config_id,
  ADD COLUMN storage_object_key VARCHAR(1000) NULL AFTER storage_provider,
  ADD KEY idx_scx_document_media_type_status (media_id, type, status),
  ADD KEY idx_scx_document_storage_config (storage_config_id);

INSERT INTO sys_menu (
  id, name, permission, parent_id, sort, type, application_key, create_time, del_flag
) VALUES (
  'perm_ad_document_delete',
  '删除接入文档',
  'ad:integration-document:delete',
  'menu_ad_documents',
  2,
  '1',
  'app_base',
  NOW(),
  '0'
)
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  permission=VALUES(permission),
  parent_id=VALUES(parent_id),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0',
  update_time=NOW();

-- Permanent deletion is deliberately administrator-only.
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'adm_perm_ad_document_delete', '1', 'perm_ad_document_delete'
WHERE EXISTS (SELECT 1 FROM sys_role WHERE id='1')
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id);
