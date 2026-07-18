-- SCX MVP-A 幂等业务基线：M1-M5 表、角色、菜单与权限。
SET NAMES utf8mb4;

SET @schema_name = DATABASE();
SET @statement = IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=@schema_name AND table_name='scx_media')=0,
  'CREATE TABLE scx_media (id VARCHAR(64) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT ''ACTIVE'', timezone VARCHAR(64) NOT NULL DEFAULT ''Asia/Shanghai'', settlement_currency VARCHAR(8) NOT NULL DEFAULT ''CNY'', version INT NOT NULL DEFAULT 0, create_by VARCHAR(64), update_by VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (id), UNIQUE KEY uk_scx_media_code (code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''SCX 媒体安全主体''',
  'SELECT 1');
PREPARE scx_mvp_statement FROM @statement;
EXECUTE scx_mvp_statement;
DEALLOCATE PREPARE scx_mvp_statement;

SET @statement = IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=@schema_name AND table_name='scx_user_subject_scope')=0,
  'CREATE TABLE scx_user_subject_scope (id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL, subject_type VARCHAR(32) NOT NULL, subject_id VARCHAR(64) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), UNIQUE KEY uk_scx_user_subject (user_id, subject_type, subject_id), KEY idx_scx_scope_subject (subject_type, subject_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''SCX 用户对象授权''',
  'SELECT 1');
PREPARE scx_mvp_statement FROM @statement;
EXECUTE scx_mvp_statement;
DEALLOCATE PREPARE scx_mvp_statement;

CREATE TABLE IF NOT EXISTS scx_partner (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  type VARCHAR(32) NOT NULL DEFAULT 'UPSTREAM', description VARCHAR(500), status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0, create_by VARCHAR(64), update_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_partner_media_code (media_id, code), KEY idx_scx_partner_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 上游合作方';

CREATE TABLE IF NOT EXISTS scx_product (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, partner_id VARCHAR(64) NOT NULL,
  code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, platform VARCHAR(32), description VARCHAR(500),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64), update_by VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_product_partner_code (partner_id, code),
  KEY idx_scx_product_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 推广产品';

CREATE TABLE IF NOT EXISTS scx_integration_document (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, type VARCHAR(32),
  document_version INT NOT NULL DEFAULT 1, storage_type VARCHAR(16), object_key VARCHAR(500), external_url VARCHAR(1000),
  content_hash VARCHAR(128), status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64), update_by VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_scx_document_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 受控接入文档元数据';

CREATE TABLE IF NOT EXISTS scx_media_channel (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  document_id VARCHAR(64), description VARCHAR(500), status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64), update_by VARCHAR(64), create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_channel_media_code (media_id, code), KEY idx_scx_channel_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 媒体渠道';

CREATE TABLE IF NOT EXISTS scx_tracking_link (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  product_id VARCHAR(64) NOT NULL, partner_id VARCHAR(64) NOT NULL, document_id VARCHAR(64), device_type VARCHAR(16) NOT NULL,
  redirect_enabled TINYINT(1) NOT NULL DEFAULT 0, target_url VARCHAR(1000) NOT NULL, download_url VARCHAR(1000),
  template_text TEXT, config_version INT NOT NULL DEFAULT 1, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  archived_at DATETIME, version INT NOT NULL DEFAULT 0, create_by VARCHAR(64), update_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_link_code (code), KEY idx_scx_link_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 版本化追踪链接';

CREATE TABLE IF NOT EXISTS scx_link_event_mapping (
  id VARCHAR(64) NOT NULL, link_id VARCHAR(64) NOT NULL, config_version INT NOT NULL,
  internal_event VARCHAR(32) NOT NULL, external_event VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_link_event_version (link_id, config_version, internal_event)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 链接事件映射';

CREATE TABLE IF NOT EXISTS scx_tracking_task (
  id VARCHAR(64) NOT NULL, media_id VARCHAR(64) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
  link_id VARCHAR(64) NOT NULL, channel_id VARCHAR(64) NOT NULL, impression_enabled TINYINT(1) NOT NULL DEFAULT 0,
  dedup_window_sec INT NOT NULL DEFAULT 0, attribution_window_hours INT NOT NULL DEFAULT 168,
  callback_target_ref VARCHAR(128) NOT NULL DEFAULT 'sandbox', status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  archived_at DATETIME, version INT NOT NULL DEFAULT 0, create_by VARCHAR(64), update_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_task_code (code), KEY idx_scx_task_media_status (media_id, status),
  KEY idx_scx_task_link_channel (link_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 监测任务';

CREATE TABLE IF NOT EXISTS scx_raw_event (
  id VARCHAR(64) NOT NULL, source VARCHAR(64) NOT NULL, event_id VARCHAR(128) NOT NULL, task_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL, business_event_key_hash VARCHAR(128), occurred_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), device_key_hash VARCHAR(128) NOT NULL,
  payload_hash VARCHAR(128) NOT NULL, payload_json JSON, status VARCHAR(24) NOT NULL DEFAULT 'ACCEPTED',
  PRIMARY KEY (id), UNIQUE KEY uk_scx_raw_source_event (source, event_id),
  KEY idx_scx_raw_task_type_time (task_id, event_type, occurred_at), KEY idx_scx_raw_device_time (device_key_hash, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 不可变原始事件';

CREATE TABLE IF NOT EXISTS scx_conversion_identity (
  id VARCHAR(64) NOT NULL, source VARCHAR(64) NOT NULL, task_id VARCHAR(64) NOT NULL, event_type VARCHAR(32) NOT NULL,
  business_event_key_hash VARCHAR(128) NOT NULL, canonical_event_id VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_scx_conversion_business (source, task_id, event_type, business_event_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 转化业务判重身份';

CREATE TABLE IF NOT EXISTS scx_conversion_dedup_result (
  conversion_event_id VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, reason_code VARCHAR(64) NOT NULL,
  canonical_event_id VARCHAR(64), processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (conversion_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 转化判重结果';

CREATE TABLE IF NOT EXISTS scx_click_dedup_result (
  click_event_id VARCHAR(64) NOT NULL, rule_version VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64) NOT NULL, canonical_click_event_id VARCHAR(64), processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (click_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 点击设备去重结果';

CREATE TABLE IF NOT EXISTS scx_attribution_result (
  id VARCHAR(64) NOT NULL, conversion_event_id VARCHAR(64) NOT NULL, revision INT NOT NULL DEFAULT 1,
  click_event_id VARCHAR(64), rule_version VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL,
  reason_code VARCHAR(64) NOT NULL, current_flag TINYINT(1) NOT NULL DEFAULT 1, supersedes_id VARCHAR(64),
  attributed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_attr_conversion_revision (conversion_event_id, revision),
  KEY idx_scx_attr_current (conversion_event_id, current_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 可解释归因结果';

CREATE TABLE IF NOT EXISTS scx_event_outbox (
  id VARCHAR(64) NOT NULL, aggregate_id VARCHAR(64) NOT NULL, event_type VARCHAR(64) NOT NULL,
  payload_json JSON NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3), create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), sent_at DATETIME(3),
  PRIMARY KEY (id), KEY idx_scx_outbox_dispatch (status, next_retry_at, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 可靠消息 Outbox';

CREATE TABLE IF NOT EXISTS scx_callback_job (
  id VARCHAR(64) NOT NULL, attribution_result_id VARCHAR(64) NOT NULL, action_type VARCHAR(24) NOT NULL DEFAULT 'ORIGINAL',
  target_ref VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempts INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3), last_error_code VARCHAR(64), last_error_message VARCHAR(500),
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_callback_action (attribution_result_id, action_type, target_ref),
  KEY idx_scx_callback_run (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 沙箱回传任务';

CREATE TABLE IF NOT EXISTS scx_report_run (
  id VARCHAR(64) NOT NULL, report_date DATE NOT NULL, timezone VARCHAR(64) NOT NULL,
  input_cutoff_received_at DATETIME(3) NOT NULL, input_snapshot_hash VARCHAR(128) NOT NULL,
  rule_version VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL, started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3), PRIMARY KEY (id), KEY idx_scx_report_run_date (report_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 报表输入快照';

CREATE TABLE IF NOT EXISTS scx_report_daily (
  id VARCHAR(64) NOT NULL, report_run_id VARCHAR(64) NOT NULL, report_date DATE NOT NULL, timezone VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL, partner_id VARCHAR(64) NOT NULL, product_id VARCHAR(64) NOT NULL,
  link_id VARCHAR(64) NOT NULL, task_id VARCHAR(64) NOT NULL, unique_click_count BIGINT NOT NULL DEFAULT 0,
  eligible_click_count BIGINT NOT NULL DEFAULT 0, deduplicated_click_count BIGINT NOT NULL DEFAULT 0,
  attributed_activate_count BIGINT NOT NULL DEFAULT 0, attributed_register_count BIGINT NOT NULL DEFAULT 0,
  attributed_pay_count BIGINT NOT NULL DEFAULT 0, revision INT NOT NULL DEFAULT 1,
  calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_scx_report_daily_grain (report_run_id, media_id, partner_id, product_id, link_id, task_id, revision),
  KEY idx_scx_report_daily_query (report_date, media_id, product_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 原子粒度日聚合';

-- 最小可运行管理员种子，密码 123456，首次登录必须修改。
INSERT INTO sys_dept (id,parent_id,dept_name,sort,status,del_flag,create_time,create_by)
SELECT '1','0','Smart Converge X',1,'0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE id='1');
INSERT INTO sys_role (id,role_name,role_code,role_desc,type,del_flag,create_time,create_by)
SELECT '1','超级管理员','ROLE_ADMIN','系统内置超级管理员','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id='1' OR role_code='ROLE_ADMIN');
INSERT INTO sys_role (id,role_name,role_code,role_desc,type,del_flag,create_time,create_by)
SELECT 'role_operator','广告运营','ROLE_OPERATOR','授权媒体范围内配置与查询','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id='role_operator' OR role_code='ROLE_OPERATOR');
INSERT INTO sys_role (id,role_name,role_code,role_desc,type,del_flag,create_time,create_by)
SELECT 'role_analyst','只读分析员','ROLE_ANALYST','授权媒体范围内只读查询','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id='role_analyst' OR role_code='ROLE_ANALYST');
INSERT INTO sys_user (id,username,password,nickname,dept_id,status,type,del_flag,create_time,create_by)
SELECT '1','system','$2a$10$9p1BH9TCqoxGG1BZe8.cZeUs7xytTbg6LUqjB5uSDbIYCeXDBYnR2','系统管理员','1','0','0','0',NOW(),'system'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id='1' OR username='system');
INSERT INTO sys_user_role (id,user_id,role_id)
SELECT '1','1','1' WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id='1' AND role_id='1');

INSERT INTO scx_media (id,code,name,status,timezone,settlement_currency)
SELECT 'media_sandbox','sandbox-media','沙箱媒体','ACTIVE','Asia/Shanghai','CNY'
WHERE NOT EXISTS (SELECT 1 FROM scx_media WHERE id='media_sandbox' OR code='sandbox-media');
INSERT INTO scx_user_subject_scope (id,user_id,subject_type,subject_id)
SELECT 'scope_system_sandbox','1','MEDIA','media_sandbox'
WHERE NOT EXISTS (SELECT 1 FROM scx_user_subject_scope WHERE user_id='1' AND subject_type='MEDIA' AND subject_id='media_sandbox');

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_delivery','投放配置','/delivery','0','lucide:send',NULL,85,'0','app_base',NOW(),'0'),
('menu_ad_master','主数据','/delivery/master-data','menu_ad_delivery','lucide:database','delivery/master-data/index',90,'0','app_base',NOW(),'0'),
('menu_ad_link','追踪链接','/delivery/tracking-link','menu_ad_delivery','lucide:link','delivery/tracking-link/index',80,'0','app_base',NOW(),'0'),
('menu_ad_task','监测任务','/delivery/tracking-task','menu_ad_delivery','lucide:list-checks','delivery/tracking-task/index',70,'0','app_base',NOW(),'0'),
('menu_ad_data','数据中心','/reporting','0','lucide:chart-no-axes-combined',NULL,75,'0','app_base',NOW(),'0'),
('menu_ad_daily','效果日报','/reporting/daily','menu_ad_data','lucide:chart-column','reporting/daily/index',90,'0','app_base',NOW(),'0'),
('menu_ad_events','事件归因','/reporting/event-detail','menu_ad_data','lucide:route','reporting/event-detail/index',80,'0','app_base',NOW(),'0'),
('menu_ad_jobs','回传任务','/operations/jobs','menu_ad_data','lucide:refresh-cw','operations/jobs/index',70,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_subject_media_read','查询媒体','subject:media:read','menu_ad_delivery',0,'1','app_base',NOW(),'0'),
('perm_ad_master_read','查询主数据','ad:master:read','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_master_write','编辑主数据','ad:master:write','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_master_status','变更主数据状态','ad:master:status','menu_ad_master',0,'1','app_base',NOW(),'0'),
('perm_ad_link_read','查询追踪链接','ad:link:read','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_link_write','编辑追踪链接','ad:link:write','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_link_status','变更链接状态','ad:link:status','menu_ad_link',0,'1','app_base',NOW(),'0'),
('perm_ad_task_read','查询监测任务','ad:task:read','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_task_write','编辑监测任务','ad:task:write','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_task_status','变更任务状态','ad:task:status','menu_ad_task',0,'1','app_base',NOW(),'0'),
('perm_ad_report_read','查询报表','ad:report:read','menu_ad_daily',0,'1','app_base',NOW(),'0'),
('perm_ad_event_read','查询事件明细','ad:event-detail:read','menu_ad_events',0,'1','app_base',NOW(),'0'),
('perm_ad_callback_replay','重放沙箱回传','ad:callback:replay','menu_ad_jobs',0,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id LIKE 'menu_ad_%' OR id LIKE 'perm_ad_%' OR id='perm_subject_media_read'
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu
WHERE id IN ('menu_ad_delivery','menu_ad_master','menu_ad_link','menu_ad_task','menu_ad_data','menu_ad_daily','menu_ad_events','menu_ad_jobs',
'perm_subject_media_read','perm_ad_master_read','perm_ad_master_write','perm_ad_master_status','perm_ad_link_read','perm_ad_link_write','perm_ad_link_status',
'perm_ad_task_read','perm_ad_task_write','perm_ad_task_status','perm_ad_report_read','perm_ad_event_read','perm_ad_callback_replay')
ON DUPLICATE KEY UPDATE id=VALUES(id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu
WHERE id IN ('menu_ad_data','menu_ad_daily','menu_ad_events','perm_subject_media_read','perm_ad_report_read','perm_ad_event_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
