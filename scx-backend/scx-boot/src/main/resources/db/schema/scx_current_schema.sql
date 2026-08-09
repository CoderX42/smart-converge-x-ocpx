-- Smart Converge X current application schema
-- Authoritative empty-database schema for the compact ADX and OCPX platform.
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

CREATE TABLE IF NOT EXISTS scx_schema_migration (
  version VARCHAR(32) NOT NULL,
  script_name VARCHAR(255) NOT NULL,
  checksum VARCHAR(128) NOT NULL,
  applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX historical schema change audit';

-- V68: replace the broad SCX advertising domains with the compact ADX management loop.
-- Historical migrations remain immutable; the separately authorized retirement script
-- removes their physical tables after this replacement schema and navigation are accepted.

CREATE TABLE IF NOT EXISTS scx_adx_channel (
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
  UNIQUE KEY uk_scx_adx_channel_name (channel_name, active_marker),
  KEY idx_scx_adx_channel_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 渠道';

CREATE TABLE IF NOT EXISTS scx_adx_advertiser (
  id VARCHAR(32) NOT NULL,
  advertiser_name VARCHAR(100) NOT NULL,
  bid_url VARCHAR(512) NOT NULL,
  secret_key VARCHAR(255) NULL,
  macro_params VARCHAR(1000) NULL,
  account VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_advertiser_name (advertiser_name, active_marker),
  UNIQUE KEY uk_scx_adx_advertiser_account (account, active_marker),
  KEY idx_scx_adx_advertiser_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 广告主';

CREATE TABLE IF NOT EXISTS scx_adx_app_link (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  link_platform TINYINT NOT NULL COMMENT '1 Android,2 iOS,3 Android+iOS,4 快应用',
  link_name VARCHAR(100) NOT NULL,
  link_channel VARCHAR(100) NULL,
  link_url VARCHAR(1000) NOT NULL,
  deeplink VARCHAR(1000) NULL,
  link_ext VARCHAR(1000) NULL,
  link_remark VARCHAR(500) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_link_name (advertiser_id, link_name, active_marker),
  KEY idx_scx_adx_link_advertiser (advertiser_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 广告主应用链接';

CREATE TABLE IF NOT EXISTS scx_adx_monitor_link (
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
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_monitor_name (channel_id, monitor_name, active_marker),
  KEY idx_scx_adx_monitor_advertiser (advertiser_id, app_link_id),
  KEY idx_scx_adx_monitor_updated (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 监测链接';

CREATE TABLE IF NOT EXISTS scx_adx_deduction_rule (
  id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  app_link_id VARCHAR(32) NOT NULL,
  ratio DECIMAL(8,4) NOT NULL DEFAULT 1.0000 COMMENT '保留比例，0 到 1',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_deduction (channel_id, app_link_id, active_marker),
  KEY idx_scx_adx_deduction_link (app_link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 扣量规则';

CREATE TABLE IF NOT EXISTS scx_adx_hourly_report (
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
  UNIQUE KEY uk_scx_adx_report_grain (time_hour, channel_id, advertiser_id, app_link_id, monitor_id),
  KEY idx_scx_adx_report_filter (time_hour, channel_id, advertiser_id),
  KEY idx_scx_adx_report_monitor (monitor_id, time_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 时段报表事实';


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
  ('menu_adx_home','首页','/adx/dashboard','0','lucide:layout-dashboard','adx/dashboard/index',1,'0','app_base',NOW(),'0'),
  ('menu_adx_report','报表中心','/adx/report','0','lucide:chart-no-axes-combined',NULL,10,'0','app_base',NOW(),'0'),
  ('menu_adx_report_time','时段报表','/adx/report/time','menu_adx_report','lucide:chart-column','adx/report/index',10,'0','app_base',NOW(),'0'),
  ('menu_adx_channel','渠道管理','/adx/channel','0','lucide:radio-tower',NULL,20,'0','app_base',NOW(),'0'),
  ('menu_adx_channel_list','渠道管理','/adx/channel/list','menu_adx_channel','lucide:waypoints','adx/channel/index',10,'0','app_base',NOW(),'0'),
  ('menu_adx_monitor','监测链接管理','/adx/channel/monitor','menu_adx_channel','lucide:link-2','adx/monitor/index',20,'0','app_base',NOW(),'0'),
  ('menu_adx_advertiser','广告主管理','/adx/advertiser','0','lucide:building-2',NULL,30,'0','app_base',NOW(),'0'),
  ('menu_adx_advertiser_list','广告主管理','/adx/advertiser/list','menu_adx_advertiser','lucide:briefcase-business','adx/advertiser/index',10,'0','app_base',NOW(),'0'),
  ('menu_adx_app_link','应用链接管理','/adx/advertiser/app-link','menu_adx_advertiser','lucide:app-window','adx/app-link/index',20,'0','app_base',NOW(),'0'),
  ('menu_adx_deduction','扣量管理','/adx/advertiser/deduction','menu_adx_advertiser','lucide:percent','adx/deduction/index',30,'0','app_base',NOW(),'0'),
  ('menu_adx_clue','线索上报','/adx/clue','0','lucide:file-up','adx/clue/index',50,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_dashboard_read','查看首页','adx:dashboard:read','menu_adx_home',10,'1','app_base',NOW(),'0'),
  ('perm_adx_report_read','查询报表','adx:report:read','menu_adx_report_time',10,'1','app_base',NOW(),'0'),
  ('perm_adx_report_export','下载报表','adx:report:export','menu_adx_report_time',20,'1','app_base',NOW(),'0'),
  ('perm_adx_channel_read','查询渠道','adx:channel:read','menu_adx_channel_list',10,'1','app_base',NOW(),'0'),
  ('perm_adx_channel_write','维护渠道','adx:channel:write','menu_adx_channel_list',20,'1','app_base',NOW(),'0'),
  ('perm_adx_monitor_read','查询监测链接','adx:monitor:read','menu_adx_monitor',10,'1','app_base',NOW(),'0'),
  ('perm_adx_monitor_write','维护监测链接','adx:monitor:write','menu_adx_monitor',20,'1','app_base',NOW(),'0'),
  ('perm_adx_advertiser_read','查询广告主','adx:advertiser:read','menu_adx_advertiser_list',10,'1','app_base',NOW(),'0'),
  ('perm_adx_advertiser_write','维护广告主','adx:advertiser:write','menu_adx_advertiser_list',20,'1','app_base',NOW(),'0'),
  ('perm_adx_app_link_read','查询应用链接','adx:app-link:read','menu_adx_app_link',10,'1','app_base',NOW(),'0'),
  ('perm_adx_app_link_write','维护应用链接','adx:app-link:write','menu_adx_app_link',20,'1','app_base',NOW(),'0'),
  ('perm_adx_deduction_read','查询扣量规则','adx:deduction:read','menu_adx_deduction',10,'1','app_base',NOW(),'0'),
  ('perm_adx_deduction_write','维护扣量规则','adx:deduction:write','menu_adx_deduction',20,'1','app_base',NOW(),'0'),
  ('perm_adx_clue_read','查询线索','adx:clue:read','menu_adx_clue',10,'1','app_base',NOW(),'0'),
  ('perm_adx_clue_write','维护线索','adx:clue:write','menu_adx_clue',20,'1','app_base',NOW(),'0'),
  ('perm_adx_clue_submit','提交线索','adx:clue:submit','menu_adx_clue',30,'1','app_base',NOW(),'0')
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
WHERE id NOT LIKE 'menu_adx_%' AND id NOT LIKE 'perm_adx_%'
  AND id NOT IN ('menu_ocpx_system','menu_system_user','menu_role')
  AND COALESCE(permission,'') NOT LIKE 'system:sysuser:%'
  AND COALESCE(permission,'') NOT LIKE 'system:sysrole:%';

UPDATE sys_menu
SET del_flag='0', update_time=NOW()
WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%'
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
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id LIKE 'menu_adx_%' OR m.id LIKE 'perm_adx_%'
  OR m.id IN ('menu_ocpx_system','menu_system_user','menu_role')
  OR m.permission LIKE 'system:sysuser:%' OR m.permission LIKE 'system:sysrole:%'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_adx_home','menu_adx_report','menu_adx_report_time','menu_adx_channel','menu_adx_channel_list',
  'menu_adx_monitor','menu_adx_advertiser','menu_adx_advertiser_list','menu_adx_app_link',
  'menu_adx_deduction','menu_adx_clue','perm_adx_dashboard_read','perm_adx_report_read',
  'perm_adx_report_export','perm_adx_channel_read','perm_adx_channel_write','perm_adx_monitor_read',
  'perm_adx_monitor_write','perm_adx_advertiser_read','perm_adx_advertiser_write','perm_adx_app_link_read',
  'perm_adx_app_link_write','perm_adx_deduction_read','perm_adx_deduction_write','perm_adx_clue_read',
  'perm_adx_clue_write','perm_adx_clue_submit')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('menu_adx_home','menu_adx_report','menu_adx_report_time',
  'perm_adx_dashboard_read','perm_adx_report_read','perm_adx_report_export')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),60)), 'role_advertiser', m.id
FROM sys_menu m
WHERE m.id IN ('menu_adx_home','menu_adx_report','menu_adx_report_time',
  'perm_adx_dashboard_read','perm_adx_report_read','perm_adx_report_export')
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
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='channel_code') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN channel_code VARCHAR(64) NULL AFTER id;
    UPDATE scx_adx_channel SET channel_code=CONCAT('channel_',LEFT(id,16)) WHERE channel_code IS NULL;
    ALTER TABLE scx_adx_channel MODIFY channel_code VARCHAR(64) NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='adapter_code') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'STANDARD' AFTER channel_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='auth_type') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN auth_type VARCHAR(16) NOT NULL DEFAULT 'HMAC' AFTER adapter_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='key_version') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN key_version INT NOT NULL DEFAULT 1 AFTER auth_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='status') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='callback_host_allowlist') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN callback_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_channel ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_adx_channel' AND index_name='uk_scx_adx_channel_code') THEN
    ALTER TABLE scx_adx_channel ADD UNIQUE KEY uk_scx_adx_channel_code (channel_code,active_marker);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='adapter_code') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN adapter_code VARCHAR(32) NOT NULL DEFAULT 'COCO' AFTER advertiser_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='status') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER balance;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='upstream_host_allowlist') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN upstream_host_allowlist VARCHAR(1000) NULL AFTER macro_params;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_advertiser' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_advertiser ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER status;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='downstream_callback_url') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN downstream_callback_url VARCHAR(1500) NULL AFTER click_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='route_status') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN route_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' AFTER downstream_callback_url;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER route_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='rta_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN rta_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER config_version;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='billing_mode') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN billing_mode VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER rta_mode;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='published_at') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN published_at DATETIME(3) NULL AFTER billing_mode;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_deduction_rule' AND column_name='config_version') THEN
    ALTER TABLE scx_adx_deduction_rule ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 AFTER ratio;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='raw_conversion_count') THEN
    ALTER TABLE scx_adx_hourly_report
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
  status VARCHAR(16) NOT NULL,
  http_status INT NULL,
  error_message VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
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
  ('menu_adx_runtime','转发运行中心','/adx/runtime','0','lucide:activity','adx/runtime/index',45,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_runtime_read','查看转发状态','adx:runtime:read','menu_adx_runtime',10,'1','app_base',NOW(),'0'),
  ('perm_adx_runtime_replay','重放失败任务','adx:runtime:replay','menu_adx_runtime',20,'1','app_base',NOW(),'0'),
  ('perm_adx_monitor_publish','发布路由配置','adx:monitor:publish','menu_adx_monitor',30,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_runtime','perm_adx_runtime_read','perm_adx_runtime_replay','perm_adx_monitor_publish')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_runtime','perm_adx_runtime_read')
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
  ALTER TABLE scx_adx_advertiser MODIFY COLUMN balance DECIMAL(20,6) NOT NULL DEFAULT 0.000000;

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
WHERE menu_id IN ('menu_adx_clue','perm_adx_clue_read','perm_adx_clue_write','perm_adx_clue_submit');

DELETE FROM sys_menu
WHERE id IN ('menu_adx_clue','perm_adx_clue_read','perm_adx_clue_write','perm_adx_clue_submit')
   OR permission IN ('adx:clue:read','adx:clue:write','adx:clue:submit');

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_account_read','查询账务流水','adx:account:read','menu_adx_advertiser_list',30,'1','app_base',NOW(),'0'),
  ('perm_adx_account_recharge','余额充值','adx:account:recharge','menu_adx_advertiser_list',40,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_account_read','perm_adx_account_recharge')
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

ALTER TABLE scx_adx_advertiser
  MODIFY COLUMN bid_url VARCHAR(5000) NOT NULL;

ALTER TABLE scx_adx_monitor_link
  ADD COLUMN upstream_feedback_url VARCHAR(5000) NULL AFTER click_url,
  ADD COLUMN published_upstream_feedback_url VARCHAR(5000) NULL AFTER upstream_feedback_url;

ALTER TABLE scx_adx_hourly_report
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
  ('menu_adx_api_debug','接口调试','/adx/api-debugger','0','lucide:terminal-square','adx/api-debugger/index',46,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_api_debug_read','查看接口调试','adx:api-debug:read','menu_adx_api_debug',10,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_case_write','维护调试用例','adx:api-debug:case:write','menu_adx_api_debug',20,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_execute','执行接口调试','adx:api-debug:execute','menu_adx_api_debug',30,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_execute_billable','执行真实扣费调试','adx:api-debug:execute-billable','menu_adx_api_debug',40,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_logs','查看调试日志','adx:api-debug:logs','menu_adx_api_debug',50,'1','app_base',NOW(),'0'),
  ('perm_adx_api_debug_logs_raw','查看近期原始日志','adx:api-debug:logs-raw','menu_adx_api_debug',60,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_adx_api_debug','perm_adx_api_debug_read','perm_adx_api_debug_case_write',
   'perm_adx_api_debug_execute','perm_adx_api_debug_execute_billable',
   'perm_adx_api_debug_logs','perm_adx_api_debug_logs_raw')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
-- Forward-only OCPX click-deduplication foundation.
-- Keep accepted click facts immutable; the decision table controls billing and forwarding.

DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_click_deduplication()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link'
      AND column_name='click_dedup_window_seconds'
  ) THEN
    ALTER TABLE scx_adx_monitor_link
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
-- ADX effect dashboard facts. Historical click quality is only reconstructed from
-- immutable V77 decisions; periods without decisions deliberately remain uncovered.

DELIMITER $$
CREATE PROCEDURE scx_upgrade_adx_effect_dashboard_metrics()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='eligible_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN eligible_click_count BIGINT NOT NULL DEFAULT 0 AFTER clicks;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='deduplicated_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN deduplicated_click_count BIGINT NOT NULL DEFAULT 0 AFTER eligible_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='skipped_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN skipped_click_count BIGINT NOT NULL DEFAULT 0 AFTER deduplicated_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='classified_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN classified_click_count BIGINT NOT NULL DEFAULT 0 AFTER skipped_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='first_activate_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN first_activate_count BIGINT NOT NULL DEFAULT 0 AFTER activate_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='reactivation_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN reactivation_count BIGINT NOT NULL DEFAULT 0 AFTER evoke_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_3d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_3d_count BIGINT NOT NULL DEFAULT 0 AFTER next_stay_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_4d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_4d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_3d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_5d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_5d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_4d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_6d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_6d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_5d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_7d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_7d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_6d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='create_time') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='update_time') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
    END IF;
END$$
DELIMITER ;

CALL scx_upgrade_adx_effect_dashboard_metrics();
DROP PROCEDURE scx_upgrade_adx_effect_dashboard_metrics;

-- Empty-database initialization intentionally performs no historical fact backfill.
SET FOREIGN_KEY_CHECKS = 1;
