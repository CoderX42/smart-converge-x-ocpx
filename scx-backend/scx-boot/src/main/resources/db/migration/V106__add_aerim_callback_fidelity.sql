-- Forward-only AERIM callback fidelity expansion. Back up and preview the
-- exact target before applying. The application never auto-runs this folder.

USE `smart_converge_x`;
SET NAMES utf8mb4;

ALTER TABLE scx_ocpx_log_callback
  ADD COLUMN partner_action_type VARCHAR(50) NULL COMMENT '合作方原始动作' AFTER user_type,
  ADD COLUMN standard_action_type TINYINT UNSIGNED NULL COMMENT 'SCX 标准动作编号' AFTER partner_action_type,
  ADD COLUMN action_time BIGINT NULL COMMENT '动作时间毫秒' AFTER standard_action_type,
  ADD COLUMN currency_amount DECIMAL(20,6) NULL COMMENT '合作方原始订单金额，不推断币种' AFTER action_time;

ALTER TABLE scx_ocpx_report_mat_temp
  ADD COLUMN first_activation INT NOT NULL DEFAULT 0 AFTER finish_the_order,
  ADD COLUMN first_order INT NOT NULL DEFAULT 0 AFTER first_activation,
  ADD COLUMN currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER first_order;

ALTER TABLE scx_ocpx_report_mat
  ADD COLUMN first_activation INT NOT NULL DEFAULT 0 AFTER finish_the_order,
  ADD COLUMN first_order INT NOT NULL DEFAULT 0 AFTER first_activation,
  ADD COLUMN currency_amount DECIMAL(20,6) NOT NULL DEFAULT 0 AFTER first_order;
