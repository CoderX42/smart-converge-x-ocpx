-- 固化日报生成时的客户、接入文档与媒体渠道维度，避免主数据后来改名/改绑影响历史归属。
ALTER TABLE scx_report_daily
  ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id,
  ADD COLUMN document_id VARCHAR(64) NULL AFTER link_id,
  ADD COLUMN channel_id VARCHAR(64) NULL AFTER task_id,
  ADD KEY idx_scx_report_daily_customer (report_date, media_id, customer_id),
  ADD KEY idx_scx_report_daily_channel (report_date, media_id, channel_id);
