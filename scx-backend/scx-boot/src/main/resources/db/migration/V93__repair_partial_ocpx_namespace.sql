-- V93: close a partially applied ADX-to-OCPX management namespace migration.
--
-- This forward-only repair is for databases where the V92 table/metadata phase
-- is already complete, but V81 object-scope tables are absent and sys_menu still
-- uses ADX primary keys with OCPX routes/permissions. It intentionally does not
-- rewrite scx_schema_migration or replay V81/V92 history.

DROP TEMPORARY TABLE IF EXISTS scx_v93_menu_rename;
CREATE TEMPORARY TABLE scx_v93_menu_rename (
  old_name VARCHAR(64) NOT NULL PRIMARY KEY,
  new_name VARCHAR(64) NOT NULL UNIQUE KEY
);

INSERT INTO scx_v93_menu_rename (old_name,new_name)
SELECT id,REPLACE(REPLACE(id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_')
FROM sys_menu
WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%';

DROP PROCEDURE IF EXISTS scx_v93_preflight;
DELIMITER $$
CREATE PROCEDURE scx_v93_preflight()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name LIKE 'scx_adx_%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the V92 OCPX table phase to be complete; ADX business tables remain';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_type='BASE TABLE'
    AND table_name IN ('scx_ocpx_advertiser','scx_ocpx_monitor_link');
  IF invalid_count<>2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the canonical OCPX advertiser and monitor-link tables';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND LOWER(index_name) LIKE '%adx%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the V92 index rename phase to be complete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.table_constraints
  WHERE constraint_schema=DATABASE() AND LOWER(constraint_name) LIKE '%adx%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the V92 constraint rename phase to be complete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.triggers
  WHERE trigger_schema=DATABASE()
    AND (LOWER(trigger_name) LIKE '%adx%' OR LOWER(action_statement) LIKE '%adx%');
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the V92 trigger rename phase to be complete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.tables
  WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND LOWER(table_comment) LIKE '%adx%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 requires the V92 table-comment phase to be complete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM scx_v93_menu_rename mapping
  JOIN sys_menu target_menu ON target_menu.id=mapping.new_name
  WHERE target_menu.del_flag<>'1';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT='V93 found an active OCPX menu conflicting with an ADX source menu';
  END IF;
END$$
DELIMITER ;

CALL scx_v93_preflight();
DROP PROCEDURE scx_v93_preflight;

CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope_revision (
  user_id VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  update_by VARCHAR(100) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='OCPX user scope set optimistic-lock revision';

CREATE TABLE IF NOT EXISTS scx_ocpx_user_scope (
  id VARCHAR(32) NOT NULL,
  user_id VARCHAR(32) NOT NULL,
  scope_type VARCHAR(32) NOT NULL COMMENT 'ALL,ADVERTISER,CHANNEL,MEDIA_ACCOUNT',
  scope_id VARCHAR(32) NULL COMMENT 'NULL only for ALL',
  permission_level VARCHAR(16) NOT NULL COMMENT 'VIEW,OPERATE,MANAGE',
  valid_from DATETIME(3) NULL,
  valid_to DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL,
  create_by VARCHAR(100) NOT NULL,
  update_by VARCHAR(100) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  active_scope_key VARCHAR(80) GENERATED ALWAYS AS (
    IF(deleted=0,CONCAT(scope_type,':',COALESCE(scope_id,'')),NULL)
  ) STORED,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_ocpx_user_scope (user_id,active_scope_key),
  KEY idx_scx_ocpx_user_scope_user (user_id,status,valid_to),
  KEY idx_scx_ocpx_user_scope_object (scope_type,scope_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Explicit OCPX object data scope for sys_user';

-- Missing scope rows never imply global access. Seed only explicit administrators
-- and the existing advertiser-account boundary retained by V81.
INSERT INTO scx_ocpx_user_scope_revision (user_id,version,update_by,update_time)
SELECT DISTINCT u.id,1,'V93_REPAIR',NOW(3)
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id=u.id
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.del_flag='0'
WHERE u.del_flag='0' AND (r.role_code='ROLE_ADMIN' OR (u.type='2' AND u.shop_id IS NOT NULL))
ON DUPLICATE KEY UPDATE version=GREATEST(scx_ocpx_user_scope_revision.version,1),
  update_time=scx_ocpx_user_scope_revision.update_time;

INSERT INTO scx_ocpx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT DISTINCT LEFT(SHA2(CONCAT('V93|ALL|',u.id),256),32),u.id,'ALL',NULL,'MANAGE','ACTIVE',v.version,
       'V93_REPAIR','V93_REPAIR',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id
WHERE u.del_flag='0' AND r.del_flag='0' AND r.role_code='ROLE_ADMIN'
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

INSERT INTO scx_ocpx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V93|ADVERTISER|',u.id,'|',u.shop_id),256),32),u.id,'ADVERTISER',u.shop_id,
       'MANAGE','ACTIVE',v.version,'V93_REPAIR','V93_REPAIR',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id
JOIN scx_ocpx_advertiser a ON BINARY a.id=BINARY u.shop_id AND a.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

INSERT INTO scx_ocpx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V93|CHANNEL|',u.id,'|',m.channel_id),256),32),u.id,'CHANNEL',m.channel_id,
       'MANAGE','ACTIVE',v.version,'V93_REPAIR','V93_REPAIR',0
FROM sys_user u
JOIN scx_ocpx_user_scope_revision v ON v.user_id=u.id
JOIN scx_ocpx_monitor_link m ON BINARY m.advertiser_id=BINARY u.shop_id AND m.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
GROUP BY u.id,m.channel_id,v.version
ON DUPLICATE KEY UPDATE id=scx_ocpx_user_scope.id;

-- Preserve role semantics while resolving the two possible states:
-- target missing, or target present only as a disabled historical menu.
START TRANSACTION;

DELETE source_mapping
FROM sys_role_menu source_mapping
JOIN scx_v93_menu_rename mapping ON mapping.old_name=source_mapping.menu_id
JOIN sys_role_menu target_mapping
  ON target_mapping.role_id=source_mapping.role_id AND target_mapping.menu_id=mapping.new_name;

DELETE target_menu
FROM sys_menu target_menu
JOIN scx_v93_menu_rename mapping ON mapping.new_name=target_menu.id
JOIN sys_menu source_menu ON source_menu.id=mapping.old_name
WHERE target_menu.del_flag='1';

UPDATE sys_role_menu role_mapping
JOIN scx_v93_menu_rename mapping ON mapping.old_name=role_mapping.menu_id
SET role_mapping.menu_id=mapping.new_name;

UPDATE sys_menu
SET parent_id=REPLACE(REPLACE(parent_id,'menu_adx_','menu_ocpx_'),'perm_adx_','perm_ocpx_'),
    path=REPLACE(path,'/adx','/ocpx'),
    component=REPLACE(component,'adx/','ocpx/'),
    permission=REPLACE(permission,'adx:','ocpx:'),
    update_time=NOW()
WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%'
   OR parent_id LIKE 'menu_adx_%' OR parent_id LIKE 'perm_adx_%'
   OR path LIKE '/adx%' OR component LIKE 'adx/%' OR permission LIKE 'adx:%';

UPDATE sys_menu source_menu
JOIN scx_v93_menu_rename mapping ON mapping.old_name=source_menu.id
SET source_menu.id=mapping.new_name,source_menu.update_time=NOW();

COMMIT;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_scope_view','查看OCPX对象范围','','menu_system_user',NULL,NULL,
   'ocpx:scope:view',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_ocpx_scope_edit','维护OCPX对象范围','','menu_system_user',NULL,NULL,
   'ocpx:scope:edit',90,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

-- Administrators must receive every active OCPX page and action. Existing
-- operator/analyst/advertiser assignments are preserved by the key migration.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id,create_time)
SELECT CONCAT('v93_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id,NOW()
FROM sys_role r
JOIN sys_menu m ON m.del_flag='0' AND (
  m.id LIKE 'menu_ocpx_%' OR m.id LIKE 'perm_ocpx_%'
  OR m.path LIKE '/ocpx%' OR m.permission LIKE 'ocpx:%'
)
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DROP PROCEDURE IF EXISTS scx_v93_verify;
DELIMITER $$
CREATE PROCEDURE scx_v93_verify()
BEGIN
  DECLARE invalid_count INT DEFAULT 0;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_menu
  WHERE id LIKE 'menu_adx_%' OR id LIKE 'perm_adx_%'
     OR parent_id LIKE 'menu_adx_%' OR parent_id LIKE 'perm_adx_%'
     OR path LIKE '/adx%' OR component LIKE 'adx/%' OR permission LIKE 'adx:%';
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V93 left ADX menu namespace residue';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM information_schema.columns
  WHERE table_schema=DATABASE() AND (
    (table_name='scx_ocpx_user_scope' AND column_name IN
      ('user_id','scope_type','scope_id','permission_level','valid_from','valid_to','status','version','deleted','active_scope_key'))
    OR (table_name='scx_ocpx_user_scope_revision' AND column_name IN
      ('user_id','version','update_by','update_time'))
  );
  IF invalid_count<>14 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V93 OCPX object-scope schema verification failed';
  END IF;

  SELECT COUNT(DISTINCT scope_row.user_id) INTO invalid_count
  FROM scx_ocpx_user_scope scope_row
  JOIN sys_user user_row ON user_row.id=scope_row.user_id AND user_row.del_flag='0'
  JOIN sys_user_role user_role ON user_role.user_id=user_row.id
  JOIN sys_role role_row ON role_row.id=user_role.role_id
    AND role_row.role_code='ROLE_ADMIN' AND role_row.del_flag='0'
  WHERE scope_row.deleted=0 AND scope_row.status='ACTIVE'
    AND scope_row.scope_type='ALL' AND scope_row.scope_id IS NULL
    AND scope_row.permission_level='MANAGE';
  IF invalid_count<1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V93 did not seed an explicit OCPX scope administrator';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_role role_row
  JOIN sys_menu menu_row ON menu_row.del_flag='0' AND (
    menu_row.id LIKE 'menu_ocpx_%' OR menu_row.id LIKE 'perm_ocpx_%'
    OR menu_row.path LIKE '/ocpx%' OR menu_row.permission LIKE 'ocpx:%'
  )
  LEFT JOIN sys_role_menu role_menu
    ON role_menu.role_id=role_row.id AND role_menu.menu_id=menu_row.id
  WHERE role_row.role_code='ROLE_ADMIN' AND role_row.del_flag='0' AND role_menu.id IS NULL;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V93 administrator OCPX menu grants are incomplete';
  END IF;

  SELECT COUNT(*) INTO invalid_count
  FROM sys_role_menu role_menu
  LEFT JOIN sys_menu menu_row ON menu_row.id=role_menu.menu_id
  WHERE (role_menu.menu_id LIKE 'menu_ocpx_%' OR role_menu.menu_id LIKE 'perm_ocpx_%')
    AND menu_row.id IS NULL;
  IF invalid_count<>0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V93 left dangling OCPX role-menu assignments';
  END IF;
END$$
DELIMITER ;

CALL scx_v93_verify();
DROP PROCEDURE scx_v93_verify;
DROP TEMPORARY TABLE scx_v93_menu_rename;
