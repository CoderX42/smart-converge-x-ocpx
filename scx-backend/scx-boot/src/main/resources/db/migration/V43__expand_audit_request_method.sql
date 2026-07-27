-- The audit aspect stores the fully-qualified Java method name, which is longer
-- than the historical HTTP-verb-sized column. Expand it without rewriting any
-- previously applied migration.
DELIMITER $$
CREATE PROCEDURE scx_expand_audit_request_method()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_log'
      AND column_name = 'request_method'
      AND character_maximum_length < 255
  ) THEN
    ALTER TABLE sys_log
      MODIFY COLUMN request_method VARCHAR(255) NULL COMMENT '请求处理方法';
  END IF;
END$$
DELIMITER ;

CALL scx_expand_audit_request_method();
DROP PROCEDURE scx_expand_audit_request_method;
