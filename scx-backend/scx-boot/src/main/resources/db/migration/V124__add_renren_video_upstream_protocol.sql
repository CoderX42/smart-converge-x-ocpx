-- V124: register the independent RENREN_VIDEO upstream protocol.
-- This migration only expands protocol constraints. It deliberately does not
-- assign an advertiser or seed an allowed host; administrators must confirm
-- the production click-template host before enabling outbound delivery.

ALTER TABLE scx_ocpx_advertiser
  DROP CHECK ck_ocpx_advertiser_upstream_protocol,
  ADD CONSTRAINT ck_ocpx_advertiser_upstream_protocol
    CHECK (upstream_protocol IS NULL OR upstream_protocol IN
      ('TEMPLATE_GET','AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'));

ALTER TABLE scx_ocpx_protocol_allowed_host
  DROP CHECK ck_ocpx_protocol_allowed_host_type,
  ADD CONSTRAINT ck_ocpx_protocol_allowed_host_type
    CHECK (protocol_type IN ('AERIM','WEIFENG','GRAVITY','RENREN_VIDEO'));
