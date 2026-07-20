-- The V28 baseline was registered against a schema that lacked post-V7 menu/role
-- data migrations. Reconcile only the missing, idempotent MVP access-control seeds.

INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag) VALUES
('menu_ad_customer', '上游客户', '/delivery/customers', 'menu_ad_delivery', 'lucide:building-2', 'delivery/customers/index', 100, '0', 'app_base', NOW(), '0'),
('menu_ad_short_link', '短链与DP工具', '/operations/short-links', 'menu_ad_delivery', 'lucide:link-2', 'operations/short-links/index', 40, '0', 'app_base', NOW(), '0'),
('menu_ad_platform_config', '广告平台配置', '/operations/platform-config', 'menu_ad_delivery', 'lucide:settings-2', 'operations/platform-config/index', 30, '0', 'app_base', NOW(), '0'),
('menu_ad_channel_user', '渠道用户', '/delivery/channel-users', 'menu_ad_delivery', 'lucide:users', 'delivery/channel-users/index', 20, '0', 'app_base', NOW(), '0'),
('menu_ad_overview', '效果概览', '/reporting/overview', 'menu_ad_data', 'lucide:chart-spline', 'reporting/overview/index', 100, '0', 'app_base', NOW(), '0'),
('menu_ad_task_scope', '任务授权', '/operations/task-scopes', 'menu_ad_delivery', 'lucide:shield-check', 'operations/task-scopes/index', 40, '0', 'app_base', NOW(), '0'),
('menu_ad_media_summary', '媒体数据汇总', '/reporting/media-summary', 'menu_ad_data', 'lucide:radio-tower', 'reporting/media-summary/index', 95, '0', 'app_base', NOW(), '0'),
('menu_ad_summary', '数据汇总', '/reporting/summary', 'menu_ad_data', 'lucide:chart-no-axes-combined', 'reporting/summary/index', 96, '0', 'app_base', NOW(), '0'),
('menu_ad_task_summary', '任务汇总', '/reporting/task-summary', 'menu_ad_data', 'lucide:clipboard-list', 'reporting/task-summary/index', 94, '0', 'app_base', NOW(), '0'),
('menu_ad_product_summary', '产品汇总', '/reporting/product-summary', 'menu_ad_data', 'lucide:package-search', 'reporting/product-summary/index', 93, '0', 'app_base', NOW(), '0'),
('menu_ad_link_summary', '链接数据汇总', '/reporting/link-summary', 'menu_ad_data', 'lucide:link-2', 'reporting/link-summary/index', 92, '0', 'app_base', NOW(), '0'),
('menu_ad_products', '产品管理', '/delivery/products', 'menu_ad_delivery', 'lucide:package', 'delivery/master-data/index', 89, '0', 'app_base', NOW(), '0'),
('menu_ad_channels', '媒体渠道管理', '/delivery/media-channels', 'menu_ad_delivery', 'lucide:radio-tower', 'delivery/master-data/index', 88, '0', 'app_base', NOW(), '0'),
('menu_ad_documents', '接入文档管理', '/delivery/documents', 'menu_ad_delivery', 'lucide:file-text', 'delivery/master-data/index', 87, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
name = VALUES(name), path = VALUES(path), parent_id = VALUES(parent_id), icon = VALUES(icon),
component = VALUES(component), sort = VALUES(sort), type = VALUES(type),
application_key = VALUES(application_key), del_flag = '0';

INSERT INTO sys_menu (id, name, permission, parent_id, sort, type, application_key, create_time, del_flag) VALUES
('perm_ad_customer_read', '查询上游客户', 'ad:customer:read', 'menu_ad_customer', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_customer_write', '编辑上游客户', 'ad:customer:write', 'menu_ad_customer', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_event_sensitive_read', '查询脱敏点击字段', 'ad:event-sensitive:read', 'menu_ad_events', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_short_link_read', '查询短链', 'ad:short-link:read', 'menu_ad_short_link', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_short_link_write', '编辑短链', 'ad:short-link:write', 'menu_ad_short_link', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_config_read', '查询广告配置', 'ad:config:read', 'menu_ad_platform_config', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_config_write', '编辑广告配置', 'ad:config:write', 'menu_ad_platform_config', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_channel_user_read', '查询渠道用户', 'ad:channel-user:read', 'menu_ad_channel_user', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_channel_user_write', '编辑渠道用户', 'ad:channel-user:write', 'menu_ad_channel_user', 1, '1', 'app_base', NOW(), '0'),
('perm_ad_task_scope_read', '查询任务授权', 'ad:task-scope:read', 'menu_ad_task_scope', 0, '1', 'app_base', NOW(), '0'),
('perm_ad_task_scope_write', '编辑任务授权', 'ad:task-scope:write', 'menu_ad_task_scope', 1, '1', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort),
type = VALUES(type), application_key = VALUES(application_key), del_flag = '0';

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN (
  'menu_ad_customer', 'perm_ad_customer_read', 'perm_ad_customer_write',
  'perm_ad_event_sensitive_read',
  'menu_ad_short_link', 'perm_ad_short_link_read', 'perm_ad_short_link_write',
  'menu_ad_platform_config', 'perm_ad_config_read', 'perm_ad_config_write',
  'menu_ad_channel_user', 'perm_ad_channel_user_read', 'perm_ad_channel_user_write',
  'menu_ad_overview',
  'menu_ad_task_scope', 'perm_ad_task_scope_read', 'perm_ad_task_scope_write',
  'menu_ad_media_summary',
  'menu_ad_summary', 'menu_ad_task_summary', 'menu_ad_product_summary', 'menu_ad_link_summary',
  'menu_ad_products', 'menu_ad_channels', 'menu_ad_documents'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN (
  'menu_ad_customer', 'perm_ad_customer_read', 'perm_ad_customer_write',
  'menu_ad_short_link', 'perm_ad_short_link_read', 'perm_ad_short_link_write',
  'menu_ad_platform_config', 'perm_ad_config_read', 'perm_ad_config_write',
  'menu_ad_channel_user', 'perm_ad_channel_user_read', 'perm_ad_channel_user_write',
  'menu_ad_overview',
  'menu_ad_media_summary',
  'menu_ad_summary', 'menu_ad_task_summary', 'menu_ad_product_summary', 'menu_ad_link_summary',
  'menu_ad_products', 'menu_ad_channels', 'menu_ad_documents'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN (
  'menu_ad_customer', 'perm_ad_customer_read',
  'menu_ad_short_link', 'perm_ad_short_link_read',
  'menu_ad_platform_config', 'perm_ad_config_read',
  'menu_ad_channel_user', 'perm_ad_channel_user_read',
  'menu_ad_overview',
  'menu_ad_media_summary',
  'menu_ad_summary', 'menu_ad_task_summary', 'menu_ad_product_summary', 'menu_ad_link_summary',
  'menu_ad_products', 'menu_ad_channels', 'menu_ad_documents'
)
ON DUPLICATE KEY UPDATE id = VALUES(id);
