-- V125: make bounded OCPX log retention deletes use time-range indexes.
-- Execute only after backup, EXPLAIN review and an approved low-traffic window.

ALTER TABLE scx_ocpx_log_feedback
  ADD KEY idx_ocpx_feedback_create_time (create_time),
  ALGORITHM=INPLACE,
  LOCK=NONE;

ALTER TABLE scx_ocpx_log_callback
  ADD KEY idx_ocpx_callback_create_time (create_time),
  ALGORITHM=INPLACE,
  LOCK=NONE;

ALTER TABLE scx_ocpx_log_feedback_day
  ADD KEY idx_ocpx_feedback_day_time_hour_res (time_hour_res),
  ALGORITHM=INPLACE,
  LOCK=NONE;
