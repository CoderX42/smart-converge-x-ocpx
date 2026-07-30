-- V64: keep qualification documents inside the configured system storage and
-- introduce an explicit permission for reading channel-user contact details.
-- Existing file_url values remain available for compatibility; new UI flows
-- populate the managed-storage columns below.

SET @scx_v64_add_original_file_name = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'original_file_name'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN original_file_name VARCHAR(255) NULL AFTER file_url'
);
PREPARE scx_v64_statement FROM @scx_v64_add_original_file_name;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_mime_type = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'mime_type'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN mime_type VARCHAR(128) NULL AFTER original_file_name'
);
PREPARE scx_v64_statement FROM @scx_v64_add_mime_type;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_file_size = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'file_size'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN file_size BIGINT NULL AFTER mime_type'
);
PREPARE scx_v64_statement FROM @scx_v64_add_file_size;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_storage_config_id = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_config_id'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN storage_config_id VARCHAR(32) NULL AFTER file_size'
);
PREPARE scx_v64_statement FROM @scx_v64_add_storage_config_id;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_storage_provider = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_provider'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN storage_provider VARCHAR(32) NULL AFTER storage_config_id'
);
PREPARE scx_v64_statement FROM @scx_v64_add_storage_provider;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_storage_object_key = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'storage_object_key'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN storage_object_key VARCHAR(1024) NULL AFTER storage_provider'
);
PREPARE scx_v64_statement FROM @scx_v64_add_storage_object_key;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

SET @scx_v64_add_content_hash = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_qualification_document'
      AND column_name = 'content_hash'
  ),
  'SELECT 1',
  'ALTER TABLE scx_qualification_document
     ADD COLUMN content_hash VARCHAR(64) NULL AFTER storage_object_key'
);
PREPARE scx_v64_statement FROM @scx_v64_add_content_hash;
EXECUTE scx_v64_statement;
DEALLOCATE PREPARE scx_v64_statement;

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ad_channel_user_sensitive_read','查看渠道用户敏感信息',
   'ad:channel-user:sensitive:read','menu_ad_channel_user',3,'1',
   'app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  permission=VALUES(permission),
  parent_id=VALUES(parent_id),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0',
  update_time=NOW();

-- Sensitive access is derived from the existing write grant. Read-only roles
-- continue to receive only masked values.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v64_',LEFT(SHA2(CONCAT(rm.role_id,'|sensitive-channel-user'),256),27)),
       rm.role_id,
       'perm_ad_channel_user_sensitive_read'
FROM sys_role_menu rm
WHERE rm.menu_id = 'perm_ad_channel_user_write'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = rm.role_id
      AND existing.menu_id = 'perm_ad_channel_user_sensitive_read'
  );
