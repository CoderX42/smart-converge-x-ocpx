-- Forward-only OCPX creative management and configurable billing.
--
-- Back up and preview the target database before applying this migration.
-- V110 is immutable and already owns advertiser manual deductions.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v111_creative_billing$$
CREATE PROCEDURE preflight_v111_creative_billing()
BEGIN
  DECLARE invalid_active_task_count BIGINT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_active_task_count
  FROM scx_ocpx_monitor_task
  WHERE is_delete=0 AND (unit_price IS NULL OR unit_price<=0);

  IF invalid_active_task_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V111 blocked: active monitor tasks require a positive unit_price before CPC backfill';
  END IF;
END$$
CALL preflight_v111_creative_billing()$$
DROP PROCEDURE preflight_v111_creative_billing$$
DELIMITER ;

CREATE TABLE scx_ocpx_creative (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  creative_name VARCHAR(128) NOT NULL,
  creative_type TINYINT UNSIGNED NOT NULL DEFAULT 1,
  asset_url VARCHAR(2000) NOT NULL,
  source_material_id VARCHAR(32) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  file_size BIGINT NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  is_delete TINYINT(1) NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_creative_owner (advertiser_id,id),
  UNIQUE KEY uk_ocpx_creative_active_name
    (advertiser_id,creative_name,(IF(is_delete=0,1,NULL))),
  KEY idx_ocpx_creative_advertiser (advertiser_id,status,is_delete,update_time),
  KEY idx_ocpx_creative_checksum (advertiser_id,checksum_sha256,is_delete),
  KEY idx_ocpx_creative_material (source_material_id),
  CONSTRAINT fk_ocpx_creative_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_creative_material FOREIGN KEY (source_material_id)
    REFERENCES sys_material(id),
  CONSTRAINT ck_ocpx_creative_type CHECK (creative_type=1),
  CONSTRAINT ck_ocpx_creative_status CHECK (status IN (0,1)),
  CONSTRAINT ck_ocpx_creative_asset CHECK (
    file_size>0 AND width>0 AND height>0
    AND width<=8192 AND height<=8192
    AND CAST(width AS UNSIGNED)*CAST(height AS UNSIGNED)<=40000000
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='BUSINESS: advertiser-owned image creative';

CREATE TABLE scx_ocpx_creative_promotion_link (
  advertiser_id VARCHAR(32) NOT NULL,
  creative_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (creative_id,promotion_link_id),
  KEY idx_ocpx_creative_link_owner (advertiser_id,promotion_link_id,creative_id),
  CONSTRAINT fk_ocpx_creative_link_creative FOREIGN KEY (advertiser_id,creative_id)
    REFERENCES scx_ocpx_creative(advertiser_id,id),
  CONSTRAINT fk_ocpx_creative_link_promotion FOREIGN KEY (advertiser_id,promotion_link_id)
    REFERENCES scx_ocpx_promotion_link(advertiser_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='RELATION: creative to promotion-link many-to-many binding';

ALTER TABLE scx_ocpx_monitor_task
  ADD COLUMN billing_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER media_id,
  ADD COLUMN billing_type TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER billing_enabled,
  ADD CONSTRAINT ck_ocpx_monitor_task_billing_enabled CHECK (billing_enabled IN (0,1)),
  ADD CONSTRAINT ck_ocpx_monitor_task_billing_type CHECK (billing_type IN (1,2)),
  ADD CONSTRAINT ck_ocpx_monitor_task_billing_price CHECK (billing_enabled=0 OR unit_price>0);

UPDATE scx_ocpx_monitor_task
SET billing_enabled=CASE WHEN is_delete=0 THEN 1 ELSE 0 END,
    billing_type=1;

CREATE TABLE scx_ocpx_billing_config_audit (
  id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  action_type VARCHAR(16) NOT NULL,
  before_config JSON NULL,
  after_config JSON NOT NULL,
  operator_user_id VARCHAR(64) NULL,
  occurred_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ocpx_billing_audit_task_time (monitor_task_id,occurred_at),
  KEY idx_ocpx_billing_audit_advertiser_time (advertiser_id,occurred_at),
  CONSTRAINT fk_ocpx_billing_audit_task FOREIGN KEY (monitor_task_id)
    REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_billing_audit_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_billing_audit_action CHECK (action_type IN ('CREATE','UPDATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='AUDIT: immutable monitor-task billing configuration history';

ALTER TABLE scx_ocpx_event_fact
  ADD COLUMN billing_enabled_snapshot TINYINT(1) NULL AFTER target_conversion,
  ADD COLUMN billing_type_snapshot TINYINT UNSIGNED NULL AFTER billing_enabled_snapshot,
  ADD COLUMN billing_unit_price_snapshot DECIMAL(20,6) NULL AFTER billing_type_snapshot,
  ADD COLUMN conversion_event_snapshot VARCHAR(32) NULL AFTER billing_unit_price_snapshot,
  ADD COLUMN billing_reason_code VARCHAR(64) NULL AFTER billing_status,
  ADD CONSTRAINT ck_ocpx_event_fact_billing_enabled_snapshot
    CHECK (billing_enabled_snapshot IS NULL OR billing_enabled_snapshot IN (0,1)),
  ADD CONSTRAINT ck_ocpx_event_fact_billing_type_snapshot
    CHECK (billing_type_snapshot IS NULL OR billing_type_snapshot IN (1,2)),
  ADD CONSTRAINT ck_ocpx_event_fact_billing_price_snapshot
    CHECK (billing_unit_price_snapshot IS NULL OR billing_unit_price_snapshot>=0);

ALTER TABLE scx_ocpx_billing_entry
  DROP CHECK ck_ocpx_billing_type,
  ADD CONSTRAINT ck_ocpx_billing_type CHECK (
    entry_type IN ('RECHARGE','MANUAL_DEBIT','CLICK_CHARGE','CONVERSION_CHARGE')
  ),
  COMMENT='LEDGER: immutable advertiser recharge, manual debit, click and conversion charge entries';

ALTER TABLE scx_ocpx_hourly_metric
  ADD COLUMN rejected_conversion_count BIGINT NOT NULL DEFAULT 0 AFTER rejected_click_count,
  DROP CHECK ck_ocpx_hourly_nonnegative,
  ADD CONSTRAINT ck_ocpx_hourly_nonnegative CHECK (
    impression_count>=0 AND click_count>=0 AND target_conversion_count>=0
    AND spend_amount>=0 AND currency_amount>=0
    AND rejected_click_count>=0 AND rejected_conversion_count>=0
  );

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_creative','创意管理','/ocpx/creative','0','lucide:images',
   'ocpx/creative/index','ocpx:creative:view',25,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),
  type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_creative_add','新增创意','','menu_ocpx_creative','ocpx:creative:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_edit','编辑创意','','menu_ocpx_creative','ocpx:creative:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_delete','删除创意','','menu_ocpx_creative','ocpx:creative:delete',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_bind','绑定推广链接','','menu_ocpx_creative','ocpx:creative:bind',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_billing_entry_view','查看账务流水','','menu_ocpx_advertiser','ocpx:billing-entry:view',45,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),
  update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v111_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),26)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id='menu_ocpx_creative'
  OR m.permission IN (
    'ocpx:creative:add','ocpx:creative:edit','ocpx:creative:delete',
    'ocpx:creative:bind','ocpx:billing-entry:view'
  )
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v111_adv_',LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),22)),
       'role_advertiser',m.id
FROM sys_menu m
WHERE m.del_flag='0' AND (
  m.id='menu_ocpx_creative'
  OR m.permission IN (
    'ocpx:creative:add','ocpx:creative:edit','ocpx:creative:delete',
    'ocpx:creative:bind','ocpx:billing-entry:view'
  )
);

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE rm.role_id='role_advertiser'
  AND m.permission IN ('ocpx:monitor-task:billing','ocpx:advertiser:recharge','ocpx:advertiser:deduct');
