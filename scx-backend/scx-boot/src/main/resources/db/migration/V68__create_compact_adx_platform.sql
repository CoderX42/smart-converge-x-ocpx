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

CREATE TABLE IF NOT EXISTS scx_adx_clue (
  id VARCHAR(32) NOT NULL,
  source_type TINYINT NOT NULL DEFAULT 1 COMMENT '1 汽车之家',
  clue_status TINYINT NOT NULL DEFAULT 0 COMMENT '0 待上报,1 成功,2 失败',
  clue_name VARCHAR(100) NULL,
  clue_phone VARCHAR(32) NOT NULL,
  clue_index VARCHAR(100) NULL,
  son_task_id VARCHAR(100) NULL,
  clue_city_id VARCHAR(64) NULL,
  clue_area_id VARCHAR(64) NULL,
  clue_brand_id VARCHAR(64) NULL,
  clue_car_series_id VARCHAR(64) NULL,
  clue_car_model_id VARCHAR(64) NULL,
  first_car_registration_time DATETIME NULL,
  car_mileage DECIMAL(12,2) NULL,
  remark VARCHAR(500) NULL,
  submit_message VARCHAR(1000) NULL,
  submitted_at DATETIME(3) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_scx_adx_clue_status (clue_status, create_time),
  KEY idx_scx_adx_clue_phone (clue_phone, clue_index),
  KEY idx_scx_adx_clue_source (source_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX 线索上报';

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
