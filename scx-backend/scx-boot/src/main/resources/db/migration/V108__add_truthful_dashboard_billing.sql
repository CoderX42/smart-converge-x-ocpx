-- Forward-only truthful Dashboard and CPC accounting foundation.
--
-- This migration intentionally refuses legacy report rows because V108 does
-- not infer event identity or financial history from the non-idempotent legacy
-- aggregates. Preview the target database before applying it.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v108_truthful_dashboard$$
CREATE PROCEDURE preflight_v108_truthful_dashboard()
BEGIN
  DECLARE legacy_report_rows BIGINT DEFAULT 0;
  DECLARE active_admin_count INT DEFAULT 0;

  SELECT
    (SELECT COUNT(*) FROM scx_ocpx_report_mat) +
    (SELECT COUNT(*) FROM scx_ocpx_report_mat_temp)
  INTO legacy_report_rows;

  IF legacy_report_rows <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V108 blocked: legacy report rows require an explicit audited migration plan';
  END IF;

  SELECT COUNT(*) INTO active_admin_count
  FROM sys_role
  WHERE role_code='ROLE_ADMIN' AND del_flag='0';

  IF active_admin_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V108 blocked: exactly one active ROLE_ADMIN is required';
  END IF;
END$$
CALL preflight_v108_truthful_dashboard()$$
DROP PROCEDURE preflight_v108_truthful_dashboard$$
DELIMITER ;

UPDATE scx_ocpx_monitor_task SET unit_price=0 WHERE unit_price IS NULL;

ALTER TABLE scx_ocpx_monitor_task
  MODIFY COLUMN unit_price DECIMAL(20,6) NOT NULL DEFAULT 0,
  ADD COLUMN conversion_event VARCHAR(32) NOT NULL DEFAULT 'ACTIVATE' AFTER unit_price,
  ADD CONSTRAINT ck_ocpx_monitor_task_conversion_event CHECK (conversion_event IN (
    'ACTIVATE','EVOKE','LOGIN','REGISTER','PAYMENT','CUSTOMER_ACQUISITION',
    'NEXT_STAY','VISITS','AUTHENTICATION','ORDER','FINISH_THE_ORDER',
    'FIRST_ACTIVATION','FIRST_ORDER'
  ));

CREATE TABLE scx_ocpx_advertiser_account (
  advertiser_id VARCHAR(32) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  available_balance DECIMAL(20,6) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (advertiser_id),
  CONSTRAINT fk_ocpx_account_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_account_currency CHECK (currency='CNY'),
  CONSTRAINT ck_ocpx_account_balance CHECK (available_balance>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='ACCOUNT: authoritative advertiser available balance';

CREATE TABLE scx_ocpx_event_fact (
  id VARCHAR(32) NOT NULL,
  fact_key CHAR(64) NOT NULL,
  flow_type VARCHAR(16) NOT NULL,
  event_code VARCHAR(32) NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  target_conversion TINYINT(1) NOT NULL DEFAULT 0,
  spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  billing_status VARCHAR(40) NOT NULL DEFAULT 'NOT_APPLICABLE',
  received_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_event_fact_key (fact_key),
  UNIQUE KEY uk_ocpx_event_fact_identity
    (advertiser_id,monitor_task_id,request_id,event_code),
  KEY idx_ocpx_event_fact_time_scope (received_at,advertiser_id,media_id,monitor_task_id,promotion_link_id),
  CONSTRAINT fk_ocpx_event_fact_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_event_fact_link FOREIGN KEY (promotion_link_id)
    REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_event_fact_media FOREIGN KEY (media_id)
    REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_event_fact_task FOREIGN KEY (monitor_task_id)
    REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_event_fact_flow CHECK (flow_type IN ('FEEDBACK','CALLBACK')),
  CONSTRAINT ck_ocpx_event_fact_billing CHECK (billing_status IN (
    'NOT_APPLICABLE','POSTED','REJECTED_INSUFFICIENT_BALANCE'
  )),
  CONSTRAINT ck_ocpx_event_fact_amounts CHECK (spend_amount>=0 AND currency_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='FACT: idempotent OCPX feedback and callback facts';

CREATE TABLE scx_ocpx_billing_entry (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NULL,
  event_fact_id VARCHAR(32) NULL,
  entry_type VARCHAR(24) NOT NULL,
  status VARCHAR(16) NOT NULL,
  amount DECIMAL(20,6) NOT NULL,
  balance_before DECIMAL(20,6) NOT NULL,
  balance_after DECIMAL(20,6) NOT NULL,
  business_key VARCHAR(255) NOT NULL,
  reason_code VARCHAR(64) NULL,
  operator_user_id VARCHAR(64) NULL,
  remark VARCHAR(255) NULL,
  occurred_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_billing_business_key (business_key),
  KEY idx_ocpx_billing_advertiser_time (advertiser_id,occurred_at),
  CONSTRAINT fk_ocpx_billing_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_billing_task FOREIGN KEY (monitor_task_id)
    REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_billing_fact FOREIGN KEY (event_fact_id)
    REFERENCES scx_ocpx_event_fact(id),
  CONSTRAINT ck_ocpx_billing_type CHECK (entry_type IN ('RECHARGE','CLICK_CHARGE')),
  CONSTRAINT ck_ocpx_billing_status CHECK (status IN ('POSTED','REJECTED')),
  CONSTRAINT ck_ocpx_billing_amount CHECK (amount>=0),
  CONSTRAINT ck_ocpx_billing_balances CHECK (balance_before>=0 AND balance_after>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='LEDGER: immutable advertiser recharge and CPC entries';

CREATE TABLE scx_ocpx_hourly_metric (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  impression_count BIGINT NOT NULL DEFAULT 0,
  click_count BIGINT NOT NULL DEFAULT 0,
  target_conversion_count BIGINT NOT NULL DEFAULT 0,
  activate_count BIGINT NOT NULL DEFAULT 0,
  evoke_count BIGINT NOT NULL DEFAULT 0,
  login_count BIGINT NOT NULL DEFAULT 0,
  register_count BIGINT NOT NULL DEFAULT 0,
  payment_count BIGINT NOT NULL DEFAULT 0,
  customer_acquisition_count BIGINT NOT NULL DEFAULT 0,
  next_stay_count BIGINT NOT NULL DEFAULT 0,
  visits_count BIGINT NOT NULL DEFAULT 0,
  authentication_count BIGINT NOT NULL DEFAULT 0,
  place_order_count BIGINT NOT NULL DEFAULT 0,
  finish_the_order_count BIGINT NOT NULL DEFAULT 0,
  first_activation_count BIGINT NOT NULL DEFAULT 0,
  first_order_count BIGINT NOT NULL DEFAULT 0,
  spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  rejected_click_count BIGINT NOT NULL DEFAULT 0,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_hourly_metric_scope
    (time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id),
  KEY idx_ocpx_hourly_metric_dashboard
    (advertiser_id,time_hour,media_id,monitor_task_id,promotion_link_id),
  CONSTRAINT fk_ocpx_hourly_advertiser FOREIGN KEY (advertiser_id)
    REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_hourly_link FOREIGN KEY (promotion_link_id)
    REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_hourly_media FOREIGN KEY (media_id)
    REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_hourly_task FOREIGN KEY (monitor_task_id)
    REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_hourly_nonnegative CHECK (
    impression_count>=0 AND click_count>=0 AND target_conversion_count>=0
    AND spend_amount>=0 AND currency_amount>=0 AND rejected_click_count>=0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='AGGREGATE: transactionally maintained truthful hourly metrics';

INSERT INTO scx_ocpx_advertiser_account (advertiser_id,currency,available_balance)
SELECT id,'CNY',0 FROM scx_ocpx_advertiser
ON DUPLICATE KEY UPDATE advertiser_id=VALUES(advertiser_id);

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_monitor_task_billing','配置监测任务计费','','menu_ocpx_monitor_task',
   'ocpx:monitor-task:billing',40,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),
  update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v108_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:monitor-task:billing' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE rm.role_id='role_advertiser'
  AND m.permission IN ('ocpx:advertiser:recharge','ocpx:monitor-task:billing');
