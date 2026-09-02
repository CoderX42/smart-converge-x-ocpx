# 上游 Callback 转化记录检索性能契约 V126

## 查询与索引

- `GET /ocpx/callback-records` 继续返回 `scx_ocpx_event_fact` 中
  `flow_type='CALLBACK'` 的唯一事实，保留精确 `total`、日期和既有筛选语义。
- 查询和总数统计统一使用 `idx_ocpx_event_fact_callback_page(flow_type,received_at,id)`。
  该索引先限定 Callback，再按接收时间和 ID 有序读取，避免在事实表与非 Callback
  Feedback 记录上执行全表扫描和排序。
- V126 不修改 Callback 入站、事实幂等、计费、扣量或下游投递语义，也不回填历史事实。

## 发布边界

生产执行 V126 前必须确认目标库身份，完成备份、`EXPLAIN` 审核、在线 DDL 能力检查和低峰审批。
必须先完成索引迁移并核验 `information_schema.statistics`，再重新打包、部署并重启 `scx-boot`。
源码、单元测试和本地 Schema 通过不代表生产请求已恢复；发布后应复测默认七日查询及带广告主/请求
筛选的查询耗时，并观察 Nginx 499/5xx 与数据库负载。
