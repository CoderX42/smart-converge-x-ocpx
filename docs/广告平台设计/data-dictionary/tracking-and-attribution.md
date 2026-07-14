# 事件与归因逻辑字典

| 实体 | 必要字段 | 关键约束 | 所有者 |
| --- | --- | --- | --- |
| raw_event | event_id、source_scope、type、schema_version、payload_ref、received_at、verify_result | source_scope + event_id 唯一；只追加。 | 事件追踪 |
| event_processing | raw_event_id、stage、status、reason_code、processed_at | raw_event + stage 唯一当前结果。 | 事件追踪 |
| dedup_record | namespace、dedup_key、first_event_id、expires_at | namespace + dedup_key 唯一。 | 事件追踪 |
| touchpoint | type、external_id、advertiser_id、投放层级、媒体层级、occurred_at、tracking_version | 来源作用域外部 ID 唯一。 | 事件追踪 |
| conversion | advertiser_id、conversion_id、type、value、currency、occurred_at | advertiser + conversion_id 唯一。 | 事件追踪 |
| attribution | conversion_id、touchpoint_id、model、rule_version、result、reason、valid_from/to | 一条转化最多一个当前有效结果。 | 归因回传 |
| callback_delivery | attribution_id、target、event_type、config_version、idempotency_key、status、attempts | idempotency_key 唯一。 | 归因回传 |
| callback_attempt | delivery_id、attempt_no、response_class、duration、occurred_at | delivery + attempt 唯一；脱敏。 | 归因回传 |

原始 payload 建议对象存储加密保存，OLTP 仅存受控字段和引用；具体保留期须关闭合规问题 Q-007。

