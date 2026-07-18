-- 竞品运营漏斗的后续转化指标。所有值均由原始事件+当前有效归因版本聚合。
ALTER TABLE scx_report_daily
  ADD COLUMN attributed_order_count BIGINT NOT NULL DEFAULT 0 AFTER attributed_pay_count,
  ADD COLUMN next_day_return_count BIGINT NOT NULL DEFAULT 0 AFTER attributed_order_count,
  ADD COLUMN retention_3d_count BIGINT NOT NULL DEFAULT 0 AFTER next_day_return_count,
  ADD COLUMN retention_7d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_3d_count,
  ADD COLUMN first_activate_count BIGINT NOT NULL DEFAULT 0 AFTER retention_7d_count,
  ADD COLUMN first_order_count BIGINT NOT NULL DEFAULT 0 AFTER first_activate_count,
  ADD COLUMN reactivate_count BIGINT NOT NULL DEFAULT 0 AFTER first_order_count,
  ADD COLUMN custom_count BIGINT NOT NULL DEFAULT 0 AFTER reactivate_count,
  ADD COLUMN credit_count BIGINT NOT NULL DEFAULT 0 AFTER custom_count;
