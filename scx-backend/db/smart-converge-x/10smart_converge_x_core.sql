CREATE TABLE IF NOT EXISTS scx_media (
                                         id VARCHAR(64) NOT NULL,
                                         code VARCHAR(64) NOT NULL,
                                         name VARCHAR(128) NOT NULL,
                                         status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                                         timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
                                         settlement_currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
                                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                         PRIMARY KEY (id),
                                         UNIQUE KEY uk_scx_media_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 媒体主体';

INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag)
SELECT 'menu_subject_media', '媒体列表', '/subjects/media', 'menu_subject', 'carbon:network-4', 'subject/media/index', 9, '0', 'app_base', NOW(), '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'menu_subject_media');

INSERT INTO sys_menu (id, name, permission, parent_id, sort, type, application_key, create_time, del_flag)
SELECT 'perm_subject_media_read', '查询媒体主体', 'subject:media:read', 'menu_subject', 0, '1', 'app_base', NOW(), '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'perm_subject_media_read');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'role_menu_subject_media', '1', 'menu_subject_media'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = '1' AND menu_id = 'menu_subject_media');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'role_perm_subject_media_read', '1', 'perm_subject_media_read'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = '1' AND menu_id = 'perm_subject_media_read');
