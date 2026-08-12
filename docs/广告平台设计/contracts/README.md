# SCX OCPX 通用 GET 契约包

本目录描述 SCX 通用 Feedback/Callback 兼容接口。所有入站和出站合作方
请求都是 HTTP GET Query String，不存在 JSON Body。

| 文件 | 用途 |
|---|---|
| `openapi.yaml` | `/feedback`、`/callback` 和媒体回传 |
| `feedback-req-v2.schema.json` | Feedback Ingress Query 对象 |
| `callback-req-v2.schema.json` | Upstream Callback Query 对象 |
| `scx-ocpx-v2.postman_collection.json` | 本机 GET 示例 |
| `historical-contracts.md` | 已退出接口，不属于当前能力 |

`mId/tId/aId/lId` 分别直接使用 `OcpxMedia`、`OcpxMonitorTask`、
`OcpxAdvertiser`、`OcpxPromotionLink` 的 SCX 字符串主键。`aId` 是 SCX 广告主主键；
`advId` 是广告主侧广告 ID，两者不得混用。Postman 只保留占位值，
不得写入真实 AES Key、签名密钥、账号或生产域名。

Feedback HTTP 成功响应不表示同步上游成功：处理链按旧实现将日志入 Redis List，
并交给固定线程 `UpstreamDeliveryDispatcher` 异步转发。Callback 成功返回空 `200`，
解析异常返回 `500`。
StorageTask 使用原 List `LRANGE/LTRIM` 消费方式，没有 processing 队列、重试或 DLQ。

```bash
make contract-check
```
