-- Rename the historical ADX management namespace to the canonical OCPX namespace.
--
-- This is a forward-only metadata migration. It does not copy, delete, or infer
-- business data. Rerunning after a complete application is a no-op. A partially
-- completed run can resume, while an ambiguous source/target pair fails closed.

DROP TEMPORARY TABLE IF EXISTS scx_v92_table_rename;
CREATE TEMPORARY TABLE scx_v92_table_rename (
  old_name VARCHAR(64) NOT NULL PRIMARY KEY,
  new_name VARCHAR(64) NOT NULL UNIQUE KEY
);

INSERT INTO scx_v92_table_rename (old_name,new_name) VALUES
  ('scx_adx_advertiser','scx_ocpx_advertiser'),
  ('scx_adx_advertiser_change_audit','scx_ocpx_advertiser_change_audit'),
  ('scx_adx_advertiser_credential_revision','scx_ocpx_advertiser_credential_revision'),
  ('scx_adx_app_link','scx_ocpx_app_link'),
  ('scx_adx_app_link_change_audit','scx_ocpx_app_link_change_audit'),
  ('scx_adx_channel','scx_ocpx_channel'),
  ('scx_adx_channel_event_mapping','scx_ocpx_channel_event_mapping'),
  ('scx_adx_deduction_rule','scx_ocpx_deduction_rule'),
  ('scx_adx_event_change_audit','scx_ocpx_event_change_audit'),
  ('scx_adx_event_definition','scx_ocpx_event_definition'),
  ('scx_adx_hourly_adjustment','scx_ocpx_hourly_adjustment'),
  ('scx_adx_hourly_aggregation_cursor','scx_ocpx_hourly_aggregation_cursor'),
  ('scx_adx_hourly_fact_contribution','scx_ocpx_hourly_fact_contribution'),
  ('scx_adx_hourly_report','scx_ocpx_hourly_report'),
  ('scx_adx_integration_document','scx_ocpx_integration_document'),
  ('scx_adx_media_account','scx_ocpx_media_account'),
  ('scx_adx_media_account_change_audit','scx_ocpx_media_account_change_audit'),
  ('scx_adx_media_account_owner','scx_ocpx_media_account_owner'),
  ('scx_adx_media_credential','scx_ocpx_media_credential'),
  ('scx_adx_media_credential_revision','scx_ocpx_media_credential_revision'),
  ('scx_adx_monitor_event_binding','scx_ocpx_monitor_event_binding'),
  ('scx_adx_monitor_link','scx_ocpx_monitor_link'),
  ('scx_adx_product','scx_ocpx_product'),
  ('scx_adx_product_change_audit','scx_ocpx_product_change_audit'),
  ('scx_adx_product_event_policy','scx_ocpx_product_event_policy'),
  ('scx_adx_product_logo_object','scx_ocpx_product_logo_object'),
  ('scx_adx_report_export_audit','scx_ocpx_report_export_audit'),
  ('scx_adx_report_export_task','scx_ocpx_report_export_task'),
  ('scx_adx_route_distribution','scx_ocpx_route_distribution'),
  ('scx_adx_route_event_snapshot','scx_ocpx_route_event_snapshot'),
  ('scx_adx_route_instance_ack','scx_ocpx_route_instance_ack'),
  ('scx_adx_route_pause_command','scx_ocpx_route_pause_command'),
  ('scx_adx_route_publish_record','scx_ocpx_route_publish_record'),
  ('scx_adx_route_runtime_state','scx_ocpx_route_runtime_state'),
  ('scx_adx_route_version','scx_ocpx_route_version'),
  ('scx_adx_runtime_command_audit','scx_ocpx_runtime_command_audit'),
  ('scx_adx_runtime_fact','scx_ocpx_runtime_fact'),
  ('scx_adx_runtime_failure','scx_ocpx_runtime_failure'),
  ('scx_adx_runtime_replay_attempt','scx_ocpx_runtime_replay_attempt'),
  ('scx_adx_runtime_replay_request','scx_ocpx_runtime_replay_request'),
  ('scx_adx_task','scx_ocpx_task'),
  ('scx_adx_task_change_audit','scx_ocpx_task_change_audit'),
  ('scx_adx_task_event_binding','scx_ocpx_task_event_binding'),
  ('scx_adx_user_scope','scx_ocpx_user_scope'),
  ('scx_adx_user_scope_revision','scx_ocpx_user_scope_revision');

DROP PROCEDURE IF EXISTS scx_v92_preflight_tables;
DELIMITER $$
CREATE PROCEDURE scx_v92_preflight_tables()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_v92_table_rename mapping
  LEFT JOIN information_schema.tables old_table
    ON old_table.table_schema=DATABASE() AND old_table.table_name=mapping.old_name
  LEFT JOIN information_schema.tables new_table
    ON new_table.table_schema=DATABASE() AND new_table.table_name=mapping.new_name
  WHERE (old_table.table_name IS NOT NULL)+(new_table.table_name IS NOT NULL)<>1;

  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V92 table preflight failed; each mapping must have exactly one source or target table';
  END IF;
END$$
DELIMITER ;
CALL scx_v92_preflight_tables();
DROP PROCEDURE scx_v92_preflight_tables;

DROP PROCEDURE IF EXISTS scx_v92_rename_tables;
DELIMITER $$
CREATE PROCEDURE scx_v92_rename_tables()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE old_name VARCHAR(64);
  DECLARE new_name VARCHAR(64);
  DECLARE old_count INT DEFAULT 0;
  DECLARE mappings CURSOR FOR SELECT mapping.old_name,mapping.new_name FROM scx_v92_table_rename mapping;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;

  OPEN mappings;
  rename_loop: LOOP
    FETCH mappings INTO old_name,new_name;
    IF done=1 THEN LEAVE rename_loop; END IF;
    SELECT COUNT(*) INTO old_count FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name=old_name;
    IF old_count=1 THEN
      SET @ddl=CONCAT('RENAME TABLE `',REPLACE(old_name,'`','``'),'` TO `',
        REPLACE(new_name,'`','``'),'`');
      PREPARE stmt FROM @ddl;
      EXECUTE stmt;
      DEALLOCATE PREPARE stmt;
    END IF;
  END LOOP;
  CLOSE mappings;
END$$
DELIMITER ;

CALL scx_v92_rename_tables();
DROP PROCEDURE scx_v92_rename_tables;
DROP TEMPORARY TABLE scx_v92_table_rename;

-- RENAME TABLE keeps dependent objects operational but retains their old names.
-- Rename indexes directly, then rebuild named foreign keys and CHECK constraints
-- from information_schema without changing their definitions.
DROP PROCEDURE IF EXISTS scx_v92_rename_indexes;
DELIMITER $$
CREATE PROCEDURE scx_v92_rename_indexes()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE object_table VARCHAR(64);
  DECLARE old_name VARCHAR(64);
  DECLARE new_name VARCHAR(64);
  DECLARE existing_count INT DEFAULT 0;
  DECLARE objects CURSOR FOR
    SELECT DISTINCT table_name,index_name
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND LEFT(table_name,9)='scx_ocpx_'
      AND index_name LIKE '%adx%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;

  OPEN objects;
  rename_loop: LOOP
    FETCH objects INTO object_table,old_name;
    IF done=1 THEN LEAVE rename_loop; END IF;
    SET new_name=REPLACE(old_name,'adx','ocpx');
    SELECT COUNT(*) INTO existing_count FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name=object_table AND index_name=new_name;
    IF existing_count<>0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target index already exists';
    END IF;
    SET @ddl=CONCAT('ALTER TABLE `',REPLACE(object_table,'`','``'),'` RENAME INDEX `',
      REPLACE(old_name,'`','``'),'` TO `',REPLACE(new_name,'`','``'),'`');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE objects;
END$$
DELIMITER ;
CALL scx_v92_rename_indexes();
DROP PROCEDURE scx_v92_rename_indexes;

DROP PROCEDURE IF EXISTS scx_v92_rename_foreign_keys;
DELIMITER $$
CREATE PROCEDURE scx_v92_rename_foreign_keys()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE object_table VARCHAR(64);
  DECLARE old_name VARCHAR(64);
  DECLARE local_columns TEXT;
  DECLARE referenced_table VARCHAR(64);
  DECLARE referenced_columns TEXT;
  DECLARE update_rule VARCHAR(16);
  DECLARE delete_rule VARCHAR(16);
  DECLARE new_name VARCHAR(64);
  DECLARE existing_count INT DEFAULT 0;
  DECLARE objects CURSOR FOR
    SELECT rc.table_name,rc.constraint_name,
      GROUP_CONCAT(CONCAT('`',REPLACE(kcu.column_name,'`','``'),'`') ORDER BY kcu.ordinal_position),
      MAX(kcu.referenced_table_name),
      GROUP_CONCAT(CONCAT('`',REPLACE(kcu.referenced_column_name,'`','``'),'`') ORDER BY kcu.ordinal_position),
      rc.update_rule,rc.delete_rule
    FROM information_schema.referential_constraints rc
    JOIN information_schema.key_column_usage kcu
      ON kcu.constraint_schema=rc.constraint_schema
      AND kcu.table_name=rc.table_name AND kcu.constraint_name=rc.constraint_name
    WHERE rc.constraint_schema=DATABASE() AND rc.constraint_name LIKE '%adx%'
    GROUP BY rc.table_name,rc.constraint_name,rc.update_rule,rc.delete_rule;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;

  OPEN objects;
  rename_loop: LOOP
    FETCH objects INTO object_table,old_name,local_columns,referenced_table,referenced_columns,update_rule,delete_rule;
    IF done=1 THEN LEAVE rename_loop; END IF;
    SET new_name=REPLACE(old_name,'adx','ocpx');
    SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name=object_table AND constraint_name=new_name;
    IF existing_count<>0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target foreign key already exists';
    END IF;
    SET @ddl=CONCAT('ALTER TABLE `',REPLACE(object_table,'`','``'),'` DROP FOREIGN KEY `',
      REPLACE(old_name,'`','``'),'`, ADD CONSTRAINT `',REPLACE(new_name,'`','``'),'` FOREIGN KEY (',
      local_columns,') REFERENCES `',REPLACE(referenced_table,'`','``'),'` (',referenced_columns,
      ') ON UPDATE ',update_rule,' ON DELETE ',delete_rule);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE objects;
END$$
DELIMITER ;
CALL scx_v92_rename_foreign_keys();
DROP PROCEDURE scx_v92_rename_foreign_keys;

DROP PROCEDURE IF EXISTS scx_v92_rename_checks;
DELIMITER $$
CREATE PROCEDURE scx_v92_rename_checks()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE object_table VARCHAR(64);
  DECLARE old_name VARCHAR(64);
  DECLARE check_clause LONGTEXT;
  DECLARE new_name VARCHAR(64);
  DECLARE existing_count INT DEFAULT 0;
  DECLARE objects CURSOR FOR
    SELECT tc.table_name,tc.constraint_name,cc.check_clause
    FROM information_schema.table_constraints tc
    JOIN information_schema.check_constraints cc
      ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
    WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK'
      AND tc.constraint_name LIKE '%adx%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;

  OPEN objects;
  rename_loop: LOOP
    FETCH objects INTO object_table,old_name,check_clause;
    IF done=1 THEN LEAVE rename_loop; END IF;
    SET new_name=REPLACE(old_name,'adx','ocpx');
    SELECT COUNT(*) INTO existing_count FROM information_schema.table_constraints
    WHERE constraint_schema=DATABASE() AND table_name=object_table AND constraint_name=new_name;
    IF existing_count<>0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target CHECK constraint already exists';
    END IF;
    -- MySQL exposes quoted string literals in CHECK_CLAUSE as backslash-escaped
    -- metadata. Normalize those literals before preparing the replacement DDL.
    SET check_clause=REPLACE(check_clause,CONCAT(CHAR(92),CHAR(39)),CHAR(39));
    SET @ddl=CONCAT('ALTER TABLE `',REPLACE(object_table,'`','``'),'` DROP CHECK `',
      REPLACE(old_name,'`','``'),'`, ADD CONSTRAINT `',REPLACE(new_name,'`','``'),'` CHECK (',check_clause,')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE objects;
END$$
DELIMITER ;
CALL scx_v92_rename_checks();
DROP PROCEDURE scx_v92_rename_checks;

-- CREATE TRIGGER is not supported by MySQL's prepared-statement protocol, so
-- canonicalize the fixed set of immutable-history triggers explicitly.
DROP TRIGGER IF EXISTS trg_scx_adx_route_event_snapshot_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_route_event_snapshot_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_route_version_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_route_version_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_hourly_adjustment_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_hourly_adjustment_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_fact_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_fact_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_failure_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_failure_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_replay_attempt_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_replay_attempt_no_delete;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_command_audit_no_update;
DROP TRIGGER IF EXISTS trg_scx_adx_runtime_command_audit_no_delete;

DROP TRIGGER IF EXISTS trg_scx_ocpx_route_event_snapshot_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_route_event_snapshot_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_route_version_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_route_version_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_hourly_adjustment_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_hourly_adjustment_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_fact_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_fact_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_failure_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_failure_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_replay_attempt_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_replay_attempt_no_delete;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_command_audit_no_update;
DROP TRIGGER IF EXISTS trg_scx_ocpx_runtime_command_audit_no_delete;

DELIMITER $$
CREATE TRIGGER trg_scx_ocpx_route_event_snapshot_no_update BEFORE UPDATE ON scx_ocpx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_ocpx_route_event_snapshot_no_delete BEFORE DELETE ON scx_ocpx_route_event_snapshot
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteEventSnapshot history cannot be deleted'$$
CREATE TRIGGER trg_scx_ocpx_route_version_no_update BEFORE UPDATE ON scx_ocpx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion is immutable; publish a new version'$$
CREATE TRIGGER trg_scx_ocpx_route_version_no_delete BEFORE DELETE ON scx_ocpx_route_version
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RouteVersion history cannot be deleted'$$
CREATE TRIGGER trg_scx_ocpx_hourly_adjustment_no_update BEFORE UPDATE ON scx_ocpx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_hourly_adjustment_no_delete BEFORE DELETE ON scx_ocpx_hourly_adjustment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hourly adjustment facts cannot be deleted'$$
CREATE TRIGGER trg_scx_ocpx_runtime_fact_no_update BEFORE UPDATE ON scx_ocpx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_fact_no_delete BEFORE DELETE ON scx_ocpx_runtime_fact
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime facts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_failure_no_update BEFORE UPDATE ON scx_ocpx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_failure_no_delete BEFORE DELETE ON scx_ocpx_runtime_failure
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 runtime failures are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_replay_attempt_no_update BEFORE UPDATE ON scx_ocpx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_replay_attempt_no_delete BEFORE DELETE ON scx_ocpx_runtime_replay_attempt
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 replay attempts are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_command_audit_no_update BEFORE UPDATE ON scx_ocpx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
CREATE TRIGGER trg_scx_ocpx_runtime_command_audit_no_delete BEFORE DELETE ON scx_ocpx_runtime_command_audit
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='S10 command audits are immutable'$$
DELIMITER ;

-- Canonicalize menu identity, routes, component paths, and permissions. These
-- tables intentionally have no foreign keys, so the update can be atomic.
DROP PROCEDURE IF EXISTS scx_v92_preflight_menus;
DELIMITER $$
CREATE PROCEDURE scx_v92_preflight_menus()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_menu old_menu
  JOIN sys_menu new_menu
    ON new_menu.id=REPLACE(REPLACE(old_menu.id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_')
  WHERE old_menu.id LIKE 'menu_adx_%' OR old_menu.id LIKE 'perm_adx_%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target OCPX menu id already exists';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_role_menu old_mapping
  JOIN sys_role_menu new_mapping
    ON new_mapping.role_id=old_mapping.role_id
    AND new_mapping.menu_id=REPLACE(REPLACE(old_mapping.menu_id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_')
  WHERE old_mapping.menu_id LIKE 'menu_adx_%' OR old_mapping.menu_id LIKE 'perm_adx_%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target OCPX role-menu mapping already exists';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_role_menu old_mapping
  JOIN sys_role_menu new_mapping ON new_mapping.id=CONCAT(
    'ocpx_',LEFT(SHA2(CONCAT(old_mapping.role_id,'|',
      REPLACE(REPLACE(old_mapping.menu_id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_')),256),59))
  WHERE old_mapping.id LIKE 'adx_%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V92 target OCPX role-menu id already exists';
  END IF;
END$$
DELIMITER ;
CALL scx_v92_preflight_menus();
DROP PROCEDURE scx_v92_preflight_menus;

START TRANSACTION;

UPDATE sys_role_menu
SET menu_id=REPLACE(REPLACE(menu_id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_')
WHERE menu_id LIKE 'menu_adx_%' OR menu_id LIKE 'perm_adx_%';

UPDATE sys_menu
SET parent_id=REPLACE(REPLACE(parent_id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_'),
    path=REPLACE(path,'/adx','/ocpx'),
    component=REPLACE(component,'adx/','ocpx/'),
    permission=REPLACE(permission,'adx:','ocpx:'),
    update_time=NOW()
WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%'
   OR parent_id LIKE 'menu_adx_%' OR parent_id LIKE 'perm_adx_%'
   OR path LIKE '/adx%' OR component LIKE 'adx/%' OR permission LIKE 'adx:%';

UPDATE sys_menu
SET id=REPLACE(REPLACE(id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_'),update_time=NOW()
WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%';

UPDATE sys_role_menu
SET id=CONCAT('ocpx_',LEFT(SHA2(CONCAT(role_id,'|',menu_id),256),59))
WHERE id LIKE 'adx_%';

COMMIT;

-- Remove the historical term from live table comments. Source/current-schema
-- comments already use OCPX; this closes metadata created by V68-V91.
DROP PROCEDURE IF EXISTS scx_v92_update_table_comments;
DELIMITER $$
CREATE PROCEDURE scx_v92_update_table_comments()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE object_table VARCHAR(64);
  DECLARE old_comment TEXT;
  DECLARE new_comment TEXT;
  DECLARE objects CURSOR FOR
    SELECT table_name,table_comment FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
      AND LOWER(table_comment) LIKE '%adx%';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;

  OPEN objects;
  update_loop: LOOP
    FETCH objects INTO object_table,old_comment;
    IF done=1 THEN LEAVE update_loop; END IF;
    SET new_comment=REPLACE(REPLACE(REPLACE(old_comment,'ADX','OCPX'),'Adx','Ocpx'),'adx','ocpx');
    SET @ddl=CONCAT('ALTER TABLE `',REPLACE(object_table,'`','``'),'` COMMENT=''',
      REPLACE(new_comment,'''',''''''),'''');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE objects;
END$$
DELIMITER ;
CALL scx_v92_update_table_comments();
DROP PROCEDURE scx_v92_update_table_comments;
