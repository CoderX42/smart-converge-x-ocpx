-- Reconcile existing databases that received the dashboard columns before the
-- V77 click-deduplication foundation. Forward-only and safe to execute again.

DROP PROCEDURE IF EXISTS scx_reconcile_ocpx_dashboard_columns;
DELIMITER $$
CREATE PROCEDURE scx_reconcile_ocpx_dashboard_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_monitor_link' AND column_name='click_dedup_window_seconds') THEN
        ALTER TABLE scx_adx_monitor_link ADD COLUMN click_dedup_window_seconds INT NOT NULL DEFAULT 3600 AFTER billing_mode;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='device_platform') THEN
        ALTER TABLE scx_ocpx_event ADD COLUMN device_platform VARCHAR(16) NULL AFTER received_at;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='device_id_type') THEN
        ALTER TABLE scx_ocpx_event ADD COLUMN device_id_type VARCHAR(32) NULL AFTER device_platform;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='device_key_version') THEN
        ALTER TABLE scx_ocpx_event ADD COLUMN device_key_version INT NULL AFTER device_id_type;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='eligible_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN eligible_click_count BIGINT NOT NULL DEFAULT 0 AFTER clicks;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='deduplicated_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN deduplicated_click_count BIGINT NOT NULL DEFAULT 0 AFTER eligible_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='skipped_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN skipped_click_count BIGINT NOT NULL DEFAULT 0 AFTER deduplicated_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='classified_click_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN classified_click_count BIGINT NOT NULL DEFAULT 0 AFTER skipped_click_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='first_activate_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN first_activate_count BIGINT NOT NULL DEFAULT 0 AFTER activate_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='reactivation_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN reactivation_count BIGINT NOT NULL DEFAULT 0 AFTER evoke_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_3d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_3d_count BIGINT NOT NULL DEFAULT 0 AFTER next_stay_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_4d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_4d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_3d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_5d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_5d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_4d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_6d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_6d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_5d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='retention_7d_count') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN retention_7d_count BIGINT NOT NULL DEFAULT 0 AFTER retention_6d_count;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='create_time') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report' AND column_name='update_time') THEN
        ALTER TABLE scx_adx_hourly_report ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
    END IF;
END$$
DELIMITER ;

CALL scx_reconcile_ocpx_dashboard_columns();
DROP PROCEDURE scx_reconcile_ocpx_dashboard_columns;

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_state (
    monitor_id VARCHAR(32) NOT NULL,
    device_key_hash CHAR(64) NOT NULL,
    canonical_event_id VARCHAR(128) NOT NULL,
    canonical_received_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (monitor_id, device_key_hash),
    KEY idx_scx_ocpx_click_state_time (canonical_received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Latest eligible click per route and device';

CREATE TABLE IF NOT EXISTS scx_ocpx_click_dedup_decision (
    event_id VARCHAR(128) NOT NULL,
    monitor_id VARCHAR(32) NOT NULL,
    device_key_hash CHAR(64) NULL,
    window_seconds INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    canonical_event_id VARCHAR(128) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id),
    KEY idx_scx_ocpx_click_decision_route (monitor_id, received_at, status),
    KEY idx_scx_ocpx_click_decision_device (monitor_id, device_key_hash, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable OCPX click deduplication decision';

-- Evidence-only backfill. Rows without immutable decisions deliberately remain
-- uncovered, so rate fields stay null instead of manufacturing history.
DROP PROCEDURE IF EXISTS scx_backfill_adx_dashboard_click_quality;
DELIMITER $$
CREATE PROCEDURE scx_backfill_adx_dashboard_click_quality()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event')
        AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_event' AND column_name='is_debug') THEN
        UPDATE scx_adx_hourly_report r
            JOIN (
                SELECT DATE_FORMAT(e.occurred_at,'%Y-%m-%d %H:00:00') AS report_hour,
                       e.channel_id,e.advertiser_id,e.app_link_id,e.monitor_id,
                       SUM(CASE WHEN d.status='ELIGIBLE' THEN 1 ELSE 0 END) AS eligible_count,
                       SUM(CASE WHEN d.status='DEDUPLICATED' THEN 1 ELSE 0 END) AS deduplicated_count,
                       SUM(CASE WHEN d.status='SKIPPED_NO_DEVICE' THEN 1 ELSE 0 END) AS skipped_count,
                       COUNT(*) AS classified_count
                FROM scx_ocpx_click_dedup_decision d
                         JOIN scx_ocpx_event e ON e.event_id=d.event_id
                WHERE e.source_direction='DOWNSTREAM_FEEDBACK' AND e.event_type='CLICK' AND e.is_debug=0
                GROUP BY report_hour,e.channel_id,e.advertiser_id,e.app_link_id,e.monitor_id
            ) q ON r.time_hour=q.report_hour AND r.channel_id=q.channel_id AND r.advertiser_id=q.advertiser_id
                AND r.app_link_id=q.app_link_id AND r.monitor_id=q.monitor_id
        SET r.eligible_click_count=q.eligible_count,
            r.deduplicated_click_count=q.deduplicated_count,
            r.skipped_click_count=q.skipped_count,
            r.classified_click_count=q.classified_count
        WHERE r.id IS NOT NULL;
    END IF;
END$$
DELIMITER ;

CALL scx_backfill_adx_dashboard_click_quality();
DROP PROCEDURE scx_backfill_adx_dashboard_click_quality;
