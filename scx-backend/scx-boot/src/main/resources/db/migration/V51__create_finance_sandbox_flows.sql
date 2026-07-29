-- ============================================================
-- V51: Finance sandbox — rebate, refund, invoice, reconciliation, closing
-- ============================================================

-- 1. Rebate rule (immutable once activated)
CREATE TABLE IF NOT EXISTS scx_rebate_rule (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    rule_name       VARCHAR(128)   NOT NULL COMMENT '规则名称',
    rule_type       VARCHAR(32)    NOT NULL COMMENT 'TIERED|FIXED_RATE|CONDITIONAL',
    condition_tree  JSON           NOT NULL COMMENT '结构化条件树（非任意脚本）',
    rate            DECIMAL(10,6)  NULL     COMMENT '返点比例',
    status          VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|ACTIVE|ARCHIVED',
    effective_from  DATE           NULL     COMMENT '生效日期',
    effective_to    DATE           NULL     COMMENT '失效日期',
    create_by       VARCHAR(64)    NULL     COMMENT '创建人',
    update_by       VARCHAR(64)    NULL     COMMENT '更新人',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_rr_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返点规则（生效后不可修改）';

-- 2. Rebate rule version (immutable revision of a rule)
CREATE TABLE IF NOT EXISTS scx_rebate_rule_version (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    rule_id         VARCHAR(32)    NOT NULL COMMENT '返点规则ID -> scx_rebate_rule.id',
    version         INT            NOT NULL COMMENT '版本号',
    condition_tree  JSON           NOT NULL COMMENT '该版本的条件树快照',
    rate            DECIMAL(10,6)  NULL     COMMENT '该版本的返点比例',
    supersedes      VARCHAR(32)    NULL     COMMENT '被替代的版本ID',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_rrv (rule_id, version),
    INDEX idx_rrv_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返点规则版本（不可变）';

-- 3. Rebate calculation (one per rule match, with revision tracking)
CREATE TABLE IF NOT EXISTS scx_rebate_calculation (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    rule_id         VARCHAR(32)    NOT NULL COMMENT '匹配的返点规则ID',
    rule_version    INT            NOT NULL COMMENT '使用的规则版本',
    base_amount     DECIMAL(20,6)  NOT NULL COMMENT '计算基数（确认消耗）',
    rebate_amount   DECIMAL(20,6)  NOT NULL COMMENT '返点金额',
    period_start    DATE           NOT NULL COMMENT '账期开始',
    period_end      DATE           NOT NULL COMMENT '账期结束',
    status          VARCHAR(32)    NOT NULL DEFAULT 'CALCULATED' COMMENT 'CALCULATED|POSTED|REVISED|REVERSED',
    revision_of     VARCHAR(32)    NULL     COMMENT '被修订的原计算ID',
    difference      DECIMAL(20,6)  NULL     COMMENT '修订差额',
    journal_id      VARCHAR(32)    NULL     COMMENT '对应日记账ID',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_rc_org_period (org_id, period_start),
    UNIQUE INDEX uq_rc_match (org_id, rule_id, period_start, period_end, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返点计算结果';

-- 4. Refund case (maker-checker separation)
CREATE TABLE IF NOT EXISTS scx_refund_case (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    amount          DECIMAL(20,6)  NOT NULL COMMENT '退款金额',
    refund_reason   VARCHAR(256)   NOT NULL COMMENT '退款原因',
    status          VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SUBMITTED|APPROVED|REJECTED|COMPLETED|REVERSED',
    maker_id        VARCHAR(32)    NULL     COMMENT '经办人ID',
    checker_id      VARCHAR(32)    NULL     COMMENT '审批人ID（必须与经办人不同）',
    submitted_at    DATETIME       NULL     COMMENT '提交时间',
    reviewed_at     DATETIME       NULL     COMMENT '审批时间',
    completed_at    DATETIME       NULL     COMMENT '完成时间',
    journal_id      VARCHAR(32)    NULL     COMMENT '退款日记账ID',
    reversal_journal_id VARCHAR(32) NULL    COMMENT '冲正日记账ID',
    create_by       VARCHAR(64)    NULL,
    update_by       VARCHAR(64)    NULL,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_ref_org (org_id),
    INDEX idx_ref_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款案件（经办审批分离）';

-- 5. Invoice application (prevent double-invoicing)
CREATE TABLE IF NOT EXISTS scx_invoice_application (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    invoice_amount  DECIMAL(20,6)  NOT NULL COMMENT '开票金额',
    settled_amount  DECIMAL(20,6)  NOT NULL COMMENT '已结算金额基数',
    invoice_status  VARCHAR(32)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SUBMITTED|ISSUED|CANCELLED',
    invoice_no      VARCHAR(64)    NULL     COMMENT '发票号（模拟）',
    issued_at       DATETIME       NULL     COMMENT '开票时间',
    create_by       VARCHAR(64)    NULL,
    update_by       VARCHAR(64)    NULL,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_inv_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票申请';

-- 6. Invoice item (line items for an invoice)
CREATE TABLE IF NOT EXISTS scx_invoice_item (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    application_id  VARCHAR(32)    NOT NULL COMMENT '发票申请ID',
    item_desc       VARCHAR(256)   NOT NULL COMMENT '项目描述',
    amount          DECIMAL(20,6)  NOT NULL COMMENT '金额',
    period_start    DATE           NULL     COMMENT '费用发生期间-开始',
    period_end      DATE           NULL     COMMENT '费用发生期间-结束',
    PRIMARY KEY (id),
    INDEX idx_ii_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票项目明细';

-- 7. Reconciliation run (fixed scope, fixed input hash)
CREATE TABLE IF NOT EXISTS scx_reconciliation_run (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    period_start    DATE           NOT NULL COMMENT '对账期间-开始',
    period_end      DATE           NOT NULL COMMENT '对账期间-结束',
    timezone        VARCHAR(32)    NOT NULL DEFAULT 'Asia/Shanghai',
    input_hash      VARCHAR(128)   NOT NULL COMMENT '输入哈希（账户范围+账期+规则版本）',
    scx_balance     DECIMAL(20,6)  NOT NULL COMMENT 'SCX计算的余额',
    external_balance DECIMAL(20,6) NULL     COMMENT '外部对账余额',
    difference      DECIMAL(20,6)  NULL     COMMENT '差异',
    status          VARCHAR(32)    NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|MATCHED|MISMATCHED|RESOLVED|CLOSED',
    resolved_at     DATETIME       NULL     COMMENT '解决时间',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_recon_org_period (org_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账运行';

-- 8. Reconciliation item (each line-item difference)
CREATE TABLE IF NOT EXISTS scx_reconciliation_item (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    run_id          VARCHAR(32)    NOT NULL COMMENT '对账运行ID',
    item_type       VARCHAR(32)    NOT NULL COMMENT 'CHARGE|REBATE|REFUND|ADJUSTMENT',
    reference_id    VARCHAR(64)    NULL     COMMENT '引用业务ID',
    scx_amount      DECIMAL(20,6)  NOT NULL COMMENT 'SCX金额',
    external_amount DECIMAL(20,6)  NULL     COMMENT '外部金额',
    difference      DECIMAL(20,6)  NOT NULL COMMENT '差异',
    note            VARCHAR(512)   NULL,
    PRIMARY KEY (id),
    INDEX idx_ri_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异项';

-- 9. Closing period (immutable after close)
CREATE TABLE IF NOT EXISTS scx_closing_period (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    period_start    DATE           NOT NULL COMMENT '关账期间-开始',
    period_end      DATE           NOT NULL COMMENT '关账期间-结束',
    timezone        VARCHAR(32)    NOT NULL DEFAULT 'Asia/Shanghai',
    status          VARCHAR(32)    NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|CLOSING|CLOSED',
    closed_at       DATETIME       NULL     COMMENT '关账时间',
    closed_by       VARCHAR(64)    NULL     COMMENT '关账人',
    input_hash      VARCHAR(128)   NULL     COMMENT '关账时输入哈希',
    diff_threshold  DECIMAL(20,6)  NOT NULL DEFAULT 0.000000 COMMENT '差异阈值',
    pre_close_snapshot_id VARCHAR(32) NULL  COMMENT '关账前余额快照ID',
    create_by       VARCHAR(64)    NULL,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE INDEX uq_cp (org_id, period_start, period_end),
    INDEX idx_cp_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关账期间（关账后不可覆盖）';

-- 10. Adjustment journal (post-closing adjustment, not overwrite)
CREATE TABLE IF NOT EXISTS scx_adjustment_journal (
    id              VARCHAR(32)    NOT NULL COMMENT '主键',
    org_id          VARCHAR(32)    NOT NULL COMMENT '组织ID',
    closing_period_id VARCHAR(32)  NOT NULL COMMENT '关联关账期间ID',
    journal_id      VARCHAR(32)    NOT NULL COMMENT '调整日记账ID',
    amount          DECIMAL(20,6)  NOT NULL COMMENT '调整金额',
    adjustment_type VARCHAR(32)    NOT NULL COMMENT 'CORRECTION|REVERSAL|SUPPLEMENT',
    reason          VARCHAR(512)   NOT NULL COMMENT '调整原因',
    approved_by     VARCHAR(32)    NULL     COMMENT '审批人',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_aj_period (closing_period_id),
    INDEX idx_aj_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调整分录（关账后追加，不覆盖原事实）';
