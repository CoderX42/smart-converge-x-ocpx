-- Forward-only hard replacement for the Vientiane-aligned OCPX management model.
--
-- This file is intentionally not auto-run. Back up and preview the exact V101
-- target first. The preflight fails instead of deleting or reviving data that
-- cannot be represented by the V102 model.

USE `smart_converge_x`;
SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS preflight_v102_ocpx_management$$
CREATE PROCEDURE preflight_v102_ocpx_management()
BEGIN
  DECLARE invalid_ids TEXT;

  SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',') INTO invalid_ids
  FROM scx_ocpx_advertiser WHERE deleted <> 0;
  IF invalid_ids IS NOT NULL THEN
    SELECT 'scx_ocpx_advertiser.deleted' AS manual_mapping_required, invalid_ids AS primary_keys;
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V102 blocked: soft-deleted advertiser ids require manual handling';
  END IF;

  SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',') INTO invalid_ids
  FROM scx_ocpx_media WHERE deleted <> 0;
  IF invalid_ids IS NOT NULL THEN
    SELECT 'scx_ocpx_media.deleted' AS manual_mapping_required, invalid_ids AS primary_keys;
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V102 blocked: soft-deleted media ids require manual handling';
  END IF;

  SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',') INTO invalid_ids
  FROM scx_ocpx_promotion_link
  WHERE platform IS NULL OR platform NOT IN ('ANDROID','IOS');
  IF invalid_ids IS NOT NULL THEN
    SELECT 'scx_ocpx_promotion_link.platform' AS manual_mapping_required,
           invalid_ids AS primary_keys;
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V102 blocked: WEB OTHER or invalid platform ids require manual mapping';
  END IF;
END$$
CALL preflight_v102_ocpx_management()$$
DROP PROCEDURE preflight_v102_ocpx_management$$
DELIMITER ;

ALTER TABLE scx_ocpx_promotion_link
  DROP CHECK ck_ocpx_promotion_link_platform;

UPDATE scx_ocpx_promotion_link
SET platform = CASE platform WHEN 'ANDROID' THEN '1' WHEN 'IOS' THEN '2' END;

ALTER TABLE scx_ocpx_advertiser
  DROP INDEX uk_ocpx_advertiser_code,
  DROP INDEX uk_ocpx_advertiser_name,
  DROP CHECK ck_ocpx_advertiser_status,
  DROP CHECK ck_ocpx_advertiser_credential_version,
  DROP CHECK ck_ocpx_advertiser_config_version,
  DROP COLUMN advertiser_code,
  DROP COLUMN adapter_code,
  MODIFY COLUMN credential_config_key VARCHAR(128) NULL,
  DROP COLUMN credential_version,
  DROP COLUMN upstream_host_allowlist,
  DROP COLUMN status,
  DROP COLUMN config_version,
  DROP COLUMN active_marker,
  DROP COLUMN deleted,
  ADD UNIQUE INDEX uk_ocpx_advertiser_name (advertiser_name);

ALTER TABLE scx_ocpx_promotion_link
  DROP INDEX uk_ocpx_promotion_link_code,
  DROP INDEX idx_ocpx_promotion_link_advertiser,
  DROP CHECK ck_ocpx_promotion_link_type,
  DROP CHECK ck_ocpx_promotion_link_status,
  DROP COLUMN link_code,
  DROP COLUMN package_name,
  MODIFY COLUMN platform TINYINT UNSIGNED NOT NULL,
  DROP COLUMN link_type,
  RENAME COLUMN link_url_template TO link_url,
  RENAME COLUMN deeplink_url TO deeplink,
  DROP COLUMN download_url,
  RENAME COLUMN macro_set TO link_ext,
  DROP COLUMN redirect_enabled,
  DROP COLUMN status,
  DROP COLUMN config_version,
  DROP COLUMN active_marker,
  RENAME COLUMN deleted TO is_delete,
  ADD INDEX idx_ocpx_promotion_link_advertiser (advertiser_id,is_delete,update_time),
  ADD CONSTRAINT ck_ocpx_promotion_link_platform CHECK (platform IN (1,2,3,4));

ALTER TABLE scx_ocpx_media
  DROP INDEX uk_ocpx_media_code,
  DROP INDEX uk_ocpx_media_name,
  DROP CHECK ck_ocpx_media_auth,
  DROP CHECK ck_ocpx_media_status,
  DROP CHECK ck_ocpx_media_credential,
  DROP COLUMN media_code,
  DROP COLUMN adapter_code,
  DROP COLUMN auth_type,
  DROP COLUMN credential_version,
  RENAME COLUMN ingress_uri TO uri,
  DROP COLUMN callback_url,
  DROP COLUMN callback_host_allowlist,
  DROP COLUMN status,
  DROP COLUMN config_version,
  DROP COLUMN active_marker,
  DROP COLUMN deleted,
  ADD UNIQUE INDEX uk_ocpx_media_name (media_name),
  ADD UNIQUE INDEX uk_ocpx_media_uri (uri);

ALTER TABLE scx_ocpx_monitor_task
  DROP FOREIGN KEY fk_ocpx_monitor_task_operator,
  DROP INDEX uk_ocpx_monitor_task_code,
  DROP INDEX idx_ocpx_monitor_task_dimensions,
  DROP CHECK ck_ocpx_monitor_task_status,
  DROP CHECK ck_ocpx_monitor_task_config_version,
  DROP COLUMN task_code,
  RENAME COLUMN task_name TO monitor_name,
  DROP COLUMN operator_user_id,
  DROP COLUMN status,
  DROP COLUMN config_version,
  DROP COLUMN archived_at,
  DROP COLUMN active_marker,
  RENAME COLUMN deleted TO is_delete,
  ADD INDEX idx_ocpx_monitor_task_dimensions
    (advertiser_id,promotion_link_id,media_id,is_delete);

ALTER TABLE scx_ocpx_deduction_rule
  DROP INDEX uk_ocpx_deduction_rule_route,
  DROP COLUMN active_marker,
  RENAME COLUMN deleted TO is_delete,
  ADD UNIQUE INDEX uk_ocpx_deduction_rule_route
    (media_id,promotion_link_id,(IF(is_delete=0,1,NULL)));

DELETE FROM sys_role_menu
WHERE menu_id IN (
  'perm_ocpx_advertiser_status','perm_ocpx_advertiser_archive',
  'perm_ocpx_promotion_link_status','perm_ocpx_promotion_link_archive',
  'perm_ocpx_promotion_link_validate','perm_ocpx_media_status',
  'perm_ocpx_media_archive','perm_ocpx_media_preflight',
  'perm_ocpx_media_connectivity','perm_ocpx_monitor_task_status',
  'perm_ocpx_monitor_task_archive'
);

UPDATE sys_menu
SET del_flag='1', update_time=NOW()
WHERE id IN (
  'perm_ocpx_advertiser_status','perm_ocpx_advertiser_archive',
  'perm_ocpx_promotion_link_status','perm_ocpx_promotion_link_archive',
  'perm_ocpx_promotion_link_validate','perm_ocpx_media_status',
  'perm_ocpx_media_archive','perm_ocpx_media_preflight',
  'perm_ocpx_media_connectivity','perm_ocpx_monitor_task_status',
  'perm_ocpx_monitor_task_archive'
);

UPDATE sys_menu
SET sort=30, update_time=NOW()
WHERE id IN ('perm_ocpx_advertiser_recharge','perm_ocpx_monitor_task_delete');

INSERT INTO sys_menu
  (id,name,path,parent_id,permission,sort,type,application_key,create_time,update_time,del_flag)
VALUES
  ('perm_ocpx_promotion_link_delete','删除推广链接','','menu_ocpx_promotion_link',
   'ocpx:promotion-link:delete',30,'1','app_base',NOW(),NOW(),'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),parent_id=VALUES(parent_id),
  permission=VALUES(permission),sort=VALUES(sort),type=VALUES(type),
  application_key=VALUES(application_key),update_time=NOW(),del_flag='0';

INSERT IGNORE INTO sys_role_menu(id,role_id,menu_id)
SELECT CONCAT('v102_',LEFT(SHA2(CONCAT(r.id,'|',m.id),256),54)),r.id,m.id
FROM sys_role r
JOIN sys_menu m ON m.id='perm_ocpx_promotion_link_delete'
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0' AND m.del_flag='0';
