# RTA Mock 联调示例

本示例只用于隔离环境联调，不会发起任何供应商请求。Engine 地址为
`http://localhost:10000/ocpx/rta`，Profile 的 `adapterCode` 必须为 `MOCK`。

```json
{
  "requestId": "req-123",
  "advertiserId": "adv-1",
  "promotionLinkId": "link-1",
  "mediaId": "media-1",
  "monitorTaskId": "task-1",
  "device": { "oaid": "transient-device-value" },
  "context": { "os": "android", "model": "demo" },
  "attributes": { "channel": "sandbox" }
}
```

将 Profile 配置为 `{"decision":"ALLOW","taskIds":["external-task-1"]}` 时，响应为：

```json
{
  "requestId": "req-123",
  "decision": "ALLOW",
  "taskIds": ["external-task-1"],
  "reasonCode": "MOCK_ALLOW"
}
```

点击请求必须使用相同的 `tId=task-1` 和 `request_id=req-123`，并在 admission 的
300 秒 TTL 内到达；否则返回 HTTP 403，且不会产生点击事实、计费或下游投递。
