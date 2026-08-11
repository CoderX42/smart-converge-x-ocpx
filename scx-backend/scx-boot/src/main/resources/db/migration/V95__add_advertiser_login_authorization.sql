-- V95: Advertiser login binding, safe account-specific authorization and
-- optional integration configuration. Forward-only; do not use the retired
-- scx_schema_migration ledger. Existing advertisers are bound only when one
-- unambiguous active type=2 account matches shop_id=advertiser_id.

ALTER TABLE scx_ocpx_advertiser
  MODIFY COLUMN adapter_code VARCHAR(32) NULL DEFAULT NULL,
  MODIFY COLUMN adapter_version BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN bid_url VARCHAR(5000) NULL,
  MODIFY COLUMN password_hash VARCHAR(100) NULL;

CREATE TABLE IF NOT EXISTS scx_ocpx_advertiser_login_binding (
  advertiser_id VARCHAR(32) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  managed_role_id VARCHAR(64) NULL,
  grant_version BIGINT NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (advertiser_id),
  UNIQUE KEY uk_scx_ocpx_advertiser_login_user (user_id),
  UNIQUE KEY uk_scx_ocpx_advertiser_login_managed_role (managed_role_id),
  CONSTRAINT ck_scx_ocpx_advertiser_login_grant_version CHECK (grant_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Advertiser to login-account authorization binding';

-- Historical OCPX and system identifier columns may use different utf8mb4
-- collations. Keep these as verified logical references instead of physical
-- foreign keys; verify-database.sh rejects every orphan binding.

INSERT INTO scx_ocpx_advertiser_login_binding
  (advertiser_id,user_id,managed_role_id,grant_version,create_by,update_by,create_time,update_time)
SELECT a.id,MIN(u.id),NULL,1,'V95_MIGRATION','V95_MIGRATION',NOW(3),NOW(3)
FROM scx_ocpx_advertiser a
JOIN sys_user u ON BINARY u.shop_id=BINARY a.id AND u.type='2' AND u.del_flag='0'
LEFT JOIN scx_ocpx_advertiser_login_binding b ON BINARY b.advertiser_id=BINARY a.id
WHERE a.deleted=0 AND b.advertiser_id IS NULL
GROUP BY a.id
HAVING COUNT(*)=1
ON DUPLICATE KEY UPDATE advertiser_id=scx_ocpx_advertiser_login_binding.advertiser_id;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_grant_v95','管理广告主账号授权','','menu_ocpx_advertiser_list',NULL,NULL,
   'ocpx:advertiser:grant',80,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id AND m.permission='ocpx:advertiser:grant'
JOIN sys_role r ON r.id=rm.role_id
WHERE r.role_code<>'ROLE_ADMIN';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v95_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),55)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:advertiser:grant' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';
