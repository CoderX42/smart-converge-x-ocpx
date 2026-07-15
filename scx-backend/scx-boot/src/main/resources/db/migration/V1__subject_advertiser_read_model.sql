CREATE TABLE IF NOT EXISTS scx_advertiser (
    id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_advertiser_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 广告主体';

CREATE TABLE IF NOT EXISTS scx_user_subject_scope (
    id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_user_subject_scope (user_id, subject_type, subject_id),
    KEY idx_scx_subject_scope_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCX 用户主体对象授权范围';

INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag)
SELECT 'menu_subject', '广告主体', '/subjects', '0', 'carbon:ibm-watson-discovery', NULL, 85, '0', 'app_base', NOW(), '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'menu_subject');

INSERT INTO sys_menu (id, name, path, parent_id, icon, component, sort, type, application_key, create_time, del_flag)
SELECT 'menu_subject_advertiser', '主体列表', '/subjects/advertisers', 'menu_subject', 'carbon:building', 'subject/advertiser/index', 10, '0', 'app_base', NOW(), '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'menu_subject_advertiser');

INSERT INTO sys_menu (id, name, permission, parent_id, sort, type, application_key, create_time, del_flag)
SELECT 'perm_subject_adv_read', '查询广告主体', 'subject:advertiser:read', 'menu_subject', 0, '1', 'app_base', NOW(), '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 'perm_subject_adv_read');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'role_menu_subject', '1', 'menu_subject'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = '1' AND menu_id = 'menu_subject');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'role_menu_subject_advertiser', '1', 'menu_subject_advertiser'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = '1' AND menu_id = 'menu_subject_advertiser');

INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 'role_perm_subject_adv_read', '1', 'perm_subject_adv_read'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = '1' AND menu_id = 'perm_subject_adv_read');
