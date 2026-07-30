-- V62: establish the configuration hierarchy
-- Media -> Delivery Channel -> Media Account -> Placement.
--
-- Existing account ids and API paths remain stable. Legacy accounts stay
-- readable when their channel cannot be proven; only an unambiguous
-- single-channel media is backfilled automatically.

SET @scx_v62_add_channel_column = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_media_account'
      AND column_name = 'channel_id'
  ),
  'SELECT 1',
  'ALTER TABLE scx_media_account
     ADD COLUMN channel_id VARCHAR(64) NULL
       COMMENT ''scx_media_channel.id; legacy rows may be unlinked''
       AFTER media_id'
);
PREPARE scx_v62_column_statement FROM @scx_v62_add_channel_column;
EXECUTE scx_v62_column_statement;
DEALLOCATE PREPARE scx_v62_column_statement;

SET @scx_v62_add_channel_index = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'scx_media_account'
      AND index_name = 'idx_ma_channel'
  ),
  'SELECT 1',
  'CREATE INDEX idx_ma_channel ON scx_media_account (channel_id)'
);
PREPARE scx_v62_index_statement FROM @scx_v62_add_channel_index;
EXECUTE scx_v62_index_statement;
DEALLOCATE PREPARE scx_v62_index_statement;

-- Backfill only when there is exactly one possible channel for the media.
UPDATE scx_media_account account
JOIN (
  SELECT media_id, MIN(id) AS channel_id
  FROM scx_media_channel
  GROUP BY media_id
  HAVING COUNT(*) = 1
) single_channel
  ON single_channel.media_id = account.media_id
SET account.channel_id = single_channel.channel_id,
    account.update_time = NOW()
WHERE account.channel_id IS NULL;

UPDATE sys_menu
SET name = CASE id
      WHEN 'menu_ad_channels' THEN '投放渠道'
      WHEN 'menu_inv_account' THEN '媒体投放账户'
      WHEN 'perm_inv_account_read' THEN '查询媒体投放账户'
      WHEN 'perm_inv_account_write' THEN '管理媒体投放账户'
      ELSE name
    END,
    update_time = NOW()
WHERE id IN (
  'menu_ad_channels',
  'menu_inv_account',
  'perm_inv_account_read',
  'perm_inv_account_write'
);
