# 流程日志下游请求参数详情契约 V116

## 能力边界

- 流程日志每行增加“详情”入口，仅拥有 `ocpx:monitor-task:integration` 的真实
  `ROLE_ADMIN` 可以访问。
- 详情来自 `scx_ocpx_log_feedback` 的异步规范化快照，不复制
  `scx_ocpx_flow_log` 数据，也不保存网络层原始 URL、签名、凭据、重复参数、参数顺序或
  原始编码形式。
- V116 新增留存 `model`、`osv`、`ipv6`、`caid2`、CAID 列表及当前已解析的哈希设备标识；
  V116 以前的历史快照不会回填这些字段。
- Callback 流程通过 `source_event_id` 关联原始 Feedback；Feedback 流程使用自身
  `event_id`。关联时必须同时匹配 `mId/aId/tId/lId`，禁止只按 request ID 读取其他路由数据。
- 快照通常约 1 分钟后可见；尚未落库或已被日明细清理时返回 404，不得把缺失解释为
  下游从未发起请求。

## 接口

### `GET /ocpx/flow-logs/{id}/request-detail`

接口返回规范化的请求 ID、曝光/点击 ID、操作系统、系统版本、设备型号、设备标识、
IPv4/IPv6、User-Agent、时间戳、解码后的 `callback_url`、外部广告维度和 SCX 四段路由。
响应使用 `Cache-Control: no-store`。

该接口只用于定位下游请求参数和上游拒绝原因；不执行重放、不发送 Callback，也不改变
事实、计费、扣量或报表。
