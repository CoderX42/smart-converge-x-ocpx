ALTER TABLE scx_short_link
  ADD COLUMN source_url VARCHAR(1000) NULL AFTER target_url;
