-- Forward-only additive migration: OCPX monitor task tracking link fields.
-- Back up and preview the exact target before applying this file to an
-- existing database; initialization never auto-runs this directory.

USE `smart_converge_x`;
SET NAMES utf8mb4;

ALTER TABLE scx_ocpx_monitor_task
  ADD COLUMN unit_price DECIMAL(18,6) NULL AFTER operator_user_id,
  ADD COLUMN biz_id VARCHAR(64) NULL AFTER unit_price,
  ADD COLUMN show_url VARCHAR(2000) NULL AFTER biz_id,
  ADD COLUMN clk_url VARCHAR(2000) NULL AFTER show_url,
  ADD COLUMN remark VARCHAR(255) NULL AFTER clk_url;
