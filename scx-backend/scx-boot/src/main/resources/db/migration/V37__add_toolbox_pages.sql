-- OCPX-102: toolbox独立菜单 + DP 转换页面
INSERT INTO sys_menu (
  id,
  name,
  path,
  parent_id,
  icon,
  component,
  sort,
  type,
  application_key,
  create_time,
  del_flag
) VALUES
(
  'menu_toolbox_short',
  '短链与DP工具',
  '/toolbox/short-link',
  'menu_ocpx_toolbox',
  'lucide:link-2',
  'toolbox/short-link/index',
  10,
  '0',
  'app_base',
  NOW(),
  '0'
),
(
  'menu_toolbox_dp',
  'DP 转换',
  '/toolbox/dp-conversion',
  'menu_ocpx_toolbox',
  'lucide:link',
  'toolbox/dp-conversion/index',
  20,
  '0',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_toolbox_dp_read',
  '查询DP转换',
  'ad:dp-convert:read',
  'menu_toolbox_dp',
  0,
  '1',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_toolbox_dp_write',
  '编辑DP转换',
  'ad:dp-convert:write',
  'menu_toolbox_dp',
  1,
  '1',
  'app_base',
  NOW(),
  '0'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  parent_id = VALUES(parent_id),
  icon = VALUES(icon),
  component = VALUES(component),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = '0';

-- OCPX-102 兼容回退：保留原短链菜单项，移动到历史位，不再作为主入口。
INSERT INTO sys_menu (
  id,
  name,
  path,
  parent_id,
  icon,
  component,
  sort,
  type,
  application_key,
  create_time,
  del_flag
) VALUES
(
  'menu_ad_short_link',
  '短链与DP工具（历史）',
  '/operations/short-links',
  'menu_ocpx_toolbox',
  'lucide:link-2',
  'operations/short-links/index',
  99,
  '0',
  'app_base',
  NOW(),
  '0'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  parent_id = VALUES(parent_id),
  icon = VALUES(icon),
  component = VALUES(component),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = '0';

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN (
  'menu_toolbox_short',
  'menu_toolbox_dp',
  'perm_toolbox_dp_read',
  'perm_toolbox_dp_write'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN (
  'menu_toolbox_short',
  'menu_toolbox_dp',
  'perm_toolbox_dp_read'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN ('perm_toolbox_dp_write')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN (
  'menu_toolbox_short',
  'menu_toolbox_dp',
  'perm_toolbox_dp_read'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);
