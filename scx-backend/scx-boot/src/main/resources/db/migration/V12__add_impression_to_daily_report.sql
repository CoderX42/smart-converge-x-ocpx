ALTER TABLE scx_report_daily
  ADD COLUMN impression_count BIGINT NOT NULL DEFAULT 0 AFTER task_id;
