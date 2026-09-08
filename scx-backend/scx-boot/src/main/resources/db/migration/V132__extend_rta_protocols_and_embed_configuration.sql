-- Forward-only. Back up and review before applying to an existing environment.
-- The V131 protocol CHECK was unnamed; locate precisely rather than assuming its generated name.
DELIMITER $$
CREATE PROCEDURE scx_rta132_protocol_constraint()
BEGIN
  DECLARE finished INT DEFAULT 0;
  DECLARE check_name VARCHAR(64);
  DECLARE checks_cursor CURSOR FOR
    SELECT tc.constraint_name FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
    WHERE tc.constraint_schema=DATABASE() AND tc.table_name='scx_ocpx_rta_upstream'
      AND tc.constraint_type='CHECK' AND cc.check_clause LIKE '%protocol%' AND cc.check_clause LIKE '%GENERIC_ASYNC%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=1;
  OPEN checks_cursor;
  drop_loop: LOOP
    FETCH checks_cursor INTO check_name;
    IF finished=1 THEN LEAVE drop_loop; END IF;
    SET @scx_rta132_sql=CONCAT('ALTER TABLE scx_ocpx_rta_upstream DROP CHECK `',REPLACE(check_name,'`','``'),'`');
    PREPARE scx_rta132_statement FROM @scx_rta132_sql;
    EXECUTE scx_rta132_statement;
    DEALLOCATE PREPARE scx_rta132_statement;
  END LOOP;
  CLOSE checks_cursor;
END$$
CALL scx_rta132_protocol_constraint()$$
DROP PROCEDURE scx_rta132_protocol_constraint$$
DELIMITER ;
ALTER TABLE scx_ocpx_rta_upstream ADD COLUMN protocol_config JSON NULL AFTER protocol,
  ADD COLUMN config_schema_version INT NOT NULL DEFAULT 1 AFTER protocol_config,
  ADD CONSTRAINT ck_rta_config_version CHECK(config_schema_version>0);
ALTER TABLE scx_ocpx_rta_binding MODIFY product_id VARCHAR(128) COLLATE utf8mb4_bin NULL,
  MODIFY external_task_id VARCHAR(128) COLLATE utf8mb4_bin NULL;

-- RTA configuration belongs to advertiser/media/task; preserve permission IDs and grants.
UPDATE sys_menu SET name='查看 RTA 接入',path='',component='',icon='',type='1',parent_id='menu_ocpx_advertiser' WHERE id='menu_ocpx_rta_upstreams';
UPDATE sys_menu SET parent_id='menu_ocpx_advertiser' WHERE id='perm_ocpx_rta_upstreams_edit';
UPDATE sys_menu SET name='查看 RTA 配置',path='',component='',icon='',type='1',parent_id='menu_ocpx_media' WHERE id='menu_ocpx_rta_media';
UPDATE sys_menu SET parent_id='menu_ocpx_media' WHERE id='perm_ocpx_rta_media_edit';
UPDATE sys_menu SET name='查看 RTA 绑定',path='',component='',icon='',type='1',parent_id='menu_ocpx_monitor_task' WHERE id='menu_ocpx_rta_bindings';
UPDATE sys_menu SET parent_id='menu_ocpx_monitor_task' WHERE id='perm_ocpx_rta_bindings_edit';
