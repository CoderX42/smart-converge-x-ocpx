-- Restrict each channel client to the explicitly granted machine endpoints.
-- Existing clients retain the historical behavior through the all-scope default.
DELIMITER $$
CREATE PROCEDURE scx_add_channel_endpoint_scopes()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_channel_client'
      AND column_name = 'allowed_scopes'
  ) THEN
    ALTER TABLE scx_channel_client
      ADD COLUMN allowed_scopes VARCHAR(64) NOT NULL
        DEFAULT 'CLICK,CONVERSION,RTA'
        AFTER source_code;
  END IF;
END$$
DELIMITER ;

CALL scx_add_channel_endpoint_scopes();
DROP PROCEDURE scx_add_channel_endpoint_scopes;
