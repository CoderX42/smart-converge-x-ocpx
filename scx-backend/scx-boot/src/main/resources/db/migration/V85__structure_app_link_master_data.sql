-- S4 AppLink structured master data. Forward-only and replay-safe on MySQL 8.x.
-- Existing rows deliberately remain LEGACY_PENDING_PRODUCT. This migration never
-- guesses Product ownership and never promotes a legacy URL into a validated URL.

CREATE TABLE IF NOT EXISTS scx_adx_app_link_change_audit (
  id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NULL,
  action VARCHAR(40) NOT NULL,
  before_config_version BIGINT NOT NULL,
  after_config_version BIGINT NOT NULL,
  mapping_status VARCHAR(32) NOT NULL,
  template_host VARCHAR(255) NULL,
  template_fingerprint CHAR(64) NULL,
  affected_route_count INT NOT NULL DEFAULT 0,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_app_link_audit (app_link_id,create_time,action),
  KEY idx_scx_adx_app_link_audit_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='URL-safe AppLink configuration audit';

DROP PROCEDURE IF EXISTS scx_v85_structure_app_link;
DELIMITER $$
CREATE PROCEDURE scx_v85_structure_app_link()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='link_url' AND data_type<>'text') THEN
    ALTER TABLE scx_adx_app_link MODIFY COLUMN link_url TEXT NOT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='deeplink' AND data_type<>'text') THEN
    ALTER TABLE scx_adx_app_link MODIFY COLUMN deeplink TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='biz_code') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN biz_code VARCHAR(32) NULL AFTER id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='document_id') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN document_id VARCHAR(32) NULL AFTER product_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='document_version') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN document_version BIGINT NOT NULL DEFAULT 0 AFTER document_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='normalized_name') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN normalized_name VARCHAR(128) NULL AFTER link_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='link_type') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN link_type VARCHAR(16) NULL AFTER link_platform;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='link_url_template') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN link_url_template VARCHAR(5000) NULL AFTER link_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='template_host') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN template_host VARCHAR(255) NULL AFTER link_url_template;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='template_fingerprint') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN template_fingerprint CHAR(64) NULL AFTER template_host;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='template_macro_set') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN template_macro_set JSON NULL AFTER template_fingerprint;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='redirect_enabled') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN redirect_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER template_macro_set;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='deeplink_url') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN deeplink_url VARCHAR(2000) NULL AFTER redirect_enabled;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='download_url') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN download_url VARCHAR(2000) NULL AFTER deeplink_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='description') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN description VARCHAR(500) NULL AFTER download_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='status') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' AFTER description;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='mapping_status') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PENDING_PRODUCT' AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN config_version BIGINT NOT NULL DEFAULT 0 AFTER mapping_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='create_request_key') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN create_request_key VARCHAR(64) NULL AFTER config_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='create_request_hash') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='archived_by') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN archived_by VARCHAR(100) NULL AFTER create_request_hash;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='archived_at') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN archived_at DATETIME(3) NULL AFTER archived_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='create_by') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN create_by VARCHAR(100) NULL AFTER archived_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='update_by') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN update_by VARCHAR(100) NULL AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='current_name_marker') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN current_name_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0 AND status<>'ARCHIVED' AND product_id IS NOT NULL,1,NULL)) STORED AFTER update_by;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='uk_scx_adx_link_name') THEN
    ALTER TABLE scx_adx_app_link DROP INDEX uk_scx_adx_link_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='uk_scx_adx_app_link_name') THEN
    ALTER TABLE scx_adx_app_link ADD UNIQUE KEY uk_scx_adx_app_link_name (product_id,normalized_name,current_name_marker);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='uk_scx_adx_app_link_request') THEN
    ALTER TABLE scx_adx_app_link ADD UNIQUE KEY uk_scx_adx_app_link_request (create_request_key);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='idx_scx_adx_app_link_filter') THEN
    ALTER TABLE scx_adx_app_link ADD KEY idx_scx_adx_app_link_filter (advertiser_id,product_id,status,mapping_status,update_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='idx_scx_adx_app_link_document') THEN
    ALTER TABLE scx_adx_app_link ADD KEY idx_scx_adx_app_link_document (document_id,document_version);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_app_link_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_app_link_id VARCHAR(32) NULL AFTER published_product_event_policy_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_app_link_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_app_link_version BIGINT NOT NULL DEFAULT 0 AFTER published_app_link_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_app_link_type') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_app_link_type VARCHAR(16) NULL AFTER published_app_link_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_link_url_template') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_link_url_template TEXT NULL AFTER published_app_link_type;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_link_url_template' AND data_type<>'text') THEN
    ALTER TABLE scx_adx_monitor_link MODIFY COLUMN published_link_url_template TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_link_template_host') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_link_template_host VARCHAR(255) NULL AFTER published_link_url_template;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_link_template_fingerprint') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_link_template_fingerprint CHAR(64) NULL AFTER published_link_template_host;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_link_template_macro_set') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_link_template_macro_set JSON NULL AFTER published_link_template_fingerprint;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_redirect_enabled') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_redirect_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER published_link_template_macro_set;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_deeplink_url') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_deeplink_url TEXT NULL AFTER published_redirect_enabled;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_deeplink_url' AND data_type<>'text') THEN
    ALTER TABLE scx_adx_monitor_link MODIFY COLUMN published_deeplink_url TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_download_url') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_download_url TEXT NULL AFTER published_deeplink_url;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_download_url' AND data_type<>'text') THEN
    ALTER TABLE scx_adx_monitor_link MODIFY COLUMN published_download_url TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND index_name='idx_scx_adx_monitor_app_link_publish') THEN
    ALTER TABLE scx_adx_monitor_link ADD KEY idx_scx_adx_monitor_app_link_publish (app_link_id,published_app_link_id,route_status,deleted);
  END IF;
END$$
DELIMITER ;
CALL scx_v85_structure_app_link();
DROP PROCEDURE scx_v85_structure_app_link;

-- No UPDATE of scx_adx_app_link appears here by design. An authorized operator
-- must explicitly map every legacy row through the S4 API after reviewing it.

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_app_link','链接管理','/adx/advertiser/app-link','menu_adx_advertiser','lucide:link','adx/app-link/index','adx:app-link:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_view_v85','查看链接','','menu_adx_app_link',NULL,NULL,'adx:app-link:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_add_v85','新增链接','','menu_adx_app_link',NULL,NULL,'adx:app-link:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_edit_v85','编辑及补映射链接','','menu_adx_app_link',NULL,NULL,'adx:app-link:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_status_v85','变更链接状态','','menu_adx_app_link',NULL,NULL,'adx:app-link:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_archive_v85','归档恢复链接','','menu_adx_app_link',NULL,NULL,'adx:app-link:archive',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_app_link_validate_v85','校验链接URL','','menu_adx_app_link',NULL,NULL,'adx:app-link:validate',60,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'menu_adx_app_link','perm_adx_app_link_view_v85','perm_adx_app_link_add_v85',
  'perm_adx_app_link_edit_v85','perm_adx_app_link_status_v85',
  'perm_adx_app_link_archive_v85','perm_adx_app_link_validate_v85')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'menu_adx_app_link','perm_adx_app_link_view_v85','perm_adx_app_link_add_v85',
  'perm_adx_app_link_edit_v85','perm_adx_app_link_status_v85','perm_adx_app_link_validate_v85')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('menu_adx_app_link','perm_adx_app_link_view_v85')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
