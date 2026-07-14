# 投放与库存逻辑字典

| 实体 | 必要字段 | 关键约束 | 所有者 |
| --- | --- | --- | --- |
| campaign | advertiser_id、objective、budget、currency、schedule、status、version | 预算非负；状态迁移合法。 | 需求投放 |
| ad_group | campaign_id、billing_type、optimization_event、bid_strategy、daily_budget、status | 币种继承；目标和计费组合合法。 | 需求投放 |
| ad | ad_group_id、landing_page_version_id、frequency_policy、status | 只能引用已审核落地页。 | 需求投放 |
| creative | logical_creative_id、version、format、asset_ref、review_status | 同逻辑创意版本唯一；一版一行。 | 需求投放 |
| targeting_rule | ad_id、schema_version、dimension、operator、value_json | value 通过对应 JSON Schema。 | 需求投放 |
| delivery_binding | ad/ad_group、publisher、placement、tracking_version、status | 发布版本不可原地修改。 | 需求投放 |
| media_app | publisher_id、code、platform、status | code 在媒体内唯一。 | 供给库存 |
| placement | publisher_id、app_id、code、format、floor_price、currency、status | app.publisher_id 必须相同。 | 供给库存 |
| config_release | domain、version、content_hash、status、released_at | domain + version 唯一。 | 配置发布 |

