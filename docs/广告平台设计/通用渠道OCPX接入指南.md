# SCX 通用渠道 OCPX 接入指南（FeedbackReq / CallbackReq v1）

> 更新日期：2026-08-07。当前为 V74 源码联调契约；目标数据库迁移、真实渠道 UAT、故障演练和 20,000 accepted feedback QPS 尚未验收。

本文是平台运营/研发联调速查。可直接交付给下游研发的完整材料见 [OCPX 下游渠道 API 接入文档](OCPX下游渠道API接入文档.md)；新增 Coco 类广告主适配器见 [OCPX 上游广告主适配器开发指南](OCPX上游广告主适配器开发指南.md)。

## 1. 数据方向

- `feedback`：下游媒体/渠道 `→ scx-ocpx-engine →` 上游广告主。首个上游适配器是 Coco，只转发 CLICK。
- `callback`：上游广告主 `→ scx-ocpx-engine →` 下游媒体/渠道。首个下游格式是标准 `CallbackReqV1`。

历史 `/boot/open-api/events/v1/*`、回执和 RTA 端点已退出当前运行契约。以 [OpenAPI](contracts/openapi.yaml)、[FeedbackReq Schema](contracts/feedback-req-v1.schema.json) 和 [CallbackReq Schema](contracts/callback-req-v1.schema.json) 为准。

## 2. 下游 feedback 接口

```http
POST /ocpx/open-api/ocpx/v1/channels/{channelCode}/feedback
Content-Type: application/json
X-Client-Id: {channelCode}
X-Key-Version: 1
X-Timestamp: 1785744000000
X-Nonce: 7858c055c6754ee688873e11b9c17088
X-Sign: {64位小写HMAC-SHA256}
```

示例：

```json
{
  "schema_version": "1.0",
  "event_id": "channel-event-001",
  "event": "click",
  "monitor_id": "published-monitor-id",
  "clickid": "channel-click-001",
  "oaid": "raw-device-id-used-only-at-boundary",
  "ip": "203.0.113.10",
  "ua": "Channel-SDK/1.0",
  "os": "android",
  "model": "device-model",
  "creative_id": "creative-001",
  "event_time": 1785744000000
}
```

`monitor_id` 或 `task_id` 至少提供一个，且必须对应当前渠道的 `PUBLISHED` 路由。原始设备标识只用于边界协议转换，内部事实仅保存 HMAC 关联键；异步重试所需请求快照以 AES-GCM 加密保存 7 天。

## 3. 签名、防重放与幂等

签名原文是：

```text
UPPERCASE_METHOD + "\n"
+ RAW_PATH + "\n"
+ X_TIMESTAMP + "\n"
+ X_NONCE + "\n"
+ lowercase_hex_sha256(RAW_REQUEST_BODY)
```

使用当前渠道版本密钥计算 HMAC-SHA256。`RAW_PATH` 包含 `/ocpx`，不包含 scheme、host 和 query。服务端接受 Unix 秒或毫秒时间戳，默认允许窗口为 5 分钟；nonce 在窗口内不可重复。

事件 ID 规则：优先 `event_id`；CLICK 可回退 `clickid`；否则由渠道、路由、事件类型与规范化 JSON 指纹生成。相同 ID 与相同规范化载荷是幂等重复；相同 ID 与不同载荷是冲突。JSON 属性顺序或空白差异不会改变幂等结果，但入口 HMAC 仍绑定实际原始请求体。

## 4. feedback 响应

| HTTP | 含义 |
|---|---|
| 202 | 新事件已写入 Redis Stream，并满足当前副本确认策略；不代表 Coco 已接收 |
| 200 | 相同事件和载荷的重复请求 |
| 400 | JSON、事件、渠道或路由字段无效 |
| 401 | 凭据、时间或签名无效 |
| 404 | 没有可用的已发布路由 |
| 409 | nonce 重放，或相同事件 ID 对应不同载荷 |
| 429 | 渠道 QPS 超限 |
| 503 | 防重放、Redis Stream 或生产副本确认不可用 |

202 响应后由平台异步投递；接入方不得据此推断上游业务成功。

## 5. SCX 向下游投递 CallbackReqV1

默认使用 POST JSON：

```json
{
  "schema_version": "1.0",
  "event_id": "normalized-callback-event-id",
  "source_event_id": "original-feedback-event-id",
  "event": "ACTIVATE",
  "action_time": "2026-08-03T09:00:00Z",
  "amount": null,
  "amount_raw": null,
  "clickid": "channel-click-001",
  "monitor_id": "published-monitor-id",
  "channel_id": "channel-id",
  "ext": {
    "callback_ref": "opaque-server-reference"
  }
}
```

请求带 `Idempotency-Key: {event_id}`，并携带 `X-Client-Id/X-Key-Version/X-Timestamp/X-Nonce/X-Sign`。签名方式与 feedback 相同，PATH 使用下游 callback URL 的原始 path。下游必须按 `Idempotency-Key` 幂等；HTTP 2xx 视为成功，其他状态或超时按 1/2/5/10 分钟重试，第 5 次失败进入死亡任务。需要 GET、表单或特殊成功体时，由双方增加专用 `DownstreamAdapter`。

## 6. Coco callback 映射

| Coco `action_type` | 规范事件 |
|---|---|
| `activate` | `ACTIVATE` |
| `register` | `REGISTER` |
| `day1retention` | `NEXT_DAY_RETENTION` |
| `retention` | `RETENTION` |
| `purchase` / `payment` | `PAYMENT` |
| `reactivation` | `REACTIVATION` |
| `new` | `CUSTOMER_ACQUISITION` |
| `firstreactivation` | `FIRST_REACTIVATION` |
| `firstorder` / `first_order` / `first_purchase` | `FIRST_ORDER` |
| `completeorder` | `COMPLETE_ORDER` |

`FIRST_ORDER` 使用独立报表指标，不并入普通下单或付费。`currency_amount` 同时保留原始值和可解析数值，但不参与 CPC 点击扣费。`ext` 是 SCX 生成的签名关联引用，有效期 7 天；`s` 仅作诊断，不参与信任判断。事件 ID 优先采用 `conversion_id/event_id/order_id`；均未提供时，使用 `ext + 除 ext/s 外的排序业务参数` 生成稳定事件 ID。

## 7. 联调清单

1. 管理端创建渠道、Coco 广告主、应用链接和监测链接；配置双方精确域名白名单、至少 32 位渠道密钥和 Coco `__CALLBACK__` 宏。监测链接可覆盖广告主默认 Coco URL。
2. 保存后的路由是 `DRAFT`；发布校验通过后才可接收流量。
3. `scx-ocpx-engine` 默认关闭上下游外发；只在对应沙箱阶段把 `upstream-delivery-enabled` 或 `downstream-delivery-enabled` 字面量改为 `true`，重新打包并重启。
4. 使用可导入 Apifox/Postman 的集合验证 202、200 重复、409 冲突、错误签名、过期时间、nonce 重放、429 和 Redis 503。
5. 使用可控 Coco Mock 验证所有宏、单次 URL 编码、空 Body 200、`code=0`、HTTP 200 + `code=402`、非 2xx、超时、重试与死亡任务。
6. 验证 Coco callback 新增/重复/无效 ext/异常金额，以及 callback 先持久化再返回。
7. 下游 Mock 验证签名、幂等、超时重试、死亡任务和授权后的人工重放。
8. 核对原始转化、成功回传、扣除转化、待投递/死亡任务和投递失败之间的关系。

## 8. 生产放行边界

必须另行完成：V70 前向迁移与备份、生产密钥替换、Redis AOF 与至少一个副本、MySQL/Redis 故障恢复、两实例 30 分钟 20,000 accepted feedback QPS、单实例终止接管、真实 Coco 文档/UAT、首个下游签字 UAT。模拟服务容量不能替代真实第三方吞吐和成功语义确认。
