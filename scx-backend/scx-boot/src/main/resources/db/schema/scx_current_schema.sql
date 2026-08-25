-- SCX OCPX minimal current-state schema.
-- Advertising data consists of the retained routing entities, advertiser-owned
-- creatives, and authoritative
-- account, ledger, event fact and hourly metric tables. Core system-management users, roles, departments, dictionaries and
-- logs remain owned by scx-system; this file only preserves their menu/RBAC
-- metadata alongside the advertising menu seed.
USE `smart_converge_x`;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser (
  id VARCHAR(32) NOT NULL,
  advertiser_name VARCHAR(128) NOT NULL,
  upstream_protocol VARCHAR(32) NULL,
  account_user_id VARCHAR(64) NULL,
  credential_config_key VARCHAR(128) NULL,
  bid_url VARCHAR(5000) NULL,
  macro_params VARCHAR(4000) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_advertiser_name (advertiser_name),
  UNIQUE KEY uk_ocpx_advertiser_account_user (account_user_id),
  CONSTRAINT fk_ocpx_advertiser_account_user FOREIGN KEY (account_user_id) REFERENCES sys_user(id),
  CONSTRAINT ck_ocpx_advertiser_upstream_protocol CHECK (
    upstream_protocol IS NULL OR upstream_protocol IN ('TEMPLATE_GET','AERIM','WEIFENG','GRAVITY')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: upstream advertiser';

CREATE TABLE IF NOT EXISTS scx_ocpx_protocol_allowed_host (
  id VARCHAR(32) NOT NULL,
  protocol_type VARCHAR(32) NOT NULL,
  host VARCHAR(253) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_protocol_allowed_host (protocol_type,host),
  CONSTRAINT ck_ocpx_protocol_allowed_host_type CHECK (protocol_type IN ('AERIM','WEIFENG','GRAVITY')),
  CONSTRAINT ck_ocpx_protocol_allowed_host_value CHECK (
    host=LOWER(host) AND host NOT LIKE '%://%' AND host NOT LIKE '%/%'
    AND host NOT LIKE '%:%' AND host NOT LIKE '%*%'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='CONFIG: administrator-managed exact upstream protocol hosts';

INSERT INTO scx_ocpx_protocol_allowed_host
  (id,protocol_type,host,create_time,update_time)
VALUES
  ('current_aerim_d','AERIM','d.aerim.cn',NOW(3),NOW(3)),
  ('current_aerim_c','AERIM','c.aerim.cn',NOW(3),NOW(3)),
  ('current_weifeng_adapi','WEIFENG','ad.adapi.cn',NOW(3),NOW(3)),
  ('current_gravity_backend','GRAVITY','backend.gravity-engine.com',NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE host=VALUES(host),update_time=NOW(3);

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_account (
  advertiser_id VARCHAR(32) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'CNY',
  available_balance DECIMAL(20,6) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (advertiser_id),
  CONSTRAINT fk_ocpx_account_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_account_currency CHECK (currency='CNY'),
  CONSTRAINT ck_ocpx_account_balance CHECK (available_balance>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='ACCOUNT: authoritative advertiser available balance';

CREATE TABLE IF NOT EXISTS scx_ocpx_promotion_link (
  id VARCHAR(32) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  link_name VARCHAR(128) NOT NULL,
  platform TINYINT UNSIGNED NOT NULL,
  link_channel VARCHAR(64) NULL,
  link_url VARCHAR(5000) NOT NULL,
  deeplink VARCHAR(2000) NULL,
  link_ext VARCHAR(4000) NULL,
  remark VARCHAR(500) NULL,
  is_delete TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_promotion_link_owner (advertiser_id,id),
  KEY idx_ocpx_promotion_link_advertiser (advertiser_id,is_delete,update_time),
  CONSTRAINT fk_ocpx_promotion_link_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_promotion_link_platform CHECK (platform IN (1,2,3,4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: advertiser promotion link';

CREATE TABLE IF NOT EXISTS scx_ocpx_creative (
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
  CONSTRAINT fk_ocpx_creative_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_creative_material FOREIGN KEY (source_material_id) REFERENCES sys_material(id),
  CONSTRAINT ck_ocpx_creative_type CHECK (creative_type=1),
  CONSTRAINT ck_ocpx_creative_status CHECK (status IN (0,1)),
  CONSTRAINT ck_ocpx_creative_asset CHECK (
    file_size>0 AND width>0 AND height>0
    AND width<=8192 AND height<=8192
    AND CAST(width AS UNSIGNED)*CAST(height AS UNSIGNED)<=40000000
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: advertiser-owned image creative';

CREATE TABLE IF NOT EXISTS scx_ocpx_creative_promotion_link (
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

CREATE TABLE IF NOT EXISTS scx_ocpx_media (
  id VARCHAR(32) NOT NULL,
  media_name VARCHAR(128) NOT NULL,
  credential_config_key VARCHAR(128) NULL,
  uri VARCHAR(255) NOT NULL,
  macro_params VARCHAR(4000) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_media_name (media_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: downstream media';

CREATE TABLE IF NOT EXISTS scx_ocpx_monitor_task (
  id VARCHAR(32) NOT NULL,
  monitor_name VARCHAR(128) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  billing_enabled TINYINT(1) NOT NULL DEFAULT 0,
  billing_type TINYINT UNSIGNED NOT NULL DEFAULT 1,
  unit_price DECIMAL(20,6) NOT NULL DEFAULT 0,
  conversion_event VARCHAR(32) NOT NULL DEFAULT 'ACTIVATE',
  biz_id VARCHAR(64) NULL,
  show_url TEXT NULL,
  clk_url TEXT NULL,
  remark VARCHAR(255) NULL,
  is_delete TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ocpx_monitor_task_dimensions (advertiser_id,promotion_link_id,media_id,is_delete),
  CONSTRAINT fk_ocpx_monitor_task_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_monitor_task_link_owner FOREIGN KEY (advertiser_id,promotion_link_id)
    REFERENCES scx_ocpx_promotion_link(advertiser_id,id),
  CONSTRAINT fk_ocpx_monitor_task_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT ck_ocpx_monitor_task_billing_enabled CHECK (billing_enabled IN (0,1)),
  CONSTRAINT ck_ocpx_monitor_task_billing_type CHECK (billing_type IN (1,2)),
  CONSTRAINT ck_ocpx_monitor_task_billing_price CHECK (billing_enabled=0 OR unit_price>0),
  CONSTRAINT ck_ocpx_monitor_task_conversion_event CHECK (conversion_event IN (
    'ACTIVATE','EVOKE','LOGIN','REGISTER','PAYMENT','CUSTOMER_ACQUISITION',
    'NEXT_STAY','VISITS','AUTHENTICATION','ORDER','FINISH_THE_ORDER',
    'FIRST_ACTIVATION','FIRST_ORDER'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: advertiser-link-media monitoring task';

CREATE TABLE IF NOT EXISTS scx_ocpx_billing_config_audit (
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
  CONSTRAINT fk_ocpx_billing_audit_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_billing_audit_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT ck_ocpx_billing_audit_action CHECK (action_type IN ('CREATE','UPDATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='AUDIT: immutable monitor-task billing configuration history';

CREATE TABLE IF NOT EXISTS scx_ocpx_deduction_rule (
  id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  ratio DECIMAL(9,6) NOT NULL DEFAULT 1.000000,
  is_delete TINYINT(1) NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_deduction_rule_route (media_id,promotion_link_id,(IF(is_delete=0,1,NULL))),
  KEY idx_ocpx_deduction_rule_link (promotion_link_id),
  CONSTRAINT fk_ocpx_deduction_rule_promotion_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_deduction_rule_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT ck_ocpx_deduction_ratio CHECK (ratio>=0 AND ratio<=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='BUSINESS: link-media callback pass ratio rule';

CREATE TABLE IF NOT EXISTS scx_ocpx_event_fact (
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
  billing_enabled_snapshot TINYINT(1) NULL,
  billing_type_snapshot TINYINT UNSIGNED NULL,
  billing_unit_price_snapshot DECIMAL(20,6) NULL,
  conversion_event_snapshot VARCHAR(32) NULL,
  deduction_decision VARCHAR(16) NULL,
  deduction_ratio_snapshot DECIMAL(9,6) NULL,
  deduction_decided_at DATETIME(3) NULL,
  spend_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  billing_status VARCHAR(40) NOT NULL DEFAULT 'NOT_APPLICABLE',
  billing_reason_code VARCHAR(64) NULL,
  received_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_event_fact_key (fact_key),
  UNIQUE KEY uk_ocpx_event_fact_identity
    (advertiser_id,monitor_task_id,request_id,event_code),
  KEY idx_ocpx_event_fact_time_scope (received_at,advertiser_id,media_id,monitor_task_id,promotion_link_id),
  KEY idx_ocpx_event_fact_deduction_time
    (deduction_decided_at,advertiser_id,media_id,monitor_task_id,promotion_link_id),
  CONSTRAINT fk_ocpx_event_fact_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_event_fact_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_event_fact_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_event_fact_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_event_fact_flow CHECK (flow_type IN ('FEEDBACK','CALLBACK')),
  CONSTRAINT ck_ocpx_event_fact_billing CHECK (billing_status IN (
    'NOT_APPLICABLE','POSTED','REJECTED_INSUFFICIENT_BALANCE'
  )),
  CONSTRAINT ck_ocpx_event_fact_billing_enabled_snapshot
    CHECK (billing_enabled_snapshot IS NULL OR billing_enabled_snapshot IN (0,1)),
  CONSTRAINT ck_ocpx_event_fact_billing_type_snapshot
    CHECK (billing_type_snapshot IS NULL OR billing_type_snapshot IN (1,2)),
  CONSTRAINT ck_ocpx_event_fact_billing_price_snapshot
    CHECK (billing_unit_price_snapshot IS NULL OR billing_unit_price_snapshot>=0),
  CONSTRAINT ck_ocpx_event_fact_deduction_decision CHECK (
    deduction_decision IS NULL OR deduction_decision IN ('PENDING','PASSED','DEDUCTED')
  ),
  CONSTRAINT ck_ocpx_event_fact_deduction_ratio CHECK (
    deduction_ratio_snapshot IS NULL
    OR (deduction_ratio_snapshot>=0 AND deduction_ratio_snapshot<=1)
  ),
  CONSTRAINT ck_ocpx_event_fact_deduction_state CHECK (
    (deduction_decision IS NULL AND deduction_ratio_snapshot IS NULL AND deduction_decided_at IS NULL)
    OR (deduction_decision='PENDING' AND deduction_ratio_snapshot IS NULL AND deduction_decided_at IS NULL)
    OR (deduction_decision IN ('PASSED','DEDUCTED')
        AND deduction_ratio_snapshot IS NOT NULL AND deduction_decided_at IS NOT NULL)
  ),
  CONSTRAINT ck_ocpx_event_fact_amounts CHECK (spend_amount>=0 AND currency_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='FACT: idempotent OCPX feedback and callback facts';

CREATE TABLE IF NOT EXISTS scx_ocpx_billing_entry (
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
  CONSTRAINT fk_ocpx_billing_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_billing_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT fk_ocpx_billing_fact FOREIGN KEY (event_fact_id) REFERENCES scx_ocpx_event_fact(id),
  CONSTRAINT ck_ocpx_billing_type CHECK (
    entry_type IN ('RECHARGE','MANUAL_DEBIT','CLICK_CHARGE','CONVERSION_CHARGE')
  ),
  CONSTRAINT ck_ocpx_billing_status CHECK (status IN ('POSTED','REJECTED')),
  CONSTRAINT ck_ocpx_billing_amount CHECK (amount>=0),
  CONSTRAINT ck_ocpx_billing_balances CHECK (balance_before>=0 AND balance_after>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='LEDGER: immutable advertiser recharge, manual debit, click and conversion charge entries';

CREATE TABLE IF NOT EXISTS scx_ocpx_hourly_metric (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  impression_count BIGINT NOT NULL DEFAULT 0,
  click_count BIGINT NOT NULL DEFAULT 0,
  target_conversion_count BIGINT NOT NULL DEFAULT 0,
  callback_count BIGINT NOT NULL DEFAULT 0,
  deduction_passed_count BIGINT NOT NULL DEFAULT 0,
  deduction_deducted_count BIGINT NOT NULL DEFAULT 0,
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
  rejected_conversion_count BIGINT NOT NULL DEFAULT 0,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_hourly_metric_scope
    (time_hour,advertiser_id,promotion_link_id,media_id,monitor_task_id),
  KEY idx_ocpx_hourly_metric_dashboard
    (advertiser_id,time_hour,media_id,monitor_task_id,promotion_link_id),
  CONSTRAINT fk_ocpx_hourly_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_hourly_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_hourly_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_hourly_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_hourly_nonnegative CHECK (
    impression_count>=0 AND click_count>=0 AND target_conversion_count>=0
    AND callback_count>=0 AND deduction_passed_count>=0 AND deduction_deducted_count>=0
    AND deduction_passed_count+deduction_deducted_count<=callback_count
    AND spend_amount>=0 AND currency_amount>=0
    AND rejected_click_count>=0 AND rejected_conversion_count>=0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='AGGREGATE: transactionally maintained truthful hourly metrics';

CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log (
  id VARCHAR(32) NOT NULL,
  trace_id CHAR(64) NULL,
  flow_type VARCHAR(16) NOT NULL,
  stage_code VARCHAR(32) NOT NULL DEFAULT 'LEGACY',
  event_id VARCHAR(128) NOT NULL,
  source_event_id VARCHAR(128) NULL,
  event_code VARCHAR(64) NOT NULL,
  request_method VARCHAR(8) NULL,
  request_endpoint VARCHAR(512) NULL,
  macro_audit JSON NULL,
  payload_hash CHAR(64) NOT NULL,
  advertiser_id VARCHAR(32) NOT NULL,
  promotion_link_id VARCHAR(32) NOT NULL,
  media_id VARCHAR(32) NOT NULL,
  monitor_task_id VARCHAR(32) NOT NULL,
  result_status VARCHAR(16) NOT NULL,
  partner_http_status INT NULL,
  message VARCHAR(500) NULL,
  response_preview VARCHAR(4096) NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  event_time DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_ocpx_flow_log_time (event_time,flow_type,result_status),
  KEY idx_ocpx_flow_log_dimensions (advertiser_id,promotion_link_id,media_id,monitor_task_id,event_time),
  KEY idx_ocpx_flow_log_event (event_id,create_time),
  KEY idx_ocpx_flow_log_trace (trace_id,event_time,stage_code),
  KEY idx_ocpx_flow_log_stage_result (stage_code,result_status,event_time),
  CONSTRAINT fk_ocpx_flow_log_advertiser FOREIGN KEY (advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CONSTRAINT fk_ocpx_flow_log_promotion_link FOREIGN KEY (promotion_link_id) REFERENCES scx_ocpx_promotion_link(id),
  CONSTRAINT fk_ocpx_flow_log_media FOREIGN KEY (media_id) REFERENCES scx_ocpx_media(id),
  CONSTRAINT fk_ocpx_flow_log_monitor_task FOREIGN KEY (monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CONSTRAINT ck_ocpx_flow_log_type CHECK (flow_type IN ('FEEDBACK','CALLBACK')),
  CONSTRAINT ck_ocpx_flow_log_status CHECK (result_status IN ('SUCCESS','FAILED')),
  CONSTRAINT ck_ocpx_flow_log_duration CHECK (duration_ms>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: sanitized Feedback and Callback flow';

CREATE TABLE IF NOT EXISTS scx_ocpx_flow_log_detail (
  id VARCHAR(32) NOT NULL,
  flow_log_id VARCHAR(32) NOT NULL,
  request_params JSON NULL,
  response_preview VARCHAR(4096) NULL,
  request_truncated TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_flow_log_detail_flow (flow_log_id),
  KEY idx_ocpx_flow_log_detail_expiry (expires_at,id),
  CONSTRAINT fk_ocpx_flow_log_detail_flow FOREIGN KEY (flow_log_id)
    REFERENCES scx_ocpx_flow_log(id) ON DELETE CASCADE,
  CONSTRAINT ck_ocpx_flow_log_detail_truncated CHECK (request_truncated IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='RESTRICTED LOG: normalized request parameters and bounded response preview';

CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_key_req VARCHAR(20) NULL, time_key_res VARCHAR(20) NULL,
  time_hour_res VARCHAR(20) NULL, date_res VARCHAR(20) NULL,
  m_id VARCHAR(32) NULL COMMENT 'OcpxMedia.id',
  a_id VARCHAR(32) NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  t_id VARCHAR(32) NULL COMMENT 'OcpxMonitorTask.id',
  l_id VARCHAR(32) NULL COMMENT 'OcpxPromotionLink.id',
  userid VARCHAR(128) NULL, user_type VARCHAR(64) NULL,
  adv_id VARCHAR(128) NULL, group_id VARCHAR(128) NULL,
  plan_id VARCHAR(128) NULL, creative_id VARCHAR(128) NULL,
  imp_id VARCHAR(128) NULL, clk_id VARCHAR(128) NULL, req_id VARCHAR(128) NULL,
  ua VARCHAR(2000) NULL, ip VARCHAR(64) NULL, device_os VARCHAR(32) NULL,
  device_did VARCHAR(128) NULL, device_oaid VARCHAR(128) NULL,
  device_dpid VARCHAR(128) NULL, device_androidid_md5 VARCHAR(64) NULL,
  device_mac VARCHAR(128) NULL, device_idfa VARCHAR(128) NULL,
  device_idfa_md5 VARCHAR(64) NULL, aaid VARCHAR(128) NULL,
  device_caid VARCHAR(128) NULL, device_caid1 VARCHAR(128) NULL,
  device_caid2 VARCHAR(128) NULL, device_caid_list VARCHAR(1024) NULL,
  last_caid VARCHAR(128) NULL, prev_caid VARCHAR(128) NULL,
  device_imei_md5 VARCHAR(64) NULL, device_oaid_md5 VARCHAR(64) NULL,
  ipv6 VARCHAR(64) NULL, model VARCHAR(512) NULL, osv VARCHAR(128) NULL,
  callback_url VARCHAR(3000) NULL,
  ts VARCHAR(32) NULL, ts_ms VARCHAR(32) NULL, base_price DECIMAL(30,0) NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_feedback_req (req_id),
  KEY idx_ocpx_feedback_route (m_id,a_id,t_id,l_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: legacy-compatible OCPX feedback detail';

CREATE TABLE IF NOT EXISTS scx_ocpx_log_feedback_day LIKE scx_ocpx_log_feedback;

CREATE TABLE IF NOT EXISTS scx_ocpx_log_callback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_key_req VARCHAR(20) NULL, time_key_res VARCHAR(20) NULL,
  time_hour_res VARCHAR(20) NULL, date_res VARCHAR(20) NULL,
  m_id VARCHAR(32) NULL COMMENT 'OcpxMedia.id',
  a_id VARCHAR(32) NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  t_id VARCHAR(32) NULL COMMENT 'OcpxMonitorTask.id',
  l_id VARCHAR(32) NULL COMMENT 'OcpxPromotionLink.id',
  adv_id VARCHAR(128) NULL, group_id VARCHAR(128) NULL,
  plan_id VARCHAR(128) NULL, creative_id VARCHAR(128) NULL,
  imp_id VARCHAR(128) NULL, clk_id VARCHAR(128) NULL, req_id VARCHAR(128) NULL,
  ua VARCHAR(2000) NULL, ip VARCHAR(64) NULL, device_os VARCHAR(32) NULL,
  device_did VARCHAR(128) NULL, device_oaid VARCHAR(128) NULL,
  device_dpid VARCHAR(128) NULL, device_mac VARCHAR(128) NULL,
  device_idfa VARCHAR(128) NULL, device_caid VARCHAR(128) NULL,
  device_caid1 VARCHAR(128) NULL, ts VARCHAR(32) NULL, ts_ms VARCHAR(32) NULL,
  userid VARCHAR(128) NULL, user_type VARCHAR(64) NULL,
  partner_action_type VARCHAR(50) NULL COMMENT '合作方原始动作',
  standard_action_type TINYINT UNSIGNED NULL COMMENT 'SCX 标准动作编号',
  action_time BIGINT NULL COMMENT '动作时间毫秒',
  currency_amount DECIMAL(20,6) NULL COMMENT '合作方原始订单金额，不推断币种',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_callback_req (req_id),
  KEY idx_ocpx_callback_route (m_id,a_id,t_id,l_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='LOG: legacy-compatible OCPX callback detail';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat_temp (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  m_id VARCHAR(32) NOT NULL COMMENT 'OcpxMedia.id',
  a_id VARCHAR(32) NOT NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  app_id INT NULL, slot_id INT NULL, dsp_slot_id INT NULL,
  t_id VARCHAR(32) NOT NULL COMMENT 'OcpxMonitorTask.id',
  l_id VARCHAR(32) NOT NULL COMMENT 'OcpxPromotionLink.id',
  req_smt INT NOT NULL DEFAULT 0, res_smt INT NOT NULL DEFAULT 0,
  uni_win_smt INT NOT NULL DEFAULT 0, show_smt INT NOT NULL DEFAULT 0,
  uni_show_smt INT NOT NULL DEFAULT 0, clk_smt INT NOT NULL DEFAULT 0,
  uni_clk_smt INT NOT NULL DEFAULT 0, activate INT NOT NULL DEFAULT 0,
  evoke INT NOT NULL DEFAULT 0, login INT NOT NULL DEFAULT 0,
  register INT NOT NULL DEFAULT 0, payment INT NOT NULL DEFAULT 0,
  customer_acquisition INT NOT NULL DEFAULT 0, next_stay INT NOT NULL DEFAULT 0,
  visits INT NOT NULL DEFAULT 0, authentication INT NOT NULL DEFAULT 0,
  place_order INT NOT NULL DEFAULT 0, finish_the_order INT NOT NULL DEFAULT 0,
  first_activation INT NOT NULL DEFAULT 0, first_order INT NOT NULL DEFAULT 0,
  currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  base_price BIGINT NOT NULL DEFAULT 0, win_price BIGINT NOT NULL DEFAULT 0,
  mark TINYINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_report_temp_group (mark,create_time),
  KEY idx_ocpx_report_temp_route (m_id,a_id,t_id,l_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AGGREGATE: legacy-compatible OCPX temporary report material';

CREATE TABLE IF NOT EXISTS scx_ocpx_report_mat (
  id BIGINT NOT NULL AUTO_INCREMENT,
  time_hour INT NOT NULL,
  m_id VARCHAR(32) NOT NULL COMMENT 'OcpxMedia.id',
  a_id VARCHAR(32) NOT NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  app_id INT NULL, slot_id INT NULL, dsp_slot_id INT NULL,
  t_id VARCHAR(32) NOT NULL COMMENT 'OcpxMonitorTask.id',
  l_id VARCHAR(32) NOT NULL COMMENT 'OcpxPromotionLink.id',
  req_smt INT NOT NULL DEFAULT 0, res_smt INT NOT NULL DEFAULT 0,
  uni_win_smt INT NOT NULL DEFAULT 0, show_smt INT NOT NULL DEFAULT 0,
  uni_show_smt INT NOT NULL DEFAULT 0, clk_smt INT NOT NULL DEFAULT 0,
  uni_clk_smt INT NOT NULL DEFAULT 0, activate INT NOT NULL DEFAULT 0,
  evoke INT NOT NULL DEFAULT 0, login INT NOT NULL DEFAULT 0,
  register INT NOT NULL DEFAULT 0, payment INT NOT NULL DEFAULT 0,
  customer_acquisition INT NOT NULL DEFAULT 0, next_stay INT NOT NULL DEFAULT 0,
  visits INT NOT NULL DEFAULT 0, authentication INT NOT NULL DEFAULT 0,
  place_order INT NOT NULL DEFAULT 0, finish_the_order INT NOT NULL DEFAULT 0,
  first_activation INT NOT NULL DEFAULT 0, first_order INT NOT NULL DEFAULT 0,
  currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
  base_price BIGINT NOT NULL DEFAULT 0, win_price BIGINT NOT NULL DEFAULT 0,
  mark TINYINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ocpx_report_time (time_hour),
  KEY idx_ocpx_report_route (m_id,a_id,t_id,l_id)
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

-- V109: keep historical legacy permissions soft-deleted and remove every role
-- mapping, including on baselines composed with older scx-system seed data.
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE m.permission IN (
  'ocpx:api-debug:read',
  'ocpx:monitor:publish',
  'ocpx:monitor:read',
  'ocpx:monitor:write'
);

UPDATE sys_menu
SET del_flag='1',update_time=NOW()
WHERE permission IN (
  'ocpx:api-debug:read',
  'ocpx:monitor:publish',
  'ocpx:monitor:read',
  'ocpx:monitor:write'
);

-- Seed only the retained advertising menus below.
INSERT INTO sys_role
  (id,role_name,role_code,role_desc,type,shop_id,create_time,update_time,del_flag)
VALUES
  ('role_advertiser','广告主','ROLE_ADVERTISER','广告主自助账号共享可配置角色',
   '0',NULL,NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name),role_desc=VALUES(role_desc),type='0',shop_id=NULL,
  update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_home','经营总览','/ocpx/dashboard','0','lucide:layout-dashboard','ocpx/dashboard/index','ocpx:dashboard:view',1,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_advertiser','广告主管理','/ocpx/advertiser','0','lucide:building-2','ocpx/advertiser/index','ocpx:advertiser:view',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_promotion_link','推广链接','/ocpx/promotion-link','0','lucide:link','ocpx/promotion-link/index','ocpx:promotion-link:view',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_creative','创意管理','/ocpx/creative','0','lucide:images','ocpx/creative/index','ocpx:creative:view',25,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_media','媒体管理','/ocpx/media','0','lucide:radio-tower','ocpx/media/index','ocpx:media:view',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_monitor_task','监测任务','/ocpx/monitor-task','0','lucide:list-checks','ocpx/monitor-task/index','ocpx:monitor-task:view',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_deduction_rule','扣量规则','/ocpx/deduction','0','lucide:percent','ocpx/deduction/index','ocpx:deduction-rule:list',45,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_report','报表中心','/ocpx/report','0','lucide:chart-column','ocpx/report/index','ocpx:report:view',50,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_callback_record','上游转化记录','/ocpx/callback-record','0','lucide:webhook','ocpx/callback-record/index','ocpx:callback-record:view',55,'0','app_base',NOW(),NOW(),'0'),
  ('menu_ocpx_flow_log','流程日志','/ocpx/flow-log','0','lucide:scroll-text','ocpx/flow-log/index','ocpx:flow-log:view',60,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_add','新增广告主','','menu_ocpx_advertiser','ocpx:advertiser:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_edit','编辑广告主','','menu_ocpx_advertiser','ocpx:advertiser:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_recharge','广告主充值','','menu_ocpx_advertiser','ocpx:advertiser:recharge',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_deduct','扣减广告主余额','','menu_ocpx_advertiser','ocpx:advertiser:deduct',35,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_billing_entry_view','查看账务流水','','menu_ocpx_advertiser','ocpx:billing-entry:view',45,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_advertiser_delete','删除广告主','','menu_ocpx_advertiser','ocpx:advertiser:delete',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_protocol_config_manage','管理上游协议与允许域名','','menu_ocpx_advertiser','ocpx:protocol-config:manage',70,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_add','新增推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_edit','编辑推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_promotion_link_delete','删除推广链接','','menu_ocpx_promotion_link','ocpx:promotion-link:delete',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_add','新增创意','','menu_ocpx_creative','ocpx:creative:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_edit','编辑创意','','menu_ocpx_creative','ocpx:creative:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_delete','删除创意','','menu_ocpx_creative','ocpx:creative:delete',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_creative_bind','绑定推广链接','','menu_ocpx_creative','ocpx:creative:bind',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_add','新增媒体','','menu_ocpx_media','ocpx:media:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_edit','编辑媒体','','menu_ocpx_media','ocpx:media:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_media_delete','删除媒体','','menu_ocpx_media','ocpx:media:delete',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_add','新增监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:add',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_edit','编辑监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:edit',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_delete','删除监测任务','','menu_ocpx_monitor_task','ocpx:monitor-task:delete',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_billing','配置监测任务计费','','menu_ocpx_monitor_task','ocpx:monitor-task:billing',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_test','执行监测任务点击测试','','menu_ocpx_monitor_task','ocpx:monitor-task:test',50,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_monitor_task_integration','下游媒体联调','','menu_ocpx_monitor_task','ocpx:monitor-task:integration',60,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_report_export','导出报表','','menu_ocpx_report','ocpx:report:export',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_list','查询扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:list',10,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_create','新增扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:create',20,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_update','编辑扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:update',30,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_deduction_rule_delete','删除扣量规则','','menu_ocpx_deduction_rule','ocpx:deduction-rule:delete',40,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_adv_role_manage','管理广告主账号角色权限','','menu_ocpx_advertiser','ocpx:advertiser:role:manage',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_adv_scope_all','全量 OCPX 数据范围','','menu_ocpx_advertiser','ocpx:advertiser:scope:all',90,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_flow_log_detail','查看链路请求原值','','menu_ocpx_flow_log','ocpx:flow-log:detail',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v96_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r JOIN sys_menu m ON (m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0' AND m.id<>'menu_ocpx_system';

DELETE FROM sys_role_menu
WHERE role_id = 'role_advertiser';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('current_',LEFT(SHA2(CONCAT('role_advertiser','|',m.id),256),52)),
       'role_advertiser',m.id
FROM sys_menu m
WHERE m.del_flag='0' AND m.id IN (
  'menu_ocpx_home','menu_ocpx_advertiser','menu_ocpx_promotion_link','menu_ocpx_creative','menu_ocpx_media',
  'menu_ocpx_monitor_task','menu_ocpx_deduction_rule','menu_ocpx_report','menu_ocpx_callback_record','menu_ocpx_flow_log',
  'perm_ocpx_advertiser_add','perm_ocpx_advertiser_edit','perm_ocpx_billing_entry_view',
  'perm_ocpx_advertiser_delete','perm_ocpx_promotion_link_add','perm_ocpx_promotion_link_edit',
  'perm_ocpx_promotion_link_delete','perm_ocpx_media_add','perm_ocpx_media_edit','perm_ocpx_media_delete',
  'perm_ocpx_creative_add','perm_ocpx_creative_edit','perm_ocpx_creative_delete','perm_ocpx_creative_bind',
  'perm_ocpx_monitor_task_add','perm_ocpx_monitor_task_edit','perm_ocpx_monitor_task_delete',
  'perm_ocpx_report_export','perm_ocpx_deduction_rule_list','perm_ocpx_deduction_rule_create',
  'perm_ocpx_deduction_rule_update','perm_ocpx_deduction_rule_delete','perm_ocpx_adv_scope_all'
);

INSERT INTO scx_ocpx_advertiser_account (advertiser_id,currency,available_balance)
SELECT id,'CNY',0 FROM scx_ocpx_advertiser
ON DUPLICATE KEY UPDATE advertiser_id=VALUES(advertiser_id);
