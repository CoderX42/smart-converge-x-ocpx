-- V116: retain additional normalized Feedback fields for administrator-only
-- flow-log request detail. Raw query strings and signature credentials remain excluded.

ALTER TABLE scx_ocpx_log_feedback
  ADD COLUMN device_androidid_md5 VARCHAR(64) NULL AFTER device_dpid,
  ADD COLUMN device_idfa_md5 VARCHAR(64) NULL AFTER device_idfa,
  ADD COLUMN aaid VARCHAR(128) NULL AFTER device_idfa_md5,
  ADD COLUMN device_caid2 VARCHAR(128) NULL AFTER device_caid1,
  ADD COLUMN device_caid_list VARCHAR(1024) NULL AFTER device_caid2,
  ADD COLUMN last_caid VARCHAR(128) NULL AFTER device_caid_list,
  ADD COLUMN prev_caid VARCHAR(128) NULL AFTER last_caid,
  ADD COLUMN device_imei_md5 VARCHAR(64) NULL AFTER prev_caid,
  ADD COLUMN device_oaid_md5 VARCHAR(64) NULL AFTER device_imei_md5,
  ADD COLUMN ipv6 VARCHAR(64) NULL AFTER device_oaid_md5,
  ADD COLUMN model VARCHAR(512) NULL AFTER ipv6,
  ADD COLUMN osv VARCHAR(128) NULL AFTER model;

ALTER TABLE scx_ocpx_log_feedback_day
  ADD COLUMN device_androidid_md5 VARCHAR(64) NULL AFTER device_dpid,
  ADD COLUMN device_idfa_md5 VARCHAR(64) NULL AFTER device_idfa,
  ADD COLUMN aaid VARCHAR(128) NULL AFTER device_idfa_md5,
  ADD COLUMN device_caid2 VARCHAR(128) NULL AFTER device_caid1,
  ADD COLUMN device_caid_list VARCHAR(1024) NULL AFTER device_caid2,
  ADD COLUMN last_caid VARCHAR(128) NULL AFTER device_caid_list,
  ADD COLUMN prev_caid VARCHAR(128) NULL AFTER last_caid,
  ADD COLUMN device_imei_md5 VARCHAR(64) NULL AFTER prev_caid,
  ADD COLUMN device_oaid_md5 VARCHAR(64) NULL AFTER device_imei_md5,
  ADD COLUMN ipv6 VARCHAR(64) NULL AFTER device_oaid_md5,
  ADD COLUMN model VARCHAR(512) NULL AFTER ipv6,
  ADD COLUMN osv VARCHAR(128) NULL AFTER model;
