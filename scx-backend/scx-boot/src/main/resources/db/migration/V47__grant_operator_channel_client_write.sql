-- Operators may manage channel credentials only for media that pass the
-- service-layer MediaScopeService.canWrite check. V41 granted write/rotate to
-- administrators only, so reconcile both missing role-to-permission edges.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 'op_ch_client_write_v47', id, 'perm_ad_channel_client_write'
FROM sys_role
WHERE role_code = 'ROLE_OPERATOR'
  AND del_flag = '0';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 'op_ch_client_rotate_v47', id, 'perm_ad_channel_client_rotate'
FROM sys_role
WHERE role_code = 'ROLE_OPERATOR'
  AND del_flag = '0';

-- Analysts are read-only but must be able to inspect channel clients for media
-- within their readable subject scope.
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 'ana_ch_client_menu_v47', id, 'menu_ad_channel_clients'
FROM sys_role
WHERE role_code = 'ROLE_ANALYST'
  AND del_flag = '0';

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id)
SELECT 'ana_ch_client_read_v47', id, 'perm_ad_channel_client_read'
FROM sys_role
WHERE role_code = 'ROLE_ANALYST'
  AND del_flag = '0';
