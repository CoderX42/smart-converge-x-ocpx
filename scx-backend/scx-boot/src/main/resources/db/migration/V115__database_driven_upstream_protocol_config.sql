-- V115: move advertiser protocol selection and protocol host allowlists from
-- Engine profile files into administrator-managed MySQL configuration.
-- Existing unregistered advertisers remain NULL and therefore fail closed
-- until an administrator explicitly selects their protocol.

ALTER TABLE scx_ocpx_advertiser
  ADD COLUMN upstream_protocol VARCHAR(32) NULL AFTER advertiser_name,
  MODIFY COLUMN bid_url VARCHAR(5000) NULL,
  ADD CONSTRAINT ck_ocpx_advertiser_upstream_protocol
    CHECK (upstream_protocol IS NULL OR upstream_protocol IN ('TEMPLATE_GET','AERIM','WEIFENG'));

UPDATE scx_ocpx_advertiser
SET upstream_protocol='AERIM'
WHERE id IN ('2088520308582047745','2089268457885999105','2089274572543692802')
AND upstream_protocol IS NULL;

UPDATE scx_ocpx_advertiser
SET upstream_protocol='WEIFENG'
WHERE id IN ('2089239825436160002','2089280261676670978')
AND upstream_protocol IS NULL;

CREATE TABLE scx_ocpx_protocol_allowed_host (
  id VARCHAR(32) NOT NULL,
  protocol_type VARCHAR(32) NOT NULL,
  host VARCHAR(253) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ocpx_protocol_allowed_host (protocol_type,host),
  CONSTRAINT ck_ocpx_protocol_allowed_host_type CHECK (protocol_type IN ('AERIM','WEIFENG')),
  CONSTRAINT ck_ocpx_protocol_allowed_host_value CHECK (
    host=LOWER(host) AND host NOT LIKE '%://%' AND host NOT LIKE '%/%'
    AND host NOT LIKE '%:%' AND host NOT LIKE '%*%'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='CONFIG: administrator-managed exact upstream protocol hosts';

INSERT INTO scx_ocpx_protocol_allowed_host
  (id,protocol_type,host,create_time,update_time)
VALUES
  ('v115_aerim_d','AERIM','d.aerim.cn',NOW(3),NOW(3)),
  ('v115_aerim_c','AERIM','c.aerim.cn',NOW(3),NOW(3)),
  ('v115_weifeng_adapi','WEIFENG','ad.adapi.cn',NOW(3),NOW(3));

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_protocol_config_manage','管理上游协议与允许域名','','menu_ocpx_advertiser',
   'ocpx:protocol-config:manage',70,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v115_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),54)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:protocol-config:manage' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id AND m.permission='ocpx:protocol-config:manage'
LEFT JOIN sys_role r ON r.id=rm.role_id
WHERE r.id IS NULL OR r.role_code<>'ROLE_ADMIN' OR r.del_flag<>'0';
