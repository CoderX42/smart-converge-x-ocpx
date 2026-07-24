DELIMITER $$
CREATE PROCEDURE scx_upgrade_ocpx_channel_management()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_channel_client' AND index_name='uk_scx_channel_media_source') THEN
    ALTER TABLE scx_channel_client ADD UNIQUE KEY uk_scx_channel_media_source (media_id, source_code);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name='payload_json') THEN
    ALTER TABLE scx_callback_job ADD COLUMN payload_json JSON NULL AFTER adapter_code;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND column_name='payload_hash') THEN
    ALTER TABLE scx_callback_job ADD COLUMN payload_hash VARCHAR(64) NULL AFTER payload_json;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_raw_event' AND index_name='idx_scx_raw_lease_claim') THEN
    ALTER TABLE scx_raw_event ADD KEY idx_scx_raw_lease_claim (processing_status, locked_until, received_at, id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_event_outbox' AND index_name='idx_scx_outbox_lease_claim') THEN
    ALTER TABLE scx_event_outbox ADD KEY idx_scx_outbox_lease_claim (locked_until, status, next_retry_at, create_time, id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='scx_callback_job' AND index_name='idx_scx_callback_lease_claim') THEN
    ALTER TABLE scx_callback_job ADD KEY idx_scx_callback_lease_claim (locked_until, status, next_retry_at, create_time, id);
  END IF;
END$$
DELIMITER ;

CALL scx_upgrade_ocpx_channel_management();
DROP PROCEDURE scx_upgrade_ocpx_channel_management;

INSERT INTO sys_menu (
  id, name, path, parent_id, icon, component, sort, type,
  application_key, create_time, del_flag
) VALUES (
  'menu_ad_channel_clients', '渠道接入凭据', '/delivery/channel-clients',
  'menu_ocpx_system', 'lucide:shield-keyhole', 'delivery/channel-clients/index',
  16, '0', 'app_base', NOW(), '0'
)
ON DUPLICATE KEY UPDATE
  name=VALUES(name), path=VALUES(path), parent_id=VALUES(parent_id),
  icon=VALUES(icon), component=VALUES(component), sort=VALUES(sort),
  type=VALUES(type), application_key=VALUES(application_key), del_flag='0';

INSERT INTO sys_menu (
  id, name, permission, parent_id, sort, type, application_key, create_time, del_flag
) VALUES
  ('perm_ad_channel_client_read', '查询渠道接入凭据', 'ad:channel-client:read', 'menu_ad_channel_clients', 0, '1', 'app_base', NOW(), '0'),
  ('perm_ad_channel_client_write', '编辑渠道接入凭据', 'ad:channel-client:write', 'menu_ad_channel_clients', 1, '1', 'app_base', NOW(), '0'),
  ('perm_ad_channel_client_rotate', '轮换渠道接入密钥', 'ad:channel-client:rotate', 'menu_ad_channel_clients', 2, '1', 'app_base', NOW(), '0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name), permission=VALUES(permission), parent_id=VALUES(parent_id),
  sort=VALUES(sort), type=VALUES(type), application_key=VALUES(application_key), del_flag='0';

-- sys_role_menu.id is VARCHAR(32); keep explicit short IDs under the limit.
INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
  ('adm_menu_ad_channel_clients', '1', 'menu_ad_channel_clients'),
  ('adm_perm_ad_ch_client_read', '1', 'perm_ad_channel_client_read'),
  ('adm_perm_ad_ch_client_write', '1', 'perm_ad_channel_client_write'),
  ('adm_perm_ad_ch_client_rotate', '1', 'perm_ad_channel_client_rotate')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), menu_id=VALUES(menu_id);

INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
  ('opr_menu_ad_channel_clients', 'role_operator', 'menu_ad_channel_clients'),
  ('opr_perm_ad_ch_client_read', 'role_operator', 'perm_ad_channel_client_read')
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id), menu_id=VALUES(menu_id);
