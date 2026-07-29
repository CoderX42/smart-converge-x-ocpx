-- V53: Creative — assets, landing pages, reviews, bindings
CREATE TABLE IF NOT EXISTS scx_creative_asset (
    id VARCHAR(32) NOT NULL, media_id VARCHAR(32) NOT NULL, org_id VARCHAR(32) NOT NULL,
    asset_type VARCHAR(32) NOT NULL COMMENT 'IMAGE|VIDEO|AUDIO|HTML|TEXT',
    asset_name VARCHAR(256) NOT NULL, file_url VARCHAR(512) NULL COMMENT '对象存储引用',
    mime_type VARCHAR(64) NULL, file_size BIGINT NULL, file_hash VARCHAR(128) NULL COMMENT 'SHA-256',
    width INT NULL, height INT NULL, duration_ms INT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|UPLOADED|SCANNING|REVIEWING|APPROVED|REJECTED|PUBLISHED|SUSPENDED',
    current_revision_id VARCHAR(32) NULL,
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_ca_media_org (media_id, org_id), INDEX idx_ca_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创意素材';

CREATE TABLE IF NOT EXISTS scx_creative_asset_revision (
    id VARCHAR(32) NOT NULL, asset_id VARCHAR(32) NOT NULL, version INT NOT NULL DEFAULT 1,
    file_url VARCHAR(512) NOT NULL, mime_type VARCHAR(64) NULL, file_size BIGINT NULL,
    file_hash VARCHAR(128) NULL, width INT NULL, height INT NULL, duration_ms INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_car (asset_id, version), INDEX idx_car_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_asset_binary (
    id VARCHAR(32) NOT NULL, revision_id VARCHAR(32) NOT NULL UNIQUE,
    storage_key VARCHAR(512) NOT NULL COMMENT '对象存储Key或URL', storage_provider VARCHAR(64) DEFAULT 'SYSTEM',
    content_length BIGINT NOT NULL, content_hash VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材二进制引用';

CREATE TABLE IF NOT EXISTS scx_creative_review_case (
    id VARCHAR(32) NOT NULL, asset_id VARCHAR(32) NOT NULL, revision_id VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SUBMITTED|REVIEWING|APPROVED|REJECTED',
    submitted_at DATETIME NULL, reviewed_at DATETIME NULL,
    reviewer_id VARCHAR(32) NULL, review_comment VARCHAR(1024) NULL,
    virus_scan_status VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING|CLEAN|INFECTED|ERROR',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_crc_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材审核案件';

CREATE TABLE IF NOT EXISTS scx_creative_review_log (
    id VARCHAR(32) NOT NULL, case_id VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL, to_status VARCHAR(32) NOT NULL,
    operator_id VARCHAR(32) NOT NULL, operator_name VARCHAR(64) NOT NULL,
    comment VARCHAR(1024) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_crl_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材审核日志（不可变）';

CREATE TABLE IF NOT EXISTS scx_landing_page (
    id VARCHAR(32) NOT NULL, org_id VARCHAR(32) NOT NULL,
    page_name VARCHAR(256) NOT NULL, page_type VARCHAR(32) NOT NULL COMMENT 'HTML|MINI_PROGRAM|APP_DEEP_LINK',
    target_url VARCHAR(512) NULL COMMENT '跳转目标（须白名单域名）',
    schema_version INT NOT NULL DEFAULT 1 COMMENT '版本化Schema',
    content JSON NULL COMMENT '页面内容（禁止任意JS）',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|PUBLISHED|SUSPENDED|ARCHIVED',
    current_revision_id VARCHAR(32) NULL,
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_lp_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='落地页';

CREATE TABLE IF NOT EXISTS scx_landing_page_revision (
    id VARCHAR(32) NOT NULL, landing_page_id VARCHAR(32) NOT NULL, version INT NOT NULL DEFAULT 1,
    target_url VARCHAR(512) NULL, content JSON NULL, schema_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_lpr (landing_page_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='落地页版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_creative_binding (
    id VARCHAR(32) NOT NULL, ad_group_id VARCHAR(32) NULL COMMENT 'AdGroup ID (R1-07)',
    creative_asset_id VARCHAR(32) NOT NULL, revision_id VARCHAR(32) NOT NULL,
    landing_page_id VARCHAR(32) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_cb_ag_asset (ad_group_id, creative_asset_id),
    INDEX idx_cb_asset (creative_asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创意绑定';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_cr_asset_read','查询素材','creative:asset:read','menu_ad_master',50,'1','app_base',NOW(),'0'),
  ('perm_cr_asset_write','管理素材','creative:asset:write','menu_ad_master',51,'1','app_base',NOW(),'0'),
  ('perm_cr_review','审核素材','creative:review','menu_ad_master',52,'1','app_base',NOW(),'0'),
  ('perm_cr_lp_read','查询落地页','creative:landing-page:read','menu_ad_master',53,'1','app_base',NOW(),'0'),
  ('perm_cr_lp_write','管理落地页','creative:landing-page:write','menu_ad_master',54,'1','app_base',NOW(),'0'),
  ('perm_cr_bind_write','绑定创意','creative:binding:write','menu_ad_master',55,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v53a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('perm_cr_asset_read','perm_cr_asset_write','perm_cr_review','perm_cr_lp_read','perm_cr_lp_write','perm_cr_bind_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v53o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN
  ('perm_cr_asset_read','perm_cr_asset_write','perm_cr_lp_read','perm_cr_lp_write','perm_cr_bind_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v53n', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('perm_cr_asset_read','perm_cr_lp_read')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';
