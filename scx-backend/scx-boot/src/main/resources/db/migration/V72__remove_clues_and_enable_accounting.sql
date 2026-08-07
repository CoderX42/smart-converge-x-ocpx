-- V72: retire the automotive clue domain and enable idempotent recharge/CPC accounting.
-- Applying this migration permanently drops scx_adx_clue. Back up that table before deployment if its old data
-- must be retained outside SCX. Historical V68 remains immutable for migration auditability.

DROP PROCEDURE IF EXISTS scx_upgrade_accounting;
DELIMITER $$
CREATE PROCEDURE scx_upgrade_accounting()
BEGIN
  ALTER TABLE scx_adx_advertiser MODIFY COLUMN balance DECIMAL(20,6) NOT NULL DEFAULT 0.000000;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='balance_before') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN balance_before DECIMAL(20,6) NULL AFTER reason;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='balance_after') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN balance_after DECIMAL(20,6) NULL AFTER balance_before;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='operator_id') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN operator_id VARCHAR(100) NULL AFTER balance_after;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_ocpx_billing_entry' AND column_name='remark') THEN
    ALTER TABLE scx_ocpx_billing_entry ADD COLUMN remark VARCHAR(255) NULL AFTER operator_id;
  END IF;
END$$
DELIMITER ;
CALL scx_upgrade_accounting();
DROP PROCEDURE scx_upgrade_accounting;

ALTER TABLE scx_ocpx_billing_entry COMMENT='Idempotent advertiser recharge and click billing ledger';

DELETE FROM sys_role_menu
WHERE menu_id IN ('menu_adx_clue','perm_adx_clue_read','perm_adx_clue_write','perm_adx_clue_submit');

DELETE FROM sys_menu
WHERE id IN ('menu_adx_clue','perm_adx_clue_read','perm_adx_clue_write','perm_adx_clue_submit')
   OR permission IN ('adx:clue:read','adx:clue:write','adx:clue:submit');

DROP TABLE IF EXISTS scx_adx_clue;

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_adx_account_read','查询账务流水','adx:account:read','menu_adx_advertiser_list',30,'1','app_base',NOW(),'0'),
  ('perm_adx_account_recharge','余额充值','adx:account:recharge','menu_adx_advertiser_list',40,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('adx_', LEFT(SHA2(CONCAT(r.id,'|',m.id),256),60)), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_adx_account_read','perm_adx_account_recharge')
WHERE r.role_code IN ('ROLE_ADMIN','ROLE_OPERATOR') AND r.del_flag='0' AND m.del_flag='0';
