-- OCPX-001 (T1):
-- Reorganize menu hierarchy toward target operations modes with backward-compatible route paths.
-- Only additive migration: no destructive DDL, no deletes.

-- 顶级目标菜单容器（用于“结构重组”，保留旧 path 不变）。
INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag) VALUES
('menu_ocpx_dashboard', '仪表盘', '/ocpx/dashboard', '0', 'lucide:layout-dashboard', NULL, 5, '0', 'app_base', NOW(), '0'),
('menu_ocpx_product', '产品管理', '/ocpx/product', '0', 'lucide:package', NULL, 10, '0', 'app_base', NOW(), '0'),
('menu_ocpx_link', '链接管理', '/ocpx/link', '0', 'lucide:link-2', NULL, 15, '0', 'app_base', NOW(), '0'),
('menu_ocpx_media', '媒体管理', '/ocpx/media', '0', 'lucide:radio-tower', NULL, 20, '0', 'app_base', NOW(), '0'),
('menu_ocpx_task', '任务管理', '/ocpx/task', '0', 'lucide:list-checks', NULL, 25, '0', 'app_base', NOW(), '0'),
('menu_ocpx_rta', 'RTA 管理', '/ocpx/rta', '0', 'lucide:waypoints', NULL, 30, '0', 'app_base', NOW(), '0'),
('menu_ocpx_data', '数据管理', '/ocpx/data', '0', 'lucide:chart-no-axes-combined', NULL, 35, '0', 'app_base', NOW(), '0'),
('menu_ocpx_system', '系统管理', '/ocpx/system', '0', 'lucide:shield-user', NULL, 40, '0', 'app_base', NOW(), '0'),
('menu_ocpx_toolbox', '工具箱', '/ocpx/toolbox', '0', 'lucide:wrench', NULL, 45, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  icon = VALUES(icon),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  del_flag = '0';

-- 目标菜单收敛：保留原有 route path，改为新父节点以形成目标结构。
INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag) VALUES
('menu_ad_overview', '仪表盘', '/reporting/overview', 'menu_ocpx_dashboard', 'lucide:chart-spline', 'reporting/overview/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_products', '产品管理', '/delivery/products', 'menu_ocpx_product', 'lucide:package-search', 'delivery/master-data/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_link', '链接管理', '/delivery/tracking-link', 'menu_ocpx_link', 'lucide:link', 'delivery/tracking-link/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_master', '主数据', '/delivery/master-data', 'menu_ocpx_media', 'lucide:database', 'delivery/master-data/index', 10, '0', 'app_base', NOW(), '0'),
('menu_ad_channels', '媒体渠道', '/delivery/media-channels', 'menu_ocpx_media', 'lucide:radio-tower', 'delivery/master-data/index', 20, '0', 'app_base', NOW(), '0'),
('menu_ad_documents', '接入文档', '/delivery/documents', 'menu_ocpx_media', 'lucide:file-text', 'delivery/master-data/index', 30, '0', 'app_base', NOW(), '0'),
('menu_ad_customer', '上游客户', '/delivery/customers', 'menu_ocpx_media', 'lucide:building-2', 'delivery/customers/index', 40, '0', 'app_base', NOW(), '0'),
('menu_ad_channel_user', '渠道用户', '/delivery/channel-users', 'menu_ocpx_media', 'lucide:users', 'delivery/channel-users/index', 50, '0', 'app_base', NOW(), '0'),
('menu_ad_task', '任务管理', '/delivery/tracking-task', 'menu_ocpx_task', 'lucide:list-checks', 'delivery/tracking-task/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_rta', 'RTA 配置', '/delivery/rta', 'menu_ocpx_rta', 'lucide:waypoints', 'delivery/rta/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_platform_config', '广告平台配置', '/operations/platform-config', 'menu_ocpx_product', 'lucide:settings-2', 'operations/platform-config/index', 30, '0', 'app_base', NOW(), '0'),
('menu_ad_task_scope', '任务授权', '/operations/task-scopes', 'menu_ocpx_system', 'lucide:shield-check', 'operations/task-scopes/index', 20, '0', 'app_base', NOW(), '0'),
('menu_ad_short_link', 'DP 转换', '/operations/short-links', 'menu_ocpx_toolbox', 'lucide:link-2', 'operations/short-links/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_jobs', '回传任务', '/operations/jobs', 'menu_ocpx_data', 'lucide:refresh-cw', 'operations/jobs/index', 0, '0', 'app_base', NOW(), '0'),
('menu_ad_daily', '效果日报', '/reporting/daily', 'menu_ocpx_data', 'lucide:chart-column', 'reporting/daily/index', 10, '0', 'app_base', NOW(), '0'),
('menu_ad_events', '事件归因', '/reporting/event-detail', 'menu_ocpx_data', 'lucide:route', 'reporting/event-detail/index', 20, '0', 'app_base', NOW(), '0'),
('menu_ad_summary', '数据汇总', '/reporting/summary', 'menu_ocpx_data', 'lucide:chart-no-axes-combined', 'reporting/summary/index', 30, '0', 'app_base', NOW(), '0'),
('menu_ad_task_summary', '任务汇总', '/reporting/task-summary', 'menu_ocpx_data', 'lucide:clipboard-list', 'reporting/task-summary/index', 40, '0', 'app_base', NOW(), '0'),
('menu_ad_product_summary', '产品汇总', '/reporting/product-summary', 'menu_ocpx_data', 'lucide:package-search', 'reporting/product-summary/index', 50, '0', 'app_base', NOW(), '0'),
('menu_ad_link_summary', '链接汇总', '/reporting/link-summary', 'menu_ocpx_data', 'lucide:link-2', 'reporting/link-summary/index', 60, '0', 'app_base', NOW(), '0'),
('menu_ad_media_summary', '媒体汇总', '/reporting/media-summary', 'menu_ocpx_data', 'lucide:radio-tower', 'reporting/media-summary/index', 70, '0', 'app_base', NOW(), '0')
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

-- 系统管理入口（仅在本轮开放给管理员）
INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag) VALUES
('menu_system_user', '用户管理', '/system/user', 'menu_ocpx_system', 'lucide:user-pen', 'system/user/index', 10, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  parent_id = VALUES(parent_id),
  icon = VALUES(icon),
  component = VALUES(component),
  sort = VALUES(sort),
  application_key = VALUES(application_key),
  del_flag = '0';

INSERT INTO sys_menu (id, name, permission, parent_id, sort, type, application_key, create_time, del_flag) VALUES
('perm_system_user_page', '查询用户', 'system:sysuser:page', 'menu_system_user', 10, '1', 'app_base', NOW(), '0'),
('perm_system_user_get', '查询用户详情', 'system:sysuser:get', 'menu_system_user', 11, '1', 'app_base', NOW(), '0'),
('perm_system_user_add', '新增用户', 'system:sysuser:add', 'menu_system_user', 12, '1', 'app_base', NOW(), '0'),
('perm_system_user_edit', '编辑用户', 'system:sysuser:edit', 'menu_system_user', 13, '1', 'app_base', NOW(), '0'),
('perm_system_user_del', '删除用户', 'system:sysuser:del', 'menu_system_user', 14, '1', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  permission = VALUES(permission),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  type = VALUES(type),
  application_key = VALUES(application_key),
  del_flag = '0';

-- 历史容器不再作为前端目标菜单入口，保留旧条目避免直接 URL 侧回归。
INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag) VALUES
('menu_ad_delivery', '历史：投放入口', '/legacy/delivery', '0', 'lucide:send', NULL, 999, '0', 'app_base', NOW(), '0'),
('menu_ad_data', '历史：数据入口', '/legacy/reporting', '0', 'lucide:chart-no-axes-combined', NULL, 998, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  parent_id = VALUES(parent_id),
  icon = VALUES(icon),
  component = VALUES(component),
  sort = VALUES(sort),
  del_flag = '0';

-- 去除旧顶级菜单入口权限：迁移后以新目标目录入口展示，防止旧层级残留。
DELETE FROM sys_role_menu WHERE role_id IN ('1', 'role_operator', 'role_analyst')
  AND menu_id IN ('menu_ad_delivery', 'menu_ad_data');

-- 为管理员补齐新菜单树入口（保持原有功能菜单 id 不变，仅补齐新容器）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN (
  'menu_ocpx_dashboard',
  'menu_ocpx_product',
  'menu_ocpx_link',
  'menu_ocpx_media',
  'menu_ocpx_task',
  'menu_ocpx_rta',
  'menu_ocpx_data',
  'menu_ocpx_system',
  'menu_ocpx_toolbox',
  'menu_system_user',
  'menu_ad_overview',
  'menu_ad_products',
  'menu_ad_link',
  'menu_ad_master',
  'menu_ad_channels',
  'menu_ad_documents',
  'menu_ad_customer',
  'menu_ad_channel_user',
  'menu_ad_task',
  'menu_ad_rta',
  'menu_ad_platform_config',
  'menu_ad_task_scope',
  'menu_ad_short_link',
  'menu_ad_jobs',
  'menu_ad_daily',
  'menu_ad_events',
  'menu_ad_summary',
  'menu_ad_task_summary',
  'menu_ad_product_summary',
  'menu_ad_link_summary',
  'menu_ad_media_summary',
  'perm_system_user_page',
  'perm_system_user_get',
  'perm_system_user_add',
  'perm_system_user_edit',
  'perm_system_user_del'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

-- 运营角色：补齐新结构入口（沿用原有投放/报表权限面）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN (
  'menu_ocpx_dashboard',
  'menu_ocpx_product',
  'menu_ocpx_link',
  'menu_ocpx_media',
  'menu_ocpx_task',
  'menu_ocpx_rta',
  'menu_ocpx_data',
  'menu_ocpx_toolbox',
  'menu_ad_overview',
  'menu_ad_products',
  'menu_ad_link',
  'menu_ad_master',
  'menu_ad_channels',
  'menu_ad_documents',
  'menu_ad_customer',
  'menu_ad_channel_user',
  'menu_ad_task',
  'menu_ad_rta',
  'menu_ad_platform_config',
  'menu_ad_short_link',
  'menu_ad_jobs',
  'menu_ad_daily',
  'menu_ad_events',
  'menu_ad_summary',
  'menu_ad_task_summary',
  'menu_ad_product_summary',
  'menu_ad_link_summary',
  'menu_ad_media_summary',
  'perm_ad_customer_read',
  'perm_ad_channel_user_read',
  'perm_ad_short_link_read',
  'perm_ad_config_read',
  'perm_ad_task_scope_read',
  'perm_ad_rta_read',
  'perm_subject_media_read'
) 
ON DUPLICATE KEY UPDATE id = VALUES(id);

-- 分析员：补齐只读结构入口
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN (
  'menu_ocpx_dashboard',
  'menu_ocpx_product',
  'menu_ocpx_link',
  'menu_ocpx_media',
  'menu_ocpx_task',
  'menu_ocpx_rta',
  'menu_ocpx_data',
  'menu_ocpx_toolbox',
  'menu_ad_overview',
  'menu_ad_products',
  'menu_ad_link',
  'menu_ad_master',
  'menu_ad_channels',
  'menu_ad_documents',
  'menu_ad_customer',
  'menu_ad_channel_user',
  'menu_ad_task',
  'menu_ad_jobs',
  'menu_ad_daily',
  'menu_ad_events',
  'menu_ad_summary',
  'menu_ad_task_summary',
  'menu_ad_product_summary',
  'menu_ad_link_summary',
  'menu_ad_media_summary',
  'menu_ad_short_link',
  'perm_ad_customer_read',
  'perm_ad_channel_user_read',
  'perm_ad_short_link_read',
  'perm_ad_config_read',
  'perm_ad_rta_read',
  'perm_ad_task_scope_read',
  'perm_subject_media_read',
  'perm_ad_report_read',
  'perm_ad_event_read'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);
