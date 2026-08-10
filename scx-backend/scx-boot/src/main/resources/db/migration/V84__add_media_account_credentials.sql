-- V84: separate protocol Channel from business MediaAccount, write-only
-- MediaCredential revisions and internal sys_user ownership.
-- Forward-only and repeat-safe. Existing Monitor rows are intentionally not
-- associated with a MediaAccount: S5 must make that business choice explicitly.

CREATE TABLE IF NOT EXISTS scx_adx_media_account (
  id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(128) NOT NULL,
  document_id VARCHAR(32) NULL,
  document_version BIGINT NOT NULL DEFAULT 0,
  description VARCHAR(500) NULL,
  default_credential_id VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT 'ACTIVE,DISABLED,PAUSED,ARCHIVED',
  config_version BIGINT NOT NULL DEFAULT 1,
  credential_revision BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NOT NULL,
  create_request_hash CHAR(64) NOT NULL,
  archived_by VARCHAR(100) NULL,
  archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  active_name_key VARCHAR(128) GENERATED ALWAYS AS (IF(status<>'ARCHIVED',normalized_name,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_media_account_code (code),
  UNIQUE KEY uk_scx_adx_media_account_name (channel_id,active_name_key),
  UNIQUE KEY uk_scx_adx_media_account_request (create_request_key),
  KEY idx_scx_adx_media_account_filter (channel_id,status,update_time),
  KEY idx_scx_adx_media_account_document (document_id,document_version),
  KEY idx_scx_adx_media_account_default_credential (default_credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Media business account separated from protocol Channel';

CREATE TABLE IF NOT EXISTS scx_adx_media_credential (
  id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  login_type VARCHAR(16) NOT NULL DEFAULT 'EMAIL',
  login_identifier_ciphertext TEXT NOT NULL COMMENT 'AES-GCM payload encrypted by existing ProtocolCrypto',
  login_identifier_hash CHAR(64) NOT NULL COMMENT 'Keyed blind index from existing ProtocolCrypto key material',
  masked_identifier VARCHAR(255) NOT NULL,
  current_credential_ref VARCHAR(64) NOT NULL,
  current_secret_revision BIGINT NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING,ACTIVE,DISABLED,REVOKED',
  version BIGINT NOT NULL DEFAULT 1,
  last_preflight_status VARCHAR(16) NULL COMMENT 'READY,BLOCKED; configuration-only',
  last_preflight_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_media_credential_identifier (media_account_id,login_type,login_identifier_hash),
  KEY idx_scx_adx_media_credential_status (media_account_id,status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='External media credential identity; never a sys_user';

CREATE TABLE IF NOT EXISTS scx_adx_media_credential_revision (
  id VARCHAR(32) NOT NULL,
  credential_id VARCHAR(32) NOT NULL,
  credential_ref VARCHAR(64) NOT NULL,
  secret_revision BIGINT NOT NULL,
  secret_ciphertext TEXT NOT NULL COMMENT 'AES-GCM payload encrypted by existing ProtocolCrypto',
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE,OVERLAP,RETIRED',
  overlap_until DATETIME(3) NULL,
  reason VARCHAR(255) NULL,
  created_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_media_credential_revision (credential_id,secret_revision),
  UNIQUE KEY uk_scx_adx_media_credential_ref (credential_ref),
  KEY idx_scx_adx_media_credential_revision_status (credential_id,status,overlap_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Write-only encrypted MediaCredential revisions';

CREATE TABLE IF NOT EXISTS scx_adx_media_account_owner (
  id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  responsibility VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE,DISABLED',
  version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_media_account_owner (media_account_id,user_id,responsibility),
  KEY idx_scx_adx_media_account_owner_user (user_id,status,media_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Internal sys_user responsibility for MediaAccount';

CREATE TABLE IF NOT EXISTS scx_adx_media_account_change_audit (
  id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  before_config_version BIGINT NOT NULL,
  after_config_version BIGINT NOT NULL,
  credential_revision BIGINT NOT NULL,
  owner_count INT NOT NULL DEFAULT 0,
  affected_route_count INT NOT NULL DEFAULT 0,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_media_account_audit (media_account_id,create_time,action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free MediaAccount configuration audit';

DROP PROCEDURE IF EXISTS scx_v84_add_media_account_contract;
DELIMITER $$
CREATE PROCEDURE scx_v84_add_media_account_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='media_account_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='media_credential_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN media_credential_id VARCHAR(32) NULL AFTER media_account_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_media_account_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_media_account_id VARCHAR(32) NULL AFTER published_product_event_policy_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_media_account_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_media_account_version BIGINT NOT NULL DEFAULT 0 AFTER published_media_account_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_media_account_credential_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_media_account_credential_revision BIGINT NOT NULL DEFAULT 0 AFTER published_media_account_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_media_credential_id') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_media_credential_id VARCHAR(32) NULL AFTER published_media_account_credential_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_media_credential_secret_revision') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_media_credential_secret_revision BIGINT NOT NULL DEFAULT 0 AFTER published_media_credential_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND index_name='idx_scx_adx_monitor_media_account') THEN
    ALTER TABLE scx_adx_monitor_link ADD KEY idx_scx_adx_monitor_media_account (media_account_id,media_credential_id,route_status,deleted);
  END IF;
END$$
DELIMITER ;
CALL scx_v84_add_media_account_contract();
DROP PROCEDURE scx_v84_add_media_account_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_media_account','媒体账号','/adx/channel/media-account','menu_adx_channel','lucide:badge-user','adx/media-account/index','adx:media-account:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_account_view','查看媒体账号','','menu_adx_media_account',NULL,NULL,'adx:media-account:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_account_add','新增媒体账号','','menu_adx_media_account',NULL,NULL,'adx:media-account:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_account_edit','编辑媒体账号','','menu_adx_media_account',NULL,NULL,'adx:media-account:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_account_status','变更媒体账号状态','','menu_adx_media_account',NULL,NULL,'adx:media-account:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_account_archive','归档恢复媒体账号','','menu_adx_media_account',NULL,NULL,'adx:media-account:archive',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_credential_view','查看外部媒体凭据','','menu_adx_media_account',NULL,NULL,'adx:media-account:credential:view',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_credential_add','新增外部媒体凭据','','menu_adx_media_account',NULL,NULL,'adx:media-account:credential:add',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_credential_edit','编辑外部媒体凭据','','menu_adx_media_account',NULL,NULL,'adx:media-account:credential:edit',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_credential_rotate','轮换外部媒体Secret','','menu_adx_media_account',NULL,NULL,'adx:media-account:credential:rotate',90,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_credential_status','变更外部媒体凭据状态','','menu_adx_media_account',NULL,NULL,'adx:media-account:credential:status',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_owner_view','查看媒体账号负责人','','menu_adx_media_account',NULL,NULL,'adx:media-account:owner:view',110,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_owner_edit','维护媒体账号负责人','','menu_adx_media_account',NULL,NULL,'adx:media-account:owner:edit',120,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_media_debug','媒体账号配置预检','','menu_adx_media_account',NULL,NULL,'adx:media-account:debug',130,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET sort=30,update_time=NOW() WHERE id='menu_adx_monitor';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'menu_adx_media_account','perm_adx_media_account_view','perm_adx_media_account_add',
  'perm_adx_media_account_edit','perm_adx_media_account_status','perm_adx_media_account_archive',
  'perm_adx_media_credential_view','perm_adx_media_credential_add','perm_adx_media_credential_edit',
  'perm_adx_media_credential_rotate','perm_adx_media_credential_status','perm_adx_media_owner_view',
  'perm_adx_media_owner_edit','perm_adx_media_debug')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'menu_adx_media_account','perm_adx_media_account_view','perm_adx_media_account_add',
  'perm_adx_media_account_edit','perm_adx_media_account_status','perm_adx_media_credential_view',
  'perm_adx_media_credential_add','perm_adx_media_credential_edit','perm_adx_media_credential_rotate',
  'perm_adx_media_owner_view','perm_adx_media_owner_edit','perm_adx_media_debug')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('menu_adx_media_account','perm_adx_media_account_view')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
