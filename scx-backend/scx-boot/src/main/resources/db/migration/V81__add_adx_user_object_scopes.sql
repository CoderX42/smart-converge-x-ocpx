-- V81: explicit ADX object scopes on top of sys_user and Sa-Token RBAC.
-- Forward-only and repeat-safe. This file is never auto-scanned by init-database.sh.

CREATE TABLE IF NOT EXISTS scx_adx_user_scope_revision (
  user_id VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  update_by VARCHAR(100) NOT NULL,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='ADX user scope set optimistic-lock revision';

CREATE TABLE IF NOT EXISTS scx_adx_user_scope (
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
  UNIQUE KEY uk_scx_adx_user_scope (user_id,active_scope_key),
  KEY idx_scx_adx_user_scope_user (user_id,status,valid_to),
  KEY idx_scx_adx_user_scope_object (scope_type,scope_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Explicit ADX object data scope for sys_user';

-- Administrators receive an explicit ALL grant. Missing rows never imply global access.
INSERT INTO scx_adx_user_scope_revision (user_id,version,update_by,update_time)
SELECT DISTINCT u.id,1,'V81_MIGRATION',NOW(3)
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id=u.id
LEFT JOIN sys_role r ON r.id=ur.role_id AND r.del_flag='0'
WHERE u.del_flag='0' AND (r.role_code='ROLE_ADMIN' OR (u.type='2' AND u.shop_id IS NOT NULL))
ON DUPLICATE KEY UPDATE version=GREATEST(scx_adx_user_scope_revision.version,1),
  update_time=scx_adx_user_scope_revision.update_time;

INSERT INTO scx_adx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|ALL|',u.id),256),32),u.id,'ALL',NULL,'MANAGE','ACTIVE',1,
       'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_adx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id
WHERE u.del_flag='0' AND r.del_flag='0' AND r.role_code='ROLE_ADMIN'
ON DUPLICATE KEY UPDATE id=scx_adx_user_scope.id;

-- Preserve the existing advertiser-account boundary with explicit advertiser and
-- currently referenced channel grants. This does not create a parallel login table.
INSERT INTO scx_adx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|ADVERTISER|',u.id,'|',u.shop_id),256),32),u.id,'ADVERTISER',u.shop_id,
       'MANAGE','ACTIVE',1,'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_adx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN scx_adx_advertiser a ON BINARY a.id=BINARY u.shop_id AND a.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
ON DUPLICATE KEY UPDATE id=scx_adx_user_scope.id;

INSERT INTO scx_adx_user_scope
  (id,user_id,scope_type,scope_id,permission_level,status,version,create_by,update_by,deleted)
SELECT LEFT(SHA2(CONCAT('V81|CHANNEL|',u.id,'|',m.channel_id),256),32),u.id,'CHANNEL',m.channel_id,
       'MANAGE','ACTIVE',1,'V81_MIGRATION','V81_MIGRATION',0
FROM sys_user u
JOIN scx_adx_user_scope_revision v ON v.user_id=u.id AND v.version=1
JOIN scx_adx_monitor_link m ON BINARY m.advertiser_id=BINARY u.shop_id AND m.deleted=0
WHERE u.del_flag='0' AND u.type='2' AND u.shop_id IS NOT NULL
GROUP BY u.id,m.channel_id
ON DUPLICATE KEY UPDATE id=scx_adx_user_scope.id;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_adx_scope_view','查看ADX对象范围','','menu_system_user',NULL,NULL,'adx:scope:view',80,'1','app_base',NOW(),NOW(),'0'),
  ('perm_adx_scope_edit','维护ADX对象范围','','menu_system_user',NULL,NULL,'adx:scope:edit',90,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v81_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_scope_view','perm_adx_scope_edit')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
