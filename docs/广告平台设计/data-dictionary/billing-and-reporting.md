# 账务与报表逻辑字典

| 实体 | 必要字段 | 关键约束 | 所有者 |
| --- | --- | --- | --- |
| budget_counter | scope_type/id、period、limit、consumed、reserved、version | 作用域 + 周期唯一，原子更新。 | 预算计费 |
| budget_reservation | business_key、scope、amount、currency、status、expires_at | business_key 唯一。 | 预算计费 |
| billing_fact | business_key、payer、payee、event_ref、billing_type、price_version、amount、currency、status | business_key 唯一；确认后用冲正。 | 预算计费 |
| financial_account | subject、account_type、currency、status | subject + type + currency 唯一。 | 账务结算 |
| ledger_entry | business_key、account_id、direction、amount、currency、reference、occurred_at | business_key + account + direction 唯一；只追加。 | 账务结算 |
| statement | subject、period、currency、status、amounts、version | subject + period + currency + version。 | 账务结算 |
| reconciliation_case | statement_id、dimension、platform_value、counterparty_value、reason、status | 关闭后不可改结论。 | 账务结算 |
| settlement | statement_id、amount、status、payment_ref | 已结算只能冲正。 | 账务结算 |
| report_hourly/daily | bucket、dimension columns、metric_version、metrics、data_status | 显式维度列，避免不透明 dimension_key。 | 数据报表 |

