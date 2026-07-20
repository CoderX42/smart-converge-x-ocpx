-- OCPX-001（T1）数据库清洁
-- 目标：清理新增 OCPX 菜单树后遗留的数据库层路由名冲突，保证菜单名称全局可追溯且幂等。

-- 1) 避免 parent/child 同名，优先修正 legacy 子菜单名称。
UPDATE sys_menu c
JOIN sys_menu p ON c.parent_id = p.id
SET c.name = '效果概览（兼容）',
    c.update_time = NOW()
WHERE c.del_flag = '0'
  AND p.del_flag = '0'
  AND c.id = 'menu_ad_overview'
  AND c.name = '仪表盘'
  AND p.id = 'menu_ocpx_dashboard';

UPDATE sys_menu c
JOIN sys_menu p ON c.parent_id = p.id
SET c.name = '产品管理（兼容）',
    c.update_time = NOW()
WHERE c.del_flag = '0'
  AND p.del_flag = '0'
  AND c.id = 'menu_ad_products'
  AND c.name = '产品管理'
  AND p.id = 'menu_ocpx_product';

UPDATE sys_menu c
JOIN sys_menu p ON c.parent_id = p.id
SET c.name = '链接管理（兼容）',
    c.update_time = NOW()
WHERE c.del_flag = '0'
  AND p.del_flag = '0'
  AND c.id = 'menu_ad_link'
  AND c.name = '链接管理'
  AND p.id = 'menu_ocpx_link';

UPDATE sys_menu c
JOIN sys_menu p ON c.parent_id = p.id
SET c.name = '任务管理（兼容）',
    c.update_time = NOW()
WHERE c.del_flag = '0'
  AND p.del_flag = '0'
  AND c.id = 'menu_ad_task'
  AND c.name = '任务管理'
  AND p.id = 'menu_ocpx_task';

-- 2) 避免顶层菜单名称重复（系统管理/用户管理），不改动路径与权限树。
UPDATE sys_menu
SET name = '系统管理（兼容）',
    update_time = NOW()
WHERE del_flag = '0'
  AND id = 'menu_system'
  AND name = '系统管理';

UPDATE sys_menu
SET name = '用户管理（兼容）',
    update_time = NOW()
WHERE del_flag = '0'
  AND id = 'menu_user'
  AND name = '用户管理';

-- 3) 清理兜底：若上述菜单因历史变更再次出现同名关系，继续一刀切为兼容后缀命名，保证可读且幂等。
UPDATE sys_menu AS c
JOIN sys_menu AS p
  ON c.parent_id = p.id
SET c.name = CONCAT(c.name, '（兼容）')
WHERE c.del_flag = '0'
  AND p.del_flag = '0'
  AND c.parent_id IN ('menu_ocpx_dashboard', 'menu_ocpx_product', 'menu_ocpx_link', 'menu_ocpx_task')
  AND c.name = p.name
  AND c.name NOT LIKE '%（兼容）';

-- 仅用于核验（返回空集则通过）：当前仅保留新结构父子同名的异常项将被清空。
-- SELECT p.id AS parent_id, p.name AS parent_name, c.id AS child_id, c.name AS child_name
-- FROM sys_menu c
-- JOIN sys_menu p ON p.id = c.parent_id
-- WHERE p.del_flag='0' AND c.del_flag='0' AND p.name = c.name;
