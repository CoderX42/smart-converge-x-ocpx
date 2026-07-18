ALTER TABLE scx_tracking_task
  ADD COLUMN  daily_conversion_limit INT NULL AFTER attribution_window_hours;

ALTER TABLE scx_attribution_result
  ADD COLUMN  over_limit TINYINT(1) NOT NULL DEFAULT 0 AFTER reason_code,
  ADD COLUMN  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER over_limit,
  ADD COLUMN  review_reason VARCHAR(500) NULL AFTER review_status,
  ADD COLUMN  reviewed_by VARCHAR(64) NULL AFTER review_reason,
  ADD COLUMN  reviewed_at DATETIME(3) NULL AFTER reviewed_by,
  ADD KEY  idx_scx_attr_review (review_status, over_limit, attributed_at);

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_attribution_review','转化审核','/operations/attribution-review','menu_ad_data','lucide:shield-check','operations/attribution-review/index',50,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';

INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_attribution_read','查询转化审核','ad:attribution:read','menu_ad_attribution_review',0,'1','app_base',NOW(),'0'),
('perm_ad_attribution_review','审核和审减转化','ad:attribution:review','menu_ad_attribution_review',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';

INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id IN ('menu_ad_attribution_review','perm_ad_attribution_read','perm_ad_attribution_review')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN ('menu_ad_attribution_review','perm_ad_attribution_read','perm_ad_attribution_review')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN ('menu_ad_attribution_review','perm_ad_attribution_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
