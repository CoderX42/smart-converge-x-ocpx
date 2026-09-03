-- V127: register the independent PHOENIX upstream and standard actions.
-- Forward-only migration. Back up the production database, preview DDL and
-- obtain approval before execution; this file deliberately seeds no advertiser,
-- promotion link or Phoenix host configuration.

ALTER TABLE scx_ocpx_advertiser
  DROP CHECK ck_ocpx_advertiser_upstream_protocol,
  ADD CONSTRAINT ck_ocpx_advertiser_upstream_protocol
    CHECK (upstream_protocol IS NULL OR upstream_protocol IN
      ('TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'));

ALTER TABLE scx_ocpx_protocol_allowed_host
  DROP CHECK ck_ocpx_protocol_allowed_host_type,
  ADD CONSTRAINT ck_ocpx_protocol_allowed_host_type
    CHECK (protocol_type IN ('AERIM','WEIFENG','GRAVITY','RENREN_VIDEO','PHOENIX'));

ALTER TABLE scx_ocpx_monitor_task
  DROP CHECK ck_ocpx_monitor_task_conversion_event,
  ADD CONSTRAINT ck_ocpx_monitor_task_conversion_event CHECK (conversion_event IN (
    'ACTIVATE','EVOKE','LOGIN','REGISTER','PAYMENT','CUSTOMER_ACQUISITION',
    'NEXT_STAY','VISITS','AUTHENTICATION','ORDER','FINISH_THE_ORDER',
    'FIRST_ACTIVATION','FIRST_ORDER','DOWNLOAD','ACTIVITY','ADDICTION'
  ));

ALTER TABLE scx_ocpx_hourly_metric
  ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0 AFTER first_order_count,
  ADD COLUMN activity_count BIGINT NOT NULL DEFAULT 0 AFTER download_count,
  ADD COLUMN addiction_count BIGINT NOT NULL DEFAULT 0 AFTER activity_count,
  ALGORITHM=INPLACE,
  LOCK=NONE;

ALTER TABLE scx_ocpx_report_mat_temp
  ADD COLUMN download INT NOT NULL DEFAULT 0 AFTER first_order,
  ADD COLUMN activity INT NOT NULL DEFAULT 0 AFTER download,
  ADD COLUMN addiction INT NOT NULL DEFAULT 0 AFTER activity,
  ALGORITHM=INPLACE,
  LOCK=NONE;

ALTER TABLE scx_ocpx_report_mat
  ADD COLUMN download INT NOT NULL DEFAULT 0 AFTER first_order,
  ADD COLUMN activity INT NOT NULL DEFAULT 0 AFTER download,
  ADD COLUMN addiction INT NOT NULL DEFAULT 0 AFTER activity,
  ALGORITHM=INPLACE,
  LOCK=NONE;
