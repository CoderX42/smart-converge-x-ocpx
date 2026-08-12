-- SCX OCPX minimal current-state schema.
-- Advertising data consists of four business tables and one sanitized flow-log
-- table. Core system-management users, roles, departments, dictionaries and
-- logs remain owned by scx-system; this file only preserves their menu/RBAC
-- metadata alongside the advertising menu seed.
USE `smart_converge_x`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser (
  id VARCHAR(32) NOT NULL,
  advertiser_code VARCHAR(64) NOT NULL,
  advertiser_name VARCHAR(128) NOT NULL,
  adapter_code VARCHAR(64) NOT NULL,
  credential_config_key VARCHAR(128) NOT NULL,
  credential_version BIGINT NOT NULL,
  bid_url VARCHAR(5000) NOT NULL,
  macro_params VARCHAR(4000) NULL,
  upstream_host_allowlist VARCHAR(2000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_advertiser_code (advertiser_code,active_marker),
  UNIQUE KEY uk_ocpx_advertiser_name (advertiser_name,active_marker),
  CONSTRAINT ck_ocpx_advertiser_status CHECK (status IN ('ACTIVE','DISABLED')),
  CONSTRAINT ck_ocpx_advertiser_credential_version CHECK (credential_version>=1),
  CONSTRAINT ck_ocpx_advertiser_config_version CHECK (config_version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: upstream advertiser';

CREATE TABLE IF NOT EXISTS scx_ocpx_promotion_link (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  link_code VARCHAR(64) NOT NULL,
  link_name VARCHAR(128) NOT NULL,
  package_name VARCHAR(255) NULL,
  platform VARCHAR(16) NOT NULL,
  link_type VARCHAR(16) NOT NULL,
  link_channel VARCHAR(64) NULL,
  link_url_template VARCHAR(5000) NOT NULL,
  deeplink_url VARCHAR(2000) NULL,
  download_url VARCHAR(2000) NULL,
  macro_set VARCHAR(4000) NULL,
  redirect_enabled TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_promotion_link_code (link_code,active_marker),
  UNIQUE KEY uk_ocpx_promotion_link_owner (advertiser_id,id),
  KEY idx_ocpx_promotion_link_advertiser (advertiser_id,status,update_time),
  CONSTRAINT fk_ocpx_promotion_link_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_promotion_link_platform CHECK (platform IN ('ANDROID','IOS','WEB','OTHER')),
  CONSTRAINT ck_ocpx_promotion_link_type CHECK (link_type IN ('LANDING','DOWNLOAD','DEEPLINK','OTHER')),
  CONSTRAINT ck_ocpx_promotion_link_status CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: advertiser promotion link';

CREATE TABLE IF NOT EXISTS scx_ocpx_media (
  id VARCHAR(32) NOT NULL,
  media_code VARCHAR(64) NOT NULL,
  media_name VARCHAR(128) NOT NULL,
  adapter_code VARCHAR(64) NOT NULL,
  auth_type VARCHAR(16) NOT NULL DEFAULT 'NONE',
  credential_config_key VARCHAR(128) NULL,
  credential_version BIGINT NULL,
  ingress_uri VARCHAR(255) NOT NULL,
  macro_params VARCHAR(4000) NULL,
  callback_url VARCHAR(2000) NULL,
  callback_host_allowlist VARCHAR(2000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_media_code (media_code,active_marker),
  UNIQUE KEY uk_ocpx_media_name (media_name,active_marker),
  CONSTRAINT ck_ocpx_media_auth CHECK (auth_type IN ('NONE','TOKEN','SIGNATURE')),
  CONSTRAINT ck_ocpx_media_status CHECK (status IN ('ACTIVE','DISABLED')),
  CONSTRAINT ck_ocpx_media_credential CHECK (
    (auth_type='NONE' AND credential_config_key IS NULL AND credential_version IS NULL) OR
    (auth_type<>'NONE' AND credential_config_key IS NOT NULL AND credential_version>=1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: downstream media';

CREATE TABLE IF NOT EXISTS scx_ocpx_monitor_task (
  id VARCHAR(32) NOT NULL,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  operator_user_id VARCHAR(64) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  config_version BIGINT NOT NULL DEFAULT 1,
  archived_at DATETIME(3) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_marker TINYINT GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_monitor_task_code (task_code,active_marker),
  KEY idx_ocpx_monitor_task_dimensions (advertiser_id,promotion_link_id,media_id,status),
  CONSTRAINT fk_ocpx_monitor_task_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_monitor_task_link_owner FOREIGN KEY (advertiser_id,promotion_link_id)
    REFERENCES scx_ocpx_promotion_link(advertiser_id,id),
  CONSTRAINT fk_ocpx_monitor_task_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_monitor_task_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_ocpx_monitor_task_status CHECK (status IN ('ACTIVE','PAUSED','ARCHIVED')),
  CONSTRAINT ck_ocpx_monitor_task_config_version CHECK (config_version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: advertiser-link-media monitoring task';

CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log (
  id VARCHAR(32) NOT NULL,
  flow_type VARCHAR(16) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  source_event_id VARCHAR(128) NULL,
  event_code VARCHAR(64) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  result_status VARCHAR(16) NOT NULL,
  partner_http_status INT NULL,
  message VARCHAR(500) NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  event_time DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ocpx_flow_log_time (event_time,flow_type,result_status),
  KEY idx_ocpx_flow_log_dimensions (advertiser_id,promotion_link_id,media_id,monitor_task_id,event_time),
  KEY idx_ocpx_flow_log_event (event_id,create_time),
  CONSTRAINT fk_ocpx_flow_log_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_flow_log_promotion_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_flow_log_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_flow_log_monitor_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_flow_log_type CHECK (flow_type IN ('FEEDBACK','CALLBACK')),
  CONSTRAINT ck_ocpx_flow_log_status CHECK (result_status IN ('SUCCESS','FAILED')),
  CONSTRAINT ck_ocpx_flow_log_duration CHECK (duration_ms>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: sanitized Feedback and Callback flow';

CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_key_req VARCHAR(20) NULL, time_key_res VARCHAR(20) NULL,
  time_hour_res VARCHAR(20) NULL, date_res VARCHAR(20) NULL,
  channel_id VARCHAR(32) NULL, dsp_id VARCHAR(32) NULL,
  monitor_id VARCHAR(32) NULL, link_id VARCHAR(32) NULL,
  userid VARCHAR(128) NULL, user_type VARCHAR(64) NULL,
  adv_id VARCHAR(128) NULL, group_id VARCHAR(128) NULL,
  plan_id VARCHAR(128) NULL, creative_id VARCHAR(128) NULL,
  imp_id VARCHAR(128) NULL, clk_id VARCHAR(128) NULL, req_id VARCHAR(128) NULL,
  ua VARCHAR(2000) NULL, ip VARCHAR(64) NULL, device_os VARCHAR(32) NULL,
  device_did VARCHAR(128) NULL, device_oaid VARCHAR(128) NULL,
  device_dpid VARCHAR(128) NULL, device_mac VARCHAR(128) NULL,
  device_idfa VARCHAR(128) NULL, device_caid VARCHAR(128) NULL,
  device_caid1 VARCHAR(128) NULL, callback_url VARCHAR(3000) NULL,
  ts VARCHAR(32) NULL, ts_ms VARCHAR(32) NULL, base_price DECIMAL(30,0) NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_feedback_req (req_id),
  KEY idx_ocpx_feedback_route (channel_id,dsp_id,monitor_id,link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: legacy-compatible OCPX feedback detail';

CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback_day LIKE scx_ocpx_log_feedback;

CREATE TABLE IF NOT EXISTS scx_ocpx_log_callback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_key_req VARCHAR(20) NULL, time_key_res VARCHAR(20) NULL,
  time_hour_res VARCHAR(20) NULL, date_res VARCHAR(20) NULL,
  channel_id VARCHAR(32) NULL, dsp_id VARCHAR(32) NULL,
  monitor_id VARCHAR(32) NULL, link_id VARCHAR(32) NULL,
  adv_id VARCHAR(128) NULL, group_id VARCHAR(128) NULL,
  plan_id VARCHAR(128) NULL, creative_id VARCHAR(128) NULL,
  imp_id VARCHAR(128) NULL, clk_id VARCHAR(128) NULL, req_id VARCHAR(128) NULL,
  ua VARCHAR(2000) NULL, ip VARCHAR(64) NULL, device_os VARCHAR(32) NULL,
  device_did VARCHAR(128) NULL, device_oaid VARCHAR(128) NULL,
  device_dpid VARCHAR(128) NULL, device_mac VARCHAR(128) NULL,
  device_idfa VARCHAR(128) NULL, device_caid VARCHAR(128) NULL,
  device_caid1 VARCHAR(128) NULL, ts VARCHAR(32) NULL, ts_ms VARCHAR(32) NULL,
  userid VARCHAR(128) NULL, user_type VARCHAR(64) NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_callback_req (req_id),
  KEY idx_ocpx_callback_route (channel_id,dsp_id,monitor_id,link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: legacy-compatible OCPX callback detail';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat_temp (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  channel_id VARCHAR(32) NOT NULL, dsp_id VARCHAR(32) NOT NULL,
  app_id INT NULL, slot_id INT NULL, dsp_slot_id INT NULL,
  monitor_id VARCHAR(32) NOT NULL, link_id VARCHAR(32) NOT NULL,
  req_smt INT NOT NULL DEFAULT 0, res_smt INT NOT NULL DEFAULT 0,
  uni_win_smt INT NOT NULL DEFAULT 0, show_smt INT NOT NULL DEFAULT 0,
  uni_show_smt INT NOT NULL DEFAULT 0, clk_smt INT NOT NULL DEFAULT 0,
  uni_clk_smt INT NOT NULL DEFAULT 0, activate INT NOT NULL DEFAULT 0,
  evoke INT NOT NULL DEFAULT 0, login INT NOT NULL DEFAULT 0,
  register INT NOT NULL DEFAULT 0, payment INT NOT NULL DEFAULT 0,
  customer_acquisition INT NOT NULL DEFAULT 0, next_stay INT NOT NULL DEFAULT 0,
  visits INT NOT NULL DEFAULT 0, authentication INT NOT NULL DEFAULT 0,
  place_order INT NOT NULL DEFAULT 0, finish_the_order INT NOT NULL DEFAULT 0,
  base_price BIGINT NOT NULL DEFAULT 0, win_price BIGINT NOT NULL DEFAULT 0,
  mark TINYINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_report_temp_group (mark,create_time),
  KEY idx_ocpx_report_temp_route (channel_id,dsp_id,monitor_id,link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AGGREGATE: legacy-compatible OCPX temporary report material';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  channel_id VARCHAR(32) NOT NULL, dsp_id VARCHAR(32) NOT NULL,
  app_id INT NULL, slot_id INT NULL, dsp_slot_id INT NULL,
  monitor_id VARCHAR(32) NOT NULL, link_id VARCHAR(32) NOT NULL,
  req_smt INT NOT NULL DEFAULT 0, res_smt INT NOT NULL DEFAULT 0,
  uni_win_smt INT NOT NULL DEFAULT 0, show_smt INT NOT NULL DEFAULT 0,
  uni_show_smt INT NOT NULL DEFAULT 0, clk_smt INT NOT NULL DEFAULT 0,
  uni_clk_smt INT NOT NULL DEFAULT 0, activate INT NOT NULL DEFAULT 0,
  evoke INT NOT NULL DEFAULT 0, login INT NOT NULL DEFAULT 0,
  register INT NOT NULL DEFAULT 0, payment INT NOT NULL DEFAULT 0,
  customer_acquisition INT NOT NULL DEFAULT 0, next_stay INT NOT NULL DEFAULT 0,
  visits INT NOT NULL DEFAULT 0, authentication INT NOT NULL DEFAULT 0,
  place_order INT NOT NULL DEFAULT 0, finish_the_order INT NOT NULL DEFAULT 0,
  base_price BIGINT NOT NULL DEFAULT 0, win_price BIGINT NOT NULL DEFAULT 0,
  mark TINYINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_report_time (time_hour),
  KEY idx_ocpx_report_route (channel_id,dsp_id,monitor_id,link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AGGREGATE: legacy-compatible OCPX report material';

-- Preserve the original scx-system management navigation and permissions.
-- This block only restores menu metadata and administrator grants; it does not
-- replace or modify the scx-system user, role, department, dictionary or log
-- implementations and data.
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

UPDATE sys_menu
SET del_flag = '1', update_time = NOW()
WHERE id IN ('menu_system','menu_user');

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

-- Seed only the retained advertising menus below.
INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_home','经营总览','/ocpx/dashboard','0','lucide:layout-dashboard','ocpx/dashboard/index','ocpx:dashboard:view',1,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_advertiser','广告主管理','/ocpx/advertiser','0','lucide:building-2','ocpx/advertiser/index','ocpx:advertiser:view',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_promotion_link','推广链接','/ocpx/promotion-link','0','lucide:link','ocpx/promotion-link/index','ocpx:promotion-link:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_media','媒体管理','/ocpx/media','0','lucide:radio-tower','ocpx/media/index','ocpx:media:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_monitor_task','监测任务','/ocpx/monitor-task','0','lucide:list-checks','ocpx/monitor-task/index','ocpx:monitor-task:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report','报表中心','/ocpx/report','0','lucide:chart-column','ocpx/report/index','ocpx:report:view',50,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_flow_log','流程日志','/ocpx/flow-log','0','lucide:scroll-text','ocpx/flow-log/index','ocpx:flow-log:view',60,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_add','新增广告主','','menu_ocpx_advertiser','ocpx:advertiser:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_edit','编辑广告主','','menu_ocpx_advertiser','ocpx:advertiser:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_status','变更广告主状态','','menu_ocpx_advertiser','ocpx:advertiser:status',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_archive','归档广告主','','menu_ocpx_advertiser','ocpx:advertiser:archive',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_add','新增推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_edit','编辑推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_status','变更推广链接状态','','menu_ocpx_promotion_link','ocpx:promotion-link:status',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_archive','归档推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:archive',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_validate','校验推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:validate',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_add','新增媒体','','menu_ocpx_media','ocpx:media:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_edit','编辑媒体','','menu_ocpx_media','ocpx:media:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_status','变更媒体状态','','menu_ocpx_media','ocpx:media:status',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_archive','归档媒体','','menu_ocpx_media','ocpx:media:archive',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_preflight','媒体配置预检','','menu_ocpx_media','ocpx:media:preflight',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_connectivity','媒体连通性检查','','menu_ocpx_media','ocpx:media:connectivity',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_add','新增监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_edit','编辑监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_status','暂停恢复监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:status',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_archive','归档监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:archive',40,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v96_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON (m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.id<>'menu_ocpx_system';
