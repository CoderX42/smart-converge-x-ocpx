-- V123: make the count-free high-volume flow-log page query use an ordered index scan.
-- Production execution must still be preceded by backup, DDL review and approval.

ALTER TABLE scx_ocpx_flow_log
  ADD KEY idx_ocpx_flow_log_page (event_time,id),
  ALGORITHM=INPLACE,
  LOCK=NONE;
