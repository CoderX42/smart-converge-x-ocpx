# 下游媒体联调契约 V114

## 能力边界

- 监测任务列表的“联调”入口只允许真实 `ROLE_ADMIN` 使用，专用权限为
  `ocpx:monitor-task:integration`。
- 请求明细来自 `scx_ocpx_log_feedback` 的异步规范化落库快照，通常约 1 分钟可见；
  它不是网络层原始报文，不保证保留原 URL 参数顺序、重复参数或原始编码形式。
- 重复 Feedback 由事实幂等边界拦截时不会新增兼容明细；不能用明细条数替代事实或计费统计。
- 手工 Callback 是真实外部 GET，但不生成广告主转化事实、不扣费、不执行媒体扣量，也不进入正式业务报表。
- 媒体 HTTP 2xx 仅表示接口已响应，不表示媒体已完成业务入库。

## 接口

### `GET /ocpx/monitor-tasks/{id}/integration/feedback-requests`

按监测任务和日期分页查询已落库的下游 Feedback 明细，支持 `requestId` 精确过滤。
日期范围最多 31 天。响应包括设备、网络、广告维度、SCX 路由关系和解码后的
`callbackUrl`；列表应默认遮罩设备标识，完整值只在管理员详情中展示。

### `POST /ocpx/monitor-tasks/{id}/integration/callbacks:send`

请求只接受：

- `feedbackLogId`：必须属于路径中的监测任务；
- `eventCode`：公开转化事件；
- `currencyAmount`：可选，非负且最多 6 位小数。

接口不接受目标 URL。服务端重新读取选中明细的 `callback_url`，校验其路由关系，
复用 Engine 正式回传的公网 HTTP(S) 安全规则，追加 `event_type` 和可选
`currency_amount` 后发送。禁止重定向，连接超时 3 秒、请求超时 5 秒，响应预览上限
4096 字节。

每次尝试生成 `SCX-CB-UAT-{yyyyMMddHHmmss}-{12位随机串}`，并在
`scx_ocpx_flow_log` 写入 `CALLBACK` 脱敏审计：保存源 request ID、事件、HTTP 状态、
稳定结论码和耗时，不保存目标 URL、设备值或响应正文。

## 稳定结论码

- `INTEGRATION_CALLBACK_RESPONDED`
- `INTEGRATION_CALLBACK_HTTP_FAILED`
- `INTEGRATION_CALLBACK_URL_REJECTED`
- `CALLBACK_TIMEOUT`
- `CALLBACK_INTERRUPTED`
- `CALLBACK_IO_ERROR`

若外部请求已经执行但审计写入失败，结论码追加 `_AUDIT_FAILED`，避免误导管理员把
接口异常理解为“未发送”。
