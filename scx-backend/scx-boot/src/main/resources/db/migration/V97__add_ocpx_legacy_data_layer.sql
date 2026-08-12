-- Forward-only delta for existing V96 databases. Apply explicitly after backup and approval.
USE `smart_converge_x`;
SET NAMES utf8mb4;

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
