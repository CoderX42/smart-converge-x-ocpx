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
('menu_clk_task', '事件回传任务', '/task/clk', 'menu_ocpx_task', 'lucide:list-checks', 'delivery/tracking-task-clk/index', 35, '0', 'app_base', NOW(), '0'),
('menu_clk_data', '点击归因明细', '/data/clk-summary', 'menu_ocpx_data', 'lucide:chart-no-axes-combined', 'reporting/clk-summary/index', 36, '0', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  sort = VALUES(sort),
  del_flag = VALUES(del_flag);

INSERT INTO sys_role (
  id,
  role_name,
  role_code,
  role_desc,
  type,
  del_flag,
  create_time,
  create_by
) VALUES
('role_clk_user','回传事件用户','ROLE_CLK_USER','只读回传任务与点击归因范围','0','0',NOW(),'system')
ON DUPLICATE KEY UPDATE
  role_desc = VALUES(role_desc),
  role_name = VALUES(role_name),
  del_flag = VALUES(del_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('clk_', id), 'role_clk_user', id
FROM sys_menu
WHERE id IN ('menu_clk_task', 'menu_clk_data')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('clk_', id), '1', id
FROM sys_menu
WHERE id IN ('menu_clk_task', 'menu_clk_data')
ON DUPLICATE KEY UPDATE id = VALUES(id);
