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
('menu_ad_cpa_summary','CPA 归因汇总','/reporting/cpa-summary','menu_ad_data','lucide:chart-no-axes-combined','reporting/cpa-summary/index',86,'0','app_base',NOW(),'0'),
('menu_ad_hourly_summary','小时归因汇总','/reporting/hourly-summary','menu_ad_data','lucide:chart-no-axes-combined','reporting/hourly-summary/index',85,'0','app_base',NOW(),'0'),
('menu_ad_result_summary','事件归因结果','/reporting/result-summary','menu_ad_data','lucide:chart-no-axes-combined','reporting/result-summary/index',84,'0','app_base',NOW(),'0'),
('menu_ad_result_cb_summary','回传归因结果','/reporting/result-cb-summary','menu_ad_data','lucide:chart-no-axes-combined','reporting/result-cb-summary/index',83,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  path = VALUES(path),
  component = VALUES(component),
  sort = VALUES(sort),
  del_flag = VALUES(del_flag);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('adm_', id), '1', id
FROM sys_menu
WHERE id IN ('menu_ad_cpa_summary', 'menu_ad_hourly_summary', 'menu_ad_result_summary', 'menu_ad_result_cb_summary')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id
FROM sys_menu
WHERE id IN ('menu_ad_cpa_summary', 'menu_ad_hourly_summary', 'menu_ad_result_summary', 'menu_ad_result_cb_summary')
ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id
FROM sys_menu
WHERE id IN ('menu_ad_cpa_summary', 'menu_ad_hourly_summary', 'menu_ad_result_summary', 'menu_ad_result_cb_summary')
ON DUPLICATE KEY UPDATE id = VALUES(id);
