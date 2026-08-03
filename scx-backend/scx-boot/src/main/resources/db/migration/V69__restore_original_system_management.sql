-- V69: restore the original system-management navigation on top of the
-- existing scx-system Sa-Token + RBAC implementation.
--
-- V68 renamed the canonical root to "权限管理" and kept only account and
-- role pages visible. The underlying users, roles, menus, departments,
-- dictionaries, storage configuration and audit-log capabilities were not
-- removed. This forward-only migration restores those original entry points
-- without introducing another authorization model or changing business data.

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_ocpx_system','系统管理','/system','0','lucide:settings',NULL,
   40,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  icon=VALUES(icon),
  component=VALUES(component),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  update_time=NOW(),
  del_flag='0';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('menu_system_user','用户管理','/system/user','menu_ocpx_system',
   'lucide:user-round-cog','system/user/index',10,'0','app_base',NOW(),NOW(),'0'),
  ('menu_role','角色管理','/system/role','menu_ocpx_system',
   'lucide:shield-check','system/role/index',20,'0','app_base',NOW(),NOW(),'0'),
  ('menu_menu','菜单管理','/system/menu','menu_ocpx_system',
   'lucide:menu','system/menu/index',30,'0','app_base',NOW(),NOW(),'0'),
  ('menu_dept','部门管理','/system/dept','menu_ocpx_system',
   'lucide:network','system/dept/index',40,'0','app_base',NOW(),NOW(),'0'),
  ('menu_dict','字典管理','/system/dict','menu_ocpx_system',
   'lucide:book-key','system/dict/index',50,'0','app_base',NOW(),NOW(),'0'),
  ('menu_storage','存储配置','/system/storage-config','menu_ocpx_system',
   'lucide:cloud-cog','system/storage-config/index',60,'0','app_base',NOW(),NOW(),'0'),
  ('menu_log','操作日志','/system/log','menu_ocpx_system',
   'lucide:scroll-text','system/log/index',70,'0','app_base',NOW(),NOW(),'0'),
  ('menu_login_log','登录日志','/system/login-log','menu_ocpx_system',
   'lucide:log-in','system/login-log/index',80,'0','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  path=VALUES(path),
  parent_id=VALUES(parent_id),
  icon=VALUES(icon),
  component=VALUES(component),
  sort=VALUES(sort),
  type=VALUES(type),
  application_key=VALUES(application_key),
  update_time=NOW(),
  del_flag='0';

-- Attach the existing button permissions to their original pages and make
-- them active again. Historical permission IDs are preserved deliberately.
UPDATE sys_menu
SET parent_id = CASE
      WHEN permission LIKE 'system:sysuser:%' THEN 'menu_system_user'
      WHEN permission LIKE 'system:sysrole:%' THEN 'menu_role'
      WHEN permission LIKE 'system:sysmenu:%' THEN 'menu_menu'
      WHEN permission LIKE 'system:sysdept:%' THEN 'menu_dept'
      WHEN permission LIKE 'system:sysdictvalue:%' THEN 'menu_dict'
      WHEN permission LIKE 'system:sysdict:%' THEN 'menu_dict'
      WHEN permission LIKE 'system:storageconfig:%' THEN 'menu_storage'
      WHEN permission LIKE 'system:syslog:%' THEN 'menu_log'
      WHEN permission LIKE 'system:sysloginlog:%' THEN 'menu_login_log'
      ELSE parent_id
    END,
    del_flag = '0',
    update_time = NOW()
WHERE permission LIKE 'system:sysuser:%'
   OR permission LIKE 'system:sysrole:%'
   OR permission LIKE 'system:sysmenu:%'
   OR permission LIKE 'system:sysdept:%'
   OR permission LIKE 'system:sysdict:%'
   OR permission LIKE 'system:sysdictvalue:%'
   OR permission LIKE 'system:storageconfig:%'
   OR permission LIKE 'system:syslog:%'
   OR permission LIKE 'system:sysloginlog:%';

-- The canonical menu_ocpx_system row now owns the original /system tree.
-- Keep the old duplicate root and duplicate user page hidden.
UPDATE sys_menu
SET del_flag = '1', update_time = NOW()
WHERE id IN ('menu_system','menu_user');

-- Ensure every active administrator can see and operate the restored system
-- management tree. Other roles remain configurable through the original role
-- management page; their existing grants are neither broadened nor revoked.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v69_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id IN (
    'menu_ocpx_system','menu_system_user','menu_role','menu_menu','menu_dept',
    'menu_dict','menu_storage','menu_log','menu_login_log'
  )
  OR m.parent_id IN (
    'menu_system_user','menu_role','menu_menu','menu_dept','menu_dict',
    'menu_storage','menu_log','menu_login_log'
  )
)
WHERE r.role_code = 'ROLE_ADMIN'
  AND r.del_flag = '0'
  AND m.del_flag = '0'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing
    WHERE existing.role_id = r.id AND existing.menu_id = m.id
  );
