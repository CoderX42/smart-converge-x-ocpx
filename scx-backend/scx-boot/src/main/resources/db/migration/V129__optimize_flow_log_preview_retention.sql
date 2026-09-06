-- V129: make sparse response-preview retention cleanup selective.
-- Execute only after backup, online-DDL capability review and approval.

SET @scx_preview_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema=DATABASE()
    AND table_name='scx_ocpx_flow_log'
    AND column_name='response_preview_present'
);
SET @scx_preview_column_sql := IF(
  @scx_preview_column_exists=0,
  'ALTER TABLE scx_ocpx_flow_log ADD COLUMN response_preview_present TINYINT GENERATED ALWAYS AS (IF(response_preview IS NULL,0,1)) VIRTUAL AFTER response_preview, ALGORITHM=INPLACE, LOCK=NONE',
  'SELECT 1'
);
PREPARE scx_preview_column_stmt FROM @scx_preview_column_sql;
EXECUTE scx_preview_column_stmt;
DEALLOCATE PREPARE scx_preview_column_stmt;

SET @scx_preview_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema=DATABASE()
    AND table_name='scx_ocpx_flow_log'
    AND index_name='idx_ocpx_flow_log_preview_retention'
);
SET @scx_preview_index_sql := IF(
  @scx_preview_index_exists=0,
  'ALTER TABLE scx_ocpx_flow_log ADD KEY idx_ocpx_flow_log_preview_retention (response_preview_present,event_time,id), ALGORITHM=INPLACE, LOCK=NONE',
  'SELECT 1'
);
PREPARE scx_preview_index_stmt FROM @scx_preview_index_sql;
EXECUTE scx_preview_index_stmt;
DEALLOCATE PREPARE scx_preview_index_stmt;
