# SCX OCPX V2 下游媒体接入文档

## 接入前准备

运营人员依次创建并启用广告主、推广链接、媒体和监测任务。下游媒体只需要保存 SCX 分配的 `mediaCode` 和 `monitor_task_id`；广告主、推广链接和媒体关系均从监测任务派生。

合作方账号和 Secret 不进数据库或流程日志，只在 Engine profile yml 中按 `configKey + version` 配置。

## Feedback 接口

```text
POST {baseUrl}/open-api/ocpx/v2/media/{mediaCode}/feedback
Content-Type: application/json
```

```json
{
  "schema_version": "2.0",
  "monitor_task_id": "task-id-assigned-by-scx",
  "event": "CLICK",
  "event_id": "media-event-0001",
  "click_id": "media-click-0001",
  "event_time": 1785744000000,
  "device": { "platform": "android" },
  "extensions": { "campaign": "sandbox" }
}
```

`monitor_task_id`、`event`、`event_id`、`event_time` 必填。时间支持 Unix 秒或毫秒，不得早于 2000 年，最多允许未来 24 小时。扩展字段不能覆盖四实体关系。

## 媒体鉴权

媒体支持 `NONE`、`TOKEN`、`SIGNATURE`。

TOKEN：

```text
Authorization: Bearer {mediaSecret}
```

SIGNATURE：

```text
X-OCPX-Key-Version: 1
X-OCPX-Timestamp: 1785744000000
X-OCPX-Nonce: 7858c055c6754ee688873e11b9c17088
X-OCPX-Signature: {64位小写十六进制HMAC-SHA256}
```

签名原文：

```text
METHOD + "\n" + RAW_PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + SHA256(RAW_BODY)
```

时间允许与 Engine 相差 5 分钟，密钥版本必须与媒体表引用的版本一致。当前最小版本不保存 Nonce，因此只校验格式、时间窗和签名，不提供服务端 Nonce 防重放。

## Feedback 响应

| HTTP | 含义 |
|---:|---|
| 202 | 广告主已同步接受请求，成功日志已写入 |
| 400 | 请求字段或时间无效 |
| 401 | TOKEN/HMAC 或密钥版本无效 |
| 404 | 媒体或 ACTIVE 监测任务不存在 |
| 422 | Adapter 不支持事件或必要配置缺失 |
| 502 | 广告主网络失败或业务拒绝，失败日志已写入 |

当前没有幂等事实表、队列或自动重试。同一个 `event_id` 重复请求可能再次转发，调用方应使用稳定事件 ID，并根据合作协议谨慎决定是否重试 502。

## SCX 回传媒体 Callback

广告主回调后，SCX 会同步请求媒体表配置的 `callbackUrl`，正文使用 `CallbackReqV2`：

```json
{
  "schema_version": "2.0",
  "event_id": "conversion-event-id",
  "source_event_id": "media-event-0001",
  "event": "ACTIVATE",
  "action_time": "2026-08-12T10:30:00+08:00",
  "amount": 0,
  "amount_raw": null,
  "click_id": "media-click-0001",
  "monitor_task_id": "task-id-assigned-by-scx",
  "extensions": {}
}
```

TOKEN/SIGNATURE 凭据与媒体配置一致。媒体应自行按 `event_id` 做幂等并返回协议约定的成功响应；当前 SCX 不自动重试失败 Callback。

## 公共监测地址

```text
GET {baseUrl}/c/{taskCode}
```

地址直接解析 ACTIVE 监测任务和推广链接模板。任务暂停/归档，或广告主、推广链接、媒体任一停用时返回 410。

机器契约位于 `contracts/openapi.yaml`、两个 V2 JSON Schema 和 Postman 集合。
