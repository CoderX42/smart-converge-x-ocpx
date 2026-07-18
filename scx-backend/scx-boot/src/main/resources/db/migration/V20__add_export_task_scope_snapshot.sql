ALTER TABLE scx_export_task
  ADD COLUMN task_scope_snapshot TEXT NULL AFTER report_end_date;
