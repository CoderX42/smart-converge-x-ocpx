-- OCPX-103/108: 队列总览菜单（回传队列/异步出库占位）
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
  'menu_ad_queue_overview',
  '队列总览',
  '/operations/queue',
  'menu_ocpx_data',
  'lucide:layout-list',
  'operations/queue/index',
  5,
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
  'perm_ad_queue_overview_read',
  '查询队列总览',
  'ad:queue:read',
  'menu_ad_queue_overview',
  0,
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
WHERE id IN ('menu_ad_queue_overview', 'perm_ad_queue_overview_read')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN ('menu_ad_queue_overview', 'perm_ad_queue_overview_read')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN ('menu_ad_queue_overview', 'perm_ad_queue_overview_read')
ON DUPLICATE KEY UPDATE id = VALUES(id);
