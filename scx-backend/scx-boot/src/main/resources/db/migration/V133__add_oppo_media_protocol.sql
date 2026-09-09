-- V132 -> V133. Explicit one-time forward change; back up and approve target before execution.
ALTER TABLE scx_ocpx_rta_media_access
  ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'GENERIC_ASYNC',
  ADD COLUMN protocol_config JSON NULL;
ALTER TABLE scx_ocpx_rta_binding
  ADD COLUMN strategy_id INT NOT NULL AUTO_INCREMENT UNIQUE,
  ADD COLUMN oppo_pkg VARCHAR(128) NOT NULL DEFAULT '';
