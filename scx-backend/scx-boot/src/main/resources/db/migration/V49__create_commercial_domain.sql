-- V49: Commercial organizations, advertiser accounts, qualification,
-- agreements and organization-scoped authorization.

CREATE TABLE IF NOT EXISTS scx_organization (
    id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    org_type VARCHAR(32) NOT NULL,
    parent_org_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    legal_person VARCHAR(64) NULL,
    contact_name VARCHAR(64) NULL,
    contact_phone VARCHAR(32) NULL,
    contact_email VARCHAR(128) NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_organization_name (name),
    KEY idx_scx_organization_type_status (org_type, status),
    KEY idx_scx_organization_parent (parent_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商业组织';

CREATE TABLE IF NOT EXISTS scx_advertiser (
    id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    advertiser_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    industry VARCHAR(64) NULL,
    website_url VARCHAR(512) NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_advertiser_org (org_id),
    KEY idx_scx_advertiser_status (status),
    KEY idx_scx_advertiser_name (advertiser_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告主';

CREATE TABLE IF NOT EXISTS scx_agency_relation (
    id VARCHAR(64) NOT NULL,
    parent_agency_org_id VARCHAR(64) NOT NULL,
    child_org_id VARCHAR(64) NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
    authorized_scopes JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NULL,
    end_date DATE NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_agency_relation (parent_agency_org_id, child_org_id),
    KEY idx_scx_agency_child_status (child_org_id, status),
    KEY idx_scx_agency_parent_status (parent_agency_org_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理关系';

CREATE TABLE IF NOT EXISTS scx_ad_account (
    id VARCHAR(64) NOT NULL,
    advertiser_org_id VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    timezone VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai',
    responsible_org_id VARCHAR(64) NOT NULL,
    rebate_org_id VARCHAR(64) NULL,
    refund_org_id VARCHAR(64) NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_ad_account_name (advertiser_org_id, account_name),
    KEY idx_scx_ad_account_status (status),
    KEY idx_scx_ad_account_responsible (responsible_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广告账户';

CREATE TABLE IF NOT EXISTS scx_qualification_case (
    id VARCHAR(64) NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at DATETIME NULL,
    reviewed_at DATETIME NULL,
    expired_at DATETIME NULL,
    reviewer_id VARCHAR(64) NULL,
    review_comment VARCHAR(1024) NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scx_qualification_org_status (org_id, status),
    KEY idx_scx_qualification_expired (expired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资质审核案件';

CREATE TABLE IF NOT EXISTS scx_qualification_document (
    id VARCHAR(64) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    doc_type VARCHAR(32) NOT NULL,
    doc_name VARCHAR(128) NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scx_qualification_document_case (case_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资质文档引用';

CREATE TABLE IF NOT EXISTS scx_qualification_review_log (
    id VARCHAR(64) NOT NULL,
    case_id VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    comment VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scx_qualification_log_case (case_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变资质审核日志';

CREATE TABLE IF NOT EXISTS scx_agreement_template (
    id VARCHAR(64) NOT NULL,
    template_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scx_agreement_template_version (template_type, version),
    KEY idx_scx_agreement_template_status (template_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本化协议模板';

CREATE TABLE IF NOT EXISTS scx_agreement_instance (
    id VARCHAR(64) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version INT NOT NULL,
    org_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    signed_at DATETIME NULL,
    expired_at DATETIME NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scx_agreement_instance_org (org_id, status, expired_at),
    KEY idx_scx_agreement_instance_template (template_id, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='固定模板版本的协议实例';

CREATE TABLE IF NOT EXISTS scx_data_authorization (
    id VARCHAR(64) NOT NULL,
    grantor_org_id VARCHAR(64) NOT NULL,
    grantee_org_id VARCHAR(64) NOT NULL,
    authorization_type VARCHAR(32) NOT NULL,
    scope JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NULL,
    end_date DATE NULL,
    create_by VARCHAR(64) NULL,
    update_by VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_scx_data_auth_grantor (grantor_org_id, status),
    KEY idx_scx_data_auth_grantee (grantee_org_id, status),
    KEY idx_scx_data_auth_dates (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商业数据授权';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_com_org_read','查询商业组织','commercial:organization:read','menu_ad_master',10,'1','app_base',NOW(),'0'),
  ('perm_com_adv_read','查询广告主','commercial:advertiser:read','menu_ad_master',11,'1','app_base',NOW(),'0'),
  ('perm_com_adv_write','管理广告主','commercial:advertiser:write','menu_ad_master',12,'1','app_base',NOW(),'0'),
  ('perm_com_agency_read','查询代理关系','commercial:agency:read','menu_ad_master',13,'1','app_base',NOW(),'0'),
  ('perm_com_agency_write','管理代理关系','commercial:agency:write','menu_ad_master',14,'1','app_base',NOW(),'0'),
  ('perm_com_account_read','查询广告账户','commercial:ad-account:read','menu_ad_master',15,'1','app_base',NOW(),'0'),
  ('perm_com_account_write','管理广告账户','commercial:ad-account:write','menu_ad_master',16,'1','app_base',NOW(),'0'),
  ('perm_com_qual_read','查询资质','commercial:qualification:read','menu_ad_master',17,'1','app_base',NOW(),'0'),
  ('perm_com_qual_write','管理资质','commercial:qualification:write','menu_ad_master',18,'1','app_base',NOW(),'0'),
  ('perm_com_qual_review','审核资质','commercial:qualification:review','menu_ad_master',19,'1','app_base',NOW(),'0'),
  ('perm_com_agree_read','查询协议','commercial:agreement:read','menu_ad_master',20,'1','app_base',NOW(),'0'),
  ('perm_com_agree_write','管理协议','commercial:agreement:write','menu_ad_master',21,'1','app_base',NOW(),'0'),
  ('perm_com_data_write','管理数据授权','commercial:data-authorization:write','menu_ad_master',22,'1','app_base',NOW(),'0'),
  ('perm_com_scope_read','查询组织范围','commercial:organization-scope:read','menu_ad_master',23,'1','app_base',NOW(),'0'),
  ('perm_com_scope_write','管理组织范围','commercial:organization-scope:write','menu_ad_master',24,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v49a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_com_org_read','perm_com_adv_read','perm_com_adv_write','perm_com_agency_read',
  'perm_com_agency_write','perm_com_account_read','perm_com_account_write',
  'perm_com_qual_read','perm_com_qual_write','perm_com_qual_review',
  'perm_com_agree_read','perm_com_agree_write','perm_com_data_write',
  'perm_com_scope_read','perm_com_scope_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v49o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_com_org_read','perm_com_adv_read','perm_com_adv_write','perm_com_agency_read',
  'perm_com_agency_write','perm_com_account_read','perm_com_account_write',
  'perm_com_qual_read','perm_com_qual_write','perm_com_agree_read',
  'perm_com_agree_write','perm_com_data_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v49n', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (
  'perm_com_org_read','perm_com_adv_read','perm_com_agency_read',
  'perm_com_account_read','perm_com_qual_read','perm_com_agree_read')
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';
