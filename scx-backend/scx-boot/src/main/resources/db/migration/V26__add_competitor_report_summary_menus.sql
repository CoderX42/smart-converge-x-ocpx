-- 竞品对标：将已有多维聚合能力提供为独立运营入口，接口仍统一受 ad:report:read 与任务范围约束。
INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_summary','数据汇总','/reporting/summary','menu_ad_data','lucide:chart-no-axes-combined','reporting/summary/index',96,'0','app_base',NOW(),'0'),
('menu_ad_task_summary','任务汇总','/reporting/task-summary','menu_ad_data','lucide:clipboard-list','reporting/task-summary/index',94,'0','app_base',NOW(),'0'),
('menu_ad_product_summary','产品汇总','/reporting/product-summary','menu_ad_data','lucide:package-search','reporting/product-summary/index',93,'0','app_base',NOW(),'0'),
('menu_ad_link_summary','链接数据汇总','/reporting/link-summary','menu_ad_data','lucide:link-2','reporting/link-summary/index',92,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu
WHERE id IN ('menu_ad_summary','menu_ad_task_summary','menu_ad_product_summary','menu_ad_link_summary')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu
WHERE id IN ('menu_ad_summary','menu_ad_task_summary','menu_ad_product_summary','menu_ad_link_summary')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu
WHERE id IN ('menu_ad_summary','menu_ad_task_summary','menu_ad_product_summary','menu_ad_link_summary')
ON DUPLICATE KEY UPDATE id=VALUES(id);
