-- S9 Dashboard does not introduce another fact table. It reads the S8 unified
-- hourly projection. This forward migration adds the proven three-object
-- scope access path and closes the canonical permission contract from the
-- legacy `read` code to `view`.

SET @s9_dashboard_scope_index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='scx_adx_hourly_report'
    AND index_name='idx_scx_adx_dashboard_scope'
);
SET @s9_dashboard_scope_index_ddl := IF(
  @s9_dashboard_scope_index_exists=0,
  'ALTER TABLE scx_adx_hourly_report ADD INDEX idx_scx_adx_dashboard_scope (advertiser_id,channel_id,media_account_id,time_hour)',
  'SELECT 1'
);
PREPARE s9_dashboard_scope_index_stmt FROM @s9_dashboard_scope_index_ddl;
EXECUTE s9_dashboard_scope_index_stmt;
DEALLOCATE PREPARE s9_dashboard_scope_index_stmt;

INSERT INTO sys_menu
  (id,name,path,parent_id,icon,component,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_adx_dashboard_view_v90','查看经营总览','','menu_adx_home',NULL,NULL,
   'adx:dashboard:view',10,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),permission=VALUES(permission),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

-- Preserve every role that previously had the legacy Dashboard grant before
-- retiring that grant. No business-object scope is inferred or expanded.
INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v90_',LEFT(SHA2(CONCAT(rm.role_id,'|perm_adx_dashboard_view_v90'),256),55)),
       rm.role_id,'perm_adx_dashboard_view_v90'
FROM sys_role_menu rm
JOIN sys_role r ON r.id=rm.role_id AND r.del_flag='0'
WHERE rm.menu_id='perm_adx_dashboard_read';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v90_',LEFT(SHA2(CONCAT(r.id,'|perm_adx_dashboard_view_v90'),256),55)),
       r.id,'perm_adx_dashboard_view_v90'
FROM sys_role r
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

DELETE FROM sys_role_menu WHERE menu_id='perm_adx_dashboard_read';
UPDATE sys_menu SET del_flag='1',update_time=NOW() WHERE id='perm_adx_dashboard_read';
