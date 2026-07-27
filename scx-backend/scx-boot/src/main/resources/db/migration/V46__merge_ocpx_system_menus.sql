-- OCPX-001 follow-up: merge the legacy system menu into the OCPX system menu.
-- Keep all existing menu IDs, routes and permission codes; only move the
-- hierarchy and hide the duplicate legacy roots.

-- Preserve access for roles that currently use the legacy root. The digest
-- keeps generated role_menu IDs below VARCHAR(64) and makes this idempotent.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('mrg_', LEFT(SHA2(CONCAT(role_id, '|menu_ocpx_system'), 256), 60)),
       role_id,
       'menu_ocpx_system'
FROM sys_role_menu
WHERE menu_id = 'menu_system';

-- Keep the legacy root's menu and permission assignments attached to the same
-- roles after the direct children are moved below the canonical root.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('mrg_', LEFT(SHA2(CONCAT(rm.role_id, '|', m.id), 256), 60)),
       rm.role_id,
       m.id
FROM sys_role_menu rm
JOIN sys_menu m ON m.parent_id = 'menu_system'
WHERE rm.menu_id = 'menu_system'
  AND m.id <> 'menu_user';

-- The OCPX user page is the canonical /system/user entry. Preserve any role
-- that only had the legacy user menu before hiding that duplicate.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('mrg_', LEFT(SHA2(CONCAT(role_id, '|menu_system_user'), 256), 60)),
       role_id,
       'menu_system_user'
FROM sys_role_menu
WHERE menu_id = 'menu_user';

UPDATE sys_menu
SET parent_id = 'menu_ocpx_system',
    update_time = NOW()
WHERE parent_id = 'menu_system'
  AND id <> 'menu_user'
  AND del_flag = '0';

UPDATE sys_menu
SET parent_id = 'menu_ocpx_system',
    update_time = NOW(),
    del_flag = '0'
WHERE id = 'menu_system_user';

-- Hide, rather than delete, the historical roots and duplicate user page so
-- old URLs and audit references remain explainable without a second sidebar
-- entry. Existing role_menu rows are intentionally retained for traceability.
UPDATE sys_menu
SET del_flag = '1',
    update_time = NOW()
WHERE id IN ('menu_system', 'menu_user');

UPDATE sys_menu
SET name = '系统管理',
    parent_id = '0',
    sort = 40,
    del_flag = '0',
    update_time = NOW()
WHERE id = 'menu_ocpx_system';

-- Reconcile the canonical root's active children for every role that owns
-- that root. This repairs older databases where the legacy root was granted
-- without its generated system permission rows.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('mrg_', LEFT(SHA2(CONCAT(rm.role_id, '|', m.id), 256), 60)),
       rm.role_id,
       m.id
FROM sys_role_menu rm
JOIN sys_menu m ON m.parent_id = 'menu_ocpx_system'
WHERE rm.menu_id = 'menu_ocpx_system'
  AND m.del_flag = '0';
