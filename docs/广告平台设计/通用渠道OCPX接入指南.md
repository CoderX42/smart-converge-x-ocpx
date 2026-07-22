# SCX 通用渠道 OCPX 接入指南

> 契约版本：1.1；更新日期：2026-07-22  
> 状态：本地代码与单元测试已对齐，尚未完成真实第三方 UAT、目标环境故障演练或 20,000 QPS 容量验收。

## 1. 接入范围

SCX 为每个第三方渠道发放独立的 `clientId`、密钥版本和共享密钥。渠道可接入：

- `POST /boot/open-api/events/v1/clicks`：Feedback 点击。
- `POST /boot/open-api/events/v1/conversions`：广告主转化 Callback。
- `GET /boot/open-api/events/v1/receipts/{receiptId}`：查询已进入 MySQL 的处理状态。
- `POST /boot/open-api/rta/v1/decisions`：同步 RTA 决策。

完整字段和响应以 [OpenAPI](contracts/openapi.yaml) 与 [事件 JSON Schema](contracts/event-envelope.schema.json) 为准。`source` 由凭据绑定的渠道配置推导，请求体不得自行填写。

## 2. 开通与联调

1. 渠道提供测试环境出口 IP、回传域名、峰值 QPS、联系人和所需事件类型。
2. SCX 在沙箱创建隔离的 `clientId` 与密钥版本；密钥只在受控渠道交付一次，不进入代码、工单或日志。
3. 渠道先完成签名、时钟、nonce、设备哈希和幂等用例，再测试点击、重复点击、转化、回执和回传。
4. 联调问题必须提供 `receiptId`、`eventId`、请求时间和 HTTP 状态；不得发送共享密钥或原始设备号。
5. 生产放行必须另行完成真实第三方 UAT、凭据轮换、限流、监控告警、故障恢复和容量验收。

## 3. 认证与签名

所有接口都携带以下请求头：

| 请求头 | 说明 |
|---|---|
| `X-Client-Id` | SCX 发放的渠道客户端 ID |
| `X-Key-Version` | 当前密钥版本；轮换期间可同时保留两个有效版本 |
| `X-Timestamp` | 13 位 Unix 毫秒时间戳，允许偏差 180 秒 |
| `X-Nonce` | 16–128 字符，每次请求唯一；在 TTL 内重用会被拒绝 |
| `X-Sign` | 64 位小写十六进制 HMAC-SHA256 |

签名原文：

```text
UPPERCASE_HTTP_METHOD + "\n"
+ REQUEST_PATH_WITHOUT_/boot_PREFIX + "\n"
+ X_TIMESTAMP + "\n"
+ X_NONCE + "\n"
+ lowercase_hex_sha256(CANONICAL_JSON)
```

事件 Canonical JSON 与服务端字段顺序一致：`schemaVersion,eventId,taskCode,eventType,occurredAt,device,businessEventKey,callbackRef,callbackUrl,attributes`。枚举使用大写；`attributes` 顶层键按字典序排列；缺省可选字段以 JSON `null` 参与签名；GET 回执接口的 Canonical JSON 是空字符串。

Python 示例（变量均由运行环境注入）：

```python
import hashlib, hmac, json, os, time, uuid

method = "POST"
path = "/open-api/events/v1/clicks"
timestamp = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
payload = {
    "schemaVersion": "1.1",
    "eventId": "channel-event-unique-id",
    "taskCode": "TASK-CODE",
    "eventType": "CLICK",
    "occurredAt": "2026-07-22T20:00:00+08:00",
    "device": {
        "platform": "ANDROID", "idType": "OAID",
        "hashAlgorithm": "SHA256", "idHash": "<64-lowercase-hex>",
        "idVersion": None,
    },
    "businessEventKey": None, "callbackRef": None,
    "callbackUrl": None, "attributes": {},
}
canonical = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
payload_hash = hashlib.sha256(canonical.encode()).hexdigest()
signing_input = "\n".join([method, path, timestamp, nonce, payload_hash])
signature = hmac.new(os.environ["SCX_CHANNEL_SECRET"].encode(),
                     signing_input.encode(), hashlib.sha256).hexdigest()
```

Java 17 示例：

```java
String payloadHash = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(UTF_8)));
String signingInput = String.join("\n", method.toUpperCase(), path, timestamp, nonce, payloadHash);
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(System.getenv("SCX_CHANNEL_SECRET").getBytes(UTF_8), "HmacSHA256"));
String signature = HexFormat.of().formatHex(mac.doFinal(signingInput.getBytes(UTF_8)));
```

## 4. Android 与 iOS 设备标识

接口只接受 `SHA256(lowercase(canonicalRawId))` 的 64 位小写十六进制值。SCX 再使用带版本的 HMAC 派生内部 `deviceKeyHash`；数据库和日志不得保留原始 ID、MD5、完整外部哈希、IP 或 UA。

| 平台 | `idType` | 获取方式与稳定性 | 约束 |
|---|---|---|---|
| Android | `OAID` | 首选；通过 MSA 或厂商合规 SDK 获取 | 尊重用户授权与系统重置 |
| Android | `GAID` | Google Play 环境，通过 Advertising ID API 获取 | 用户限制广告跟踪时不得使用 |
| Android | `ANDROID_ID` | `Settings.Secure.ANDROID_ID` | 仅回退；受用户、应用签名和设备恢复影响 |
| Android | `IMEI` | 受限系统 API | 默认关闭，仅经批准适配器可用 |
| iOS | `IDFA` | ATT 授权后通过 AdSupport 获取 | 未授权或全零值无效 |
| iOS | `IDFV` | `UIDevice.identifierForVendor` | 仅同一 Vendor 范围稳定，不能承诺跨渠道匹配 |
| iOS | `CAID` | 非 Apple 通用标识，由批准适配器提供 | 必须同时填写 `idVersion` |

平台和类型必须匹配；全零或同一字符重复 64 次的哈希会被拒绝。业务应允许设备标识不可用，不得通过指纹拼接绕过用户授权。

## 5. Feedback 点击与点击验重

- 本地可配置容量 10,000 的内存队列；生产必须配置 Redis Stream，默认 64 分区。
- Redis 使用同槽 Lua 同时创建传输幂等标记和写入 Stream；Redis 不可用时返回 `503`，不会降级到内存后错误确认。
- Consumer Group 只在 MySQL `RawEvent` 与 `RAW_EVENT_ACCEPTED` Outbox 同事务提交后 ACK；超时 Pending 会被其他 Worker 领取，失败最多投递 5 次后进入 DLQ。
- 相同渠道 `eventId` 与相同载荷返回 `200`；相同 `eventId` 不同载荷返回 `409`。
- 不同 `eventId` 但处于同一任务、设备类型、内部设备键、规则版本和去重窗口的点击全部保留原始事实；数据库守卫行串行化窗口竞争，仅首个受理点击标记 `ELIGIBLE`，其余为 `DEDUPLICATED`。

内存队列不持久，进程退出可能丢失尚未消费的点击，因此禁止用于生产。

## 6. 广告主转化 Callback

转化请求线程仅完成验签、字段/任务校验、设备派生，以及一个最小 MySQL 事务：

```text
RawEvent(不可变原始事实) + RAW_EVENT_ACCEPTED Outbox -> COMMIT -> HTTP 202
```

事务失败返回 `503`，不得返回成功。归因、转化业务键判重、限量和渠道 Postback 由 Worker 幂等执行。调用方对超时和 `5xx` 使用指数退避重试，并保持同一 `eventId` 与完全相同的请求体。

Postback 使用 `POST application/json`，携带稳定的 `Idempotency-Key` 以及
`X-Client-Id`、`X-Key-Version`、`X-Timestamp`、`X-Nonce`、`X-Sign`。签名原文为
`POST + raw path + epoch-ms + nonce + SHA256(exact UTF-8 body)`（字段之间换行），签名值为
64 位小写十六进制 HMAC-SHA256。首次派发会冻结 JSON 正文及哈希，后续重试复用同一正文和
幂等键；密钥版本可在轮换后更新。客户端不得自动跟随重定向，发送前会重新验证 HTTPS、精确
域名、DNS 公网地址和 URL 指纹。仅 HTTP `200` 且 JSON `code=0` 成功。

第 1–4 次失败后的等待严格为 1、2、5、10 分钟，第 5 次失败进入 `DEAD`。人工重放继续执行
媒体/任务范围校验并写入审计日志。真实第三方仍须使用本 UAT 包完成签收，代码实现通过不等于
生产协议已签收。

## 7. HTTP 状态、重试与回执

| 状态 | 含义 | 调用方动作 |
|---|---|---|
| `202` | 新事件已可靠受理 | 保存 `receiptId`，无需立即重投 |
| `200` | 相同载荷的幂等重投 | 视为成功 |
| `400` | 字段、设备或版本无效 | 修正请求，不原样重试 |
| `401` | 凭据、签名、时间或 nonce 无效 | 校时并检查密钥版本；新请求使用新 nonce |
| `409` | `eventId` 已对应不同载荷 | 停止重试并排查事件 ID 生成逻辑 |
| `429` | 超过渠道/接口配额 | 按 `1s,2s,5s,10s` 退避并降低并发 |
| `503` | 必需存储或认证依赖不可用 | 保持同一事件与载荷，使用新 nonce 重签后重试 |

回执查询只返回当前渠道的数据；不存在和不属于当前渠道统一返回 `404`。点击刚返回 `202` 时可能仍在 Buffer 中，待进入 MySQL 后才能通过回执接口查询。

## 8. 联调验收清单

- [ ] 正常签名、错误签名、过期时间戳、nonce 重放和密钥双版本轮换。
- [ ] Android/iOS 合法组合；原值、MD5、全零、平台类型错配、CAID 缺版本均被拒绝。
- [ ] 点击首次受理、相同 `eventId` 重投、载荷冲突、不同 `eventId` 设备窗口验重。
- [ ] 转化首次受理、业务键重复、无点击、超窗和归因成功。
- [ ] Redis/MySQL 不可用时不返回成功；恢复后重试可对账。
- [ ] Postback 重试、死信和授权重放不产生重复业务回传。
- [ ] 工单只携带 `receiptId` 等非敏感信息。

## 9. 容量与放行边界

目标容量是 Feedback/Callback/RTA 合计持续 5 分钟 20,000 QPS，30 秒突发 40,000 QPS；流量占比 70/20/10。验收门为事件受理 p99 不超过 100ms、RTA p99 不超过 80ms、非预期错误率低于 0.1%、已确认事件丢失和重复 Postback 均为 0、结束后 10 分钟内积压归零。

当前仓库尚无上述压测结果，因此不得对外宣称已经达到 20,000 QPS。完成隔离压测、故障注入和真实第三方 UAT 后，才可更新该结论。

压测脚本位于 `scripts/perf/ocpx-20k.js`，只从环境变量读取目标地址、渠道凭据、任务和合成设备哈希。执行前必须确认目标是可丢弃、可监控、与生产隔离的容量环境；脚本存在或静态校验通过不等于容量达标。

无凭据 UAT 交付件位于 `docs/广告平台设计/uat/`，包括 Java/Python 签名示例、可验签回调
模拟器、测试数据说明、错误注入清单、排查步骤和签收模板。所有密钥仅通过运行环境注入。
