-- ============================================================
-- V50: Finance core — double-entry ledger, credit, budget reservation
-- All monetary amounts: DECIMAL(20,6), CNY only for Phase 1
-- ============================================================

-- 1. Financial account (one per organization per account type)
CREATE TABLE IF NOT EXISTS scx_fin_account (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID -> scx_organization.id',
    account_type    VARCHAR(32)    NOT NULL COMMENT 'CASH|CREDIT|RESERVATION|ESTIMATED_SPEND|CONFIRMED_SPEND|REBATE|REFUND_PAYABLE|INVOICEABLE',
    balance         DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '当前余额',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status          VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|FROZEN|CLOSED',
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    update_by       VARCHAR(64)    NULL     COMMENT '更新人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uq_fin_acct (org_id, account_type),
    INDEX idx_fin_acct_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务账户';

-- 2. Journal (immutable double-entry header, balanced)
CREATE TABLE IF NOT EXISTS scx_fin_journal (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    journal_type    VARCHAR(32)    NOT NULL COMMENT 'RECHARGE|GIFT|CREDIT_GRANT|BUDGET_RESERVE|BUDGET_RELEASE|ESTIMATED_SPEND|CONFIRMED_SPEND|REBATE|REFUND|ADJUSTMENT|CLOSING|REVERSAL',
    business_type   VARCHAR(32)    NOT NULL COMMENT '业务幂等类型',
    business_id     VARCHAR(64)    NOT NULL COMMENT '业务幂等ID',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_debit     DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '借方合计',
    total_credit    DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '贷方合计',
    status          VARCHAR(32)    NOT NULL DEFAULT 'POSTED' COMMENT 'POSTED|REVERSED',
    memo            VARCHAR(512)   NULL     COMMENT '摘要',
    posted_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入账时间',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uq_journal_idem (business_type, business_id),
    INDEX idx_journal_org (org_id),
    INDEX idx_journal_type (journal_type),
    INDEX idx_journal_posted (posted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务日记账（不可变、借贷平衡）';

-- 3. Journal entry (individual debit/credit lines, immutable)
CREATE TABLE IF NOT EXISTS scx_fin_entry (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    journal_id      VARCHAR(32)    NOT NULL COMMENT '日记账ID -> scx_fin_journal.id',
    fin_account_id  VARCHAR(32)    NOT NULL COMMENT '财务账户ID -> scx_fin_account.id',
    direction       VARCHAR(6)     NOT NULL COMMENT 'DEBIT|CREDIT',
    amount          DECIMAL(20,6)  NOT NULL COMMENT '金额',
    balance_after   DECIMAL(20,6)  NOT NULL COMMENT '分录后账户余额',
    memo            VARCHAR(256)   NULL     COMMENT '分录摘要',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_entry_journal (journal_id),
    INDEX idx_entry_account (fin_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记账分录（不可变）';

-- 4. Credit line (available credit for an organization)
CREATE TABLE IF NOT EXISTS scx_credit_line (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID -> scx_organization.id',
    credit_amount   DECIMAL(20,6)  NOT NULL COMMENT '授信总额',
    used_amount     DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '已使用额度',
    available_amount DECIMAL(20,6) NOT NULL COMMENT '可用额度（冗余加速）',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status          VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|EXHAUSTED|EXPIRED|REVOKED',
    granted_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授予时间',
    expired_at      DATETIME       NULL     COMMENT '过期时间',
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    update_by       VARCHAR(64)    NULL     COMMENT '更新人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_cl_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信额度';

-- 5. Budget reservation (frozen funds for campaigns)
CREATE TABLE IF NOT EXISTS scx_budget_reservation (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID -> scx_organization.id',
    ad_account_id   VARCHAR(32)    NULL     COMMENT '广告账户ID -> scx_ad_account.id',
    campaign_id     VARCHAR(32)    NULL     COMMENT '计划ID（R1-07 后启用）',
    reserved_amount DECIMAL(20,6)  NOT NULL COMMENT '预留金额',
    consumed_amount DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '已消耗金额',
    released_amount DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '已释放金额',
    cash_reserved_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '现金预留分摊',
    credit_reserved_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '授信预留分摊',
    cash_consumed_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '现金消耗分摊',
    credit_consumed_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '授信消耗分摊',
    cash_released_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '现金释放分摊',
    credit_released_amount DECIMAL(20,6) NOT NULL DEFAULT 0.000000 COMMENT '授信释放分摊',
    reserve_journal_id VARCHAR(32) NULL COMMENT '预留日记账ID',
    business_id VARCHAR(64) NOT NULL COMMENT '预留业务幂等ID',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status          VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|CONSUMED|RELEASED|EXPIRED',
    reserved_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预留时间',
    released_at     DATETIME       NULL     COMMENT '释放时间',
    version         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    update_by       VARCHAR(64)    NULL     COMMENT '更新人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_br_org (org_id),
    INDEX idx_br_adacct (ad_account_id),
    UNIQUE INDEX uq_br_business_id (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算预留';

-- 6. Ledger balance snapshot (point-in-time for reconciliation)
CREATE TABLE IF NOT EXISTS scx_ledger_balance_snapshot (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID -> scx_organization.id',
    snapshot_type   VARCHAR(32)    NOT NULL COMMENT 'DAILY|CLOSING|MANUAL',
    snapshot_date   DATE           NOT NULL COMMENT '快照日期',
    cash_balance    DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '现金余额',
    credit_balance  DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '授信余额',
    reserved_total  DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '预留总额',
    estimated_spend DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '预估消耗',
    confirmed_spend DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '确认消耗',
    rebate_balance  DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '返点余额',
    currency        VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
    input_hash      VARCHAR(128)   NULL     COMMENT '输入哈希（用于重放验证）',
    snapshot_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uq_balance_snap (org_id, snapshot_type, snapshot_date),
    INDEX idx_snap_org_date (org_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='余额快照';

-- 7. Idempotency guard (lightweight duplicate detection)
CREATE TABLE IF NOT EXISTS scx_fin_idempotency (
    business_type   VARCHAR(32)    NOT NULL COMMENT '业务幂等类型',
    business_id     VARCHAR(64)    NOT NULL COMMENT '业务幂等ID',
    journal_id      VARCHAR(32)    NOT NULL COMMENT '对应日记账ID',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务幂等记录';

INSERT INTO sys_menu
  (id,name,permission,parent_id,sort,type,application_key,create_time,del_flag)
VALUES
  ('perm_fin_ledger_read','查询财务账本','finance:ledger:read','menu_ad_master',30,'1','app_base',NOW(),'0'),
  ('perm_fin_ledger_write','管理财务账本','finance:ledger:write','menu_ad_master',31,'1','app_base',NOW(),'0')
ON DUPLICATE KEY UPDATE
  name=VALUES(name),permission=VALUES(permission),parent_id=VALUES(parent_id),
  sort=VALUES(sort),type=VALUES(type),application_key=VALUES(application_key),del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v50a', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_fin_ledger_read','perm_fin_ledger_write')
WHERE r.role_code='ROLE_ADMIN' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v50o', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN ('perm_fin_ledger_read','perm_fin_ledger_write')
WHERE r.role_code='ROLE_OPERATOR' AND r.del_flag='0';

INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id)
SELECT CONCAT('v50n', LPAD(ROW_NUMBER() OVER (ORDER BY m.id), 3, '0')), r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id='perm_fin_ledger_read'
WHERE r.role_code='ROLE_ANALYST' AND r.del_flag='0';
