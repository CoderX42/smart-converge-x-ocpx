CREATE TABLE IF NOT EXISTS scx_upstream_customer (
  id VARCHAR(64) NOT NULL,
  media_id VARCHAR(64) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version INT NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scx_upstream_customer_media_code (media_id, code),
  KEY idx_scx_upstream_customer_media_status (media_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SCX 上游客户';

INSERT INTO scx_upstream_customer (id,media_id,code,name,description,status,version,create_by,update_by,create_time,update_time)
SELECT CONCAT('customer_', p.id),p.media_id,p.code,p.name,p.description,p.status,p.version,p.create_by,p.update_by,p.create_time,p.update_time
FROM scx_partner p
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status=VALUES(status),update_time=VALUES(update_time);

ALTER TABLE scx_product
  ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id,
  ADD KEY idx_scx_product_customer (customer_id);
ALTER TABLE scx_tracking_link
  ADD COLUMN customer_id VARCHAR(64) NULL AFTER partner_id,
  ADD KEY idx_scx_link_customer (customer_id);

UPDATE scx_product p SET p.customer_id=CONCAT('customer_',p.partner_id) WHERE p.customer_id IS NULL;
UPDATE scx_tracking_link l JOIN scx_product p ON p.id=l.product_id SET l.customer_id=p.customer_id WHERE l.customer_id IS NULL;

INSERT INTO sys_menu (id,name,path,parent_id,icon,component,sort,type,application_key,create_time,del_flag) VALUES
('menu_ad_customer','上游客户','/delivery/customers','menu_ad_delivery','lucide:building-2','delivery/customers/index',100,'0','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),path=VALUES(path),component=VALUES(component),del_flag='0';
INSERT INTO sys_menu (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag) VALUES
('perm_ad_customer_read','查询上游客户','ad:customer:read','menu_ad_customer',0,'1','app_base',NOW(),'0'),
('perm_ad_customer_write','编辑上游客户','ad:customer:write','menu_ad_customer',1,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),del_flag='0';
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adm_', id), '1', id FROM sys_menu WHERE id IN ('menu_ad_customer','perm_ad_customer_read','perm_ad_customer_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('opr_', id), 'role_operator', id FROM sys_menu WHERE id IN ('menu_ad_customer','perm_ad_customer_read','perm_ad_customer_write')
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('ana_', id), 'role_analyst', id FROM sys_menu WHERE id IN ('menu_ad_customer','perm_ad_customer_read')
ON DUPLICATE KEY UPDATE id=VALUES(id);
