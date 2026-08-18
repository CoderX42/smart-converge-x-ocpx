-- Forward-only media callback deduction facts, ratio validation and reporting.
--
-- Back up and preview the target database before applying this migration.
-- Historical callback decisions are intentionally not inferred or backfilled.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v113_media_deduction$$
CREATE PROCEDURE preflight_v113_media_deduction()
BEGIN
  DECLARE invalid_ratio_count BIGINT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_ratio_count
  FROM scx_ocpx_deduction_rule
  WHERE ratio<0 OR ratio>1;

  IF invalid_ratio_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V113 blocked: stored media callback ratios must be between 0 and 1';
  END IF;
END$$
CALL preflight_v113_media_deduction()$$
DROP PROCEDURE preflight_v113_media_deduction$$
DELIMITER ;

ALTER TABLE scx_ocpx_deduction_rule
  MODIFY COLUMN ratio DECIMAL(9,6) NOT NULL DEFAULT 1.000000,
  ADD CONSTRAINT ck_ocpx_deduction_ratio CHECK (ratio>=0 AND ratio<=1),
  COMMENT='BUSINESS: link-media callback pass ratio rule';

ALTER TABLE scx_ocpx_event_fact
  ADD COLUMN deduction_decision VARCHAR(16) NULL AFTER conversion_event_snapshot,
  ADD COLUMN deduction_ratio_snapshot DECIMAL(9,6) NULL AFTER deduction_decision,
  ADD COLUMN deduction_decided_at DATETIME(3) NULL AFTER deduction_ratio_snapshot,
  ADD KEY idx_ocpx_event_fact_deduction_time (
    deduction_decided_at,advertiser_id,media_id,monitor_task_id,promotion_link_id
  ),
  ADD CONSTRAINT ck_ocpx_event_fact_deduction_decision CHECK (
    deduction_decision IS NULL OR deduction_decision IN ('PENDING','PASSED','DEDUCTED')
  ),
  ADD CONSTRAINT ck_ocpx_event_fact_deduction_ratio CHECK (
    deduction_ratio_snapshot IS NULL
    OR (deduction_ratio_snapshot>=0 AND deduction_ratio_snapshot<=1)
  ),
  ADD CONSTRAINT ck_ocpx_event_fact_deduction_state CHECK (
    (deduction_decision IS NULL AND deduction_ratio_snapshot IS NULL AND deduction_decided_at IS NULL)
    OR (deduction_decision='PENDING' AND deduction_ratio_snapshot IS NULL AND deduction_decided_at IS NULL)
    OR (deduction_decision IN ('PASSED','DEDUCTED')
        AND deduction_ratio_snapshot IS NOT NULL AND deduction_decided_at IS NOT NULL)
  );

ALTER TABLE scx_ocpx_hourly_metric
  ADD COLUMN callback_count BIGINT NOT NULL DEFAULT 0 AFTER target_conversion_count,
  ADD COLUMN deduction_passed_count BIGINT NOT NULL DEFAULT 0 AFTER callback_count,
  ADD COLUMN deduction_deducted_count BIGINT NOT NULL DEFAULT 0 AFTER deduction_passed_count,
  DROP CHECK ck_ocpx_hourly_nonnegative,
  ADD CONSTRAINT ck_ocpx_hourly_nonnegative CHECK (
    impression_count>=0 AND click_count>=0 AND target_conversion_count>=0
    AND callback_count>=0 AND deduction_passed_count>=0 AND deduction_deducted_count>=0
    AND deduction_passed_count+deduction_deducted_count<=callback_count
    AND spend_amount>=0 AND currency_amount>=0
    AND rejected_click_count>=0 AND rejected_conversion_count>=0
  );
