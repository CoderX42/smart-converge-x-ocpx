-- V48: media and media-scope management permissions.
-- Production acceptance fixtures are created through audited APIs, never by migration.

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_subject_media_write','管理媒体','subject:media:write','menu_ad_master',1,'1','app_base',NOW(),'0'),
  ('perm_subject_media_scope_read','查询媒体范围','subject:media-scope:read','menu_ad_master',2,'1','app_base',NOW(),'0'),
  ('perm_subject_media_scope_write','管理媒体范围','subject:media-scope:write','menu_ad_master',3,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  permission=VALUES(permission),
  parent_id=VALUES(parent_id),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  del_flag='0';

-- Keep role-menu identifiers short enough for older installations whose
-- sys_role_menu.id is VARCHAR(32), and resolve the administrator by its
-- stable role code instead of assuming its historical id is always `1`.
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT 'v48_media_write', r.id, 'perm_subject_media_write'
FROM sys_role r
WHERE r.role_code = 'ROLE_ADMIN' AND r.del_flag = '0'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), menu_id=VALUES(menu_id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT 'v48_scope_read', r.id, 'perm_subject_media_scope_read'
FROM sys_role r
WHERE r.role_code = 'ROLE_ADMIN' AND r.del_flag = '0'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), menu_id=VALUES(menu_id);

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT 'v48_scope_write', r.id, 'perm_subject_media_scope_write'
FROM sys_role r
WHERE r.role_code = 'ROLE_ADMIN' AND r.del_flag = '0'
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), menu_id=VALUES(menu_id);
