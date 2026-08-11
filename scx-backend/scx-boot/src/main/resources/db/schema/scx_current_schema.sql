-- Smart Converge X current application schema
-- Authoritative empty-database schema for the OCPX management and data planes.
-- Historical V1-V78 files were consolidated on 2026-08-09 and are not replayed.
-- Existing databases must use an explicitly reviewed forward-only delta SQL.
--
-- Maintenance rule:
--   1. Add a forward-only delta under db/migration for an existing database.
--   2. Apply that delta only after target backup and explicit authorization.
--   3. Synchronize the final structure and seed state into this file.
--   4. Update schema-required-tables.sh and database contract tests together.

USE `smart_converge_x`;
SET NAMES utf8mb4;

-- Database-resident migration ledgers are intentionally excluded. Existing
-- databases use explicitly reviewed forward-only deltas and structural
-- postcondition verification; empty databases consume this current-state file.

-- V68: replace the broad SCX advertising domains with the compact OCPX management loop.
-- Historical migrations remain immutable; the separately authorized retirement script
-- removes their physical tables after this replacement schema and navigation are accepted.

CREATE TABLE IF NOT EXISTS scx_ocpx_channel (
  id VARCHAR(32) NOT NULL,
  channel_name VARCHAR(100) NOT NULL,
  uri VARCHAR(512) NOT NULL,
  secret_key VARCHAR(255) NULL,
  macro_params VARCHAR(1000) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_channel_name (channel_name, active_marker),
  KEY idx_scx_ocpx_channel_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 渠道';

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser (
  id VARCHAR(32) NOT NULL,
  advertiser_code VARCHAR(32) NOT NULL,
  advertiser_name VARCHAR(100) NOT NULL,
  adapter_version BIGINT NOT NULL DEFAULT 1,
  bid_url VARCHAR(512) NOT NULL,
  secret_key VARCHAR(255) NULL,
  credential_ref VARCHAR(64) NULL,
  credential_revision BIGINT NOT NULL DEFAULT 0,
  current_document_id VARCHAR(32) NULL,
  document_revision BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NULL,
  create_request_hash CHAR(64) NULL,
  macro_params VARCHAR(1000) NULL,
  account VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_advertiser_code (advertiser_code, active_marker),
  UNIQUE KEY uk_scx_ocpx_advertiser_name (advertiser_name, active_marker),
  UNIQUE KEY uk_scx_ocpx_advertiser_account (account, active_marker),
  UNIQUE KEY uk_scx_ocpx_advertiser_create_request (create_request_key),
  KEY idx_scx_ocpx_advertiser_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 广告主';

CREATE TABLE IF NOT EXISTS scx_ocpx_integration_document (
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
  UNIQUE KEY uk_scx_ocpx_document_version (owner_type,owner_id,document_type,document_version),
  KEY idx_scx_ocpx_document_owner (owner_type,owner_id,status,create_time),
  KEY idx_scx_ocpx_document_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Controlled OCPX integration-document revisions';

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_credential_revision (
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
  UNIQUE KEY uk_scx_ocpx_advertiser_credential_revision (advertiser_id,credential_revision),
  UNIQUE KEY uk_scx_ocpx_advertiser_credential_ref (credential_ref),
  KEY idx_scx_ocpx_advertiser_credential_status (advertiser_id,status,overlap_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Write-only encrypted Advertiser credential revisions';

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_change_audit (
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
  KEY idx_scx_ocpx_advertiser_audit (advertiser_id,create_time,action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Advertiser configuration audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_app_link (
  id VARCHAR(32) NOT NULL,
  biz_code VARCHAR(32) NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NULL,
  document_id VARCHAR(32) NULL,
  document_version BIGINT NOT NULL DEFAULT 0,
  link_platform TINYINT NOT NULL COMMENT '1 Android,2 iOS,3 Android+iOS,4 快应用',
  link_type VARCHAR(16) NULL,
  link_name VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(128) NULL,
  link_channel VARCHAR(100) NULL,
  link_url TEXT NOT NULL,
  link_url_template VARCHAR(5000) NULL,
  template_host VARCHAR(255) NULL,
  template_fingerprint CHAR(64) NULL,
  template_macro_set JSON NULL,
  redirect_enabled TINYINT(1) NOT NULL DEFAULT 0,
  deeplink TEXT NULL,
  deeplink_url VARCHAR(2000) NULL,
  download_url VARCHAR(2000) NULL,
  link_ext VARCHAR(1000) NULL,
  link_remark VARCHAR(500) NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PENDING_PRODUCT',
  config_version BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NULL,
  create_request_hash CHAR(64) NULL,
  archived_by VARCHAR(100) NULL,
  archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NULL,
  update_by VARCHAR(100) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  current_name_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0 AND status<>'ARCHIVED' AND product_id IS NOT NULL,1,NULL)) STORED,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_app_link_name (product_id,normalized_name,current_name_marker),
  UNIQUE KEY uk_scx_ocpx_app_link_request (create_request_key),
  KEY idx_scx_ocpx_link_advertiser (advertiser_id, update_time),
  KEY idx_scx_ocpx_app_link_product (product_id,advertiser_id,deleted),
  KEY idx_scx_ocpx_app_link_filter (advertiser_id,product_id,status,mapping_status,update_time),
  KEY idx_scx_ocpx_app_link_document (document_id,document_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 广告主应用链接';

CREATE TABLE IF NOT EXISTS scx_ocpx_app_link_change_audit (
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
  KEY idx_scx_ocpx_app_link_audit (app_link_id,create_time,action),
  KEY idx_scx_ocpx_app_link_audit_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='URL-safe AppLink configuration audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_monitor_link (
  id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  monitor_name VARCHAR(100) NOT NULL,
  biz_id VARCHAR(100) NULL,
  unit_price DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  remark VARCHAR(500) NULL,
  show_url VARCHAR(1500) NULL,
  click_url VARCHAR(1500) NULL,
  republish_required TINYINT(1) NOT NULL DEFAULT 0,
  republish_reason VARCHAR(255) NULL,
  published_advertiser_credential_revision BIGINT NOT NULL DEFAULT 0,
  published_upstream_adapter_version BIGINT NOT NULL DEFAULT 1,
  published_document_id VARCHAR(32) NULL,
  published_document_revision BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_monitor_name (channel_id, monitor_name, active_marker),
  KEY idx_scx_ocpx_monitor_advertiser (advertiser_id, app_link_id),
  KEY idx_scx_ocpx_monitor_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 监测链接';

CREATE TABLE IF NOT EXISTS scx_ocpx_deduction_rule (
  id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  ratio DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT '保留比例，0 到 1',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_deduction (channel_id, app_link_id, active_marker),
  KEY idx_scx_ocpx_deduction_link (app_link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 扣量规则';

CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_report (
  id VARCHAR(32) NOT NULL,
  time_hour DATETIME NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  impressions BIGINT NOT NULL DEFAULT 0,
  clicks BIGINT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  revenue_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  activate_count BIGINT NOT NULL DEFAULT 0,
  evoke_count BIGINT NOT NULL DEFAULT 0,
  login_count BIGINT NOT NULL DEFAULT 0,
  register_count BIGINT NOT NULL DEFAULT 0,
  payment_count BIGINT NOT NULL DEFAULT 0,
  acquisition_count BIGINT NOT NULL DEFAULT 0,
  next_stay_count BIGINT NOT NULL DEFAULT 0,
  visit_count BIGINT NOT NULL DEFAULT 0,
  authentication_count BIGINT NOT NULL DEFAULT 0,
  order_count BIGINT NOT NULL DEFAULT 0,
  finish_order_count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_report_grain (time_hour, channel_id, advertiser_id, app_link_id, monitor_id),
  KEY idx_scx_ocpx_report_filter (time_hour, channel_id, advertiser_id),
  KEY idx_scx_ocpx_report_monitor (monitor_id, time_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX 时段报表事实';


-- Remove active grants to the superseded domains, then hide their navigation.
UPDATE sys_menu
SET del_flag='1', update_time=NOW()
WHERE id <> 'menu_ocpx_system'
  AND (
    path LIKE '/commercial%' OR path LIKE '/finance%' OR path LIKE '/inventory%'
    OR path LIKE '/creative%' OR path LIKE '/campaign%' OR path LIKE '/delivery%'
    OR path LIKE '/operations%' OR path LIKE '/reporting%' OR path LIKE '/ocpx/%'
    OR permission LIKE 'commercial:%' OR permission LIKE 'finance:%'
    OR permission LIKE 'inventory:%' OR permission LIKE 'creative:%'
    OR permission LIKE 'campaign:%' OR permission LIKE 'ad:%'
    OR permission LIKE 'report:%' OR permission LIKE 'media:%'
  );

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE m.del_flag='1'
  AND (
    m.path LIKE '/commercial%' OR m.path LIKE '/finance%' OR m.path LIKE '/inventory%'
    OR m.path LIKE '/creative%' OR m.path LIKE '/campaign%' OR m.path LIKE '/delivery%'
    OR m.path LIKE '/operations%' OR m.path LIKE '/reporting%' OR m.path LIKE '/ocpx/%'
    OR m.permission LIKE 'commercial:%' OR m.permission LIKE 'finance:%'
    OR m.permission LIKE 'inventory:%' OR m.permission LIKE 'creative:%'
    OR m.permission LIKE 'campaign:%' OR m.permission LIKE 'ad:%'
    OR m.permission LIKE 'report:%' OR m.permission LIKE 'media:%'
  );

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_ocpx_home','首页','/ocpx/dashboard','0','lucide:layout-dashboard','ocpx/dashboard/index',1,'0','app_base',NOW(),'0'),
  ('menu_ocpx_report','报表中心','/ocpx/report','0','lucide:chart-no-axes-combined',NULL,10,'0','app_base',NOW(),'0'),
  ('menu_ocpx_report_time','时段报表','/ocpx/report/time','menu_ocpx_report','lucide:chart-column','ocpx/report/index',10,'0','app_base',NOW(),'0'),
  ('menu_ocpx_channel','渠道管理','/ocpx/channel','0','lucide:radio-tower',NULL,20,'0','app_base',NOW(),'0'),
  ('menu_ocpx_channel_list','渠道管理','/ocpx/channel/list','menu_ocpx_channel','lucide:waypoints','ocpx/channel/index',10,'0','app_base',NOW(),'0'),
  ('menu_ocpx_monitor','监测链接管理','/ocpx/channel/monitor','menu_ocpx_channel','lucide:link-2','ocpx/monitor/index',20,'0','app_base',NOW(),'0'),
  ('menu_ocpx_advertiser','广告主管理','/ocpx/advertiser','0','lucide:building-2',NULL,30,'0','app_base',NOW(),'0'),
  ('menu_ocpx_advertiser_list','广告主管理','/ocpx/advertiser/list','menu_ocpx_advertiser','lucide:briefcase-business','ocpx/advertiser/index',10,'0','app_base',NOW(),'0'),
  ('menu_ocpx_app_link','应用链接管理','/ocpx/advertiser/app-link','menu_ocpx_advertiser','lucide:app-window','ocpx/app-link/index',20,'0','app_base',NOW(),'0'),
  ('menu_ocpx_deduction','扣量管理','/ocpx/advertiser/deduction','menu_ocpx_advertiser','lucide:percent','ocpx/deduction/index',30,'0','app_base',NOW(),'0'),
  ('menu_ocpx_clue','线索上报','/ocpx/clue','0','lucide:file-up','ocpx/clue/index',50,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_ocpx_dashboard_read','查看首页','ocpx:dashboard:read','menu_ocpx_home',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_report_read','查询报表','ocpx:report:read','menu_ocpx_report_time',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_report_export','下载报表','ocpx:report:export','menu_ocpx_report_time',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_channel_read','查询渠道','ocpx:channel:read','menu_ocpx_channel_list',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_channel_write','维护渠道','ocpx:channel:write','menu_ocpx_channel_list',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_monitor_read','查询监测链接','ocpx:monitor:read','menu_ocpx_monitor',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_monitor_write','维护监测链接','ocpx:monitor:write','menu_ocpx_monitor',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_advertiser_read','查询广告主','ocpx:advertiser:read','menu_ocpx_advertiser_list',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_advertiser_write','维护广告主','ocpx:advertiser:write','menu_ocpx_advertiser_list',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_app_link_read','查询应用链接','ocpx:app-link:read','menu_ocpx_app_link',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_app_link_write','维护应用链接','ocpx:app-link:write','menu_ocpx_app_link',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_deduction_read','查询扣量规则','ocpx:deduction:read','menu_ocpx_deduction',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_deduction_write','维护扣量规则','ocpx:deduction:write','menu_ocpx_deduction',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_clue_read','查询线索','ocpx:clue:read','menu_ocpx_clue',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_clue_write','维护线索','ocpx:clue:write','menu_ocpx_clue',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_clue_submit','提交线索','ocpx:clue:submit','menu_ocpx_clue',30,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

UPDATE sys_menu
SET name='权限管理', parent_id='0', sort=40, del_flag='0', update_time=NOW()
WHERE id='menu_ocpx_system';

UPDATE sys_menu
SET name='账号管理', parent_id='menu_ocpx_system', del_flag='0', update_time=NOW()
WHERE id='menu_system_user';

UPDATE sys_menu
SET name='角色管理', parent_id='menu_ocpx_system', del_flag='0', update_time=NOW()
WHERE id='menu_role';

-- Keep the visible console surface identical to the target platform. Historical
-- bootstrap menus and their grants remain auditable in the migration history, but are
-- no longer active navigation or permission entries after this forward migration.
UPDATE sys_menu
SET parent_id='menu_system_user', update_time=NOW()
WHERE permission LIKE 'system:sysuser:%';

UPDATE sys_menu
SET parent_id='menu_role', update_time=NOW()
WHERE permission LIKE 'system:sysrole:%';

UPDATE sys_menu
SET del_flag='1', update_time=NOW()
WHERE id NOT LIKE 'menu_ocpx_%' AND id NOT LIKE 'perm_ocpx_%'
  AND id NOT IN ('menu_ocpx_system','menu_system_user','menu_role')
  AND COALESCE(permission,'') NOT LIKE 'system:sysuser:%'
  AND COALESCE(permission,'') NOT LIKE 'system:sysrole:%';

UPDATE sys_menu
SET del_flag='0', update_time=NOW()
WHERE id LIKE 'menu_ocpx_%' OR id LIKE 'perm_ocpx_%'
  OR id IN ('menu_ocpx_system','menu_system_user','menu_role')
  OR permission LIKE 'system:sysuser:%' OR permission LIKE 'system:sysrole:%';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE m.del_flag='1';

INSERT INTO sys_role
  (id,role_name,role_code,role_desc,type,del_flag,create_time)
VALUES
  ('role_advertiser','广告主','ROLE_ADVERTISER','广告主账号，仅查看本广告主首页和报表','0','0',NOW())
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),role_desc=VALUES(role_desc),type='0',del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%'
  OR m.id IN ('menu_ocpx_system','menu_system_user','menu_role')
  OR m.permission LIKE 'system:sysuser:%' OR m.permission LIKE 'system:sysrole:%'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_ocpx_home','menu_ocpx_report','menu_ocpx_report_time','menu_ocpx_channel','menu_ocpx_channel_list',
  'menu_ocpx_monitor','menu_ocpx_advertiser','menu_ocpx_advertiser_list','menu_ocpx_app_link',
  'menu_ocpx_deduction','menu_ocpx_clue','perm_ocpx_dashboard_read','perm_ocpx_report_read',
  'perm_ocpx_report_export','perm_ocpx_channel_read','perm_ocpx_channel_write','perm_ocpx_monitor_read',
  'perm_ocpx_monitor_write','perm_ocpx_advertiser_read','perm_ocpx_advertiser_write','perm_ocpx_app_link_read',
  'perm_ocpx_app_link_write','perm_ocpx_deduction_read','perm_ocpx_deduction_write','perm_ocpx_clue_read',
  'perm_ocpx_clue_write','perm_ocpx_clue_submit')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';


INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_', LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),59)), 'role_advertiser', m.id
FROM sys_menu m
WHERE m.id IN ('menu_ocpx_home','menu_ocpx_report','menu_ocpx_report_time',
  'perm_ocpx_dashboard_read','perm_ocpx_report_read','perm_ocpx_report_export')
  AND m.del_flag='0';
-- V69: restore the original system-management navigation on top of the
-- existing scx-system Sa-Token + RBAC implementation.
--
-- V68 renamed the canonical root to "权限管理" and kept only account and
-- role pages visible. The underlying users, roles, menus, departments,
-- dictionaries, storage configuration and audit-log capabilities were not
-- removed. This forward-only migration restores those original entry points
-- without introducing another authorization model or changing business data.

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_system','系统管理','/system','0','lucide:settings',NULL,
   40,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  icon=VALUES(icon),
  component=VALUES(component),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  update_time=NOW(),
  del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_system_user','用户管理','/system/user','menu_ocpx_system',
   'lucide:user-round-cog','system/user/index',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_role','角色管理','/system/role','menu_ocpx_system',
   'lucide:shield-check','system/role/index',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_menu','菜单管理','/system/menu','menu_ocpx_system',
   'lucide:menu','system/menu/index',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_dept','部门管理','/system/dept','menu_ocpx_system',
   'lucide:network','system/dept/index',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_dict','字典管理','/system/dict','menu_ocpx_system',
   'lucide:book-key','system/dict/index',50,'0','app_base',NOW(),NOW(),'0'),
  ('menu_storage','存储配置','/system/storage-config','menu_ocpx_system',
   'lucide:cloud-cog','system/storage-config/index',60,'0','app_base',NOW(),NOW(),'0'),
  ('menu_log','操作日志','/system/log','menu_ocpx_system',
   'lucide:scroll-text','system/log/index',70,'0','app_base',NOW(),NOW(),'0'),
  ('menu_login_log','登录日志','/system/login-log','menu_ocpx_system',
   'lucide:log-in','system/login-log/index',80,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  icon=VALUES(icon),
  component=VALUES(component),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  update_time=NOW(),
  del_flag='0';

-- Attach the existing button permissions to their original pages and make
-- them active again. Historical permission IDs are preserved deliberately.
UPDATE sys_menu
SET parent_id = CASE
      WHEN permission LIKE 'system:sysuser:%' THEN 'menu_system_user'
      WHEN permission LIKE 'system:sysrole:%' THEN 'menu_role'
      WHEN permission LIKE 'system:sysmenu:%' THEN 'menu_menu'
      WHEN permission LIKE 'system:sysdept:%' THEN 'menu_dept'
      WHEN permission LIKE 'system:sysdictvalue:%' THEN 'menu_dict'
      WHEN permission LIKE 'system:sysdict:%' THEN 'menu_dict'
      WHEN permission LIKE 'system:storageconfig:%' THEN 'menu_storage'
      WHEN permission LIKE 'system:syslog:%' THEN 'menu_log'
      WHEN permission LIKE 'system:sysloginlog:%' THEN 'menu_login_log'
      ELSE parent_id
    END,
    del_flag = '0',
    update_time = NOW()
WHERE permission LIKE 'system:sysuser:%'
   OR permission LIKE 'system:sysrole:%'
   OR permission LIKE 'system:sysmenu:%'
   OR permission LIKE 'system:sysdept:%'
   OR permission LIKE 'system:sysdict:%'
   OR permission LIKE 'system:sysdictvalue:%'
   OR permission LIKE 'system:storageconfig:%'
   OR permission LIKE 'system:syslog:%'
   OR permission LIKE 'system:sysloginlog:%';

-- The canonical menu_ocpx_system row now owns the original /system tree.
-- Keep the old duplicate root and duplicate user page hidden.
UPDATE sys_menu
SET del_flag = '1', update_time = NOW()
WHERE id IN ('menu_system','menu_user');

-- Ensure every active administrator can see and operate the restored system
-- management tree. Other roles remain configurable through the original role
-- management page; their existing grants are neither broadened nor revoked.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v69_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id IN (
    'menu_ocpx_system','menu_system_user','menu_role','menu_menu','menu_dept',
    'menu_dict','menu_storage','menu_log','menu_login_log'
  )
  OR m.parent_id IN (
    'menu_system_user','menu_role','menu_menu','menu_dept','menu_dict',
    'menu_storage','menu_log','menu_login_log'
  )
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
-- V70: add the independent OCPX forwarding data plane without reviving retired domains.
-- This is a forward-only migration. Existing V68/V69 migrations remain immutable.

DROP PROCEDURE IF EXISTS scx_upgrade_ocpx_forwarding_engine;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_forwarding_engine()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='channel_code') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN channel_code VARCHAR(64) NULL AFTER id;
    UPDATE scx_ocpx_channel SET channel_code=CONCAT('channel_',LEFT(id,16)) WHERE channel_code IS NULL;
    ALTER TABLE scx_ocpx_channel MODIFY channel_code VARCHAR(64) NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='adapter_code') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'STANDARD' AFTER channel_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='auth_type') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN auth_type VARCHAR(16) NOT NULL DEFAULT 'HMAC' AFTER adapter_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='key_version') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN key_version INT NOT NULL DEFAULT 1 AFTER auth_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='status') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='callback_host_allowlist') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN callback_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND column_name='config_version') THEN
    ALTER TABLE scx_ocpx_channel ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel' AND index_name='uk_scx_ocpx_channel_code') THEN
    ALTER TABLE scx_ocpx_channel ADD UNIQUE KEY uk_scx_ocpx_channel_code (channel_code,active_marker);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser' AND column_name='adapter_code') THEN
    ALTER TABLE scx_ocpx_advertiser ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'COCO' AFTER advertiser_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser' AND column_name='status') THEN
    ALTER TABLE scx_ocpx_advertiser ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER balance;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser' AND column_name='upstream_host_allowlist') THEN
    ALTER TABLE scx_ocpx_advertiser ADD COLUMN upstream_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_advertiser' AND column_name='config_version') THEN
    ALTER TABLE scx_ocpx_advertiser ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='downstream_callback_url') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN downstream_callback_url VARCHAR(1500) NULL AFTER click_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='route_status') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN route_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER downstream_callback_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='config_version') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER route_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='rta_mode') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN rta_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER config_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='billing_mode') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN billing_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER rta_mode;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_at') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_at DATETIME(3) NULL AFTER billing_mode;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_rule' AND column_name='config_version') THEN
    ALTER TABLE scx_ocpx_deduction_rule ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER ratio;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='raw_conversion_count') THEN
    ALTER TABLE scx_ocpx_hourly_report
      ADD COLUMN raw_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER finish_order_count,
      ADD COLUMN forwarded_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER raw_conversion_count,
      ADD COLUMN deducted_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER forwarded_conversion_count,
      ADD COLUMN delivery_failure_count BIGINT NOT NULL DEFAULT 0 AFTER deducted_conversion_count;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_ocpx_forwarding_engine();
DROP PROCEDURE scx_upgrade_ocpx_forwarding_engine;

CREATE TABLE IF NOT EXISTS scx_ocpx_adapter (
  adapter_code VARCHAR(32) NOT NULL,
  adapter_name VARCHAR(100) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  capability_revision BIGINT NOT NULL DEFAULT 1,
  capabilities_json JSON NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (adapter_code),
  KEY idx_scx_ocpx_adapter_direction (direction,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Published OCPX adapter registry';

INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('STANDARD','标准 FeedbackReq/CallbackReq','DOWNSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','POST','callbackMethod','POST','auth','HMAC','callback',TRUE),NOW(3),NOW(3)),
  ('COCO','Coco CPA','UPSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','GET','callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000),NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE adapter_name=VALUES(adapter_name),direction=VALUES(direction),status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),update_time=NOW(3);

CREATE TABLE IF NOT EXISTS scx_ocpx_event (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  parent_event_id VARCHAR(128) NULL,
  source_direction VARCHAR(32) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  device_key_hash CHAR(64) NULL,
  payload_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  callback_ref VARCHAR(32) NULL,
  amount DECIMAL(20,6) NULL,
  amount_raw VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_event_source (source_direction,event_id),
  KEY idx_scx_ocpx_event_parent (parent_event_id,event_type),
  KEY idx_scx_ocpx_event_route (monitor_id,occurred_at),
  KEY idx_scx_ocpx_event_received (received_at,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX immutable normalized event';

CREATE TABLE IF NOT EXISTS scx_ocpx_protocol_snapshot (
  feedback_event_id VARCHAR(128) NOT NULL,
  feedback_ciphertext MEDIUMTEXT NOT NULL,
  route_ciphertext TEXT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (feedback_event_id),
  KEY idx_scx_ocpx_snapshot_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Encrypted minimum protocol retry snapshot';

CREATE TABLE IF NOT EXISTS scx_ocpx_callback_ref (
  id VARCHAR(32) NOT NULL,
  feedback_event_id VARCHAR(128) NOT NULL,
  route_version BIGINT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_callback_feedback (feedback_event_id),
  KEY idx_scx_ocpx_callback_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Opaque upstream callback correlation';

CREATE TABLE IF NOT EXISTS scx_ocpx_delivery_job (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  adapter_code VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NOT NULL,
  lease_owner VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  callback_ref VARCHAR(32) NULL,
  last_error VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_delivery_event (direction,event_id),
  KEY idx_scx_ocpx_delivery_claim (status,next_retry_at,lease_until),
  KEY idx_scx_ocpx_delivery_source (source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reliable outbound delivery job';

CREATE TABLE IF NOT EXISTS scx_ocpx_delivery_attempt (
  id VARCHAR(32) NOT NULL,
  job_id VARCHAR(32) NOT NULL,
  attempt_no INT NOT NULL,
  original_attempt_no INT NULL COMMENT 'Original sequence before forward integrity repair',
  status VARCHAR(16) NOT NULL,
  http_status INT NULL,
  error_message VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no),
  KEY idx_scx_ocpx_attempt_job (job_id,attempt_no),
  KEY idx_scx_ocpx_attempt_time (create_time,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbound delivery attempt audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_deduction_decision (
  id VARCHAR(32) NOT NULL,
  callback_event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  rule_version BIGINT NOT NULL,
  ratio DECIMAL(8,4) NOT NULL,
  forwarded TINYINT(1) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_deduction_event (callback_event_id),
  KEY idx_scx_ocpx_deduction_route (monitor_id,event_type,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable callback deduction decision';

CREATE TABLE IF NOT EXISTS scx_ocpx_ingress_dlq (
  id VARCHAR(32) NOT NULL,
  partition_no INT NOT NULL,
  stream_record_id VARCHAR(64) NOT NULL,
  event_id VARCHAR(128) NULL,
  envelope MEDIUMTEXT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  replayed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_ingress_dlq_record (partition_no,stream_record_id),
  KEY idx_scx_ocpx_ingress_dlq_status (status,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Feedback ingress dead-letter audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_config_outbox (
  id VARCHAR(32) NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(32) NOT NULL,
  config_version BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL,
  published_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_config_version (aggregate_type,aggregate_id,config_version),
  KEY idx_scx_ocpx_config_outbox (published,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional configuration publication outbox';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_decision_log (
  id VARCHAR(32) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(128) NULL,
  latency_ms INT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_rta_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reserved RTA decision audit';

CREATE TABLE IF NOT EXISTS scx_ocpx_billing_entry (
  id VARCHAR(32) NOT NULL,
  charge_event_id VARCHAR(128) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  amount DECIMAL(20,6) NULL,
  currency CHAR(3) NULL,
  mode VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  reason VARCHAR(128) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_billing_event (charge_event_id),
  KEY idx_scx_ocpx_billing_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reserved idempotent billing ledger';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_ocpx_runtime','转发运行中心','/ocpx/runtime','0','lucide:activity','ocpx/runtime/index',45,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_ocpx_runtime_read','查看转发状态','ocpx:runtime:read','menu_ocpx_runtime',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_runtime_replay','重放失败任务','ocpx:runtime:replay','menu_ocpx_runtime',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_monitor_publish','发布路由配置','ocpx:monitor:publish','menu_ocpx_monitor',30,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_ocpx_runtime','perm_ocpx_runtime_read','perm_ocpx_runtime_replay','perm_ocpx_monitor_publish')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_ocpx_runtime','perm_ocpx_runtime_read')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
-- V71: register the generic macro based upstream adapter used for sgyz-ocpx
-- style show/click forwarding. Partner-specific signatures remain separate adapters.

INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('TEMPLATE_GET','通用宏模板 GET','UPSTREAM','ACTIVE',
   JSON_OBJECT(
     'feedbackMethod','GET',
     'feedbackEvents',JSON_ARRAY('SHOW','CLICK'),
     'callbackMethod','GET',
     'callback',TRUE,
     'auth','NONE',
     'timeoutMs',3000,
     'compatibility','sgyz-ocpx-base-flow'
   ),NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
  adapter_name=VALUES(adapter_name),
  direction=VALUES(direction),
  status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),
  update_time=NOW(3);
-- V72: retire the automotive clue domain and enable idempotent recharge/CPC accounting.

DROP PROCEDURE IF EXISTS scx_upgrade_accounting;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_accounting()
BEGIN
  ALTER TABLE scx_ocpx_advertiser MODIFY COLUMN balance DECIMAL(20,6) NOT NULL DEFAULT 0.000000;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='balance_before') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN balance_before DECIMAL(20,6) NULL AFTER reason;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='balance_after') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN balance_after DECIMAL(20,6) NULL AFTER balance_before;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='operator_id') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN operator_id VARCHAR(100) NULL AFTER balance_after;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='remark') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN remark VARCHAR(255) NULL AFTER operator_id;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_accounting();
DROP PROCEDURE scx_upgrade_accounting;

ALTER TABLE scx_ocpx_billing_entry COMMENT='Idempotent advertiser recharge and click billing ledger';

DELETE FROM sys_role_menu
WHERE menu_id IN ('menu_ocpx_clue','perm_ocpx_clue_read','perm_ocpx_clue_write','perm_ocpx_clue_submit');

DELETE FROM sys_menu
WHERE id IN ('menu_ocpx_clue','perm_ocpx_clue_read','perm_ocpx_clue_write','perm_ocpx_clue_submit')
   OR permission IN ('ocpx:clue:read','ocpx:clue:write','ocpx:clue:submit');

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_ocpx_account_read','查询账务流水','ocpx:account:read','menu_ocpx_advertiser_list',30,'1','app_base',NOW(),'0'),
  ('perm_ocpx_account_recharge','余额充值','ocpx:account:recharge','menu_ocpx_advertiser_list',40,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ocpx_account_read','perm_ocpx_account_recharge')
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR') AND r.del_flag='0' AND m.del_flag='0';
-- V73: register the generic macro-based downstream GET callback adapter.
INSERT INTO scx_ocpx_adapter
  (adapter_code,adapter_name,direction,status,capabilities_json,create_time,update_time)
VALUES
  ('TEMPLATE_GET_DOWNSTREAM','通用下游 GET 回传','DOWNSTREAM','ACTIVE',
   JSON_OBJECT('feedbackMethod','POST','callbackMethod','GET','auth','HMAC','callback',TRUE,'timeoutMs',3000),
   NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE adapter_name=VALUES(adapter_name),direction=VALUES(direction),status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),update_time=NOW(3);
-- Coco v1.1 protocol completion and runtime request audit.
-- Forward-only migration: do not amend V70-V73 after they may have been applied.

ALTER TABLE scx_ocpx_advertiser
  MODIFY COLUMN bid_url VARCHAR(5000) NOT NULL;

ALTER TABLE scx_ocpx_monitor_link
  ADD COLUMN upstream_feedback_url VARCHAR(5000) NULL AFTER click_url,
  ADD COLUMN published_upstream_feedback_url VARCHAR(5000) NULL AFTER upstream_feedback_url;

ALTER TABLE scx_ocpx_hourly_report
  ADD COLUMN first_order_count BIGINT NOT NULL DEFAULT 0 AFTER order_count;

ALTER TABLE scx_ocpx_delivery_attempt
  ADD COLUMN request_method VARCHAR(16) NULL AFTER http_status,
  ADD COLUMN target_host VARCHAR(255) NULL AFTER request_method,
  ADD COLUMN target_path VARCHAR(1000) NULL AFTER target_host,
  ADD COLUMN request_summary VARCHAR(4000) NULL AFTER target_path,
  ADD COLUMN response_excerpt VARCHAR(2000) NULL AFTER request_summary,
  ADD COLUMN duration_ms BIGINT NULL AFTER response_excerpt;

CREATE TABLE IF NOT EXISTS scx_ocpx_ingress_audit (
  id VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NULL,
  channel_code VARCHAR(64) NULL,
  event_id VARCHAR(128) NULL,
  route_reference VARCHAR(100) NULL,
  result_status VARCHAR(32) NOT NULL,
  http_status INT NOT NULL,
  reason VARCHAR(1000) NULL,
  payload_hash CHAR(64) NULL,
  client_id VARCHAR(128) NULL,
  key_version VARCHAR(32) NULL,
  remote_ip_masked VARCHAR(64) NULL,
  duration_ms BIGINT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_ingress_audit_time (create_time,result_status),
  KEY idx_scx_ocpx_ingress_audit_event (event_id),
  KEY idx_scx_ocpx_ingress_audit_route (channel_code,route_reference,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Best-effort rejected and abnormal feedback ingress audit';
-- V75: normalize built-in adapter capabilities for protocol-driven route publishing.
-- The management service now validates URLs from these fields instead of hard-coding adapter codes.

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','DEDICATED',
      'feedbackMethod','GET','feedbackEvents',JSON_ARRAY('CLICK'),
      'callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(
        '__IDFA__','__IDFA_MD5__','__IMEI__','__IMEI_MD5__','__OAID__','__OAID_MD5__','__IP__',
        '__CAID__','__CAID_LIST__','__ANDROIDID__','__ANDROID_ID__','__ANDROIDID_MD5__',
        '__ANDROID_ID_MD5__','__AAID__','__UA__','__OS__','__MODEL__','__CALLBACK__','__CLICKID__',
        '__CAMPAIGNID__','__ADGROUPID__','__CREATIVEID__','__ADVERTISERID__'),
      'requiredUrlMacros',JSON_ARRAY('__CALLBACK__'),
      'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='COCO' AND direction='UPSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','GET_TEMPLATE',
      'feedbackMethod','GET','feedbackEvents',JSON_ARRAY('SHOW','CLICK'),
      'callbackMethod','GET','auth','NONE','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(
        '__EVENT__','__EVENT_TIME__','__EVENT_ID__','__TS__','__MTS__','__REQUEST_ID__','__CHANNEL_ID__',
        '__DSP_ID__','__LINK_ID__','__TASK_ID__','__MONITOR_ID__','__BIZ_ID__','__CAMPAIGN_ID__',
        '__ADGROUP_ID__','__CREATIVE_ID__','__CREATIVEID__','__CLICKID__','__IDFA__','__IDFA_MD5__',
        '__IDFA__MD5__','__IMEI__','__IMEI_MD5__','__IMEI__MD5__','__OAID__','__OAID_MD5__',
        '__OAID__MD5__','__CAID__','__CAID_LIST__','__ANDROID_ID__','__ANDROID_ID_MD5__','__AAID__',
        '__GAID__','__IDFV__','__IP__','__UA__','__OS__','__MODEL__','__CALLBACK__','__CALLBACK_URL__'),
      'requiredUrlMacros',JSON_ARRAY(),
      'requiredAnyUrlMacros',JSON_ARRAY(JSON_ARRAY('__CALLBACK__','__CALLBACK_URL__')),
      'compatibility','sgyz-ocpx-base-flow'
    ),
    update_time=NOW(3)
WHERE adapter_code='TEMPLATE_GET' AND direction='UPSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','STANDARD_JSON',
      'feedbackMethod','POST','feedbackEvents',JSON_ARRAY(
        'SHOW','CLICK','ACTIVATE','EVOKE','LOGIN','REGISTER','NEXT_DAY_RETENTION','PAYMENT',
        'CUSTOMER_ACQUISITION','VISIT','AUTHENTICATION','ORDER','FIRST_ORDER','COMPLETE_ORDER',
        'RETENTION','REACTIVATION','FIRST_REACTIVATION'),
      'callbackMethod','POST','auth','HMAC','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY(),'requiredUrlMacros',JSON_ARRAY(),'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='STANDARD' AND direction='DOWNSTREAM';

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_OBJECT(
      'contractVersion','1.0','protocolKind','GET_TEMPLATE',
      'feedbackMethod','POST','feedbackEvents',JSON_ARRAY(
        'SHOW','CLICK','ACTIVATE','EVOKE','LOGIN','REGISTER','NEXT_DAY_RETENTION','PAYMENT',
        'CUSTOMER_ACQUISITION','VISIT','AUTHENTICATION','ORDER','FIRST_ORDER','COMPLETE_ORDER',
        'RETENTION','REACTIVATION','FIRST_REACTIVATION'),
      'callbackMethod','GET','auth','HMAC','callback',TRUE,'timeoutMs',3000,
      'supportedMacros',JSON_ARRAY('__EVENT__','__EVENT_ID__','__SOURCE_EVENT_ID__','__CLICKID__',
        '__MONITOR_ID__','__CHANNEL_ID__','__AMOUNT__','__AMOUNT_RAW__','__TS__'),
      'requiredUrlMacros',JSON_ARRAY('__EVENT__','__EVENT_ID__'),
      'requiredAnyUrlMacros',JSON_ARRAY()
    ),
    update_time=NOW(3)
WHERE adapter_code='TEMPLATE_GET_DOWNSTREAM' AND direction='DOWNSTREAM';
-- V76: built-in API debugger, execution audit and OCPX debug correlation.
-- Forward-only migration. Debug facts remain immutable; scheduled retention only removes debugger-owned rows.

CREATE TABLE IF NOT EXISTS scx_api_debug_case (
  id VARCHAR(32) NOT NULL,
  case_name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  mode VARCHAR(32) NOT NULL,
  scenario VARCHAR(40) NULL,
  monitor_id VARCHAR(32) NULL,
  request_json MEDIUMTEXT NOT NULL,
  created_by VARCHAR(32) NOT NULL,
  updated_by VARCHAR(32) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_scx_api_debug_case_updated (deleted,update_time),
  KEY idx_scx_api_debug_case_name (case_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Shared administrator API debugger cases';

CREATE TABLE IF NOT EXISTS scx_api_debug_execution (
  id VARCHAR(32) NOT NULL,
  draft_id VARCHAR(32) NOT NULL,
  mode VARCHAR(32) NOT NULL,
  scenario VARCHAR(40) NULL,
  status VARCHAR(24) NOT NULL,
  monitor_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  request_method VARCHAR(16) NOT NULL,
  request_url VARCHAR(5000) NOT NULL,
  request_headers MEDIUMTEXT NULL,
  body_type VARCHAR(24) NOT NULL,
  request_body MEDIUMTEXT NULL,
  timeout_seconds INT NOT NULL DEFAULT 5,
  response_status INT NULL,
  response_headers MEDIUMTEXT NULL,
  response_body MEDIUMTEXT NULL,
  response_mime VARCHAR(255) NULL,
  response_size BIGINT NULL,
  response_sha256 CHAR(64) NULL,
  duration_ms BIGINT NULL,
  risk_summary VARCHAR(2000) NULL,
  side_effect_summary VARCHAR(4000) NULL,
  error_message VARCHAR(1000) NULL,
  created_by VARCHAR(32) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_api_debug_draft (draft_id),
  KEY idx_scx_api_debug_history (expires_at,create_time),
  KEY idx_scx_api_debug_status (status,update_time),
  KEY idx_scx_api_debug_monitor (monitor_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API debugger execution and seven-day history';

CREATE TABLE IF NOT EXISTS scx_api_debug_execution_log (
  id VARCHAR(32) NOT NULL,
  execution_id VARCHAR(32) NOT NULL,
  sequence_no BIGINT NOT NULL,
  source VARCHAR(16) NOT NULL,
  level VARCHAR(8) NOT NULL,
  phase VARCHAR(40) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  detail_json VARCHAR(4000) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_api_debug_log_sequence (execution_id,sequence_no),
  KEY idx_scx_api_debug_log_time (execution_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Sanitized management and engine debugger timeline';

CREATE TABLE IF NOT EXISTS scx_api_debug_log_access_audit (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,
  level_filter VARCHAR(16) NULL,
  logger_filter VARCHAR(255) NULL,
  keyword_sha256 CHAR(64) NULL,
  range_start DATETIME(3) NOT NULL,
  range_end DATETIME(3) NOT NULL,
  returned_count INT NOT NULL,
  client_ip VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_scx_api_debug_log_audit_expiry (expires_at),
  KEY idx_scx_api_debug_log_audit_user (user_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Raw debugger log access audit';

DROP PROCEDURE IF EXISTS scx_upgrade_api_debug_workbench;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_api_debug_workbench()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_ref,
      ADD COLUMN is_debug TINYINT(1) NOT NULL DEFAULT 0 AFTER debug_execution_id,
      ADD KEY idx_scx_ocpx_event_debug (debug_execution_id,received_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_callback_ref' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_callback_ref
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER route_version,
      ADD KEY idx_scx_ocpx_callback_debug (debug_execution_id,expires_at);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_job' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_delivery_job
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_ref,
      ADD KEY idx_scx_ocpx_delivery_debug (debug_execution_id,status);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_delivery_attempt
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER job_id,
      ADD KEY idx_scx_ocpx_attempt_debug (debug_execution_id,create_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_billing_entry
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER charge_event_id,
      ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'BUSINESS' AFTER debug_execution_id,
      ADD KEY idx_scx_ocpx_billing_debug (debug_execution_id,create_time);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_deduction_decision' AND column_name='debug_execution_id') THEN
    ALTER TABLE scx_ocpx_deduction_decision
      ADD COLUMN debug_execution_id VARCHAR(32) NULL AFTER callback_event_id,
      ADD KEY idx_scx_ocpx_deduction_debug (debug_execution_id,create_time);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_api_debug_workbench();
DROP PROCEDURE scx_upgrade_api_debug_workbench;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_ocpx_api_debug','接口调试','/ocpx/api-debugger','0','lucide:terminal-square','ocpx/api-debugger/index',46,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_ocpx_api_debug_read','查看接口调试','ocpx:api-debug:read','menu_ocpx_api_debug',10,'1','app_base',NOW(),'0'),
  ('perm_ocpx_api_debug_case_write','维护调试用例','ocpx:api-debug:case:write','menu_ocpx_api_debug',20,'1','app_base',NOW(),'0'),
  ('perm_ocpx_api_debug_execute','执行接口调试','ocpx:api-debug:execute','menu_ocpx_api_debug',30,'1','app_base',NOW(),'0'),
  ('perm_ocpx_api_debug_execute_billable','执行真实扣费调试','ocpx:api-debug:execute-billable','menu_ocpx_api_debug',40,'1','app_base',NOW(),'0'),
  ('perm_ocpx_api_debug_logs','查看调试日志','ocpx:api-debug:logs','menu_ocpx_api_debug',50,'1','app_base',NOW(),'0'),
  ('perm_ocpx_api_debug_logs_raw','查看近期原始日志','ocpx:api-debug:logs-raw','menu_ocpx_api_debug',60,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ocpx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_ocpx_api_debug','perm_ocpx_api_debug_read','perm_ocpx_api_debug_case_write',
   'perm_ocpx_api_debug_execute','perm_ocpx_api_debug_execute_billable',
   'perm_ocpx_api_debug_logs','perm_ocpx_api_debug_logs_raw')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
-- Forward-only OCPX click-deduplication foundation.
-- Keep accepted click facts immutable; the decision table controls billing and forwarding.

DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_click_deduplication()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link'
      AND column_name='click_dedup_window_seconds'
  ) THEN
    ALTER TABLE scx_ocpx_monitor_link
      ADD COLUMN click_dedup_window_seconds INT NOT NULL DEFAULT 3600 AFTER billing_mode;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_platform'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_platform VARCHAR(16) NULL AFTER received_at;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_id_type'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_id_type VARCHAR(32) NULL AFTER device_platform;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event'
      AND column_name='device_key_version'
  ) THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN device_key_version INT NULL AFTER device_id_type;
  END IF;
END$$
DELIMITER ;

CALL scx_upgrade_ocpx_click_deduplication();
DROP PROCEDURE scx_upgrade_ocpx_click_deduplication;

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_state (
  monitor_id VARCHAR(32) NOT NULL,
  device_key_hash CHAR(64) NOT NULL,
  canonical_event_id VARCHAR(128) NOT NULL,
  canonical_received_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (monitor_id, device_key_hash),
  KEY idx_scx_ocpx_click_state_time (canonical_received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Latest eligible click per route and device';

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_decision (
  event_id VARCHAR(128) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  device_key_hash CHAR(64) NULL,
  window_seconds INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  canonical_event_id VARCHAR(128) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY idx_scx_ocpx_click_decision_route (monitor_id, received_at, status),
  KEY idx_scx_ocpx_click_decision_device (monitor_id, device_key_hash, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable OCPX click deduplication decision';
-- OCPX effect dashboard facts. Historical click quality is only reconstructed from
-- immutable V77 decisions; periods without decisions deliberately remain uncovered.

DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_effect_dashboard_metrics()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='eligible_click_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN eligible_click_count BIGINT NOT NULL DEFAULT 0 AFTER clicks;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='deduplicated_click_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN deduplicated_click_count BIGINT NOT NULL DEFAULT 0 AFTER eligible_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='skipped_click_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN skipped_click_count BIGINT NOT NULL DEFAULT 0 AFTER deduplicated_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='classified_click_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN classified_click_count BIGINT NOT NULL DEFAULT 0 AFTER skipped_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='first_activate_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN first_activate_count BIGINT NOT NULL DEFAULT 0 AFTER activate_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='reactivation_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN reactivation_count BIGINT NOT NULL DEFAULT 0 AFTER evoke_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='retention_3d_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN retention_3d_count BIGINT NOT NULL DEFAULT 0 AFTER next_stay_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='retention_4d_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN retention_4d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_3d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='retention_5d_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN retention_5d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_4d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='retention_6d_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN retention_6d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_5d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='retention_7d_count') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN retention_7d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_6d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='create_time') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report' AND column_name='update_time') THEN
        ALTER TABLE scx_ocpx_hourly_report ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
    END IF;
END$$
DELIMITER ;

CALL scx_upgrade_ocpx_effect_dashboard_metrics();
DROP PROCEDURE scx_upgrade_ocpx_effect_dashboard_metrics;

-- V80 current state: channel event management and immutable CPA runtime routing.
CREATE TABLE IF NOT EXISTS scx_ocpx_event_definition (
  id VARCHAR(32) NOT NULL, category VARCHAR(24) NOT NULL, channel_id VARCHAR(32) NOT NULL DEFAULT '',
  event_code VARCHAR(64) NOT NULL, event_name VARCHAR(100) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0, remark VARCHAR(500) NULL, built_in TINYINT(1) NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_event_scope_code (category,channel_id,event_code),
  KEY idx_scx_ocpx_event_filter (category,channel_id,status,sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCPX managed event dictionary';

CREATE TABLE IF NOT EXISTS scx_ocpx_channel_event_mapping (
  id VARCHAR(32) NOT NULL, channel_id VARCHAR(32) NOT NULL, upstream_event_id VARCHAR(32) NOT NULL,
  conversion_event_id VARCHAR(32) NOT NULL, media_event_id VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(500) NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_mapping_upstream (channel_id,upstream_event_id),
  KEY idx_scx_ocpx_mapping_target (conversion_event_id,media_event_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel event mapping';

CREATE TABLE IF NOT EXISTS scx_ocpx_monitor_event_binding (
  id VARCHAR(32) NOT NULL, monitor_id VARCHAR(32) NOT NULL, event_mapping_id VARCHAR(32) NOT NULL,
  assessment_unit_price DECIMAL(20,6) NOT NULL DEFAULT 0.000000, daily_limit INT NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_monitor_event (monitor_id,event_mapping_id),
  KEY idx_scx_ocpx_monitor_binding_mapping (event_mapping_id,monitor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Draft monitor event bindings';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_event_snapshot (
  id VARCHAR(32) NOT NULL, monitor_id VARCHAR(32) NOT NULL, route_version BIGINT NOT NULL,
  event_mapping_id VARCHAR(32) NOT NULL, upstream_event_code VARCHAR(64) NOT NULL,
  upstream_event_name VARCHAR(100) NOT NULL, conversion_event_code VARCHAR(64) NOT NULL,
  conversion_event_name VARCHAR(100) NOT NULL, media_event_code VARCHAR(64) NOT NULL,
  media_event_name VARCHAR(100) NOT NULL, assessment_unit_price DECIMAL(20,6) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 0, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_route_event (monitor_id,route_version,upstream_event_code),
  KEY idx_scx_ocpx_route_event_version (monitor_id,route_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable published route event snapshot';

CREATE TABLE IF NOT EXISTS scx_ocpx_event_daily_quota (
  monitor_id VARCHAR(32) NOT NULL, upstream_event_code VARCHAR(64) NOT NULL, business_date DATE NOT NULL,
  queued_count INT NOT NULL DEFAULT 0, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (monitor_id,upstream_event_code,business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Strict Asia Shanghai callback quota';

DROP PROCEDURE IF EXISTS scx_upgrade_v80_event_runtime;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v80_event_runtime()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='event_snapshot_version') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN event_snapshot_version INT NOT NULL DEFAULT 0 AFTER click_dedup_window_seconds;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='source_event_code') THEN
    ALTER TABLE scx_ocpx_event ADD COLUMN source_event_code VARCHAR(64) NULL AFTER event_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='conversion_event_code') THEN
    ALTER TABLE scx_ocpx_event ADD COLUMN conversion_event_code VARCHAR(64) NULL AFTER source_event_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='media_event_code') THEN
    ALTER TABLE scx_ocpx_event ADD COLUMN media_event_code VARCHAR(64) NULL AFTER conversion_event_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='cpa_charge_amount') THEN
    ALTER TABLE scx_ocpx_event ADD COLUMN cpa_charge_amount DECIMAL(20,6) NULL AFTER amount_raw;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='daily_limit_result') THEN
    ALTER TABLE scx_ocpx_event ADD COLUMN daily_limit_result VARCHAR(24) NULL AFTER cpa_charge_amount;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='channel_id') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN channel_id VARCHAR(32) NULL AFTER advertiser_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='app_link_id') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN app_link_id VARCHAR(32) NULL AFTER channel_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='monitor_id') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN monitor_id VARCHAR(32) NULL AFTER app_link_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='source_event_code') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN source_event_code VARCHAR(64) NULL AFTER monitor_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='conversion_event_code') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN conversion_event_code VARCHAR(64) NULL AFTER source_event_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='media_event_code') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN media_event_code VARCHAR(64) NULL AFTER conversion_event_code;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v80_event_runtime();
DROP PROCEDURE scx_upgrade_v80_event_runtime;

CREATE TEMPORARY TABLE scx_v80_conversion_seed (event_code VARCHAR(64) PRIMARY KEY,event_name VARCHAR(100),sort_order INT);
INSERT INTO scx_v80_conversion_seed VALUES
 ('ACTIVATE','激活',10),('REGISTER','注册',20),('NEXT_DAY_RETENTION','次留',30),
 ('REACTIVATION','拉活/唤醒/唤起/唤端',40),('FIRST_ACTIVATION','首活首唤',50),('FIRST_ORDER','首单/首购',60),
 ('COMPLETE_ORDER','完单',70),('PAYMENT','购买付费',80),('ORDER','下单',90),('VIDEO_COMPLETE_90S','完播90秒',100),
 ('KEY_ACTION','自定义事件/关键行为',110),('RETENTION_D2','2日留存',120),('RETENTION_D3','3日留存',130),
 ('RETENTION_D4','4日留存',140),('RETENTION_D5','5日留存',150),('RETENTION_D6','6日留存',160),
 ('RETENTION_D7','7日留存',170),('FORM_SUBMIT','表单提交',180),('KEY_PAGE_VIEW','关键页面行为/关键页面浏览',190),
 ('AUTHENTICATION','授信/授权',200),('RECALL','拉回',210),('UNKNOWN','未知',220);
INSERT INTO scx_ocpx_event_definition
 (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
SELECT LEFT(SHA2(CONCAT('CONVERSION||',event_code),256),32),'CONVERSION','',event_code,event_name,'ACTIVE',sort_order,1,0
FROM scx_v80_conversion_seed
ON DUPLICATE KEY UPDATE id=scx_ocpx_event_definition.id;

CREATE TEMPORARY TABLE scx_v80_upstream_seed (event_code VARCHAR(64) PRIMARY KEY,event_name VARCHAR(100),sort_order INT);
INSERT INTO scx_v80_upstream_seed VALUES
 ('ACTIVATE','激活',10),('ACQUISITION','拉新',20),('REGISTER','注册',30),('FIRST_REACTIVATION','首唤',40),
 ('REACTIVATION_COMMON','拉活（常用）',50),('ALIPAY_ASSIST_SUCCESS_5S','支付宝预算“助力成功&5s播放”',60),
 ('PAYMENT','付费',70),('NEXT_DAY_RETENTION','次留',80),('RETENTION_D3','3日留存',90),('RETENTION_D4','4日留存',100),
 ('RETENTION_D5','5日留存',110),('RETENTION_D6','6日留存',120),('RETENTION_D7','7日留存',130),
 ('APP_VISIT','APP内访问',140),('RECALL','召回',150),('KEY_ACTION','关键行为',160),('FIRST_ORDER','首购',170),
 ('REACTIVATION','拉活',180),('VALID_EVOKE','有效唤端',190),('ORDER','下单',200),('LEAD','线索',210),
 ('BUDGET_UNDEFINED','预算未定义',220);
INSERT INTO scx_ocpx_event_definition
 (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
SELECT LEFT(SHA2(CONCAT('UPSTREAM|',c.id,'|',s.event_code),256),32),'UPSTREAM',c.id,s.event_code,s.event_name,
 'ACTIVE',s.sort_order,1,0 FROM scx_ocpx_channel c JOIN scx_v80_upstream_seed s ON 1=1 WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_ocpx_event_definition.id;

CREATE TEMPORARY TABLE scx_v80_media_seed LIKE scx_v80_conversion_seed;
INSERT INTO scx_v80_media_seed SELECT * FROM scx_v80_conversion_seed;
UPDATE scx_v80_media_seed SET event_name='购买/付费' WHERE event_code='PAYMENT';
INSERT INTO scx_ocpx_event_definition
 (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
SELECT LEFT(SHA2(CONCAT('MEDIA|',c.id,'|',s.event_code),256),32),'MEDIA',c.id,s.event_code,s.event_name,
 'ACTIVE',s.sort_order,1,0 FROM scx_ocpx_channel c JOIN scx_v80_media_seed s ON 1=1 WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_ocpx_event_definition.id;

INSERT INTO scx_ocpx_channel_event_mapping
 (id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status,remark,deleted)
SELECT LEFT(SHA2(CONCAT('MAPPING|',c.id,'|',x.event_code),256),32),c.id,u.id,v.id,m.id,'ACTIVE','系统默认映射',0
FROM scx_ocpx_channel c
JOIN (SELECT 'ACTIVATE' event_code UNION ALL SELECT 'REGISTER' UNION ALL SELECT 'PAYMENT' UNION ALL SELECT 'NEXT_DAY_RETENTION') x ON 1=1
JOIN scx_ocpx_event_definition u ON u.category='UPSTREAM' AND u.channel_id=c.id AND u.event_code=x.event_code
JOIN scx_ocpx_event_definition v ON v.category='CONVERSION' AND v.channel_id='' AND v.event_code=x.event_code
JOIN scx_ocpx_event_definition m ON m.category='MEDIA' AND m.channel_id=c.id AND m.event_code=x.event_code
WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_ocpx_channel_event_mapping.id;

DROP TEMPORARY TABLE scx_v80_conversion_seed;
DROP TEMPORARY TABLE scx_v80_upstream_seed;
DROP TEMPORARY TABLE scx_v80_media_seed;

INSERT INTO sys_menu
 (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
 ('menu_ocpx_event','事件管理','/system/event','menu_ocpx_system','lucide:workflow','system/event/index','ocpx:event:read',55,'0','app_base',NOW(),NOW(),'0'),
 ('perm_ocpx_event_write','维护事件','','menu_ocpx_event',NULL,NULL,'ocpx:event:write',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
 component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
 application_key=VALUES(application_key),update_time=NOW(),del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v80_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('menu_ocpx_event','perm_ocpx_event_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

-- V81 current state: explicit OCPX object scopes on top of sys_user and Sa-Token RBAC.
CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope_revision (
  user_id VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  update_by VARCHAR(100) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OCPX user scope set optimistic-lock revision';

CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  scope_type VARCHAR(32) NOT NULL COMMENT 'ALL,ADVERTISER,CHANNEL,MEDIA_ACCOUNT',
  scope_id VARCHAR(32) NULL COMMENT 'NULL only for ALL',
  permission_level VARCHAR(16) NOT NULL COMMENT 'VIEW,OPERATE,MANAGE',
  valid_from DATETIME(3) NULL,
  valid_to DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_scope_key VARCHAR(80) GENERATED ALWAYS AS (
    IF(deleted=0,CONCAT(scope_type,':',COALESCE(scope_id,'')),NULL)
  ) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_user_scope (user_id,active_scope_key),
  KEY idx_scx_ocpx_user_scope_user (user_id,status,valid_to),
  KEY idx_scx_ocpx_user_scope_object (scope_type,scope_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Explicit OCPX object data scope for sys_user';

INSERT INTO scx_ocpx_user_scope_revision (user_id,version,update_by,update_time)
SELECT DISTINCT u.id,1,'V81_MIGRATION',NOW(3)
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id=u.id
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.del_flag='0'
WHERE u.del_flag='0' AND (r.role_code='ROLE_ADMIN' OR (u.type='2' AND u.shop_id IS NOT NULL))
ON DUPLICATE KEY UPDATE version=GREATEST(scx_ocpx_user_scope_revision.version,1),
 update_time=scx_ocpx_user_scope_revision.update_time;

INSERT INTO scx_ocpx_user_scope
 (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|ALL|',u.id),256),32),u.id,'ALL',NULL,'MANAGE','ACTIVE',1,
 'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id
WHERE u.del_flag='0' AND r.del_flag='0' AND r.role_code='ROLE_ADMIN'
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

INSERT INTO scx_ocpx_user_scope
 (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|ADVERTISER|',u.id,'|',u.shop_id),256),32),u.id,'ADVERTISER',u.shop_id,
 'MANAGE','ACTIVE',1,'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY u.shop_id AND a.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

INSERT INTO scx_ocpx_user_scope
 (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|CHANNEL|',u.id,'|',m.channel_id),256),32),u.id,'CHANNEL',m.channel_id,
 'MANAGE','ACTIVE',1,'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN scx_ocpx_monitor_link m ON BINARY m.advertiser_id=BINARY u.shop_id AND m.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
GROUP BY u.id,m.channel_id
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

INSERT INTO sys_menu
 (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
 ('perm_ocpx_scope_view','查看OCPX对象范围','','menu_system_user',NULL,NULL,'ocpx:scope:view',80,'1','app_base',NOW(),NOW(),'0'),
 ('perm_ocpx_scope_edit','维护OCPX对象范围','','menu_system_user',NULL,NULL,'ocpx:scope:edit',90,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
 sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v81_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('perm_ocpx_scope_view','perm_ocpx_scope_edit')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

-- V82 current state: Advertiser onboarding, write-only credential revisions,
-- controlled integration-document revisions and explicit republish-required state.
INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_view','查看广告主','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_add','新增广告主','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_edit','编辑广告主','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_status','变更广告主状态','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_credential','轮换广告主凭据','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:credential',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_document_view','查看广告主资料','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:document:view',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_document_edit','维护广告主资料','','menu_ocpx_advertiser_list',NULL,NULL,'ocpx:advertiser:document:edit',70,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_ocpx_advertiser_view','perm_ocpx_advertiser_add','perm_ocpx_advertiser_edit',
  'perm_ocpx_advertiser_status','perm_ocpx_advertiser_credential',
  'perm_ocpx_advertiser_document_view','perm_ocpx_advertiser_document_edit'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_ocpx_advertiser_view','perm_ocpx_advertiser_add','perm_ocpx_advertiser_edit',
  'perm_ocpx_advertiser_document_view','perm_ocpx_advertiser_document_edit'
)
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v82_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ocpx_advertiser_view','perm_ocpx_advertiser_document_view')
WHERE r.role_code IN ('ROLE_ANALYST','ROLE_ADVERTISER') AND r.del_flag='0' AND m.del_flag='0';

-- V83 current state: independent Product aggregate, managed Logo metadata,
-- default event-policy templates and forward-compatible AppLink/Route slots.
CREATE TABLE IF NOT EXISTS scx_ocpx_product_logo_object (
  id VARCHAR(32) NOT NULL, storage_config_id VARCHAR(32) NOT NULL, storage_provider VARCHAR(32) NOT NULL,
  object_key VARCHAR(1000) NOT NULL, file_name VARCHAR(255) NOT NULL, content_type VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, image_width INT NOT NULL, image_height INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', created_by VARCHAR(100) NOT NULL, retired_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), KEY idx_scx_ocpx_product_logo_status (status,create_time),
  KEY idx_scx_ocpx_product_logo_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Managed Product Logo object metadata';

CREATE TABLE IF NOT EXISTS scx_ocpx_product (
  id VARCHAR(32) NOT NULL, advertiser_id VARCHAR(32) NOT NULL, code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL, normalized_name VARCHAR(128) NOT NULL, package_name VARCHAR(255) NULL,
  logo_object_id VARCHAR(32) NULL, description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', config_version BIGINT NOT NULL DEFAULT 1,
  event_policy_revision BIGINT NOT NULL DEFAULT 1, create_request_key VARCHAR(64) NOT NULL,
  create_request_hash CHAR(64) NOT NULL, archived_by VARCHAR(100) NULL, archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_product_code (advertiser_id,code),
  UNIQUE KEY uk_scx_ocpx_product_name (advertiser_id,normalized_name),
  UNIQUE KEY uk_scx_ocpx_product_create_request (create_request_key),
  KEY idx_scx_ocpx_product_filter (advertiser_id,status,update_time),
  KEY idx_scx_ocpx_product_package (advertiser_id,package_name), KEY idx_scx_ocpx_product_logo (logo_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Independent OCPX Product master data';

CREATE TABLE IF NOT EXISTS scx_ocpx_product_event_policy (
  id VARCHAR(32) NOT NULL, product_id VARCHAR(32) NOT NULL, event_definition_id VARCHAR(32) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_product_event (product_id,event_definition_id),
  KEY idx_scx_ocpx_product_event_definition (event_definition_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Product default event-policy template, copied at later Task binding';

CREATE TABLE IF NOT EXISTS scx_ocpx_product_change_audit (
  id VARCHAR(32) NOT NULL, product_id VARCHAR(32) NOT NULL, advertiser_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL, before_config_version BIGINT NOT NULL, after_config_version BIGINT NOT NULL,
  event_policy_revision BIGINT NOT NULL, affected_app_link_count INT NOT NULL DEFAULT 0,
  affected_route_count INT NOT NULL DEFAULT 0, actor VARCHAR(100) NOT NULL, change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id),
  KEY idx_scx_ocpx_product_audit (product_id,create_time,action),
  KEY idx_scx_ocpx_product_audit_advertiser (advertiser_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Product configuration audit';

DROP PROCEDURE IF EXISTS scx_upgrade_v83_product_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v83_product_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_app_link' AND column_name='product_id') THEN
    ALTER TABLE scx_ocpx_app_link ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_app_link' AND index_name='idx_scx_ocpx_app_link_product') THEN
    ALTER TABLE scx_ocpx_app_link ADD KEY idx_scx_ocpx_app_link_product (product_id,advertiser_id,deleted);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_product_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_product_id VARCHAR(32) NULL AFTER published_document_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_product_version') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_product_version BIGINT NOT NULL DEFAULT 0 AFTER published_product_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_product_event_policy_revision') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_product_event_policy_revision BIGINT NOT NULL DEFAULT 0 AFTER published_product_version;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v83_product_contract();
DROP PROCEDURE scx_upgrade_v83_product_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_product','产品管理','/ocpx/advertiser/product','menu_ocpx_advertiser','lucide:package-open','ocpx/product/index','ocpx:product:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_product_view','查看产品','','menu_ocpx_product',NULL,NULL,'ocpx:product:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_product_add','新增产品','','menu_ocpx_product',NULL,NULL,'ocpx:product:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_product_edit','编辑产品','','menu_ocpx_product',NULL,NULL,'ocpx:product:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_product_archive','归档恢复产品','','menu_ocpx_product',NULL,NULL,'ocpx:product:archive',40,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';
UPDATE sys_menu SET sort=30,update_time=NOW() WHERE id='menu_ocpx_app_link';
UPDATE sys_menu SET sort=40,update_time=NOW() WHERE id='menu_ocpx_deduction';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_product','perm_ocpx_product_view','perm_ocpx_product_add','perm_ocpx_product_edit','perm_ocpx_product_archive')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_product','perm_ocpx_product_view','perm_ocpx_product_add','perm_ocpx_product_edit')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v83_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_product','perm_ocpx_product_view')
WHERE r.role_code IN ('ROLE_ANALYST','ROLE_ADVERTISER') AND r.del_flag='0' AND m.del_flag='0';

-- V84 current state: Channel/MediaAccount separation, encrypted external
-- credential revisions, internal owners and frozen route references.
CREATE TABLE IF NOT EXISTS scx_ocpx_media_account (
  id VARCHAR(32) NOT NULL, channel_id VARCHAR(32) NOT NULL, code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL, normalized_name VARCHAR(128) NOT NULL, document_id VARCHAR(32) NULL,
  document_version BIGINT NOT NULL DEFAULT 0, description VARCHAR(500) NULL,
  default_credential_id VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT 'ACTIVE,DISABLED,PAUSED,ARCHIVED',
  config_version BIGINT NOT NULL DEFAULT 1, credential_revision BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NOT NULL, create_request_hash CHAR(64) NOT NULL,
  archived_by VARCHAR(100) NULL, archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  active_name_key VARCHAR(128) GENERATED ALWAYS AS (IF(status<>'ARCHIVED',normalized_name,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_media_account_code (code),
  UNIQUE KEY uk_scx_ocpx_media_account_name (channel_id,active_name_key),
  UNIQUE KEY uk_scx_ocpx_media_account_request (create_request_key),
  KEY idx_scx_ocpx_media_account_filter (channel_id,status,update_time),
  KEY idx_scx_ocpx_media_account_document (document_id,document_version),
  KEY idx_scx_ocpx_media_account_default_credential (default_credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Media business account separated from protocol Channel';

CREATE TABLE IF NOT EXISTS scx_ocpx_media_credential (
  id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL,
  login_type VARCHAR(16) NOT NULL DEFAULT 'EMAIL', login_identifier_ciphertext TEXT NOT NULL,
  login_identifier_hash CHAR(64) NOT NULL, masked_identifier VARCHAR(255) NOT NULL,
  current_credential_ref VARCHAR(64) NOT NULL, current_secret_revision BIGINT NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING,ACTIVE,DISABLED,REVOKED',
  version BIGINT NOT NULL DEFAULT 1, last_preflight_status VARCHAR(16) NULL,
  last_preflight_at DATETIME(3) NULL, create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_media_credential_identifier (media_account_id,login_type,login_identifier_hash),
  KEY idx_scx_ocpx_media_credential_status (media_account_id,status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='External media credential identity; never a sys_user';

CREATE TABLE IF NOT EXISTS scx_ocpx_media_credential_revision (
  id VARCHAR(32) NOT NULL, credential_id VARCHAR(32) NOT NULL, credential_ref VARCHAR(64) NOT NULL,
  secret_revision BIGINT NOT NULL, secret_ciphertext TEXT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE,OVERLAP,RETIRED', overlap_until DATETIME(3) NULL,
  reason VARCHAR(255) NULL, created_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_media_credential_revision (credential_id,secret_revision),
  UNIQUE KEY uk_scx_ocpx_media_credential_ref (credential_ref),
  KEY idx_scx_ocpx_media_credential_revision_status (credential_id,status,overlap_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Write-only encrypted MediaCredential revisions';

CREATE TABLE IF NOT EXISTS scx_ocpx_media_account_owner (
  id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL, user_id VARCHAR(32) NOT NULL,
  responsibility VARCHAR(32) NOT NULL DEFAULT 'PRIMARY', status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 1, create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_media_account_owner (media_account_id,user_id,responsibility),
  KEY idx_scx_ocpx_media_account_owner_user (user_id,status,media_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Internal sys_user responsibility for MediaAccount';

CREATE TABLE IF NOT EXISTS scx_ocpx_media_account_change_audit (
  id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL, action VARCHAR(32) NOT NULL,
  before_config_version BIGINT NOT NULL, after_config_version BIGINT NOT NULL,
  credential_revision BIGINT NOT NULL, owner_count INT NOT NULL DEFAULT 0,
  affected_route_count INT NOT NULL DEFAULT 0, actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), KEY idx_scx_ocpx_media_account_audit (media_account_id,create_time,action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free MediaAccount configuration audit';

DROP PROCEDURE IF EXISTS scx_upgrade_v84_media_account_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v84_media_account_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='media_account_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='media_credential_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN media_credential_id VARCHAR(32) NULL AFTER media_account_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_media_account_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_media_account_id VARCHAR(32) NULL AFTER published_product_event_policy_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_media_account_version') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_media_account_version BIGINT NOT NULL DEFAULT 0 AFTER published_media_account_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_media_account_credential_revision') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_media_account_credential_revision BIGINT NOT NULL DEFAULT 0 AFTER published_media_account_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_media_credential_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_media_credential_id VARCHAR(32) NULL AFTER published_media_account_credential_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_media_credential_secret_revision') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_media_credential_secret_revision BIGINT NOT NULL DEFAULT 0 AFTER published_media_credential_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND index_name='idx_scx_ocpx_monitor_media_account') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD KEY idx_scx_ocpx_monitor_media_account (media_account_id,media_credential_id,route_status,deleted);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v84_media_account_contract();
DROP PROCEDURE scx_upgrade_v84_media_account_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_media_account','媒体账号','/ocpx/channel/media-account','menu_ocpx_channel','lucide:badge-user','ocpx/media-account/index','ocpx:media-account:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_account_view','查看媒体账号','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_account_add','新增媒体账号','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_account_edit','编辑媒体账号','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_account_status','变更媒体账号状态','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_account_archive','归档恢复媒体账号','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:archive',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_credential_view','查看外部媒体凭据','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:credential:view',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_credential_add','新增外部媒体凭据','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:credential:add',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_credential_edit','编辑外部媒体凭据','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:credential:edit',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_credential_rotate','轮换外部媒体Secret','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:credential:rotate',90,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_credential_status','变更外部媒体凭据状态','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:credential:status',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_owner_view','查看媒体账号负责人','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:owner:view',110,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_owner_edit','维护媒体账号负责人','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:owner:edit',120,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_debug','媒体账号配置预检','','menu_ocpx_media_account',NULL,NULL,'ocpx:media-account:debug',130,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';
UPDATE sys_menu SET sort=30,update_time=NOW() WHERE id='menu_ocpx_monitor';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_media_account','perm_ocpx_media_account_view','perm_ocpx_media_account_add',
  'perm_ocpx_media_account_edit','perm_ocpx_media_account_status','perm_ocpx_media_account_archive',
  'perm_ocpx_media_credential_view','perm_ocpx_media_credential_add','perm_ocpx_media_credential_edit',
  'perm_ocpx_media_credential_rotate','perm_ocpx_media_credential_status','perm_ocpx_media_owner_view',
  'perm_ocpx_media_owner_edit','perm_ocpx_media_debug')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_media_account','perm_ocpx_media_account_view','perm_ocpx_media_account_add',
  'perm_ocpx_media_account_edit','perm_ocpx_media_account_status','perm_ocpx_media_credential_view',
  'perm_ocpx_media_credential_add','perm_ocpx_media_credential_edit','perm_ocpx_media_credential_rotate',
  'perm_ocpx_media_owner_view','perm_ocpx_media_owner_edit','perm_ocpx_media_debug')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v84_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_media_account','perm_ocpx_media_account_view')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';

-- V85 current state: structured AppLink with explicit legacy mapping review.
DROP PROCEDURE IF EXISTS scx_upgrade_v85_app_link_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v85_app_link_contract()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_app_link' AND column_name='link_url' AND data_type<>'text') THEN
    ALTER TABLE scx_ocpx_app_link MODIFY COLUMN link_url TEXT NOT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_app_link' AND column_name='deeplink' AND data_type<>'text') THEN
    ALTER TABLE scx_ocpx_app_link MODIFY COLUMN deeplink TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_app_link_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_app_link_id VARCHAR(32) NULL AFTER published_product_event_policy_revision;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_app_link_version') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_app_link_version BIGINT NOT NULL DEFAULT 0 AFTER published_app_link_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_app_link_type') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_app_link_type VARCHAR(16) NULL AFTER published_app_link_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_link_url_template') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_link_url_template TEXT NULL AFTER published_app_link_type;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_link_url_template' AND data_type<>'text') THEN
    ALTER TABLE scx_ocpx_monitor_link MODIFY COLUMN published_link_url_template TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_link_template_host') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_link_template_host VARCHAR(255) NULL AFTER published_link_url_template;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_link_template_fingerprint') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_link_template_fingerprint CHAR(64) NULL AFTER published_link_template_host;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_link_template_macro_set') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_link_template_macro_set JSON NULL AFTER published_link_template_fingerprint;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_redirect_enabled') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_redirect_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER published_link_template_macro_set;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_deeplink_url') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_deeplink_url TEXT NULL AFTER published_redirect_enabled;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_deeplink_url' AND data_type<>'text') THEN
    ALTER TABLE scx_ocpx_monitor_link MODIFY COLUMN published_deeplink_url TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_download_url') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN published_download_url TEXT NULL AFTER published_deeplink_url;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='published_download_url' AND data_type<>'text') THEN
    ALTER TABLE scx_ocpx_monitor_link MODIFY COLUMN published_download_url TEXT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND index_name='idx_scx_ocpx_monitor_app_link_publish') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD KEY idx_scx_ocpx_monitor_app_link_publish (app_link_id,published_app_link_id,route_status,deleted);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v85_app_link_contract();
DROP PROCEDURE scx_upgrade_v85_app_link_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_app_link','链接管理','/ocpx/advertiser/app-link','menu_ocpx_advertiser','lucide:link','ocpx/app-link/index','ocpx:app-link:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_view_v85','查看链接','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_add_v85','新增链接','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_edit_v85','编辑及补映射链接','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_status_v85','变更链接状态','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_archive_v85','归档恢复链接','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:archive',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_app_link_validate_v85','校验链接URL','','menu_ocpx_app_link',NULL,NULL,'ocpx:app-link:validate',60,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_app_link','perm_ocpx_app_link_view_v85','perm_ocpx_app_link_add_v85',
  'perm_ocpx_app_link_edit_v85','perm_ocpx_app_link_status_v85','perm_ocpx_app_link_archive_v85','perm_ocpx_app_link_validate_v85')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_app_link','perm_ocpx_app_link_view_v85','perm_ocpx_app_link_add_v85',
  'perm_ocpx_app_link_edit_v85','perm_ocpx_app_link_status_v85','perm_ocpx_app_link_validate_v85')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v85_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_app_link','perm_ocpx_app_link_view_v85')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';

-- V86 current state: Task operational aggregate with explicit legacy Monitor mapping.
CREATE TABLE IF NOT EXISTS scx_ocpx_task (
  id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL, biz_code VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL, product_id VARCHAR(32) NOT NULL,
  click_app_link_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  exposure_app_link_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  channel_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL, media_account_id VARCHAR(32) NOT NULL,
  media_credential_id VARCHAR(32) NOT NULL, media_credential_secret_revision BIGINT NOT NULL,
  operator_user_id VARCHAR(64) NOT NULL, task_name VARCHAR(64) NOT NULL,
  normalized_name VARCHAR(128) NOT NULL, exposure_enabled TINYINT(1) NOT NULL DEFAULT 0,
  exposure_as_click TINYINT(1) NOT NULL DEFAULT 0, dedup_window_seconds INT NOT NULL DEFAULT 3600,
  rta_mode VARCHAR(16) NOT NULL DEFAULT 'OFF', description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT,READY,ARCHIVED; not traffic state',
  config_version BIGINT NOT NULL DEFAULT 1, republish_required TINYINT(1) NOT NULL DEFAULT 1,
  republish_reason VARCHAR(255) NOT NULL DEFAULT 'TASK_DRAFT_SAVED',
  published_task_version BIGINT NOT NULL DEFAULT 0,
  create_request_key VARCHAR(64) NOT NULL, create_request_hash CHAR(64) NOT NULL,
  archived_by VARCHAR(100) NULL, archived_at DATETIME(3) NULL,
  create_by VARCHAR(100) NOT NULL, update_by VARCHAR(100) NOT NULL,
  current_name_key VARCHAR(128) GENERATED ALWAYS AS (IF(status<>'ARCHIVED',normalized_name,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_ocpx_task_code (biz_code),
  UNIQUE KEY uk_scx_ocpx_task_name (advertiser_id,current_name_key),
  UNIQUE KEY uk_scx_ocpx_task_request (create_request_key),
  KEY idx_scx_ocpx_task_filter (advertiser_id,channel_id,media_account_id,status,update_time),
  KEY idx_scx_ocpx_task_product_link (product_id,click_app_link_id,exposure_app_link_id),
  KEY idx_scx_ocpx_task_operator (operator_user_id,status),
  KEY idx_scx_ocpx_task_credential (media_credential_id,media_credential_secret_revision),
  CONSTRAINT fk_scx_ocpx_task_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_product FOREIGN KEY (product_id) REFERENCES scx_ocpx_product(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_click_link FOREIGN KEY (click_app_link_id) REFERENCES scx_ocpx_app_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_exposure_link FOREIGN KEY (exposure_app_link_id) REFERENCES scx_ocpx_app_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_channel FOREIGN KEY (channel_id) REFERENCES scx_ocpx_channel(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_media_account FOREIGN KEY (media_account_id) REFERENCES scx_ocpx_media_account(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_media_credential FOREIGN KEY (media_credential_id) REFERENCES scx_ocpx_media_credential(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_task_status CHECK (status IN ('DRAFT','READY','ARCHIVED')),
  CONSTRAINT ck_scx_ocpx_task_rta CHECK (rta_mode='OFF'),
  CONSTRAINT ck_scx_ocpx_task_dedup CHECK (dedup_window_seconds BETWEEN 0 AND 86400),
  CONSTRAINT ck_scx_ocpx_task_exposure CHECK ((exposure_enabled=0 AND exposure_app_link_id IS NULL AND exposure_as_click=0) OR (exposure_enabled=1 AND exposure_app_link_id IS NOT NULL)),
  CONSTRAINT ck_scx_ocpx_task_revision CHECK (config_version>=1 AND media_credential_secret_revision>=1 AND published_task_version>=0 AND published_task_version<=config_version),
  CONSTRAINT ck_scx_ocpx_task_archive CHECK ((status='ARCHIVED' AND archived_at IS NOT NULL) OR (status<>'ARCHIVED' AND archived_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S5 Task draft aggregate; publishing belongs to S7';

CREATE TABLE IF NOT EXISTS scx_ocpx_task_change_audit (
  id VARCHAR(32) NOT NULL, task_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL, channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL, action VARCHAR(40) NOT NULL,
  before_config_version BIGINT NOT NULL, after_config_version BIGINT NOT NULL,
  affected_monitor_count INT NOT NULL DEFAULT 0, actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), KEY idx_scx_ocpx_task_audit (task_id,create_time,action),
  KEY idx_scx_ocpx_task_audit_scope (advertiser_id,channel_id,media_account_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Secret-free Task configuration audit';

DROP PROCEDURE IF EXISTS scx_upgrade_v86_task_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v86_task_contract()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='task_id') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN task_id VARCHAR(32) NULL AFTER id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='task_mapping_status') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN task_mapping_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER task_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='task_route_kind') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN task_route_kind VARCHAR(16) NOT NULL DEFAULT 'LEGACY' AFTER task_mapping_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='task_config_revision') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN task_config_revision BIGINT NOT NULL DEFAULT 0 AFTER task_route_kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND index_name='idx_scx_ocpx_monitor_task') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD KEY idx_scx_ocpx_monitor_task (task_id,task_route_kind,route_status,deleted);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND constraint_name='fk_scx_ocpx_monitor_task') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD CONSTRAINT fk_scx_ocpx_monitor_task FOREIGN KEY (task_id) REFERENCES scx_ocpx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND constraint_name='ck_scx_ocpx_monitor_task_mapping') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD CONSTRAINT ck_scx_ocpx_monitor_task_mapping CHECK ((task_id IS NULL AND task_mapping_status='LEGACY_UNMAPPED' AND task_route_kind='LEGACY' AND task_config_revision=0) OR (task_id IS NOT NULL AND task_mapping_status='READY' AND task_route_kind IN ('CLICK','EXPOSURE') AND task_config_revision>=1));
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v86_task_contract();
DROP PROCEDURE scx_upgrade_v86_task_contract;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_task','任务管理','/ocpx/task','0','lucide:list-checks','ocpx/task/index','ocpx:task:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_view_v86','查看任务','','menu_ocpx_task',NULL,NULL,'ocpx:task:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_add_v86','新增任务草稿','','menu_ocpx_task',NULL,NULL,'ocpx:task:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_edit_v86','编辑任务草稿','','menu_ocpx_task',NULL,NULL,'ocpx:task:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_status_v86','变更任务就绪状态','','menu_ocpx_task',NULL,NULL,'ocpx:task:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_archive_v86','归档恢复任务','','menu_ocpx_task',NULL,NULL,'ocpx:task:archive',50,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_task','perm_ocpx_task_view_v86','perm_ocpx_task_add_v86','perm_ocpx_task_edit_v86','perm_ocpx_task_status_v86','perm_ocpx_task_archive_v86')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_task','perm_ocpx_task_view_v86','perm_ocpx_task_add_v86','perm_ocpx_task_edit_v86','perm_ocpx_task_status_v86')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v86_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_task','perm_ocpx_task_view_v86')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';

-- Empty-database initialization intentionally performs no historical fact backfill.
SET FOREIGN_KEY_CHECKS = 1;

-- V87 current state: canonical Event model. Kept textually synchronized with
-- V87__close_event_model_truth.sql for empty-database initialization.
-- S6 canonical EventDefinition -> ChannelEventMapping -> TaskEventBinding ->
-- RouteEventSnapshot contract. Forward-only and replay-safe on MySQL 8.x.
-- Existing V80 rows remain LEGACY_UNMAPPED/LEGACY_V80. No business mapping is
-- inferred from names, external codes, URLs, or old Monitor relationships.

DROP PROCEDURE IF EXISTS scx_v87_close_event_model;
DELIMITER $$
CREATE PROCEDURE scx_v87_close_event_model()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event_definition' AND column_name='direction') THEN
    ALTER TABLE scx_ocpx_event_definition
      ADD COLUMN direction VARCHAR(32) NULL AFTER event_name,
      ADD COLUMN standard_type VARCHAR(64) NULL AFTER direction,
      ADD COLUMN billing_eligible TINYINT(1) NOT NULL DEFAULT 0 AFTER standard_type,
      ADD COLUMN subject_type VARCHAR(24) NULL AFTER billing_eligible,
      ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER built_in,
      ADD COLUMN mapping_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER config_version,
      ADD COLUMN create_request_key VARCHAR(64) NULL AFTER mapping_state,
      ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key,
      ADD COLUMN archived_at DATETIME(3) NULL AFTER create_request_hash,
      ADD COLUMN create_by VARCHAR(100) NULL AFTER archived_at,
      ADD COLUMN update_by VARCHAR(100) NULL AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event_definition' AND index_name='uk_scx_ocpx_event_create_request') THEN
    ALTER TABLE scx_ocpx_event_definition ADD UNIQUE KEY uk_scx_ocpx_event_create_request (create_request_key);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event_definition' AND index_name='idx_scx_ocpx_event_canonical') THEN
    ALTER TABLE scx_ocpx_event_definition ADD KEY idx_scx_ocpx_event_canonical (mapping_state,status,direction,standard_type,event_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_event_definition' AND constraint_name='ck_scx_ocpx_event_canonical') THEN
    ALTER TABLE scx_ocpx_event_definition ADD CONSTRAINT ck_scx_ocpx_event_canonical CHECK (
      (mapping_state='LEGACY_UNMAPPED') OR
      (mapping_state='CANONICAL' AND category='STANDARD' AND channel_id='' AND
       direction IS NOT NULL AND standard_type IS NOT NULL AND subject_type IS NOT NULL AND
       direction IN ('UPSTREAM_CALLBACK','DOWNSTREAM_DELIVERY','BIDIRECTIONAL') AND
       standard_type REGEXP '^[A-Z][A-Z0-9_]{1,63}$' AND
       event_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$' AND
       subject_type IN ('DEVICE','ACCOUNT','ORDER','LEAD') AND
       status IN ('ACTIVE','DISABLED','ARCHIVED') AND config_version>=1)
    );
  END IF;

  ALTER TABLE scx_ocpx_channel_event_mapping
    MODIFY upstream_event_id VARCHAR(32) NULL,
    MODIFY conversion_event_id VARCHAR(32) NULL,
    MODIFY media_event_id VARCHAR(32) NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND column_name='adapter_code') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping
      ADD COLUMN adapter_code VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN external_code VARCHAR(128) NULL AFTER adapter_code,
      ADD COLUMN direction VARCHAR(32) NULL AFTER external_code,
      ADD COLUMN event_definition_id VARCHAR(32) NULL AFTER direction,
      ADD COLUMN parameter_template_code VARCHAR(64) NULL AFTER event_definition_id,
      ADD COLUMN success_rule_code VARCHAR(32) NULL AFTER parameter_template_code,
      ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status,
      ADD COLUMN mapping_state VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER config_version,
      ADD COLUMN create_request_key VARCHAR(64) NULL AFTER mapping_state,
      ADD COLUMN create_request_hash CHAR(64) NULL AFTER create_request_key,
      ADD COLUMN archived_at DATETIME(3) NULL AFTER create_request_hash,
      ADD COLUMN create_by VARCHAR(100) NULL AFTER archived_at,
      ADD COLUMN update_by VARCHAR(100) NULL AFTER create_by;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND index_name='uk_scx_ocpx_mapping_external') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD UNIQUE KEY uk_scx_ocpx_mapping_external (channel_id,adapter_code,direction,external_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND index_name='uk_scx_ocpx_mapping_channel_id') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD UNIQUE KEY uk_scx_ocpx_mapping_channel_id (id,channel_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND index_name='uk_scx_ocpx_mapping_request') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD UNIQUE KEY uk_scx_ocpx_mapping_request (create_request_key);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND index_name='idx_scx_ocpx_mapping_canonical') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD KEY idx_scx_ocpx_mapping_canonical (mapping_state,channel_id,adapter_code,direction,status,event_definition_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND constraint_name='fk_scx_ocpx_mapping_channel_v87') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD CONSTRAINT fk_scx_ocpx_mapping_channel_v87 FOREIGN KEY (channel_id) REFERENCES scx_ocpx_channel(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND constraint_name='fk_scx_ocpx_mapping_adapter_v87') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD CONSTRAINT fk_scx_ocpx_mapping_adapter_v87 FOREIGN KEY (adapter_code) REFERENCES scx_ocpx_adapter(adapter_code) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND constraint_name='fk_scx_ocpx_mapping_definition_v87') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD CONSTRAINT fk_scx_ocpx_mapping_definition_v87 FOREIGN KEY (event_definition_id) REFERENCES scx_ocpx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_channel_event_mapping' AND constraint_name='ck_scx_ocpx_mapping_canonical') THEN
    ALTER TABLE scx_ocpx_channel_event_mapping ADD CONSTRAINT ck_scx_ocpx_mapping_canonical CHECK (
      (mapping_state='LEGACY_UNMAPPED' AND upstream_event_id IS NOT NULL AND conversion_event_id IS NOT NULL AND media_event_id IS NOT NULL) OR
      (mapping_state='CANONICAL' AND adapter_code IS NOT NULL AND external_code IS NOT NULL AND
       parameter_template_code IS NOT NULL AND success_rule_code IS NOT NULL AND
       external_code REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND
       direction IN ('UPSTREAM_CALLBACK','DOWNSTREAM_DELIVERY') AND event_definition_id IS NOT NULL AND
       parameter_template_code IN ('STANDARD_EVENT_V1','FLAT_EVENT_V1') AND success_rule_code IN ('HTTP_2XX','BODY_CODE_ZERO') AND
       upstream_event_id IS NULL AND conversion_event_id IS NULL AND media_event_id IS NULL AND
       status IN ('ACTIVE','DISABLED','ARCHIVED') AND config_version>=1)
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_task' AND index_name='uk_scx_ocpx_task_channel_id') THEN
    ALTER TABLE scx_ocpx_task ADD UNIQUE KEY uk_scx_ocpx_task_channel_id (id,channel_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND column_name='event_mapping_mode') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD COLUMN event_mapping_mode VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNMAPPED' AFTER event_snapshot_version;
  END IF;
  UPDATE scx_ocpx_monitor_link SET event_mapping_mode='LEGACY_V80_SNAPSHOT'
    WHERE event_snapshot_version=1 AND event_mapping_mode='LEGACY_UNMAPPED';
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_monitor_link' AND constraint_name='ck_scx_ocpx_monitor_event_mode') THEN
    ALTER TABLE scx_ocpx_monitor_link ADD CONSTRAINT ck_scx_ocpx_monitor_event_mode CHECK (
      (event_snapshot_version=0 AND event_mapping_mode IN ('LEGACY_UNMAPPED','LEGACY_ADAPTER')) OR
      (event_snapshot_version=1 AND event_mapping_mode='LEGACY_V80_SNAPSHOT') OR
      (event_snapshot_version>=2 AND event_mapping_mode='SNAPSHOT')
    );
  END IF;
END$$
DELIMITER ;
CALL scx_v87_close_event_model();
DROP PROCEDURE scx_v87_close_event_model;

CREATE TABLE IF NOT EXISTS scx_ocpx_task_event_binding (
  id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  channel_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  event_definition_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  channel_event_mapping_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  callback_ratio_basis_points INT NOT NULL DEFAULT 10000,
  limit_value BIGINT NULL COMMENT 'NULL unlimited; 0 rejects every sampled event',
  limit_window VARCHAR(24) NOT NULL DEFAULT 'NATURAL_DAY',
  limit_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  over_limit_delivery TINYINT(1) NOT NULL DEFAULT 0,
  sampling_algorithm VARCHAR(32) NOT NULL DEFAULT 'SHA256_MOD_10000_V1',
  source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  source_id VARCHAR(32) NULL,
  source_revision BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_task_event (task_id,event_definition_id),
  KEY idx_scx_ocpx_task_event_mapping (channel_event_mapping_id,status),
  KEY idx_scx_ocpx_task_event_channel (channel_id,status),
  CONSTRAINT fk_scx_ocpx_task_event_task FOREIGN KEY (task_id,channel_id) REFERENCES scx_ocpx_task(id,channel_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_event_definition FOREIGN KEY (event_definition_id) REFERENCES scx_ocpx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_task_event_mapping FOREIGN KEY (channel_event_mapping_id,channel_id) REFERENCES scx_ocpx_channel_event_mapping(id,channel_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_task_event_ratio CHECK (callback_ratio_basis_points BETWEEN 0 AND 10000),
  CONSTRAINT ck_scx_ocpx_task_event_limit CHECK (limit_value IS NULL OR limit_value>=0),
  CONSTRAINT ck_scx_ocpx_task_event_window CHECK (limit_window='NATURAL_DAY' AND limit_timezone='Asia/Shanghai'),
  CONSTRAINT ck_scx_ocpx_task_event_sampling CHECK (sampling_algorithm='SHA256_MOD_10000_V1'),
  CONSTRAINT ck_scx_ocpx_task_event_source CHECK ((source_type='MANUAL' AND source_id IS NULL AND source_revision=0) OR (source_type='PRODUCT_DEFAULT' AND source_id IS NOT NULL AND source_revision>=1)),
  CONSTRAINT ck_scx_ocpx_task_event_status CHECK (status IN ('ACTIVE','DISABLED') AND config_version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S6 Task event binding draft truth';

CREATE TABLE IF NOT EXISTS scx_ocpx_event_change_audit (
  id VARCHAR(32) NOT NULL,
  object_type VARCHAR(32) NOT NULL,
  object_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  action VARCHAR(40) NOT NULL,
  before_version BIGINT NOT NULL,
  after_version BIGINT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_event_audit_object (object_type,object_id,create_time),
  KEY idx_scx_ocpx_event_audit_scope (channel_id,task_id,create_time),
  CONSTRAINT ck_scx_ocpx_event_audit_type CHECK (object_type IN ('EVENT_DEFINITION','CHANNEL_EVENT_MAPPING','TASK_EVENT_BINDING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Sensitive-payload-free S6 event configuration audit';

DROP PROCEDURE IF EXISTS scx_v87_close_snapshot_contract;
DELIMITER $$
CREATE PROCEDURE scx_v87_close_snapshot_contract()
BEGIN
  ALTER TABLE scx_ocpx_route_event_snapshot
    MODIFY event_mapping_id VARCHAR(32) NULL,
    MODIFY upstream_event_code VARCHAR(64) NULL,
    MODIFY upstream_event_name VARCHAR(100) NULL,
    MODIFY conversion_event_code VARCHAR(64) NULL,
    MODIFY conversion_event_name VARCHAR(100) NULL,
    MODIFY media_event_code VARCHAR(64) NULL,
    MODIFY media_event_name VARCHAR(100) NULL,
    MODIFY assessment_unit_price DECIMAL(20,6) NULL,
    MODIFY daily_limit INT NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND column_name='snapshot_schema_version') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot
      ADD COLUMN snapshot_schema_version INT NOT NULL DEFAULT 1 AFTER route_version,
      ADD COLUMN snapshot_mode VARCHAR(24) NOT NULL DEFAULT 'LEGACY_V80' AFTER snapshot_schema_version,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER snapshot_mode,
      ADD COLUMN task_config_revision BIGINT NULL AFTER task_id,
      ADD COLUMN task_event_binding_id VARCHAR(32) NULL AFTER task_config_revision,
      ADD COLUMN task_event_binding_revision BIGINT NULL AFTER task_event_binding_id,
      ADD COLUMN event_definition_id VARCHAR(32) NULL AFTER task_event_binding_revision,
      ADD COLUMN event_definition_revision BIGINT NULL AFTER event_definition_id,
      ADD COLUMN event_mapping_revision BIGINT NULL AFTER event_mapping_id,
      ADD COLUMN standard_event_code VARCHAR(64) NULL AFTER event_mapping_revision,
      ADD COLUMN direction VARCHAR(32) NULL AFTER standard_event_code,
      ADD COLUMN external_event_code VARCHAR(128) NULL AFTER direction,
      ADD COLUMN parameter_template_code VARCHAR(64) NULL AFTER external_event_code,
      ADD COLUMN callback_ratio_basis_points INT NULL AFTER parameter_template_code,
      ADD COLUMN limit_value BIGINT NULL AFTER callback_ratio_basis_points,
      ADD COLUMN limit_window VARCHAR(24) NULL AFTER limit_value,
      ADD COLUMN limit_timezone VARCHAR(64) NULL AFTER limit_window,
      ADD COLUMN over_limit_delivery TINYINT(1) NULL AFTER limit_timezone,
      ADD COLUMN sampling_algorithm VARCHAR(32) NULL AFTER over_limit_delivery,
      ADD COLUMN snapshot_fingerprint CHAR(64) NULL AFTER sampling_algorithm;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND index_name='uk_scx_ocpx_route_event_canonical') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD UNIQUE KEY uk_scx_ocpx_route_event_canonical (monitor_id,route_version,event_definition_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND index_name='idx_scx_ocpx_route_event_task') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD KEY idx_scx_ocpx_route_event_task (task_id,task_event_binding_id,snapshot_schema_version);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND constraint_name='fk_scx_ocpx_snapshot_binding_v87') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD CONSTRAINT fk_scx_ocpx_snapshot_binding_v87 FOREIGN KEY (task_event_binding_id) REFERENCES scx_ocpx_task_event_binding(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND constraint_name='fk_scx_ocpx_snapshot_definition_v87') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD CONSTRAINT fk_scx_ocpx_snapshot_definition_v87 FOREIGN KEY (event_definition_id) REFERENCES scx_ocpx_event_definition(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND constraint_name='fk_scx_ocpx_snapshot_mapping_v87') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD CONSTRAINT fk_scx_ocpx_snapshot_mapping_v87 FOREIGN KEY (event_mapping_id) REFERENCES scx_ocpx_channel_event_mapping(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_route_event_snapshot' AND constraint_name='ck_scx_ocpx_snapshot_v87') THEN
    ALTER TABLE scx_ocpx_route_event_snapshot ADD CONSTRAINT ck_scx_ocpx_snapshot_v87 CHECK (
      (snapshot_schema_version=1 AND snapshot_mode='LEGACY_V80') OR
      (snapshot_schema_version=2 AND snapshot_mode='CANONICAL' AND task_id IS NOT NULL AND task_config_revision>=1 AND
       task_event_binding_id IS NOT NULL AND task_event_binding_revision>=1 AND event_definition_id IS NOT NULL AND
       event_definition_revision>=1 AND event_mapping_id IS NOT NULL AND event_mapping_revision>=1 AND
       standard_event_code IS NOT NULL AND direction IS NOT NULL AND external_event_code IS NOT NULL AND
       parameter_template_code IS NOT NULL AND callback_ratio_basis_points IS NOT NULL AND
       limit_window IS NOT NULL AND limit_timezone IS NOT NULL AND sampling_algorithm IS NOT NULL AND
       direction='DOWNSTREAM_DELIVERY' AND parameter_template_code IN ('STANDARD_EVENT_V1','FLAT_EVENT_V1') AND
       callback_ratio_basis_points BETWEEN 0 AND 10000 AND (limit_value IS NULL OR limit_value>=0) AND
       limit_window='NATURAL_DAY' AND limit_timezone='Asia/Shanghai' AND
       sampling_algorithm='SHA256_MOD_10000_V1' AND snapshot_fingerprint IS NOT NULL)
    );
  END IF;
END$$
DELIMITER ;
CALL scx_v87_close_snapshot_contract();
DROP PROCEDURE scx_v87_close_snapshot_contract;

DROP TRIGGER IF EXISTS trg_scx_ocpx_route_event_snapshot_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_route_event_snapshot_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_ocpx_route_event_snapshot_no_update BEFORE UPDATE ON scx_ocpx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_ocpx_route_event_snapshot_no_delete BEFORE DELETE ON scx_ocpx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot history cannot be deleted'$$
DELIMITER ;

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_SET(capabilities_json,
  '$.eventParameterTemplates',JSON_ARRAY('STANDARD_EVENT_V1','FLAT_EVENT_V1'),
  '$.eventMappingDirections',IF(direction='UPSTREAM',JSON_ARRAY('UPSTREAM_CALLBACK'),JSON_ARRAY('DOWNSTREAM_DELIVERY'))),
  update_time=NOW(3)
WHERE status='ACTIVE';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_event','事件字典','/ocpx/event','menu_ocpx_system','lucide:workflow','ocpx/event/index','ocpx:event:view',45,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_view_v87','查看事件定义','','menu_ocpx_event',NULL,NULL,'ocpx:event:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_add_v87','新增事件定义','','menu_ocpx_event',NULL,NULL,'ocpx:event:add',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_edit_v87','编辑事件定义','','menu_ocpx_event',NULL,NULL,'ocpx:event:edit',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_status_v87','变更事件状态','','menu_ocpx_event',NULL,NULL,'ocpx:event:status',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_mapping_view_v87','查看渠道事件映射','','menu_ocpx_event',NULL,NULL,'ocpx:event-mapping:view',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_mapping_add_v87','新增渠道事件映射','','menu_ocpx_event',NULL,NULL,'ocpx:event-mapping:add',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_mapping_edit_v87','编辑渠道事件映射','','menu_ocpx_event',NULL,NULL,'ocpx:event-mapping:edit',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_event_mapping_status_v87','变更渠道事件映射状态','','menu_ocpx_event',NULL,NULL,'ocpx:event-mapping:status',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_task_event_edit_v87','编辑任务事件绑定','','menu_ocpx_task',NULL,NULL,'ocpx:task:event:edit',60,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id='perm_ocpx_event_write';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_event','perm_ocpx_event_view_v87','perm_ocpx_event_add_v87','perm_ocpx_event_edit_v87',
  'perm_ocpx_event_status_v87','perm_ocpx_event_mapping_view_v87','perm_ocpx_event_mapping_add_v87',
  'perm_ocpx_event_mapping_edit_v87','perm_ocpx_event_mapping_status_v87','perm_ocpx_task_event_edit_v87')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_event','perm_ocpx_event_view_v87','perm_ocpx_event_mapping_view_v87','perm_ocpx_task_event_edit_v87')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v87_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_event','perm_ocpx_event_view_v87','perm_ocpx_event_mapping_view_v87')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';
-- S7 immutable MonitorRoute publication, transactional distribution and runtime
-- acceptance dimensions. Forward-only and replay-safe on MySQL 8.x.
-- Legacy Monitor rows remain unmapped; this migration never infers a Task,
-- Product, MediaAccount, event mapping or route code from names or URLs.

CREATE TABLE IF NOT EXISTS scx_ocpx_route_version (
  id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  route_version BIGINT NOT NULL,
  route_kind VARCHAR(16) NOT NULL,
  route_code_hash CHAR(64) NOT NULL,
  route_code_ciphertext TEXT NOT NULL,
  public_template_ciphertext TEXT NOT NULL,
  route_schema_version INT NOT NULL DEFAULT 3,
  snapshot_fingerprint CHAR(64) NOT NULL,
  runtime_snapshot_ciphertext LONGTEXT NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  advertiser_revision BIGINT NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  product_revision BIGINT NOT NULL,
  product_event_policy_revision BIGINT NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  app_link_revision BIGINT NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  media_account_revision BIGINT NOT NULL,
  media_credential_id VARCHAR(32) NOT NULL,
  media_credential_secret_revision BIGINT NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  channel_adapter_code VARCHAR(32) NOT NULL,
  channel_adapter_revision BIGINT NOT NULL,
  task_config_revision BIGINT NOT NULL,
  event_snapshot_schema_version INT NOT NULL,
  macro_schema_json JSON NOT NULL,
  dedup_window_seconds INT NOT NULL,
  exposure_enabled TINYINT(1) NOT NULL,
  exposure_as_click TINYINT(1) NOT NULL,
  rta_mode VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
  published_by VARCHAR(100) NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_version (monitor_id,route_version),
  UNIQUE KEY uk_scx_ocpx_route_code_hash (route_code_hash),
  KEY idx_scx_ocpx_route_version_task (task_id,published_at),
  KEY idx_scx_ocpx_route_version_scope (advertiser_id,channel_id,media_account_id,published_at),
  CONSTRAINT fk_scx_ocpx_route_version_monitor FOREIGN KEY (monitor_id) REFERENCES scx_ocpx_monitor_link(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_route_version_task FOREIGN KEY (task_id) REFERENCES scx_ocpx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_version_core CHECK (
    route_version>=1 AND route_schema_version=3 AND route_kind IN ('CLICK','EXPOSURE') AND
    CHAR_LENGTH(route_code_hash)=64 AND CHAR_LENGTH(snapshot_fingerprint)=64 AND
    advertiser_revision>=1 AND product_revision>=1 AND product_event_policy_revision>=1 AND
    app_link_revision>=1 AND media_account_revision>=1 AND media_credential_secret_revision>=1 AND
    channel_adapter_revision>=1 AND task_config_revision>=1 AND event_snapshot_schema_version=2 AND
    dedup_window_seconds BETWEEN 0 AND 86400 AND rta_mode='OFF' AND status='PUBLISHED' AND
    ((exposure_enabled=0 AND exposure_as_click=0) OR exposure_enabled=1)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S7 immutable published route version';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_runtime_state (
  id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  acceptance_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_DISTRIBUTION',
  accept_from DATETIME(3) NULL,
  accept_until DATETIME(3) NULL,
  deny_at DATETIME(3) NULL,
  state_revision BIGINT NOT NULL DEFAULT 1,
  reason_code VARCHAR(64) NULL,
  update_by VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_runtime_version (route_version_id),
  UNIQUE KEY uk_scx_ocpx_route_runtime_monitor (monitor_id,route_version),
  KEY idx_scx_ocpx_route_runtime_accept (acceptance_status,accept_from,accept_until,deny_at),
  CONSTRAINT fk_scx_ocpx_route_runtime_version FOREIGN KEY (route_version_id) REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_runtime_state CHECK (
    acceptance_status IN ('PENDING_DISTRIBUTION','ACTIVE','OVERLAP','SUPERSEDED','DENIED','EXPIRED') AND
    route_version>=1 AND state_revision>=1 AND
    (accept_until IS NULL OR accept_from IS NULL OR accept_until>=accept_from)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Mutable audited acceptance state outside immutable route snapshot';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_publish_record (
  id VARCHAR(32) NOT NULL,
  publish_request_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  expected_task_version BIGINT NOT NULL,
  check_fingerprint CHAR(64) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  route_bundle_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
  actor VARCHAR(100) NOT NULL,
  response_fingerprint CHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_publish_request (publish_request_id),
  UNIQUE KEY uk_scx_ocpx_route_publish_version (route_version_id),
  KEY idx_scx_ocpx_route_publish_task (task_id,create_time),
  CONSTRAINT fk_scx_ocpx_route_publish_task FOREIGN KEY (task_id) REFERENCES scx_ocpx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_route_publish_version FOREIGN KEY (route_version_id) REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_publish_record CHECK (
    publish_request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_task_version>=1 AND
    route_version>=1 AND CHAR_LENGTH(check_fingerprint)=64 AND CHAR_LENGTH(response_fingerprint)=64 AND
    status='PUBLISHED'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Idempotent S7 route publication record';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_distribution (
  id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  target_group VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
  minimum_required_acks INT NOT NULL DEFAULT 1,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_distribution (route_version_id,target_group),
  KEY idx_scx_ocpx_route_distribution_claim (status,next_attempt_at,lease_until),
  CONSTRAINT fk_scx_ocpx_route_distribution_version FOREIGN KEY (route_version_id) REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_distribution CHECK (
    route_version>=1 AND minimum_required_acks>=1 AND attempt_count>=0 AND
    status IN ('PENDING','DISTRIBUTING','ACTIVE','DEGRADED','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='S7 route distribution truth';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_instance_ack (
  id VARCHAR(32) NOT NULL,
  distribution_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  instance_id VARCHAR(100) NOT NULL,
  applied_version BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_code VARCHAR(64) NULL,
  acked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_instance_ack (distribution_id,instance_id),
  KEY idx_scx_ocpx_route_instance_version (route_version_id,status,acked_at),
  CONSTRAINT fk_scx_ocpx_route_ack_distribution FOREIGN KEY (distribution_id) REFERENCES scx_ocpx_route_distribution(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_route_ack_version FOREIGN KEY (route_version_id) REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_ack CHECK (applied_version>=1 AND status IN ('APPLIED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Per-instance applied route version acknowledgement';

CREATE TABLE IF NOT EXISTS scx_ocpx_route_pause_command (
  id VARCHAR(32) NOT NULL,
  pause_request_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  expected_task_version BIGINT NOT NULL,
  preview_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DENY_MARKER_WRITTEN',
  affected_route_count INT NOT NULL,
  actor VARCHAR(100) NOT NULL,
  reason_summary VARCHAR(255) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_route_pause_request (pause_request_id),
  KEY idx_scx_ocpx_route_pause_task (task_id,create_time),
  CONSTRAINT fk_scx_ocpx_route_pause_task FOREIGN KEY (task_id) REFERENCES scx_ocpx_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_route_pause CHECK (
    pause_request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_task_version>=1 AND
    CHAR_LENGTH(preview_fingerprint)=64 AND affected_route_count>=0 AND status='DENY_MARKER_WRITTEN'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Idempotent auditable S7 emergency deny command';

DROP PROCEDURE IF EXISTS scx_upgrade_v88_publication_contract;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v88_publication_contract()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='scx_ocpx_task' AND constraint_name='ck_scx_ocpx_task_status') THEN
    ALTER TABLE scx_ocpx_task DROP CHECK ck_scx_ocpx_task_status;
  END IF;
  ALTER TABLE scx_ocpx_task ADD CONSTRAINT ck_scx_ocpx_task_status CHECK (status IN ('DRAFT','READY','ACTIVE','PAUSED','ARCHIVED'));

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_config_outbox' AND column_name='payload_json') THEN
    ALTER TABLE scx_ocpx_config_outbox
      ADD COLUMN payload_json JSON NULL AFTER event_type,
      ADD COLUMN delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER published,
      ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER delivery_status,
      ADD COLUMN available_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER attempt_count,
      ADD COLUMN lease_owner VARCHAR(100) NULL AFTER available_at,
      ADD COLUMN lease_until DATETIME(3) NULL AFTER lease_owner,
      ADD COLUMN last_error_code VARCHAR(64) NULL AFTER lease_until,
      ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER published_at;
  END IF;
  UPDATE scx_ocpx_config_outbox
  SET delivery_status=IF(published=1,'DELIVERED','PENDING'),
      available_at=COALESCE(create_time,NOW(3)),attempt_count=GREATEST(attempt_count,0)
  WHERE delivery_status='PENDING';
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_ocpx_config_outbox' AND index_name='idx_scx_ocpx_config_claim') THEN
    ALTER TABLE scx_ocpx_config_outbox ADD KEY idx_scx_ocpx_config_claim (delivery_status,available_at,lease_until);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v88_publication_contract();
DROP PROCEDURE scx_upgrade_v88_publication_contract;

DROP TRIGGER IF EXISTS trg_scx_ocpx_route_version_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_route_version_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_ocpx_route_version_no_update BEFORE UPDATE ON scx_ocpx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_ocpx_route_version_no_delete BEFORE DELETE ON scx_ocpx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion history cannot be deleted'$$
DELIMITER ;

UPDATE scx_ocpx_adapter
SET capabilities_json=JSON_SET(capabilities_json,
  '$.trackingMacroSchemaVersion',1,
  '$.trackingMacroPairs',JSON_ARRAY(
    JSON_OBJECT('key','imei','macro','__IMEI__','required',FALSE),
    JSON_OBJECT('key','imei_md5','macro','__IMEI_MD5__','required',FALSE),
    JSON_OBJECT('key','oaid','macro','__OAID__','required',FALSE),
    JSON_OBJECT('key','oaid_md5','macro','__OAID_MD5__','required',FALSE),
    JSON_OBJECT('key','ip','macro','__IP__','required',FALSE),
    JSON_OBJECT('key','android_id','macro','__ANDROIDID__','required',FALSE),
    JSON_OBJECT('key','android_id_md5','macro','__ANDROIDID_MD5__','required',FALSE),
    JSON_OBJECT('key','aaid','macro','__AAID__','required',FALSE),
    JSON_OBJECT('key','ua','macro','__UA__','required',FALSE),
    JSON_OBJECT('key','os','macro','__OS__','required',FALSE),
    JSON_OBJECT('key','model','macro','__MODEL__','required',FALSE),
    JSON_OBJECT('key','callback','macro','__CALLBACK__','required',FALSE),
    JSON_OBJECT('key','clickid','macro','__CLICKID__','required',TRUE),
    JSON_OBJECT('key','campaign_id','macro','__CAMPAIGNID__','required',FALSE),
    JSON_OBJECT('key','adgroup_id','macro','__ADGROUPID__','required',FALSE),
    JSON_OBJECT('key','creative_id','macro','__CREATIVEID__','required',FALSE),
    JSON_OBJECT('key','advertiser_id','macro','__ADVERTISERID__','required',FALSE)
  )),
  capability_revision=capability_revision+1,update_time=NOW(3)
WHERE direction='DOWNSTREAM' AND status='ACTIVE'
  AND NOT JSON_CONTAINS_PATH(capabilities_json,'all','$.trackingMacroSchemaVersion','$.trackingMacroPairs');

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_task_publish_v88','发布任务路由','','menu_ocpx_task',NULL,NULL,'ocpx:task:publish',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_view_v88','查看路由版本','','menu_ocpx_task',NULL,NULL,'ocpx:monitor:view',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_publish_v88','发布路由','','menu_ocpx_task',NULL,NULL,'ocpx:monitor:publish',90,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_copy_v88','复制监测地址','','menu_ocpx_task',NULL,NULL,'ocpx:monitor:copy',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_status_v88','停流及恢复发布','','menu_ocpx_task',NULL,NULL,'ocpx:monitor:status',110,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v88_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ocpx_task_publish_v88','perm_ocpx_monitor_view_v88','perm_ocpx_monitor_publish_v88',
  'perm_ocpx_monitor_copy_v88','perm_ocpx_monitor_status_v88')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v88_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_ocpx_task_publish_v88','perm_ocpx_monitor_view_v88','perm_ocpx_monitor_publish_v88',
  'perm_ocpx_monitor_copy_v88','perm_ocpx_monitor_status_v88')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

-- S8 unified hourly facts and reports. Forward-only and replay-safe on MySQL 8.x.
-- Existing report/event rows are explicitly UNMAPPED. This migration never infers
-- Product, Task, MediaAccount, operator or RouteVersion from mutable names or URLs.

DROP PROCEDURE IF EXISTS scx_upgrade_v89_hourly_fact;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_v89_hourly_fact()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='scx_ocpx_hourly_report' AND index_name='uk_scx_ocpx_report_grain') THEN
    ALTER TABLE scx_ocpx_hourly_report DROP INDEX uk_scx_ocpx_report_grain;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='scx_ocpx_hourly_report' AND column_name='tenant_id') THEN
    ALTER TABLE scx_ocpx_hourly_report
      ADD COLUMN tenant_id VARCHAR(32) NULL AFTER time_hour,
      ADD COLUMN stat_date DATE GENERATED ALWAYS AS (DATE(time_hour)) STORED AFTER tenant_id,
      ADD COLUMN stat_hour TINYINT GENERATED ALWAYS AS (HOUR(time_hour)) STORED AFTER stat_date,
      ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER stat_hour,
      ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER app_link_id,
      ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN operator_user_id VARCHAR(32) NULL AFTER media_account_id,
      ADD COLUMN route_version_id VARCHAR(32) NULL AFTER monitor_id,
      ADD COLUMN route_version BIGINT NULL AFTER route_version_id,
      ADD COLUMN dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED' AFTER route_version,
      ADD COLUMN raw_clicks BIGINT NOT NULL DEFAULT 0 AFTER clicks,
      ADD COLUMN valid_clicks BIGINT NOT NULL DEFAULT 0 AFTER raw_clicks,
      ADD COLUMN duplicate_clicks BIGINT NOT NULL DEFAULT 0 AFTER valid_clicks,
      ADD COLUMN adjustment_count BIGINT NOT NULL DEFAULT 0 AFTER duplicate_clicks,
      ADD COLUMN deduction_count BIGINT NOT NULL DEFAULT 0 AFTER adjustment_count,
      ADD COLUMN adjustment_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 AFTER deduction_count,
      ADD COLUMN deduction_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 AFTER adjustment_amount,
      ADD COLUMN source_watermark DATETIME(3) NULL AFTER deduction_amount,
      ADD COLUMN as_of DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER source_watermark,
      ADD COLUMN finality VARCHAR(16) NOT NULL DEFAULT 'PROVISIONAL' AFTER as_of,
      ADD COLUMN quality_status VARCHAR(24) NOT NULL DEFAULT 'UNMAPPED' AFTER finality,
      ADD COLUMN metric_definitions_version VARCHAR(32) NOT NULL DEFAULT 'S8_V1' AFTER quality_status,
      ADD COLUMN dimension_key CHAR(64) GENERATED ALWAYS AS
        (SHA2(CONCAT_WS('|',COALESCE(tenant_id,'~'),COALESCE(advertiser_id,'~'),
          COALESCE(product_id,'~'),COALESCE(app_link_id,'~'),COALESCE(task_id,'~'),
          COALESCE(channel_id,'~'),COALESCE(media_account_id,'~'),COALESCE(operator_user_id,'~'),
          COALESCE(monitor_id,'~'),COALESCE(route_version_id,'~'),COALESCE(route_version,-1)),256)) STORED;
  END IF;

  UPDATE scx_ocpx_hourly_report
  SET raw_clicks=clicks,valid_clicks=eligible_click_count,
      duplicate_clicks=deduplicated_click_count,dimension_status='UNMAPPED',
      quality_status='UNMAPPED',finality='PROVISIONAL',timezone='Asia/Shanghai',
      metric_definitions_version='S8_V1',as_of=COALESCE(as_of,update_time,NOW(3))
  WHERE dimension_status='UNMAPPED';

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='scx_ocpx_hourly_report' AND index_name='uk_scx_ocpx_hourly_frozen_grain') THEN
    ALTER TABLE scx_ocpx_hourly_report
      ADD UNIQUE KEY uk_scx_ocpx_hourly_frozen_grain (time_hour,dimension_key),
      ADD KEY idx_scx_ocpx_hourly_time_task (time_hour,task_id,app_link_id),
      ADD KEY idx_scx_ocpx_hourly_scope (time_hour,advertiser_id,channel_id,media_account_id),
      ADD KEY idx_scx_ocpx_hourly_product (time_hour,product_id,app_link_id),
      ADD KEY idx_scx_ocpx_hourly_media (time_hour,media_account_id,channel_id),
      ADD KEY idx_scx_ocpx_hourly_freshness (finality,source_watermark,as_of);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='scx_ocpx_hourly_report' AND constraint_name='ck_scx_ocpx_hourly_v89_dimensions') THEN
    ALTER TABLE scx_ocpx_hourly_report
      ADD CONSTRAINT ck_scx_ocpx_hourly_v89_dimensions CHECK (
        timezone='Asia/Shanghai' AND stat_hour BETWEEN 0 AND 23 AND
        dimension_status IN ('UNMAPPED','FROZEN') AND
        ((dimension_status='UNMAPPED') OR
         (tenant_id IS NOT NULL AND product_id IS NOT NULL AND task_id IS NOT NULL AND
          media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND
          route_version_id IS NOT NULL AND route_version>=1)) AND
        raw_clicks>=0 AND valid_clicks>=0 AND duplicate_clicks>=0 AND
        adjustment_count>=0 AND deduction_count>=0 AND
        finality IN ('DELAYED','PROVISIONAL','FINAL') AND
        quality_status IN ('COMPLETE','PARTIAL','UNMAPPED') AND
        metric_definitions_version='S8_V1'
      );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='scx_ocpx_event' AND column_name='tenant_id') THEN
    ALTER TABLE scx_ocpx_event
      ADD COLUMN tenant_id VARCHAR(32) NULL AFTER event_type,
      ADD COLUMN product_id VARCHAR(32) NULL AFTER advertiser_id,
      ADD COLUMN task_id VARCHAR(32) NULL AFTER app_link_id,
      ADD COLUMN media_account_id VARCHAR(32) NULL AFTER channel_id,
      ADD COLUMN operator_user_id VARCHAR(32) NULL AFTER media_account_id,
      ADD COLUMN route_version_id VARCHAR(32) NULL AFTER monitor_id,
      ADD COLUMN dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED' AFTER route_version,
      ADD KEY idx_scx_ocpx_event_frozen_hour (occurred_at,task_id,route_version_id),
      ADD KEY idx_scx_ocpx_event_frozen_scope (advertiser_id,channel_id,media_account_id,occurred_at);
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_v89_hourly_fact();
DROP PROCEDURE scx_upgrade_v89_hourly_fact;

CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_fact_contribution (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  contribution_kind VARCHAR(32) NOT NULL,
  tenant_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(32) NOT NULL,
  monitor_route_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  stat_hour DATETIME NOT NULL,
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  impressions BIGINT NOT NULL DEFAULT 0,
  raw_clicks BIGINT NOT NULL DEFAULT 0,
  valid_clicks BIGINT NOT NULL DEFAULT 0,
  duplicate_clicks BIGINT NOT NULL DEFAULT 0,
  skipped_clicks BIGINT NOT NULL DEFAULT 0,
  activation_count BIGINT NOT NULL DEFAULT 0,
  registration_count BIGINT NOT NULL DEFAULT 0,
  payment_count BIGINT NOT NULL DEFAULT 0,
  order_count BIGINT NOT NULL DEFAULT 0,
  first_activation_count BIGINT NOT NULL DEFAULT 0,
  first_order_count BIGINT NOT NULL DEFAULT 0,
  reactivation_count BIGINT NOT NULL DEFAULT 0,
  retention_d2_count BIGINT NOT NULL DEFAULT 0,
  retention_d3_count BIGINT NOT NULL DEFAULT 0,
  retention_d7_count BIGINT NOT NULL DEFAULT 0,
  raw_conversion_count BIGINT NOT NULL DEFAULT 0,
  forwarded_conversion_count BIGINT NOT NULL DEFAULT 0,
  delivery_failure_count BIGINT NOT NULL DEFAULT 0,
  adjustment_count BIGINT NOT NULL DEFAULT 0,
  deduction_count BIGINT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  event_counts_json JSON NULL,
  aggregated_at DATETIME(3) NULL,
  aggregate_run_id VARCHAR(32) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_ocpx_fact_contribution_id (id),
  UNIQUE KEY uk_scx_ocpx_fact_contribution_source (source_event_id,contribution_kind),
  KEY idx_scx_ocpx_fact_contribution_claim (aggregated_at,sequence_no),
  KEY idx_scx_ocpx_fact_contribution_hour (stat_hour,task_id,app_link_id),
  KEY idx_scx_ocpx_fact_contribution_scope (advertiser_id,channel_id,media_account_id,stat_hour),
  CONSTRAINT ck_scx_ocpx_fact_contribution CHECK (
    contribution_kind IN ('FEEDBACK','CALLBACK','DELIVERY_SUCCESS','DELIVERY_FAILURE') AND
    route_version>=1 AND timezone='Asia/Shanghai' AND stat_hour=DATE_FORMAT(event_time,'%Y-%m-%d %H:00:00') AND
    impressions>=0 AND raw_clicks>=0 AND valid_clicks>=0 AND duplicate_clicks>=0 AND skipped_clicks>=0 AND
    activation_count>=0 AND registration_count>=0 AND payment_count>=0 AND order_count>=0 AND
    first_activation_count>=0 AND first_order_count>=0 AND reactivation_count>=0 AND
    retention_d2_count>=0 AND retention_d3_count>=0 AND retention_d7_count>=0 AND
    raw_conversion_count>=0 AND forwarded_conversion_count>=0 AND delivery_failure_count>=0 AND
    adjustment_count>=0 AND deduction_count>=0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Immutable idempotent S8 source contributions';

CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_aggregation_cursor (
  source_name VARCHAR(64) NOT NULL,
  partition_no INT NOT NULL DEFAULT 0,
  last_sequence_no BIGINT NOT NULL DEFAULT 0,
  source_watermark DATETIME(3) NULL,
  last_run_id VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'IDLE',
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (source_name,partition_no),
  KEY idx_scx_ocpx_aggregate_cursor_lease (status,lease_until),
  CONSTRAINT ck_scx_ocpx_aggregate_cursor CHECK (
    partition_no>=0 AND last_sequence_no>=0 AND status IN ('IDLE','RUNNING','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S8 incremental aggregation cursor';

CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_adjustment (
  id VARCHAR(32) NOT NULL,
  source_event_id VARCHAR(128) NOT NULL,
  source_hourly_fact_id VARCHAR(32) NULL,
  adjustment_type VARCHAR(16) NOT NULL,
  metric_code VARCHAR(64) NOT NULL,
  delta_count BIGINT NOT NULL DEFAULT 0,
  delta_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  reason_code VARCHAR(64) NOT NULL,
  rule_version BIGINT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  monitor_route_id VARCHAR(32) NOT NULL,
  route_version_id VARCHAR(32) NOT NULL,
  stat_hour DATETIME NOT NULL,
  actor VARCHAR(100) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_hourly_adjustment_source (source_event_id,adjustment_type,metric_code,reason_code),
  KEY idx_scx_ocpx_hourly_adjustment_fact (source_hourly_fact_id,create_time),
  KEY idx_scx_ocpx_hourly_adjustment_scope (stat_hour,advertiser_id,channel_id,media_account_id),
  CONSTRAINT fk_scx_ocpx_hourly_adjustment_fact FOREIGN KEY (source_hourly_fact_id)
    REFERENCES scx_ocpx_hourly_report(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_hourly_adjustment CHECK (
    adjustment_type IN ('ADJUSTMENT','DEDUCTION') AND metric_code REGEXP '^[a-z][A-Za-z0-9]{1,63}$' AND
    route_version_id<>'' AND reason_code<>''
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable S8 adjustment and deduction facts';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_export_task (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  export_type VARCHAR(24) NOT NULL,
  query_json JSON NOT NULL,
  query_hash CHAR(64) NOT NULL,
  scope_json JSON NOT NULL,
  scope_hash CHAR(64) NOT NULL,
  idempotency_bucket DATETIME NOT NULL,
  metric_definitions_version VARCHAR(32) NOT NULL DEFAULT 'S8_V1',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  storage_config_id VARCHAR(32) NULL,
  storage_provider VARCHAR(32) NULL,
  storage_object_key VARCHAR(1024) NULL,
  storage_object_url VARCHAR(2048) NULL,
  file_size BIGINT NULL,
  row_count BIGINT NULL,
  expires_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_report_export_idempotency
    (user_id,scope_hash,query_hash,export_type,idempotency_bucket),
  KEY idx_scx_ocpx_report_export_claim (status,next_attempt_at,lease_until),
  KEY idx_scx_ocpx_report_export_expiry (status,expires_at),
  CONSTRAINT ck_scx_ocpx_report_export CHECK (
    export_type IN ('STANDARD','DEDUCTION') AND metric_definitions_version='S8_V1' AND
    status IN ('PENDING','PROCESSING','SUCCEEDED','RETRY_WAIT','FAILED','CANCELLED','EXPIRED') AND
    attempt_count>=0 AND (file_size IS NULL OR file_size>=0) AND (row_count IS NULL OR row_count>=0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='S8 asynchronous managed-storage export task';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_export_audit (
  id VARCHAR(32) NOT NULL,
  export_task_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  result VARCHAR(16) NOT NULL,
  query_hash CHAR(64) NOT NULL,
  scope_hash CHAR(64) NOT NULL,
  row_count BIGINT NULL,
  detail_summary VARCHAR(500) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_ocpx_report_export_audit_task (export_task_id,create_time),
  KEY idx_scx_ocpx_report_export_audit_user (user_id,create_time),
  CONSTRAINT fk_scx_ocpx_report_export_audit_task FOREIGN KEY (export_task_id)
    REFERENCES scx_ocpx_report_export_task(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_report_export_audit CHECK (
    action IN ('CREATE','CLAIM','SUCCEED','FAIL','RETRY','DOWNLOAD','CANCEL','EXPIRE','CLEANUP') AND
    result IN ('SUCCESS','DENIED','FAILED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Payload-free S8 export audit';

DROP TRIGGER IF EXISTS trg_scx_ocpx_hourly_adjustment_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_hourly_adjustment_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_ocpx_hourly_adjustment_no_update BEFORE UPDATE ON scx_ocpx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_hourly_adjustment_no_delete BEFORE DELETE ON scx_ocpx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts cannot be deleted'$$
DELIMITER ;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_report_time_v89','数据汇总','/ocpx/report/time','menu_ocpx_report','lucide:clock-3','ocpx/report/index','ocpx:report:view',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report_product_v89','产品汇总','/ocpx/report/product','menu_ocpx_report','lucide:package','ocpx/report/index','ocpx:report:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report_link_v89','链接汇总','/ocpx/report/link','menu_ocpx_report','lucide:link-2','ocpx/report/index','ocpx:report:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report_task_v89','任务汇总','/ocpx/report/task','menu_ocpx_report','lucide:list-checks','ocpx/report/index','ocpx:report:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report_media_v89','媒体汇总','/ocpx/report/media','menu_ocpx_report','lucide:radio-tower','ocpx/report/index','ocpx:report:view',50,'0','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_report_view_v89','查看统一报表','','menu_ocpx_report',NULL,NULL,'ocpx:report:view',100,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_report_export_v89','创建普通导出','','menu_ocpx_report',NULL,NULL,'ocpx:report:export',110,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_report_deduction_export_v89','创建审减导出','','menu_ocpx_report',NULL,NULL,'ocpx:report:deduction:export',120,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_report_sensitive_v89','查看敏感指标','','menu_ocpx_report',NULL,NULL,'ocpx:report:metric:sensitive',130,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id IN ('menu_ocpx_report_time','perm_ocpx_report_read');

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_report','menu_ocpx_report_time_v89','menu_ocpx_report_product_v89',
  'menu_ocpx_report_link_v89','menu_ocpx_report_task_v89','menu_ocpx_report_media_v89','perm_ocpx_report_view_v89',
  'perm_ocpx_report_export_v89','perm_ocpx_report_deduction_export_v89','perm_ocpx_report_sensitive_v89')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_report','menu_ocpx_report_time_v89','menu_ocpx_report_product_v89',
  'menu_ocpx_report_link_v89','menu_ocpx_report_task_v89','menu_ocpx_report_media_v89','perm_ocpx_report_view_v89',
  'perm_ocpx_report_export_v89')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v89_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_ocpx_report','menu_ocpx_report_time_v89','menu_ocpx_report_product_v89',
  'menu_ocpx_report_link_v89','menu_ocpx_report_task_v89','menu_ocpx_report_media_v89','perm_ocpx_report_view_v89')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';

-- S9 Dashboard permission closure. Dashboard facts remain the S8 unified
-- hourly projection; no Dashboard-specific persistence is introduced.
SET @s9_dashboard_scope_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_hourly_report'
    AND index_name='idx_scx_ocpx_dashboard_scope'
);
SET @s9_dashboard_scope_index_ddl := IF(
  @s9_dashboard_scope_index_exists=0,
  'ALTER TABLE scx_ocpx_hourly_report ADD INDEX idx_scx_ocpx_dashboard_scope (advertiser_id,channel_id,media_account_id,time_hour)',
  'SELECT 1'
);
PREPARE s9_dashboard_scope_index_stmt FROM @s9_dashboard_scope_index_ddl;
EXECUTE s9_dashboard_scope_index_stmt;
DEALLOCATE PREPARE s9_dashboard_scope_index_stmt;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_dashboard_view_v90','查看经营总览','','menu_ocpx_home',NULL,NULL,
   'ocpx:dashboard:view',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v90_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_ocpx_dashboard_view_v90'),256),55)),
       rm.role_id,'perm_ocpx_dashboard_view_v90'
FROM sys_role_menu rm
JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_ocpx_dashboard_read';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v90_',LEFT(SHA2(CONCAT(r.id,'|perm_ocpx_dashboard_view_v90'),256),55)),
       r.id,'perm_ocpx_dashboard_view_v90'
FROM sys_role r
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE FROM sys_role_menu WHERE menu_id='perm_ocpx_dashboard_read';
UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id='perm_ocpx_dashboard_read';

-- S10 runtime centre and failure compensation closure.
--
-- Runtime facts/failures/attempts are append-only. Replay requests are the
-- recoverable command truth and never contain a raw payload, URL, header,
-- credential or third-party response body. Legacy OCPX rows are deliberately
-- not backfilled because their frozen Product/Task/MediaAccount dimensions
-- cannot be inferred safely.

CREATE TABLE IF NOT EXISTS scx_ocpx_runtime_fact (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  fact_type VARCHAR(32) NOT NULL,
  direction VARCHAR(32) NOT NULL,
  acceptance_status VARCHAR(24) NOT NULL,
  processing_status VARCHAR(24) NOT NULL,
  tenant_id VARCHAR(32) NULL,
  advertiser_id VARCHAR(32) NULL,
  product_id VARCHAR(32) NULL,
  app_link_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  channel_id VARCHAR(32) NULL,
  media_account_id VARCHAR(32) NULL,
  operator_user_id VARCHAR(32) NULL,
  route_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  event_definition_id VARCHAR(32) NULL,
  event_code VARCHAR(64) NULL,
  event_snapshot_schema_version INT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  request_id_hash CHAR(64) NULL,
  trace_id VARCHAR(128) NULL,
  correlation_id_hash CHAR(64) NULL,
  external_click_id_hash CHAR(64) NULL,
  media_task_id_hash CHAR(64) NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NULL,
  summary_code VARCHAR(64) NOT NULL,
  dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED',
  quality_status VARCHAR(24) NOT NULL DEFAULT 'UNMAPPED',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_ocpx_runtime_fact_id (id),
  UNIQUE KEY uk_scx_ocpx_runtime_fact_idempotency (idempotency_key_hash),
  KEY idx_scx_ocpx_runtime_fact_time_scope (advertiser_id,channel_id,media_account_id,received_time),
  KEY idx_scx_ocpx_runtime_fact_task_time (task_id,received_time,fact_type),
  KEY idx_scx_ocpx_runtime_fact_trace (trace_id,received_time),
  KEY idx_scx_ocpx_runtime_fact_route (route_version_id,received_time),
  CONSTRAINT ck_scx_ocpx_runtime_fact CHECK (
    source_type IN ('FEEDBACK','CALLBACK','DELIVERY','CONFIG_DISTRIBUTION','REPLAY','LEGACY') AND
    fact_type IN ('INTEGRATION','CLICK','CONVERSION','DELIVERY_ATTEMPT','CONFIG_APPLY','REPLAY_RESULT') AND
    direction IN ('INGRESS','UPSTREAM','DOWNSTREAM','INTERNAL') AND
    acceptance_status IN ('ACCEPTED','REJECTED','DUPLICATE','UNMAPPED') AND
    processing_status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','DLQ','ISOLATED') AND
    dimension_status IN ('FROZEN','UNMAPPED') AND quality_status IN ('COMPLETE','PARTIAL','UNMAPPED') AND
    CHAR_LENGTH(idempotency_key_hash)=64 AND
    (payload_hash IS NULL OR CHAR_LENGTH(payload_hash)=64) AND
    (request_id_hash IS NULL OR CHAR_LENGTH(request_id_hash)=64) AND
    (correlation_id_hash IS NULL OR CHAR_LENGTH(correlation_id_hash)=64) AND
    (external_click_id_hash IS NULL OR CHAR_LENGTH(external_click_id_hash)=64) AND
    (media_task_id_hash IS NULL OR CHAR_LENGTH(media_task_id_hash)=64) AND
    received_time>=event_time AND
    ((dimension_status='UNMAPPED') OR
     (tenant_id IS NOT NULL AND advertiser_id IS NOT NULL AND product_id IS NOT NULL AND
      app_link_id IS NOT NULL AND task_id IS NOT NULL AND channel_id IS NOT NULL AND
      media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND route_id IS NOT NULL AND
      route_version_id IS NOT NULL AND route_version>=1))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 sanitized runtime fact index';

CREATE TABLE IF NOT EXISTS scx_ocpx_runtime_failure (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  source_fact_id VARCHAR(32) NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  failure_class VARCHAR(32) NOT NULL,
  error_code VARCHAR(64) NOT NULL,
  replayable TINYINT(1) NOT NULL DEFAULT 0,
  failure_revision BIGINT NOT NULL DEFAULT 1,
  tenant_id VARCHAR(32) NULL,
  advertiser_id VARCHAR(32) NULL,
  product_id VARCHAR(32) NULL,
  app_link_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  channel_id VARCHAR(32) NULL,
  media_account_id VARCHAR(32) NULL,
  operator_user_id VARCHAR(32) NULL,
  route_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  route_version BIGINT NULL,
  event_definition_id VARCHAR(32) NULL,
  event_snapshot_schema_version INT NULL,
  event_time DATETIME(3) NOT NULL,
  received_time DATETIME(3) NOT NULL,
  trace_id VARCHAR(128) NULL,
  correlation_id_hash CHAR(64) NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NULL,
  summary_hash CHAR(64) NOT NULL,
  dimension_status VARCHAR(16) NOT NULL DEFAULT 'UNMAPPED',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_ocpx_runtime_failure_id (id),
  UNIQUE KEY uk_scx_ocpx_runtime_failure_source (source_type,source_id,error_code),
  KEY idx_scx_ocpx_runtime_failure_dlq (failure_class,replayable,received_time),
  KEY idx_scx_ocpx_runtime_failure_scope (advertiser_id,channel_id,media_account_id,received_time),
  KEY idx_scx_ocpx_runtime_failure_task (task_id,received_time),
  CONSTRAINT fk_scx_ocpx_runtime_failure_fact FOREIGN KEY (source_fact_id)
    REFERENCES scx_ocpx_runtime_fact(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_runtime_failure CHECK (
    source_type IN ('FEEDBACK','CALLBACK','DELIVERY','CONFIG_DISTRIBUTION','LEGACY') AND
    failure_class IN ('RETRYABLE','NON_RETRYABLE','PROTOCOL_REJECTED','SIGNATURE_FAILED',
      'SCOPE_REJECTED','DUPLICATE','LATE_ARRIVAL','UNKNOWN') AND
    failure_revision>=1 AND replayable IN (0,1) AND
    CHAR_LENGTH(idempotency_key_hash)=64 AND CHAR_LENGTH(summary_hash)=64 AND
    (payload_hash IS NULL OR CHAR_LENGTH(payload_hash)=64) AND
    (correlation_id_hash IS NULL OR CHAR_LENGTH(correlation_id_hash)=64) AND
    received_time>=event_time AND dimension_status IN ('FROZEN','UNMAPPED') AND
    ((dimension_status='UNMAPPED') OR
     (tenant_id IS NOT NULL AND advertiser_id IS NOT NULL AND product_id IS NOT NULL AND
      app_link_id IS NOT NULL AND task_id IS NOT NULL AND channel_id IS NOT NULL AND
      media_account_id IS NOT NULL AND operator_user_id IS NOT NULL AND route_id IS NOT NULL AND
      route_version_id IS NOT NULL AND route_version>=1))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 failure envelope without raw payload';

CREATE TABLE IF NOT EXISTS scx_ocpx_runtime_replay_request (
  id VARCHAR(32) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  source_failure_id VARCHAR(32) NOT NULL,
  expected_failure_revision BIGINT NOT NULL,
  source_route_version_id VARCHAR(32) NOT NULL,
  source_route_version BIGINT NOT NULL,
  command_type VARCHAR(24) NOT NULL DEFAULT 'REPLAY',
  mode VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL_SNAPSHOT_ONLY',
  tenant_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  product_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  task_id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  media_account_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(32) NOT NULL,
  requested_by VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  ticket VARCHAR(128) NULL,
  command_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(100) NULL,
  lease_until DATETIME(3) NULL,
  result_fact_id VARCHAR(32) NULL,
  result_contribution_id VARCHAR(32) NULL,
  last_error_class VARCHAR(32) NULL,
  last_error_code VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_runtime_replay_request (request_id),
  UNIQUE KEY uk_scx_ocpx_runtime_replay_fingerprint (command_fingerprint),
  KEY idx_scx_ocpx_runtime_replay_claim (status,next_attempt_at,lease_until,tenant_id),
  KEY idx_scx_ocpx_runtime_replay_scope (advertiser_id,channel_id,media_account_id,create_time),
  KEY idx_scx_ocpx_runtime_replay_task (task_id,create_time),
  CONSTRAINT fk_scx_ocpx_runtime_replay_failure FOREIGN KEY (source_failure_id)
    REFERENCES scx_ocpx_runtime_failure(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_runtime_replay_route_version FOREIGN KEY (source_route_version_id)
    REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_runtime_replay_result FOREIGN KEY (result_fact_id)
    REFERENCES scx_ocpx_runtime_fact(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_runtime_replay_request CHECK (
    request_id REGEXP '^[A-Za-z0-9_-]{16,64}$' AND expected_failure_revision>=1 AND
    source_route_version>=1 AND command_type='REPLAY' AND mode='ORIGINAL_SNAPSHOT_ONLY' AND
    CHAR_LENGTH(command_fingerprint)=64 AND CHAR_LENGTH(TRIM(reason)) BETWEEN 4 AND 500 AND
    status IN ('QUEUED','CLAIMED','RETRY_WAIT','SUCCEEDED','TERMINAL','CANCELLED') AND
    attempt_count BETWEEN 0 AND max_attempts AND max_attempts BETWEEN 1 AND 10 AND
    (lease_until IS NULL OR lease_owner IS NOT NULL) AND
    (status<>'SUCCEEDED' OR result_fact_id IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Recoverable S10 replay command truth';

CREATE TABLE IF NOT EXISTS scx_ocpx_runtime_replay_attempt (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  replay_request_id VARCHAR(32) NOT NULL,
  attempt_no INT NOT NULL,
  worker_id_hash CHAR(64) NOT NULL,
  source_route_version_id VARCHAR(32) NOT NULL,
  source_event_snapshot_schema_version INT NOT NULL,
  status VARCHAR(24) NOT NULL,
  error_class VARCHAR(32) NULL,
  error_code VARCHAR(64) NULL,
  result_summary_hash CHAR(64) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  next_retry_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_ocpx_runtime_replay_attempt_id (id),
  UNIQUE KEY uk_scx_ocpx_runtime_replay_attempt_no (replay_request_id,attempt_no),
  KEY idx_scx_ocpx_runtime_replay_attempt_time (started_at,status),
  CONSTRAINT fk_scx_ocpx_runtime_replay_attempt_request FOREIGN KEY (replay_request_id)
    REFERENCES scx_ocpx_runtime_replay_request(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_scx_ocpx_runtime_replay_attempt_version FOREIGN KEY (source_route_version_id)
    REFERENCES scx_ocpx_route_version(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT ck_scx_ocpx_runtime_replay_attempt CHECK (
    attempt_no>=1 AND CHAR_LENGTH(worker_id_hash)=64 AND source_event_snapshot_schema_version>=1 AND
    status IN ('STARTED','SUCCEEDED','RETRY_WAIT','TERMINAL') AND
    (result_summary_hash IS NULL OR CHAR_LENGTH(result_summary_hash)=64) AND
    (finished_at IS NULL OR finished_at>=started_at)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 replay attempt timeline';

CREATE TABLE IF NOT EXISTS scx_ocpx_runtime_command_audit (
  sequence_no BIGINT NOT NULL AUTO_INCREMENT,
  id VARCHAR(32) NOT NULL,
  command_type VARCHAR(32) NOT NULL,
  command_id VARCHAR(32) NULL,
  source_failure_id VARCHAR(32) NULL,
  task_id VARCHAR(32) NULL,
  route_version_id VARCHAR(32) NULL,
  action VARCHAR(32) NOT NULL,
  outcome VARCHAR(24) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  reason_hash CHAR(64) NULL,
  trace_id VARCHAR(128) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (sequence_no),
  UNIQUE KEY uk_scx_ocpx_runtime_command_audit_id (id),
  KEY idx_scx_ocpx_runtime_command_audit_command (command_id,create_time),
  KEY idx_scx_ocpx_runtime_command_audit_task (task_id,create_time),
  CONSTRAINT ck_scx_ocpx_runtime_command_audit CHECK (
    command_type IN ('REPLAY','EMERGENCY_STOP') AND
    action IN ('PREVIEW','CONFIRM','CLAIM','ATTEMPT','COMPLETE','REJECT') AND
    outcome IN ('PASS','WARN','FAIL','QUEUED','SUCCEEDED','TERMINAL') AND
    (reason_hash IS NULL OR CHAR_LENGTH(reason_hash)=64)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Append-only S10 management command audit';

SET @s10_delivery_attempt_unique_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_ocpx_delivery_attempt'
    AND index_name='uk_scx_ocpx_delivery_attempt_no'
);
SET @s10_delivery_attempt_unique_ddl := IF(
  @s10_delivery_attempt_unique_exists=0,
  'ALTER TABLE scx_ocpx_delivery_attempt ADD UNIQUE KEY uk_scx_ocpx_delivery_attempt_no (job_id,attempt_no)',
  'SELECT 1'
);
PREPARE s10_delivery_attempt_unique_stmt FROM @s10_delivery_attempt_unique_ddl;
EXECUTE s10_delivery_attempt_unique_stmt;
DEALLOCATE PREPARE s10_delivery_attempt_unique_stmt;

DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_fact_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_fact_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_failure_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_failure_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_replay_attempt_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_replay_attempt_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_command_audit_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_command_audit_no_delete;
DELIMITER $$
CREATE TRIGGER trg_scx_ocpx_runtime_fact_no_update BEFORE UPDATE ON scx_ocpx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_fact_no_delete BEFORE DELETE ON scx_ocpx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_failure_no_update BEFORE UPDATE ON scx_ocpx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_failure_no_delete BEFORE DELETE ON scx_ocpx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_replay_attempt_no_update BEFORE UPDATE ON scx_ocpx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_replay_attempt_no_delete BEFORE DELETE ON scx_ocpx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_command_audit_no_update BEFORE UPDATE ON scx_ocpx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_command_audit_no_delete BEFORE DELETE ON scx_ocpx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
DELIMITER ;

-- Canonical S10 permissions. Existing legacy grants are copied once before
-- the old permissions are retired. A later accidental replay cannot restore a
-- revoked canonical grant because the old role-menu relation no longer exists.
INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_runtime_view_v91','查看运行事实','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:view',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_detail_v91','查看运行详情','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:detail',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_body_v91','查看受控正文','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:body',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_export_v91','导出运行事实','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:export',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_dlq_view_v91','查看失败队列','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:dlq:view',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_retry_v91','重试失败事实','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:retry',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_replay_v91','补偿重放','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:replay',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_debug_v91','受控运行调试','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:debug',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_runtime_stop_v91','紧急停流','','menu_ocpx_runtime',NULL,NULL,'ocpx:runtime:emergency-stop',90,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_ocpx_runtime_view_v91'),256),55)),
       rm.role_id,'perm_ocpx_runtime_view_v91'
FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_ocpx_runtime_read';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_ocpx_runtime_replay_v91'),256),55)),
       rm.role_id,'perm_ocpx_runtime_replay_v91'
FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_ocpx_runtime_replay';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v91_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (
  'perm_ocpx_runtime_view_v91','perm_ocpx_runtime_detail_v91','perm_ocpx_runtime_body_v91',
  'perm_ocpx_runtime_export_v91','perm_ocpx_runtime_dlq_view_v91','perm_ocpx_runtime_retry_v91',
  'perm_ocpx_runtime_replay_v91','perm_ocpx_runtime_debug_v91','perm_ocpx_runtime_stop_v91')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

DELETE FROM sys_role_menu WHERE menu_id IN ('perm_ocpx_runtime_read','perm_ocpx_runtime_replay');
UPDATE sys_menu SET del_flag='1',update_time=NOW()
WHERE id IN ('perm_ocpx_runtime_read','perm_ocpx_runtime_replay');

-- V95 current state: advertiser login binding, safe account authorization and
-- optional integration fields. No retired migration ledger is read or written.
ALTER TABLE scx_ocpx_advertiser
  MODIFY COLUMN adapter_code VARCHAR(32) NULL DEFAULT NULL,
  MODIFY COLUMN adapter_version BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN bid_url VARCHAR(5000) NULL,
  MODIFY COLUMN password_hash VARCHAR(100) NULL;

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_login_binding (
  advertiser_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  managed_role_id VARCHAR(64) NULL,
  grant_version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (advertiser_id),
  UNIQUE KEY uk_scx_ocpx_advertiser_login_user (user_id),
  UNIQUE KEY uk_scx_ocpx_advertiser_login_managed_role (managed_role_id),
  CONSTRAINT ck_scx_ocpx_advertiser_login_grant_version CHECK (grant_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Advertiser to login-account authorization binding';

-- Identifier collations differ across some historical OCPX/system tables.
-- These are verified logical references; verify-database.sh rejects orphans.

INSERT INTO scx_ocpx_advertiser_login_binding
  (advertiser_id,user_id,managed_role_id,grant_version,create_by,update_by,create_time,update_time)
SELECT a.id,MIN(u.id),NULL,1,'V95_BASELINE','V95_BASELINE',NOW(3),NOW(3)
FROM scx_ocpx_advertiser a
JOIN sys_user u ON BINARY u.shop_id=BINARY a.id AND u.type='2' AND u.del_flag='0'
LEFT JOIN scx_ocpx_advertiser_login_binding b ON BINARY b.advertiser_id=BINARY a.id
WHERE a.deleted=0 AND b.advertiser_id IS NULL
GROUP BY a.id
HAVING COUNT(*)=1
ON DUPLICATE KEY UPDATE advertiser_id=scx_ocpx_advertiser_login_binding.advertiser_id;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_grant_v95','管理广告主账号授权','','menu_ocpx_advertiser_list',NULL,NULL,
   'ocpx:advertiser:grant',80,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id AND m.permission='ocpx:advertiser:grant'
JOIN sys_role r ON r.id=rm.role_id
WHERE r.role_code<>'ROLE_ADMIN';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v95_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:advertiser:grant' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';
