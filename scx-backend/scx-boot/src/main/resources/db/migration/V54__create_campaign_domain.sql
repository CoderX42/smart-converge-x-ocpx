-- V54: Campaign — Campaign/AdGroup/Ad hierarchy, revisions, budget, targeting, publishing
CREATE TABLE IF NOT EXISTS scx_campaign (
    id VARCHAR(32) NOT NULL, ad_account_id VARCHAR(32) NOT NULL COMMENT 'scx_ad_account.id',
    name VARCHAR(256) NOT NULL, objective VARCHAR(32) NOT NULL COMMENT '品牌推广|线索获取|应用推广|商品销售',
    billing_mode VARCHAR(32) NOT NULL DEFAULT 'CPM' COMMENT 'CPM|CPC|MEDIA_MANAGED_OCPC',
    target_cpa DECIMAL(20,6) NULL, total_budget DECIMAL(20,6) NULL, daily_budget DECIMAL(20,6) NULL,
    currency VARCHAR(8) DEFAULT 'CNY', timezone VARCHAR(32) DEFAULT 'Asia/Shanghai',
    start_at DATETIME NULL, end_at DATETIME NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|VALIDATING|PENDING_REVIEW|APPROVED|PUBLISHING|PARTIALLY_PUBLISHED|DELIVERING|PAUSED|FAILED|ENDED|ARCHIVED',
    version INT NOT NULL DEFAULT 1, current_revision_id VARCHAR(32) NULL,
    budget_reservation_id VARCHAR(32) NULL COMMENT '财务预算预留ID',
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_camp_acct (ad_account_id), INDEX idx_camp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广计划';

CREATE TABLE IF NOT EXISTS scx_campaign_revision (
    id VARCHAR(32) NOT NULL, campaign_id VARCHAR(32) NOT NULL, version INT NOT NULL,
    snapshot JSON NOT NULL COMMENT '该版本的完整字段快照',
    supersedes VARCHAR(32) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_cr (campaign_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_ad_group (
    id VARCHAR(32) NOT NULL, campaign_id VARCHAR(32) NOT NULL,
    name VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    optimization_goal_id VARCHAR(32) NULL, budget_plan_id VARCHAR(32) NULL,
    targeting_revision_id VARCHAR(32) NULL, bid_cap DECIMAL(20,6) NULL,
    version INT NOT NULL DEFAULT 1, current_revision_id VARCHAR(32) NULL,
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_ag_camp (campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告组';

CREATE TABLE IF NOT EXISTS scx_ad_group_revision (
    id VARCHAR(32) NOT NULL, ad_group_id VARCHAR(32) NOT NULL, version INT NOT NULL,
    snapshot JSON NOT NULL, supersedes VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_agr (ad_group_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告组版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_ad (
    id VARCHAR(32) NOT NULL, ad_group_id VARCHAR(32) NOT NULL,
    name VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    creative_binding_id VARCHAR(32) NULL, version INT NOT NULL DEFAULT 1,
    current_revision_id VARCHAR(32) NULL,
    create_by VARCHAR(64) NULL, update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), INDEX idx_ad_ag (ad_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告';

CREATE TABLE IF NOT EXISTS scx_ad_revision (
    id VARCHAR(32) NOT NULL, ad_id VARCHAR(32) NOT NULL, version INT NOT NULL,
    snapshot JSON NOT NULL, supersedes VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE INDEX uq_ar (ad_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_optimization_goal (
    id VARCHAR(32) NOT NULL, event_code VARCHAR(32) NOT NULL COMMENT 'CLICK|LANDING|ACTIVATE|REGISTER|PAY',
    target_cpa DECIMAL(20,6) NULL, target_cpa_currency VARCHAR(8) DEFAULT 'CNY',
    attribution_window_hours INT NOT NULL DEFAULT 168, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优化目标';

CREATE TABLE IF NOT EXISTS scx_budget_plan (
    id VARCHAR(32) NOT NULL, total_amount DECIMAL(20,6) NULL, daily_amount DECIMAL(20,6) NULL,
    pacing_strategy VARCHAR(32) DEFAULT 'STANDARD' COMMENT 'STANDARD|ACCELERATED|SMOOTH',
    currency VARCHAR(8) DEFAULT 'CNY', status VARCHAR(32) DEFAULT 'ACTIVE',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算计划';

CREATE TABLE IF NOT EXISTS scx_targeting_revision (
    id VARCHAR(32) NOT NULL, targeting_ast JSON NOT NULL COMMENT '版本化定向AST（不拼接用户SQL）',
    version INT NOT NULL, supersedes VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定向版本（不可变）';

CREATE TABLE IF NOT EXISTS scx_media_publishing_binding (
    id VARCHAR(32) NOT NULL, campaign_id VARCHAR(32) NOT NULL, media_account_id VARCHAR(32) NOT NULL,
    placement_id VARCHAR(32) NOT NULL, external_campaign_id VARCHAR(128) NULL COMMENT '外部媒体计划ID',
    external_status VARCHAR(32) NULL, last_synced_at DATETIME NULL,
    PRIMARY KEY (id), UNIQUE INDEX uq_mpb (campaign_id, media_account_id, placement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体发布绑定';

CREATE TABLE IF NOT EXISTS scx_publishing_operation (
    id VARCHAR(32) NOT NULL, campaign_id VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL COMMENT 'PUBLISH|PAUSE|RESUME|STOP|MODIFY',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|COMPLETED|FAILED',
    request_snapshot JSON NOT NULL, result_detail JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (id), INDEX idx_po_camp (campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布操作记录';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_camp_read','查询推广计划','campaign:read','menu_ad_master',60,'1','app_base',NOW(),'0'),
  ('perm_camp_write','管理推广计划','campaign:write','menu_ad_master',61,'1','app_base',NOW(),'0'),
  ('perm_camp_review','审核推广计划','campaign:review','menu_ad_master',62,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v54a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('perm_camp_read','perm_camp_write','perm_camp_review')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v54o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN ('perm_camp_read','perm_camp_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v54n', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id='perm_camp_read'
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';
