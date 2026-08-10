-- V83: add the independent Product aggregate, managed Logo metadata and
-- default event-policy contract on top of V81 object scope and V82 Advertiser.
-- Forward-only and repeat-safe. Existing AppLinks are intentionally not
-- auto-associated: cross-advertiser/package-name inference is not authorized.

CREATE TABLE IF NOT EXISTS scx_adx_product_logo_object (
  id VARCHAR(32) NOT NULL,
  storage_config_id VARCHAR(32) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  object_key VARCHAR(1000) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  image_width INT NOT NULL,
  image_height INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,RETIRED',
  created_by VARCHAR(100) NOT NULL,
  retired_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_product_logo_status (status,create_time),
  KEY idx_scx_adx_product_logo_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Managed Product Logo object metadata';

CREATE TABLE IF NOT EXISTS scx_adx_product (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(128) NOT NULL,
  package_name VARCHAR(255) NULL,
  logo_object_id VARCHAR(32) NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,DISABLED,ARCHIVED',
  config_version BIGINT NOT NULL DEFAULT 1,
  event_policy_revision BIGINT NOT NULL DEFAULT 1,
  create_request_key VARCHAR(64) NOT NULL,
  create_request_hash CHAR(64) NOT NULL,
  archived_by VARCHAR(100) NULL,
  archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_product_code (advertiser_id,code),
  UNIQUE KEY uk_scx_adx_product_name (advertiser_id,normalized_name),
  UNIQUE KEY uk_scx_adx_product_create_request (create_request_key),
  KEY idx_scx_adx_product_filter (advertiser_id,status,update_time),
  KEY idx_scx_adx_product_package (advertiser_id,package_name),
  KEY idx_scx_adx_product_logo (logo_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Independent ADX Product master data';

CREATE TABLE IF NOT EXISTS scx_adx_product_event_policy (
  id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  event_definition_id VARCHAR(32) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,DISABLED',
  version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_product_event (product_id,event_definition_id),
  KEY idx_scx_adx_product_event_definition (event_definition_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Product default event-policy template, copied at later Task binding';

CREATE TABLE IF NOT EXISTS scx_adx_product_change_audit (
  id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_config_version BIGINT NOT NULL,
  after_config_version BIGINT NOT NULL,
  event_policy_revision BIGINT NOT NULL,
  affected_app_link_count INT NOT NULL DEFAULT 0,
  affected_route_count INT NOT NULL DEFAULT 0,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_product_audit (product_id,create_time,action),
  KEY idx_scx_adx_product_audit_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Product configuration audit';

DROP PROCEDURE IF EXISTS scx_v83_add_product_contract;
DELIMITER $$
CREATE PROCEDURE scx_v83_add_product_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND column_name='product_id') THEN
    ALTER TABLE scx_adx_app_link ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_app_link' AND index_name='idx_scx_adx_app_link_product') THEN
    ALTER TABLE scx_adx_app_link ADD KEY idx_scx_adx_app_link_product (product_id,advertiser_id,deleted);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_product_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_product_id VARCHAR(32) NULL AFTER published_document_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_product_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_product_version BIGINT NOT NULL DEFAULT 0 AFTER published_product_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_product_event_policy_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_product_event_policy_revision BIGINT NOT NULL DEFAULT 0 AFTER published_product_version;
  END IF;
END$$
DELIMITER ;
CALL scx_v83_add_product_contract();
DROP PROCEDURE scx_v83_add_product_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_product','产品管理','/adx/advertiser/product','menu_adx_advertiser','lucide:package-open','adx/product/index','adx:product:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_product_view','查看产品','','menu_adx_product',NULL,NULL,'adx:product:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_product_add','新增产品','','menu_adx_product',NULL,NULL,'adx:product:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_product_edit','编辑产品','','menu_adx_product',NULL,NULL,'adx:product:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_product_archive','归档恢复产品','','menu_adx_product',NULL,NULL,'adx:product:archive',40,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET sort=30,update_time=NOW() WHERE id='menu_adx_app_link';
UPDATE sys_menu SET sort=40,update_time=NOW() WHERE id='menu_adx_deduction';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_product','perm_adx_product_view','perm_adx_product_add',
  'perm_adx_product_edit','perm_adx_product_archive')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_product','perm_adx_product_view','perm_adx_product_add',
  'perm_adx_product_edit')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_product','perm_adx_product_view')
WHERE r.role_code IN ('ROLE_ANALYST','ROLE_ADVERTISER') AND r.del_flag='0' AND m.del_flag='0';
