-- 每个链接事件映射可设置单价和独立日限量；限量会参与归因超量判定，单价仅作为配置事实，不直接入账。
DROP PROCEDURE IF EXISTS scx_add_event_mapping_pricing_columns;

DELIMITER $$
CREATE PROCEDURE scx_add_event_mapping_pricing_columns()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_link_event_mapping'
      AND column_name = 'unit_price'
  ) THEN
    ALTER TABLE scx_link_event_mapping
      ADD COLUMN unit_price DECIMAL(18,6) NOT NULL DEFAULT 0 AFTER external_event;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_link_event_mapping'
      AND column_name = 'daily_limit'
  ) THEN
    ALTER TABLE scx_link_event_mapping
      ADD COLUMN daily_limit INT NULL AFTER unit_price;
  END IF;
END$$
DELIMITER ;

CALL scx_add_event_mapping_pricing_columns();
DROP PROCEDURE scx_add_event_mapping_pricing_columns;
