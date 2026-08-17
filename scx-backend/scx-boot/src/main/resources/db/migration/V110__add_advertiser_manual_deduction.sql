-- Forward-only advertiser manual balance deduction.
--
-- Back up and preview the target database before applying this migration.
-- The application must be deployed only after this constraint extension is present.

USE `smart_converge_x`;
SET NAMES utf8mb4;

ALTER TABLE scx_ocpx_billing_entry
  DROP CHECK ck_ocpx_billing_type,
  ADD CONSTRAINT ck_ocpx_billing_type
    CHECK (entry_type IN ('RECHARGE','CLICK_CHARGE','MANUAL_DEBIT')),
  COMMENT='LEDGER: immutable advertiser recharge, CPC and manual debit entries';

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,
   create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_advertiser_deduct','扣减广告主余额','','menu_ocpx_advertiser',
   'ocpx:advertiser:deduct',35,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),
  update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v110_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),27)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.permission='ocpx:advertiser:deduct' AND m.del_flag='0'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id
WHERE rm.role_id='role_advertiser'
  AND m.permission='ocpx:advertiser:deduct';
