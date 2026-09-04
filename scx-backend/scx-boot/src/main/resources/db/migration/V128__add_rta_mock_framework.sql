-- V128: add the disabled-by-default SCX RTA framework and Mock-only data path.
-- Forward-only migration. Back up the target database, preview DDL and obtain
-- approval before execution. No supplier endpoint, credential or binding is seeded.

ALTER TABLE scx_ocpx_monitor_task
  ADD COLUMN rta_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER media_id,
  ADD CONSTRAINT ck_ocpx_monitor_task_rta_enabled CHECK (rta_enabled IN (0,1));

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_profile (
  id VARCHAR(32) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  adapter_code VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  config_json JSON NOT NULL,
  version INT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_rta_profile_code (code),
  CONSTRAINT ck_ocpx_rta_profile_adapter CHECK (adapter_code='MOCK'),
  CONSTRAINT ck_ocpx_rta_profile_status CHECK (status IN ('DISABLED','ACTIVE')),
  CONSTRAINT ck_ocpx_rta_profile_version CHECK (version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='CONFIG: RTA decision profile without secrets';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_task_binding (
  id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  profile_id VARCHAR(32) NOT NULL,
  external_task_id VARCHAR(128) NOT NULL,
  external_product_id VARCHAR(128) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version INT NOT NULL DEFAULT 1,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_rta_binding_task_profile_external (monitor_task_id,profile_id,external_task_id),
  KEY idx_ocpx_rta_binding_scope (advertiser_id,monitor_task_id,enabled),
  CONSTRAINT fk_ocpx_rta_binding_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_rta_binding_profile FOREIGN KEY (profile_id) REFERENCES scx_ocpx_rta_profile(id),
  CONSTRAINT fk_ocpx_rta_binding_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_rta_binding_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_rta_binding_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT ck_ocpx_rta_binding_enabled CHECK (enabled IN (0,1)),
  CONSTRAINT ck_ocpx_rta_binding_version CHECK (version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='RELATION: monitor task to RTA external task binding';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_decision_fact (
  id VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  profile_id VARCHAR(32) NULL,
  adapter_code VARCHAR(32) NOT NULL,
  decision VARCHAR(8) NOT NULL,
  outcome_class VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  latency_ms BIGINT NOT NULL DEFAULT 0,
  matched_task_ids JSON NULL,
  received_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NOT NULL,
  metric_version INT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_rta_decision_identity (monitor_task_id,request_id),
  KEY idx_ocpx_rta_decision_scope_time (advertiser_id,received_at,monitor_task_id,profile_id),
  CONSTRAINT fk_ocpx_rta_decision_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_rta_decision_profile FOREIGN KEY (profile_id) REFERENCES scx_ocpx_rta_profile(id),
  CONSTRAINT ck_ocpx_rta_decision_value CHECK (decision IN ('ALLOW','DENY')),
  CONSTRAINT ck_ocpx_rta_decision_outcome CHECK (outcome_class IN ('SUCCESS','TIMEOUT','ERROR','REJECTED')),
  CONSTRAINT ck_ocpx_rta_decision_latency CHECK (latency_ms>=0),
  CONSTRAINT ck_ocpx_rta_decision_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='FACT: asynchronous RTA decision result';

CREATE TABLE IF NOT EXISTS scx_ocpx_rta_hourly_metric (
  id VARCHAR(32) NOT NULL,
  time_hour DATETIME(3) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  profile_id VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
  request_count BIGINT NOT NULL DEFAULT 0,
  allow_count BIGINT NOT NULL DEFAULT 0,
  deny_count BIGINT NOT NULL DEFAULT 0,
  timeout_count BIGINT NOT NULL DEFAULT 0,
  error_count BIGINT NOT NULL DEFAULT 0,
  allow_rate DECIMAL(12,8) NULL,
  metric_version INT NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_rta_hourly_metric_scope
    (time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id,profile_id),
  KEY idx_ocpx_rta_hourly_scope_time (advertiser_id,time_hour,monitor_task_id,profile_id),
  CONSTRAINT fk_ocpx_rta_metric_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_rta_metric_nonnegative CHECK (
    request_count>=0 AND allow_count>=0 AND deny_count>=0 AND timeout_count>=0 AND error_count>=0
    AND allow_count<=request_count
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='METRIC: asynchronous RTA hourly projection';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_rta','RTA 管理','/ocpx/rta','0','lucide:shield-check','ocpx/rta/index','ocpx:rta:view',42,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_rta_add','新增 RTA Profile','','menu_ocpx_rta','ocpx:rta:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_rta_edit','编辑 RTA Profile','','menu_ocpx_rta','ocpx:rta:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_rta_bind','配置 RTA 任务绑定','','menu_ocpx_rta','ocpx:rta:bind',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_rta_decision_view','查看 RTA 决策','','menu_ocpx_rta','ocpx:rta:decision:view',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_rta_report','查看 RTA 报表','','menu_ocpx_rta','ocpx:rta:report',50,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v128_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_ocpx_rta','perm_ocpx_rta_add','perm_ocpx_rta_edit','perm_ocpx_rta_bind',
   'perm_ocpx_rta_decision_view','perm_ocpx_rta_report')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v128_adv_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),50)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('menu_ocpx_rta','perm_ocpx_rta_bind','perm_ocpx_rta_decision_view','perm_ocpx_rta_report')
WHERE r.role_code='ROLE_ADVERTISER' AND r.del_flag='0';
