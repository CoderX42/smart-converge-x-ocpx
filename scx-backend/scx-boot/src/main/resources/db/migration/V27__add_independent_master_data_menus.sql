-- 竞品对标：主数据保留统一控制台，同时提供产品、媒体渠道、接入文档的独立运营入口。
INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_products','产品管理','/delivery/products','menu_ad_delivery','lucide:package','delivery/master-data/index',89,'0','app_base',NOW(),'0'),
('menu_ad_channels','媒体渠道管理','/delivery/media-channels','menu_ad_delivery','lucide:radio-tower','delivery/master-data/index',88,'0','app_base',NOW(),'0'),
('menu_ad_documents','接入文档管理','/delivery/documents','menu_ad_delivery','lucide:file-text','delivery/master-data/index',87,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu
WHERE id IN ('menu_ad_products','menu_ad_channels','menu_ad_documents')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu
WHERE id IN ('menu_ad_products','menu_ad_channels','menu_ad_documents')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu
WHERE id IN ('menu_ad_products','menu_ad_channels','menu_ad_documents')
ON DUPLICATE KEY UPDATE id=VALUES(id);
