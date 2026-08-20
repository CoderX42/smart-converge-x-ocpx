-- V120: register the independent GRAVITY upstream protocol and its exact
-- outbound host. This migration only expands protocol constraints; it does not
-- enable GRAVITY delivery or assign any advertiser to the protocol.

ALTER TABLE scx_ocpx_advertiser
  DROP CHECK ck_ocpx_advertiser_upstream_protocol,
  ADD CONSTRAINT ck_ocpx_advertiser_upstream_protocol
    CHECK (upstream_protocol IS NULL OR upstream_protocol IN ('TEMPLATE_GET','AERIM','WEIFENG','GRAVITY'));

ALTER TABLE scx_ocpx_protocol_allowed_host
  DROP CHECK ck_ocpx_protocol_allowed_host_type,
  ADD CONSTRAINT ck_ocpx_protocol_allowed_host_type
    CHECK (protocol_type IN ('AERIM','WEIFENG','GRAVITY'));

INSERT INTO scx_ocpx_protocol_allowed_host
  (id,protocol_type,host,create_time,update_time)
VALUES
  ('v120_gravity_backend','GRAVITY','backend.gravity-engine.com',NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE host=VALUES(host),update_time=NOW(3);
