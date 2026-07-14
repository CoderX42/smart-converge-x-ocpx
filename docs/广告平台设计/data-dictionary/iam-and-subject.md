# IAM 与主体逻辑字典

| 实体 | 必要字段 | 唯一约束 | 所有者 | 删除/保留 |
| --- | --- | --- | --- | --- |
| user | id、login、password_hash、status、last_login_at、version | normalized_login | IAM | 停用，不物理删审计关联。 |
| role | id、code、name、scope_type、status | code | IAM | 被引用时不可删。 |
| permission | id、code、resource、action | code | IAM | 版本化发布。 |
| user_role | user_id、role_id、subject_type/id、valid_from/to | 作用域内组合唯一 | IAM | 保留变更历史。 |
| service_credential | app_id、subject、secret_ref、version、status、expires_at | app_id + version | IAM | 撤销后保留审计。 |
| advertiser | id、code、name、status、timezone、currency、qualification_status | code | 主体管理 | 关闭，不删除。 |
| publisher | id、code、name、status、timezone、currency、settlement_mode | code | 主体管理 | 关闭，不删除。 |
| audit_log | actor、action、resource、before/after_digest、reason、trace_id、occurred_at | id | IAM/审计 | 只追加，独立保留。 |

`subject_type/id` 用于权限作用域，不代表 SaaS tenant。主体行级隔离必须由服务端校验。

