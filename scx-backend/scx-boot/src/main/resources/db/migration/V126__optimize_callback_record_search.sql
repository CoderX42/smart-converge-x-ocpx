-- V126: keep the authoritative Callback-record query on a selective, ordered index.
-- Production execution must still be preceded by backup, EXPLAIN review and approval.

ALTER TABLE scx_ocpx_event_fact
  ADD KEY idx_ocpx_event_fact_callback_page (flow_type,received_at,id),
  ALGORITHM=INPLACE,
  LOCK=NONE;
