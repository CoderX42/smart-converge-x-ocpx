-- Forward-only corrective migration: downstream media share the generic
-- /feedback ingress URI, so the media URI must not be unique. Drop the unique
-- index added by V102 (uk_ocpx_media_uri). Back up and preview the exact
-- target before applying; initialization never auto-runs this directory.

USE `smart_converge_x`;
SET NAMES utf8mb4;

ALTER TABLE scx_ocpx_media
  DROP INDEX uk_ocpx_media_uri;
