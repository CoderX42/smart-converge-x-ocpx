-- V55: five business-domain navigation, permission matrix and responsibility reorganization.
-- V49-V54 remain immutable; this migration only moves their permission nodes forward.

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_biz_commercial','商业管理','/commercial','0','lucide:briefcase-business',NULL,10,'0','app_base',NOW(),'0'),
  ('menu_biz_finance','财务管理','/finance','0','lucide:landmark',NULL,15,'0','app_base',NOW(),'0'),
  ('menu_biz_inventory','媒体资源','/inventory','0','lucide:radio-tower',NULL,20,'0','app_base',NOW(),'0'),
  ('menu_biz_creative','创意管理','/creative','0','lucide:images',NULL,25,'0','app_base',NOW(),'0'),
  ('menu_biz_campaign','投放管理','/campaign','0','lucide:send',NULL,30,'0','app_base',NOW(),'0'),
  ('menu_biz_monitoring','监测归因','/monitoring','0','lucide:route',NULL,35,'0','app_base',NOW(),'0'),
  ('menu_biz_reporting','数据报表','/reporting','0','lucide:chart-no-axes-combined',NULL,40,'0','app_base',NOW(),'0'),
  ('menu_biz_operations','运营中心','/operations','0','lucide:workflow',NULL,45,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),del_flag='0',update_time=NOW();

UPDATE sys_menu SET name='工作台',sort=5,parent_id='0',del_flag='0',update_time=NOW()
WHERE id='menu_ocpx_dashboard';
UPDATE sys_menu SET name='系统管理',sort=50,parent_id='0',del_flag='0',update_time=NOW()
WHERE id='menu_ocpx_system';
UPDATE sys_menu SET name='工具箱',sort=55,parent_id='0',del_flag='0',update_time=NOW()
WHERE id='menu_ocpx_toolbox';

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag)
VALUES
  ('menu_com_org','组织与授权','/commercial/organizations','menu_biz_commercial','lucide:network','commercial/index',10,'0','app_base',NOW(),'0'),
  ('menu_com_adv','广告主管理','/commercial/advertisers','menu_biz_commercial','lucide:building-2','commercial/index',20,'0','app_base',NOW(),'0'),
  ('menu_com_agency','代理关系','/commercial/agencies','menu_biz_commercial','lucide:handshake','commercial/index',30,'0','app_base',NOW(),'0'),
  ('menu_com_account','广告账户','/commercial/ad-accounts','menu_biz_commercial','lucide:badge-cent','commercial/index',40,'0','app_base',NOW(),'0'),
  ('menu_com_qual','资质审核','/commercial/qualifications','menu_biz_commercial','lucide:badge-check','commercial/index',50,'0','app_base',NOW(),'0'),
  ('menu_com_agreement','协议与数据授权','/commercial/agreements','menu_biz_commercial','lucide:file-signature','commercial/index',60,'0','app_base',NOW(),'0'),

  ('menu_fin_account','资金账户','/finance/accounts','menu_biz_finance','lucide:wallet-cards','finance/index',10,'0','app_base',NOW(),'0'),
  ('menu_fin_journal','财务流水与借贷明细','/finance/journals','menu_biz_finance','lucide:book-open-check','finance/index',20,'0','app_base',NOW(),'0'),
  ('menu_fin_credit','授信与预算','/finance/credit-budget','menu_biz_finance','lucide:gauge','finance/index',30,'0','app_base',NOW(),'0'),
  ('menu_fin_refund','退款管理','/finance/refunds','menu_biz_finance','lucide:undo-2','finance/index',40,'0','app_base',NOW(),'0'),
  ('menu_fin_rebate','返点管理','/finance/rebates','menu_biz_finance','lucide:percent','finance/index',50,'0','app_base',NOW(),'0'),
  ('menu_fin_invoice','发票管理','/finance/invoices','menu_biz_finance','lucide:receipt-text','finance/index',60,'0','app_base',NOW(),'0'),
  ('menu_fin_recon','对账与关账','/finance/reconciliation','menu_biz_finance','lucide:scale','finance/index',70,'0','app_base',NOW(),'0'),

  ('menu_inv_account','媒体账户','/inventory/media-accounts','menu_biz_inventory','lucide:server-cog','inventory/index',10,'0','app_base',NOW(),'0'),
  ('menu_inv_placement','广告位与能力矩阵','/inventory/placements','menu_biz_inventory','lucide:panels-top-left','inventory/index',20,'0','app_base',NOW(),'0'),

  ('menu_cr_asset','素材库','/creative/assets','menu_biz_creative','lucide:library','creative/index',10,'0','app_base',NOW(),'0'),
  ('menu_cr_review','素材审核','/creative/reviews','menu_biz_creative','lucide:scan-search','creative/index',20,'0','app_base',NOW(),'0'),
  ('menu_cr_landing','落地页','/creative/landing-pages','menu_biz_creative','lucide:panel-top','creative/index',30,'0','app_base',NOW(),'0'),

  ('menu_camp_plan','推广计划','/campaign/plans','menu_biz_campaign','lucide:megaphone','campaign/index',10,'0','app_base',NOW(),'0'),
  ('menu_camp_review','计划审核中心','/campaign/reviews','menu_biz_campaign','lucide:list-checks','campaign/index',20,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),path=VALUES(path),parent_id=VALUES(parent_id),icon=VALUES(icon),
  component=VALUES(component),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),del_flag='0',update_time=NOW();

-- Reattach permissions created by V48-V54 to their operating pages.
UPDATE sys_menu SET parent_id='menu_com_org',update_time=NOW()
WHERE id IN ('perm_com_org_read','perm_com_scope_read','perm_com_scope_write');
UPDATE sys_menu SET parent_id='menu_com_adv',update_time=NOW()
WHERE id IN ('perm_com_adv_read','perm_com_adv_write');
UPDATE sys_menu SET parent_id='menu_com_agency',update_time=NOW()
WHERE id IN ('perm_com_agency_read','perm_com_agency_write');
UPDATE sys_menu SET parent_id='menu_com_account',update_time=NOW()
WHERE id IN ('perm_com_account_read','perm_com_account_write');
UPDATE sys_menu SET parent_id='menu_com_qual',update_time=NOW()
WHERE id IN ('perm_com_qual_read','perm_com_qual_write','perm_com_qual_review');
UPDATE sys_menu SET parent_id='menu_com_agreement',update_time=NOW()
WHERE id IN ('perm_com_agree_read','perm_com_agree_write','perm_com_data_write');

UPDATE sys_menu SET parent_id='menu_fin_journal',update_time=NOW()
WHERE id IN ('perm_fin_ledger_read','perm_fin_ledger_write');
UPDATE sys_menu SET parent_id='menu_inv_account',update_time=NOW()
WHERE id IN ('perm_inv_account_read','perm_inv_account_write');
UPDATE sys_menu SET parent_id='menu_inv_placement',update_time=NOW()
WHERE id IN ('perm_inv_place_read','perm_inv_place_write');
UPDATE sys_menu SET parent_id='menu_cr_asset',update_time=NOW()
WHERE id IN ('perm_cr_asset_read','perm_cr_asset_write');
UPDATE sys_menu SET parent_id='menu_cr_review',update_time=NOW() WHERE id='perm_cr_review';
UPDATE sys_menu SET parent_id='menu_cr_landing',update_time=NOW()
WHERE id IN ('perm_cr_lp_read','perm_cr_lp_write');
UPDATE sys_menu SET parent_id='menu_camp_plan',update_time=NOW()
WHERE id IN ('perm_cr_bind_write','perm_camp_read','perm_camp_write');
UPDATE sys_menu SET parent_id='menu_camp_review',update_time=NOW() WHERE id='perm_camp_review';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_com_org_write','管理商业组织','commercial:organization:write','menu_com_org',2,'1','app_base',NOW(),'0'),
  ('perm_com_data_read','查询数据授权','commercial:data-authorization:read','menu_com_agreement',3,'1','app_base',NOW(),'0'),

  ('perm_fin_refund_read','查询退款','finance:refund:read','menu_fin_refund',1,'1','app_base',NOW(),'0'),
  ('perm_fin_refund_write','管理退款','finance:refund:write','menu_fin_refund',2,'1','app_base',NOW(),'0'),
  ('perm_fin_refund_review','审核退款','finance:refund:review','menu_fin_refund',3,'1','app_base',NOW(),'0'),
  ('perm_fin_rebate_read','查询返点','finance:rebate:read','menu_fin_rebate',1,'1','app_base',NOW(),'0'),
  ('perm_fin_rebate_write','管理返点','finance:rebate:write','menu_fin_rebate',2,'1','app_base',NOW(),'0'),
  ('perm_fin_invoice_read','查询发票','finance:invoice:read','menu_fin_invoice',1,'1','app_base',NOW(),'0'),
  ('perm_fin_invoice_write','管理发票','finance:invoice:write','menu_fin_invoice',2,'1','app_base',NOW(),'0'),
  ('perm_fin_recon_read','查询对账','finance:reconciliation:read','menu_fin_recon',1,'1','app_base',NOW(),'0'),
  ('perm_fin_recon_write','管理对账','finance:reconciliation:write','menu_fin_recon',2,'1','app_base',NOW(),'0'),
  ('perm_fin_closing_read','查询关账','finance:closing:read','menu_fin_recon',3,'1','app_base',NOW(),'0'),
  ('perm_fin_closing_write','执行关账','finance:closing:write','menu_fin_recon',4,'1','app_base',NOW(),'0'),
  ('perm_fin_adjust_read','查询调整流水','finance:adjustment:read','menu_fin_recon',5,'1','app_base',NOW(),'0'),
  ('perm_fin_adjust_write','管理调整流水','finance:adjustment:write','menu_fin_recon',6,'1','app_base',NOW(),'0'),

  ('perm_inv_cap_write','维护媒体能力','inventory:capability:write','menu_inv_placement',3,'1','app_base',NOW(),'0'),
  ('perm_cr_bind_read','查询创意绑定','creative:binding:read','menu_camp_plan',4,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),
  del_flag='0',update_time=NOW();

-- Responsibility-based reorganization of existing SCX administration pages.
UPDATE sys_menu SET parent_id='menu_biz_commercial',sort=70,update_time=NOW()
WHERE id IN ('menu_ad_products','menu_ad_customer');
UPDATE sys_menu SET parent_id='menu_biz_inventory',sort=60,update_time=NOW()
WHERE id IN ('menu_ad_master','menu_ad_channels','menu_ad_documents','menu_ad_channel_user');
UPDATE sys_menu SET parent_id='menu_biz_monitoring',sort=10,update_time=NOW()
WHERE id IN ('menu_ad_link','menu_ad_task','menu_ad_rta','menu_ad_events');
UPDATE sys_menu SET parent_id='menu_biz_reporting',update_time=NOW()
WHERE id IN ('menu_ad_daily','menu_ad_summary','menu_ad_task_summary','menu_ad_product_summary',
             'menu_ad_link_summary','menu_ad_media_summary','menu_ad_cpa_summary',
             'menu_ad_hourly_summary','menu_ad_result_summary','menu_ad_result_cb_summary');
UPDATE sys_menu SET parent_id='menu_biz_operations',update_time=NOW()
WHERE id IN ('menu_ad_jobs','menu_ad_queue_overview');
UPDATE sys_menu SET parent_id='menu_ocpx_system',update_time=NOW()
WHERE id IN ('menu_ad_platform_config','menu_ad_platform_credentials','menu_ad_task_scope','menu_ad_channel_clients');
UPDATE sys_menu SET parent_id='menu_ocpx_toolbox',update_time=NOW()
WHERE id IN ('menu_toolbox_dp','menu_toolbox_short');

-- Preserve historical rows/routes but remove duplicate or responsibility-mixed roots.
UPDATE sys_menu SET del_flag='1',update_time=NOW()
WHERE id IN ('menu_ocpx_product','menu_ocpx_link','menu_ocpx_media','menu_ocpx_task',
             'menu_ocpx_rta','menu_ocpx_data','menu_ad_delivery','menu_ad_data',
             'menu_ad_short_link','menu_pay','menu_generator','menu_dashboard');

-- Preserve legacy page visibility after moving it below a new responsibility root.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT DISTINCT CONCAT('v55_',LEFT(SHA2(CONCAT(rm.role_id,'|',m.parent_id),256),59)),
       rm.role_id,m.parent_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE m.parent_id IN ('menu_biz_commercial','menu_biz_inventory','menu_biz_monitoring',
                      'menu_biz_reporting','menu_biz_operations')
  AND m.del_flag='0';

-- Administrator: every five-domain page and action.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v55_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON (
  m.id LIKE 'menu_biz_%' OR m.id LIKE 'menu_com_%' OR m.id LIKE 'menu_fin_%'
  OR m.id LIKE 'menu_inv_%' OR m.id LIKE 'menu_cr_%' OR m.id LIKE 'menu_camp_%'
  OR m.id LIKE 'perm_com_%' OR m.id LIKE 'perm_fin_%' OR m.id LIKE 'perm_inv_%'
  OR m.id LIKE 'perm_cr_%' OR m.id LIKE 'perm_camp_%')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';

-- Operator: all business pages and daily writes; no organization/scope administration,
-- qualification/creative/campaign/refund review, closing or adjustment.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v55_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_commercial','menu_biz_finance','menu_biz_inventory','menu_biz_creative','menu_biz_campaign',
  'menu_com_org','menu_com_adv','menu_com_agency','menu_com_account','menu_com_qual','menu_com_agreement',
  'menu_fin_account','menu_fin_journal','menu_fin_credit','menu_fin_refund','menu_fin_rebate','menu_fin_invoice','menu_fin_recon',
  'menu_inv_account','menu_inv_placement','menu_cr_asset','menu_cr_review','menu_cr_landing','menu_camp_plan','menu_camp_review',
  'perm_com_org_read','perm_com_adv_read','perm_com_adv_write','perm_com_agency_read','perm_com_agency_write',
  'perm_com_account_read','perm_com_account_write','perm_com_qual_read','perm_com_qual_write',
  'perm_com_agree_read','perm_com_agree_write','perm_com_data_read','perm_com_data_write',
  'perm_fin_ledger_read','perm_fin_ledger_write','perm_fin_refund_read','perm_fin_refund_write',
  'perm_fin_rebate_read','perm_fin_rebate_write','perm_fin_invoice_read','perm_fin_invoice_write',
  'perm_fin_recon_read','perm_fin_recon_write','perm_fin_closing_read','perm_fin_adjust_read',
  'perm_inv_account_read','perm_inv_account_write','perm_inv_place_read','perm_inv_place_write','perm_inv_cap_write',
  'perm_cr_asset_read','perm_cr_asset_write','perm_cr_lp_read','perm_cr_lp_write','perm_cr_bind_read','perm_cr_bind_write',
  'perm_camp_read','perm_camp_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

-- Analyst: five domains are visible but strictly read-only.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v55_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),59)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'menu_biz_commercial','menu_biz_finance','menu_biz_inventory','menu_biz_creative','menu_biz_campaign',
  'menu_com_org','menu_com_adv','menu_com_agency','menu_com_account','menu_com_qual','menu_com_agreement',
  'menu_fin_account','menu_fin_journal','menu_fin_credit','menu_fin_refund','menu_fin_rebate','menu_fin_invoice','menu_fin_recon',
  'menu_inv_account','menu_inv_placement','menu_cr_asset','menu_cr_review','menu_cr_landing','menu_camp_plan','menu_camp_review',
  'perm_com_org_read','perm_com_adv_read','perm_com_agency_read','perm_com_account_read',
  'perm_com_qual_read','perm_com_agree_read','perm_com_data_read',
  'perm_fin_ledger_read','perm_fin_refund_read','perm_fin_rebate_read','perm_fin_invoice_read',
  'perm_fin_recon_read','perm_fin_closing_read','perm_fin_adjust_read',
  'perm_inv_account_read','perm_inv_place_read','perm_cr_asset_read','perm_cr_lp_read',
  'perm_cr_bind_read','perm_camp_read')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';

-- Reconcile forbidden grants from earlier broad module migrations.
DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id=rm.role_id
WHERE r.role_code='ROLE_OPERATOR'
  AND rm.menu_id IN ('perm_com_org_write','perm_com_scope_read','perm_com_scope_write',
    'perm_com_qual_review','perm_fin_refund_review','perm_fin_closing_write',
    'perm_fin_adjust_write','perm_cr_review','perm_camp_review');

DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id=rm.role_id
WHERE r.role_code='ROLE_ANALYST'
  AND rm.menu_id IN (
    'perm_com_org_write','perm_com_adv_write','perm_com_agency_write','perm_com_account_write',
    'perm_com_qual_write','perm_com_qual_review','perm_com_agree_write','perm_com_data_write',
    'perm_com_scope_write','perm_fin_ledger_write','perm_fin_refund_write','perm_fin_refund_review',
    'perm_fin_rebate_write','perm_fin_invoice_write','perm_fin_recon_write','perm_fin_closing_write',
    'perm_fin_adjust_write','perm_inv_account_write','perm_inv_place_write','perm_inv_cap_write',
    'perm_cr_asset_write','perm_cr_review','perm_cr_lp_write','perm_cr_bind_write',
    'perm_camp_write','perm_camp_review');

-- Callback users must not see or call any of the five business domains.
DELETE rm FROM sys_role_menu rm
JOIN sys_role r ON r.id=rm.role_id
JOIN sys_menu m ON m.id=rm.menu_id
WHERE r.role_code='ROLE_CLK_USER'
  AND (m.id LIKE 'menu_biz_%' OR m.id LIKE 'menu_com_%' OR m.id LIKE 'menu_fin_%'
    OR m.id LIKE 'menu_inv_%' OR m.id LIKE 'menu_cr_%' OR m.id LIKE 'menu_camp_%'
    OR m.id LIKE 'perm_com_%' OR m.id LIKE 'perm_fin_%' OR m.id LIKE 'perm_inv_%'
    OR m.id LIKE 'perm_cr_%' OR m.id LIKE 'perm_camp_%');
