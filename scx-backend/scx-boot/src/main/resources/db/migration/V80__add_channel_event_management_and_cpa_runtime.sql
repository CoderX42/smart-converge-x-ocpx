-- V80: channel-scoped event dictionaries, monitor bindings, immutable publish snapshots,
-- strict daily callback quota and CPA accounting support.
-- Forward-only and repeat-safe. This file is never auto-scanned by init-database.sh.

CREATE TABLE IF NOT EXISTS scx_adx_event_definition (
  id VARCHAR(32) NOT NULL,
  category VARCHAR(24) NOT NULL COMMENT 'UPSTREAM,CONVERSION,MEDIA',
  channel_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Empty only for global conversion events',
  event_code VARCHAR(64) NOT NULL,
  event_name VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  built_in TINYINT(1) NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_event_scope_code (category,channel_id,event_code),
  KEY idx_scx_adx_event_filter (category,channel_id,status,sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADX managed event dictionary';

CREATE TABLE IF NOT EXISTS scx_adx_channel_event_mapping (
  id VARCHAR(32) NOT NULL,
  channel_id VARCHAR(32) NOT NULL,
  upstream_event_id VARCHAR(32) NOT NULL,
  conversion_event_id VARCHAR(32) NOT NULL,
  media_event_id VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(500) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_mapping_upstream (channel_id,upstream_event_id),
  KEY idx_scx_adx_mapping_target (conversion_event_id,media_event_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel upstream to conversion and media event mapping';

CREATE TABLE IF NOT EXISTS scx_adx_monitor_event_binding (
  id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  event_mapping_id VARCHAR(32) NOT NULL,
  assessment_unit_price DECIMAL(20,6) NOT NULL DEFAULT 0.000000,
  daily_limit INT NOT NULL DEFAULT 0 COMMENT '0 means unlimited',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_monitor_event (monitor_id,event_mapping_id),
  KEY idx_scx_adx_monitor_binding_mapping (event_mapping_id,monitor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Draft monitor event binding and CPA assessment';

CREATE TABLE IF NOT EXISTS scx_adx_route_event_snapshot (
  id VARCHAR(32) NOT NULL,
  monitor_id VARCHAR(32) NOT NULL,
  route_version BIGINT NOT NULL,
  event_mapping_id VARCHAR(32) NOT NULL,
  upstream_event_code VARCHAR(64) NOT NULL,
  upstream_event_name VARCHAR(100) NOT NULL,
  conversion_event_code VARCHAR(64) NOT NULL,
  conversion_event_name VARCHAR(100) NOT NULL,
  media_event_code VARCHAR(64) NOT NULL,
  media_event_name VARCHAR(100) NOT NULL,
  assessment_unit_price DECIMAL(20,6) NOT NULL,
  daily_limit INT NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_adx_route_event (monitor_id,route_version,upstream_event_code),
  KEY idx_scx_adx_route_event_version (monitor_id,route_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable published route event snapshot';

CREATE TABLE IF NOT EXISTS scx_ocpx_event_daily_quota (
  monitor_id VARCHAR(32) NOT NULL,
  upstream_event_code VARCHAR(64) NOT NULL,
  business_date DATE NOT NULL,
  queued_count INT NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (monitor_id,upstream_event_code,business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Strict Asia Shanghai media callback daily quota';

DROP PROCEDURE IF EXISTS scx_v80_add_ocpx_event_columns;
DELIMITER $$
CREATE PROCEDURE scx_v80_add_ocpx_event_columns()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='event_snapshot_version') THEN
    ALTER TABLE scx_adx_monitor_link ADD COLUMN event_snapshot_version INT NOT NULL DEFAULT 0 AFTER click_dedup_window_seconds;
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
CALL scx_v80_add_ocpx_event_columns();
DROP PROCEDURE scx_v80_add_ocpx_event_columns;

-- Global conversion dictionary.
INSERT INTO scx_adx_event_definition
  (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
VALUES
  (LEFT(SHA2('CONVERSION||ACTIVATE',256),32),'CONVERSION','','ACTIVATE','激活','ACTIVE',10,1,0),
  (LEFT(SHA2('CONVERSION||REGISTER',256),32),'CONVERSION','','REGISTER','注册','ACTIVE',20,1,0),
  (LEFT(SHA2('CONVERSION||NEXT_DAY_RETENTION',256),32),'CONVERSION','','NEXT_DAY_RETENTION','次留','ACTIVE',30,1,0),
  (LEFT(SHA2('CONVERSION||REACTIVATION',256),32),'CONVERSION','','REACTIVATION','拉活/唤醒/唤起/唤端','ACTIVE',40,1,0),
  (LEFT(SHA2('CONVERSION||FIRST_ACTIVATION',256),32),'CONVERSION','','FIRST_ACTIVATION','首活首唤','ACTIVE',50,1,0),
  (LEFT(SHA2('CONVERSION||FIRST_ORDER',256),32),'CONVERSION','','FIRST_ORDER','首单/首购','ACTIVE',60,1,0),
  (LEFT(SHA2('CONVERSION||COMPLETE_ORDER',256),32),'CONVERSION','','COMPLETE_ORDER','完单','ACTIVE',70,1,0),
  (LEFT(SHA2('CONVERSION||PAYMENT',256),32),'CONVERSION','','PAYMENT','购买付费','ACTIVE',80,1,0),
  (LEFT(SHA2('CONVERSION||ORDER',256),32),'CONVERSION','','ORDER','下单','ACTIVE',90,1,0),
  (LEFT(SHA2('CONVERSION||VIDEO_COMPLETE_90S',256),32),'CONVERSION','','VIDEO_COMPLETE_90S','完播90秒','ACTIVE',100,1,0),
  (LEFT(SHA2('CONVERSION||KEY_ACTION',256),32),'CONVERSION','','KEY_ACTION','自定义事件/关键行为','ACTIVE',110,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D2',256),32),'CONVERSION','','RETENTION_D2','2日留存','ACTIVE',120,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D3',256),32),'CONVERSION','','RETENTION_D3','3日留存','ACTIVE',130,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D4',256),32),'CONVERSION','','RETENTION_D4','4日留存','ACTIVE',140,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D5',256),32),'CONVERSION','','RETENTION_D5','5日留存','ACTIVE',150,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D6',256),32),'CONVERSION','','RETENTION_D6','6日留存','ACTIVE',160,1,0),
  (LEFT(SHA2('CONVERSION||RETENTION_D7',256),32),'CONVERSION','','RETENTION_D7','7日留存','ACTIVE',170,1,0),
  (LEFT(SHA2('CONVERSION||FORM_SUBMIT',256),32),'CONVERSION','','FORM_SUBMIT','表单提交','ACTIVE',180,1,0),
  (LEFT(SHA2('CONVERSION||KEY_PAGE_VIEW',256),32),'CONVERSION','','KEY_PAGE_VIEW','关键页面行为/关键页面浏览','ACTIVE',190,1,0),
  (LEFT(SHA2('CONVERSION||AUTHENTICATION',256),32),'CONVERSION','','AUTHENTICATION','授信/授权','ACTIVE',200,1,0),
  (LEFT(SHA2('CONVERSION||RECALL',256),32),'CONVERSION','','RECALL','拉回','ACTIVE',210,1,0),
  (LEFT(SHA2('CONVERSION||UNKNOWN',256),32),'CONVERSION','','UNKNOWN','未知','ACTIVE',220,1,0)
ON DUPLICATE KEY UPDATE id=scx_adx_event_definition.id;

-- Every current channel receives the complete upstream dictionary.
INSERT INTO scx_adx_event_definition
  (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
SELECT LEFT(SHA2(CONCAT('UPSTREAM|',c.id,'|',d.event_code),256),32),'UPSTREAM',c.id,d.event_code,d.event_name,
       'ACTIVE',d.sort_order,1,0
FROM scx_adx_channel c
JOIN (
  SELECT 'ACTIVATE' event_code,'激活' event_name,10 sort_order UNION ALL
  SELECT 'ACQUISITION','拉新',20 UNION ALL SELECT 'REGISTER','注册',30 UNION ALL
  SELECT 'FIRST_REACTIVATION','首唤',40 UNION ALL SELECT 'REACTIVATION_COMMON','拉活（常用）',50 UNION ALL
  SELECT 'ALIPAY_ASSIST_SUCCESS_5S','支付宝预算“助力成功&5s播放”',60 UNION ALL SELECT 'PAYMENT','付费',70 UNION ALL
  SELECT 'NEXT_DAY_RETENTION','次留',80 UNION ALL SELECT 'RETENTION_D3','3日留存',90 UNION ALL
  SELECT 'RETENTION_D4','4日留存',100 UNION ALL SELECT 'RETENTION_D5','5日留存',110 UNION ALL
  SELECT 'RETENTION_D6','6日留存',120 UNION ALL SELECT 'RETENTION_D7','7日留存',130 UNION ALL
  SELECT 'APP_VISIT','APP内访问',140 UNION ALL SELECT 'RECALL','召回',150 UNION ALL
  SELECT 'KEY_ACTION','关键行为',160 UNION ALL SELECT 'FIRST_ORDER','首购',170 UNION ALL
  SELECT 'REACTIVATION','拉活',180 UNION ALL SELECT 'VALID_EVOKE','有效唤端',190 UNION ALL
  SELECT 'ORDER','下单',200 UNION ALL SELECT 'LEAD','线索',210 UNION ALL
  SELECT 'BUDGET_UNDEFINED','预算未定义',220
) d ON 1=1
WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_adx_event_definition.id;

-- Every current channel receives the complete media dictionary.
INSERT INTO scx_adx_event_definition
  (id,category,channel_id,event_code,event_name,status,sort_order,built_in,deleted)
SELECT LEFT(SHA2(CONCAT('MEDIA|',c.id,'|',d.event_code),256),32),'MEDIA',c.id,d.event_code,d.event_name,
       'ACTIVE',d.sort_order,1,0
FROM scx_adx_channel c
JOIN (
  SELECT 'ACTIVATE' event_code,'激活' event_name,10 sort_order UNION ALL SELECT 'REGISTER','注册',20 UNION ALL
  SELECT 'NEXT_DAY_RETENTION','次留',30 UNION ALL SELECT 'REACTIVATION','拉活/唤醒/唤起/唤端',40 UNION ALL
  SELECT 'FIRST_ACTIVATION','首活首唤',50 UNION ALL SELECT 'FIRST_ORDER','首单/首购',60 UNION ALL
  SELECT 'COMPLETE_ORDER','完单',70 UNION ALL SELECT 'PAYMENT','购买/付费',80 UNION ALL SELECT 'ORDER','下单',90 UNION ALL
  SELECT 'VIDEO_COMPLETE_90S','完播90秒',100 UNION ALL SELECT 'KEY_ACTION','自定义事件/关键行为',110 UNION ALL
  SELECT 'RETENTION_D2','2日留存',120 UNION ALL SELECT 'RETENTION_D3','3日留存',130 UNION ALL
  SELECT 'RETENTION_D4','4日留存',140 UNION ALL SELECT 'RETENTION_D5','5日留存',150 UNION ALL
  SELECT 'RETENTION_D6','6日留存',160 UNION ALL SELECT 'RETENTION_D7','7日留存',170 UNION ALL
  SELECT 'FORM_SUBMIT','表单提交',180 UNION ALL SELECT 'KEY_PAGE_VIEW','关键页面行为/关键页面浏览',190 UNION ALL
  SELECT 'AUTHENTICATION','授信/授权',200 UNION ALL SELECT 'RECALL','拉回',210 UNION ALL SELECT 'UNKNOWN','未知',220
) d ON 1=1
WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_adx_event_definition.id;

-- Only four mappings are usable by default.
INSERT INTO scx_adx_channel_event_mapping
  (id,channel_id,upstream_event_id,conversion_event_id,media_event_id,status,remark,deleted)
SELECT LEFT(SHA2(CONCAT('MAPPING|',c.id,'|',x.event_code),256),32),c.id,u.id,v.id,m.id,'ACTIVE','系统默认映射',0
FROM scx_adx_channel c
JOIN (
  SELECT 'ACTIVATE' event_code UNION ALL SELECT 'REGISTER' UNION ALL SELECT 'PAYMENT' UNION ALL SELECT 'NEXT_DAY_RETENTION'
) x ON 1=1
JOIN scx_adx_event_definition u ON u.category='UPSTREAM' AND u.channel_id=c.id AND u.event_code=x.event_code
JOIN scx_adx_event_definition v ON v.category='CONVERSION' AND v.channel_id='' AND v.event_code=x.event_code
JOIN scx_adx_event_definition m ON m.category='MEDIA' AND m.channel_id=c.id AND m.event_code=x.event_code
WHERE c.deleted=0
ON DUPLICATE KEY UPDATE id=scx_adx_channel_event_mapping.id;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_adx_event','事件管理','/system/event','menu_ocpx_system','lucide:workflow','system/event/index','adx:event:read',55,'0','app_base',NOW(),NOW(),'0'),
  ('perm_adx_event_write','维护事件','', 'menu_adx_event',NULL,NULL,'adx:event:write',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v80_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('menu_adx_event','perm_adx_event_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
