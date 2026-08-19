-- V117: retain a bounded, sanitized upstream response preview for flow-log diagnosis.
-- Raw outbound URLs, request signatures and credentials remain outside the flow log.
ALTER TABLE scx_ocpx_flow_log
  ADD COLUMN response_preview VARCHAR(4096) NULL AFTER message;
