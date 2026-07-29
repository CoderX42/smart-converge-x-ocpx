-- V52: Inventory — media accounts, placements, capability matrix
CREATE TABLE IF NOT EXISTS scx_media_account (
    id VARCHAR(32) NOT NULL, media_id VARCHAR(32) NOT NULL COMMENT 'scx_media.id',
    account_name VARCHAR(128) NOT NULL, account_id_external VARCHAR(128) NULL COMMENT '外部媒体账户ID',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', currency VARCHAR(8) DEFAULT 'CNY',
    timezone VARCHAR(32) DEFAULT 'Asia/Shanghai', adapter_type VARCHAR(64) DEFAULT 'GENERIC_SANDBOX',
    adapter_config JSON NULL,
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_ma_media (media_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体账户';

CREATE TABLE IF NOT EXISTS scx_placement (
    id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL,
    placement_code VARCHAR(64) NOT NULL, placement_name VARCHAR(128) NOT NULL,
    placement_type VARCHAR(32) NOT NULL COMMENT 'SPLASH|FEED|REWARDED_VIDEO|BANNER|SEARCH|PRE_ROLL|MID_ROLL|POST_ROLL',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    supported_creatives JSON NULL COMMENT '支持的素材类型/尺寸/时长/大小',
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_pl_code (media_account_id, placement_code),
    INDEX idx_pl_type (placement_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告位';

CREATE TABLE IF NOT EXISTS scx_media_capability (
    id VARCHAR(32) NOT NULL, placement_id VARCHAR(32) NOT NULL,
    billing_modes JSON NOT NULL COMMENT '支持的计费方式: CPM,CPC,MEDIA_MANAGED_OCPC',
    target_events JSON NULL COMMENT '支持的目标事件',
    targeting_fields JSON NULL COMMENT '支持的定向字段',
    creative_formats JSON NULL COMMENT '支持的素材格式',
    media_managed_ocpc TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持媒体托管oCPC',
    status_sync_supported TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持状态同步',
    report_pull_supported TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持报表拉取',
    daily_impression_cap BIGINT NULL COMMENT '日曝光上限',
    daily_click_cap BIGINT NULL COMMENT '日点击上限',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_mc_pl (placement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体能力矩阵';

CREATE TABLE IF NOT EXISTS scx_placement_quality_snapshot (
    id VARCHAR(32) NOT NULL, placement_id VARCHAR(32) NOT NULL,
    snapshot_date DATE NOT NULL, ctr_30d DECIMAL(10,6) NULL, cvr_30d DECIMAL(10,6) NULL,
    avg_cpm DECIMAL(10,6) NULL, avg_cpc DECIMAL(10,6) NULL,
    fill_rate DECIMAL(6,4) NULL, viewability_rate DECIMAL(6,4) NULL,
    snapshot_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_pqs (placement_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告位质量快照';

CREATE TABLE IF NOT EXISTS scx_media_adapter_config (
    id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL,
    adapter_type VARCHAR(64) NOT NULL, endpoint_url VARCHAR(512) NULL,
    auth_config JSON NULL COMMENT '认证配置（不存明文密钥）',
    rate_limit_qps INT DEFAULT 10, timeout_ms INT DEFAULT 3000,
    retry_config JSON NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_mac_adapter (media_account_id, adapter_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体适配器配置';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_inv_account_read','查询媒体账户','inventory:media-account:read','menu_ad_master',40,'1','app_base',NOW(),'0'),
  ('perm_inv_account_write','管理媒体账户','inventory:media-account:write','menu_ad_master',41,'1','app_base',NOW(),'0'),
  ('perm_inv_place_read','查询广告位','inventory:placement:read','menu_ad_master',42,'1','app_base',NOW(),'0'),
  ('perm_inv_place_write','管理广告位','inventory:placement:write','menu_ad_master',43,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v52a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_inv_account_read','perm_inv_account_write','perm_inv_place_read','perm_inv_place_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v52o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_inv_account_read','perm_inv_account_write','perm_inv_place_read','perm_inv_place_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v52n', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_inv_account_read','perm_inv_place_read')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';
