-- 为旧 M0 主体表补充 MVP 必需的乐观锁和审计列。
-- 使用 information_schema 检查，兼容已经存在 scx_media 的本地库。
SET @schema_name = DATABASE();
SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='scx_media' AND column_name='version'
);
SET @statement = IF(@has_column=0,
  'ALTER TABLE scx_media ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER settlement_currency',
  'SELECT 1');
PREPARE scx_mvp_statement FROM @statement;
EXECUTE scx_mvp_statement;
DEALLOCATE PREPARE scx_mvp_statement;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='scx_media' AND column_name='create_by'
);
SET @statement = IF(@has_column=0,
  'ALTER TABLE scx_media ADD COLUMN create_by VARCHAR(64) NULL AFTER version',
  'SELECT 1');
PREPARE scx_mvp_statement FROM @statement;
EXECUTE scx_mvp_statement;
DEALLOCATE PREPARE scx_mvp_statement;

SET @has_column = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema=@schema_name AND table_name='scx_media' AND column_name='update_by'
);
SET @statement = IF(@has_column=0,
  'ALTER TABLE scx_media ADD COLUMN update_by VARCHAR(64) NULL AFTER create_by',
  'SELECT 1');
PREPARE scx_mvp_statement FROM @statement;
EXECUTE scx_mvp_statement;
DEALLOCATE PREPARE scx_mvp_statement;
