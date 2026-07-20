-- OCPX-105: 广告平台凭据菜单
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
  'menu_ad_platform_credentials',
  '平台凭据',
  '/system/config',
  'menu_ocpx_system',
  'lucide:key-round',
  'system/config/index',
  15,
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

INSERT INTO sys_menu (
  id,
  name,
  permission,
  parent_id,
  sort,
  type,
  application_key,
  create_time,
  del_flag
) VALUES
(
  'perm_system_config_page',
  '查询系统配置',
  'system:config:page',
  'menu_ad_platform_credentials',
  0,
  '1',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_system_config_get',
  '查询配置明细',
  'system:config:get',
  'menu_ad_platform_credentials',
  1,
  '1',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_system_config_add',
  '新增系统配置',
  'system:config:add',
  'menu_ad_platform_credentials',
  2,
  '1',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_system_config_edit',
  '编辑系统配置',
  'system:config:edit',
  'menu_ad_platform_credentials',
  3,
  '1',
  'app_base',
  NOW(),
  '0'
),
(
  'perm_system_config_del',
  '删除系统配置',
  'system:config:del',
  'menu_ad_platform_credentials',
  4,
  '1',
  'app_base',
  NOW(),
  '0'
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  permission = VALUES(permission),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = '0';

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN (
  'menu_ad_platform_credentials',
  'perm_system_config_page',
  'perm_system_config_get',
  'perm_system_config_add',
  'perm_system_config_edit',
  'perm_system_config_del'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);
