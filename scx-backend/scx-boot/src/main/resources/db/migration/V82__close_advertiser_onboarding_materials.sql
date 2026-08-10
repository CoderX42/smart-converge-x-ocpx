-- V82: close the Advertiser onboarding-material, credential-revision and
-- republish-required contract on top of the explicit V81 ADX object scopes.
-- Forward-only and repeat-safe. This file is never auto-scanned by init-database.sh.

CREATE TABLE IF NOT EXISTS scx_adx_integration_document (
  id VARCHAR(32) NOT NULL,
  owner_type VARCHAR(32) NOT NULL COMMENT 'ADVERTISER',
  owner_id VARCHAR(32) NOT NULL,
  document_type VARCHAR(32) NOT NULL,
  storage_config_id VARCHAR(32) NOT NULL,
  storage_provider VARCHAR(32) NOT NULL,
  object_key VARCHAR(1000) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  file_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  document_version BIGINT NOT NULL,
  scan_status VARCHAR(16) NOT NULL COMMENT 'CLEAN,REJECTED',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,RETIRED',
  summary VARCHAR(500) NULL,
  created_by VARCHAR(100) NOT NULL,
  retired_by VARCHAR(100) NULL,
  retired_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_document_version (owner_type,owner_id,document_type,document_version),
  KEY idx_scx_adx_document_owner (owner_type,owner_id,status,create_time),
  KEY idx_scx_adx_document_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Controlled ADX integration-document revisions';

CREATE TABLE IF NOT EXISTS scx_adx_advertiser_credential_revision (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  credential_ref VARCHAR(64) NOT NULL,
  credential_revision BIGINT NOT NULL,
  secret_ciphertext TEXT NULL COMMENT 'AES-GCM payload encrypted by existing ProtocolCrypto',
  storage_mode VARCHAR(16) NOT NULL COMMENT 'LEGACY,ENCRYPTED',
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE,OVERLAP,RETIRED',
  overlap_until DATETIME(3) NULL,
  reason VARCHAR(255) NULL,
  created_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_advertiser_credential_revision (advertiser_id,credential_revision),
  UNIQUE KEY uk_scx_adx_advertiser_credential_ref (credential_ref),
  KEY idx_scx_adx_advertiser_credential_status (advertiser_id,status,overlap_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Write-only encrypted Advertiser credential revisions';

CREATE TABLE IF NOT EXISTS scx_adx_advertiser_change_audit (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_config_version BIGINT NOT NULL,
  after_config_version BIGINT NOT NULL,
  credential_revision BIGINT NOT NULL,
  document_revision BIGINT NOT NULL,
  affected_route_count INT NOT NULL DEFAULT 0,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_advertiser_audit (advertiser_id,create_time,action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Advertiser configuration audit';

DROP PROCEDURE IF EXISTS scx_v82_close_advertiser_onboarding;
DELIMITER $$
CREATE PROCEDURE scx_v82_close_advertiser_onboarding()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_adapter' AND column_name='capability_revision') THEN
    ALTER TABLE scx_ocpx_adapter ADD COLUMN capability_revision BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='advertiser_code') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN advertiser_code VARCHAR(32) NULL AFTER id;
    UPDATE scx_adx_advertiser
    SET advertiser_code=CONCAT('adv_',LEFT(LOWER(REGEXP_REPLACE(id,'[^a-zA-Z0-9]','')),28))
    WHERE advertiser_code IS NULL;
    ALTER TABLE scx_adx_advertiser MODIFY advertiser_code VARCHAR(32) NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='adapter_version') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN adapter_version BIGINT NOT NULL DEFAULT 1 AFTER adapter_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='credential_ref') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN credential_ref VARCHAR(64) NULL AFTER secret_key;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='credential_revision') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN credential_revision BIGINT NOT NULL DEFAULT 0 AFTER credential_ref;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='current_document_id') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN current_document_id VARCHAR(32) NULL AFTER credential_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='document_revision') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN document_revision BIGINT NOT NULL DEFAULT 0 AFTER current_document_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='create_request_key') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN create_request_key VARCHAR(64) NULL AFTER document_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='create_request_hash') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND index_name='uk_scx_adx_advertiser_code') THEN
    ALTER TABLE scx_adx_advertiser ADD UNIQUE KEY uk_scx_adx_advertiser_code (advertiser_code,active_marker);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND index_name='uk_scx_adx_advertiser_create_request') THEN
    ALTER TABLE scx_adx_advertiser ADD UNIQUE KEY uk_scx_adx_advertiser_create_request (create_request_key);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='republish_required') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN republish_required TINYINT(1) NOT NULL DEFAULT 0 AFTER route_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='republish_reason') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN republish_reason VARCHAR(255) NULL AFTER republish_required;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_advertiser_credential_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_advertiser_credential_revision BIGINT NOT NULL DEFAULT 0 AFTER published_upstream_feedback_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_upstream_adapter_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_upstream_adapter_version BIGINT NOT NULL DEFAULT 1 AFTER published_advertiser_credential_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_document_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_document_id VARCHAR(32) NULL AFTER published_upstream_adapter_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_document_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_document_revision BIGINT NOT NULL DEFAULT 0 AFTER published_document_id;
  END IF;
END$$
DELIMITER ;
CALL scx_v82_close_advertiser_onboarding();
DROP PROCEDURE scx_v82_close_advertiser_onboarding;

UPDATE scx_adx_advertiser
SET credential_revision=1,
    credential_ref=CONCAT('advcred_',LEFT(SHA2(CONCAT('V82|',id),256),24))
WHERE secret_key IS NOT NULL AND secret_key<>'' AND credential_revision=0;

INSERT INTO scx_adx_advertiser_credential_revision
  (id,advertiser_id,credential_ref,credential_revision,secret_ciphertext,storage_mode,status,reason,created_by)
SELECT LEFT(SHA2(CONCAT('V82|CREDENTIAL|',id),256),32),id,credential_ref,1,NULL,'LEGACY','ACTIVE',
       'Legacy value retained in place until first authorized rotation','V82_MIGRATION'
FROM scx_adx_advertiser
WHERE credential_revision=1 AND credential_ref IS NOT NULL
ON DUPLICATE KEY UPDATE id=scx_adx_advertiser_credential_revision.id;

UPDATE scx_adx_monitor_link m
JOIN scx_adx_advertiser a ON BINARY a.id=BINARY m.advertiser_id
SET m.published_advertiser_credential_revision=a.credential_revision,
    m.published_upstream_adapter_version=a.adapter_version,
    m.published_document_id=a.current_document_id,
    m.published_document_revision=a.document_revision
WHERE m.route_status='PUBLISHED' AND m.published_advertiser_credential_revision=0;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_adx_advertiser_view','查看广告主','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_add','新增广告主','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_edit','编辑广告主','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_status','变更广告主状态','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_credential','轮换广告主凭据','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:credential',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_document_view','查看广告主资料','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:document:view',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_advertiser_document_edit','维护广告主资料','','menu_adx_advertiser_list',NULL,NULL,'adx:advertiser:document:edit',70,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_adx_advertiser_view','perm_adx_advertiser_add','perm_adx_advertiser_edit',
  'perm_adx_advertiser_status','perm_adx_advertiser_credential',
  'perm_adx_advertiser_document_view','perm_adx_advertiser_document_edit'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_adx_advertiser_view','perm_adx_advertiser_add','perm_adx_advertiser_edit',
  'perm_adx_advertiser_document_view','perm_adx_advertiser_document_edit'
)
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_advertiser_view','perm_adx_advertiser_document_view')
WHERE r.role_code IN ('ROLE_ANALYST','ROLE_ADVERTISER') AND r.del_flag='0' AND m.del_flag='0';
