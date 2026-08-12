-- Forward-only OCPX route identifier rename.
-- Stop scx-boot and scx-ocpx-engine, drain legacy Redis report payloads, and
-- take a verified backup before applying this file to an existing database.

USE `smart_converge_x`;
SET NAMES utf8mb4;

ALTER TABLE scx_ocpx_log_feedback
  CHANGE COLUMN channel_id m_id VARCHAR(32) NULL COMMENT 'OcpxMedia.id',
  CHANGE COLUMN dsp_id a_id VARCHAR(32) NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  CHANGE COLUMN monitor_id t_id VARCHAR(32) NULL COMMENT 'OcpxMonitorTask.id',
  CHANGE COLUMN link_id l_id VARCHAR(32) NULL COMMENT 'OcpxPromotionLink.id';

ALTER TABLE scx_ocpx_log_feedback_day
  CHANGE COLUMN channel_id m_id VARCHAR(32) NULL COMMENT 'OcpxMedia.id',
  CHANGE COLUMN dsp_id a_id VARCHAR(32) NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  CHANGE COLUMN monitor_id t_id VARCHAR(32) NULL COMMENT 'OcpxMonitorTask.id',
  CHANGE COLUMN link_id l_id VARCHAR(32) NULL COMMENT 'OcpxPromotionLink.id';

ALTER TABLE scx_ocpx_log_callback
  CHANGE COLUMN channel_id m_id VARCHAR(32) NULL COMMENT 'OcpxMedia.id',
  CHANGE COLUMN dsp_id a_id VARCHAR(32) NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  CHANGE COLUMN monitor_id t_id VARCHAR(32) NULL COMMENT 'OcpxMonitorTask.id',
  CHANGE COLUMN link_id l_id VARCHAR(32) NULL COMMENT 'OcpxPromotionLink.id';

ALTER TABLE scx_ocpx_report_mat_temp
  CHANGE COLUMN channel_id m_id VARCHAR(32) NOT NULL COMMENT 'OcpxMedia.id',
  CHANGE COLUMN dsp_id a_id VARCHAR(32) NOT NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  CHANGE COLUMN monitor_id t_id VARCHAR(32) NOT NULL COMMENT 'OcpxMonitorTask.id',
  CHANGE COLUMN link_id l_id VARCHAR(32) NOT NULL COMMENT 'OcpxPromotionLink.id';

ALTER TABLE scx_ocpx_report_mat
  CHANGE COLUMN channel_id m_id VARCHAR(32) NOT NULL COMMENT 'OcpxMedia.id',
  CHANGE COLUMN dsp_id a_id VARCHAR(32) NOT NULL COMMENT 'OcpxAdvertiser.id; distinct from adv_id',
  CHANGE COLUMN monitor_id t_id VARCHAR(32) NOT NULL COMMENT 'OcpxMonitorTask.id',
  CHANGE COLUMN link_id l_id VARCHAR(32) NOT NULL COMMENT 'OcpxPromotionLink.id';
