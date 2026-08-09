# SCX OCPX 下游渠道 API 接入文档

> 对外版本：1.0；更新日期：2026-08-09。本文可直接交付给下游媒体/渠道研发团队。具体沙箱域名、`channelCode`、`monitor_id`、密钥和密钥版本由 SCX 对接人员单独提供，不应写入公共文档或客户端应用。

## 1. 接入目标与数据方向

下游需要实现两个方向：

```text
下游渠道 -- FeedbackReqV1 --> SCX OCPX
下游渠道 <-- CallbackReqV1 -- SCX OCPX
```

- **feedback**：下游把曝光、点击或其他已约定事件上报给 SCX。
- **callback**：SCX 收到上游转化后，主动回调下游提供的 HTTPS 地址。
- 两个方向都使用 HMAC-SHA256、时间戳、nonce 和密钥版本。
- 两个方向都必须实现幂等。网络超时不能作为业务失败依据，应使用原事件 ID 重试。

## 2. SCX 与下游分别提供什么

SCX 提供：

| 项目 | 示例/说明 |
|---|---|
| 沙箱 base URL | `https://{sandbox-domain}/ocpx` |
| 生产 base URL | UAT 通过后单独提供 |
| `channelCode` | 路径和 `X-Client-Id` 使用的渠道编码 |
| `monitor_id` | 已发布路由标识；不能使用 DRAFT 路由 |
| `X-Key-Version` | 当前密钥版本，从 1 开始递增 |
| HMAC 密钥 | 至少 32 位，通过安全渠道交付 |
| Postman/Schema | 本仓库 `contracts/` 中的机器可读契约 |

下游提供：

| 项目 | 要求 |
|---|---|
| callback URL | 公网 HTTPS，禁止重定向，域名必须加入精确白名单 |
| callback 验签 | 使用同一版本密钥验证 SCX 请求 |
| 幂等存储 | 以 `Idempotency-Key/event_id` 唯一处理 |
| 成功响应 | 持久化成功后返回任意 HTTP 2xx |
| 联调信息 | 测试域名、联系人、出口/入口 IP、限流和维护窗口 |

密钥不得放在浏览器、小程序、移动 App、公开 Git 仓库、日志或工单截图中。下游应由服务端调用 feedback 和接收 callback。

SCX 管理员可以在“监测链接”列表点击“接入信息”，查看当前监测链接对应的 Feedback URL、协议版本、请求头、签名公式、callback 路径和脱敏请求示例。该抽屉只显示“密钥是否已配置”，不会把 HMAC 密钥或完整设备标识返回给浏览器；真实密钥仍必须通过安全渠道单独交付。

## 3. Feedback 接口

### 3.1 请求

```http
POST {baseUrl}/open-api/ocpx/v1/channels/{channelCode}/feedback
Content-Type: application/json
X-Client-Id: {channelCode}
X-Key-Version: 1
X-Timestamp: 1785744000000
X-Nonce: 7858c055c6754ee688873e11b9c17088
X-Sign: {64位小写十六进制HMAC-SHA256}
```

约束：

- Body 最大 64 KiB。
- `X-Nonce` 长度 16–128，同一密钥时间窗内不可重复。
- `X-Timestamp` 支持 Unix 秒或毫秒，默认允许与服务端相差 5 分钟。
- `X-Client-Id` 必须等于路径中的 `channelCode`。
- 必须使用请求实际发送的原始 JSON 字节计算摘要，签名后不得再格式化 Body。

### 3.2 请求体

推荐的点击上报：

```json
{
  "schema_version": "1.0",
  "event_id": "channel-event-001",
  "event": "click",
  "monitor_id": "SCX分配的已发布路由ID",
  "clickid": "channel-click-001",
  "event_time": 1785744000000,
  "ip": "203.0.113.10",
  "ua": "Channel-SDK/1.0",
  "os": "android",
  "model": "device-model",
  "campaign_id": "campaign-001",
  "adgroup_id": "adgroup-001",
  "creative_id": "creative-001",
  "oaid": "raw-device-id-used-only-at-boundary"
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `schema_version` | 否 | 当前固定为 `1.0`；缺省按 1.0 处理 |
| `event_id` | 推荐 | 下游全局稳定事件 ID，最长 128；重试必须保持不变 |
| `event` | 是 | 事件类型，见 3.3 |
| `monitor_id` / `task_id` | 二选一 | SCX 分配的已发布路由标识，最长 100 |
| `clickid` | CLICK 推荐 | 点击唯一标识；CLICK 未传 `event_id` 时作为幂等 ID |
| `event_time` | 否 | 事件发生的 Unix 秒或毫秒；范围为 2000-01-01 至服务端当前时间后 24 小时 |
| `channel_id` | 否 | 如传入，必须等于 SCX 渠道 ID 或 `channelCode` |
| `ip` / `ua` | 按路由 | 点击发生时的客户端 IP 和原始 User-Agent |
| `os` / `model` | 按路由 | `android`、`ios` 及设备型号 |
| `campaign_id` | 否 | 下游计划标识 |
| `adgroup_id` | 否 | 下游广告组标识 |
| `creative_id` | 按路由 | 下游创意标识 |
| `idfa/idfa_md5` | iOS 按路由 | IDFA 原值或双方约定的 MD5 |
| `idfv` | 否 | iOS IDFV |
| `oaid/oaid_md5` | Android 按路由 | OAID 原值或双方约定的 MD5 |
| `imei/imei_md5` | Android 按路由 | 仅在法规、平台政策和双方协议允许时提供 |
| `android_id/android_idmd5` | Android 按路由 | 注意兼容字段名为 `android_idmd5` |
| `caid` | iOS 按路由 | CAID 或上游约定列表格式 |
| `aaid/gaid` | 否 | Android 广告标识 |
| 其他字段 | 否 | 作为扩展字段接收；生产使用前须书面约定名称和语义 |

“按路由”表示该字段可能是绑定上游的必填宏。缺失字段可能在异步上游投递阶段形成失败任务，因此接入前双方必须确认每个操作系统的最小字段集合，并在 UAT 覆盖，不能依赖未书面约定的自动猜测。

原始设备标识只用于协议边界转换。SCX 事实表保存版本化 HMAC 关联键，异步重试需要的最小快照使用 AES-GCM 加密并按策略清理。

设备窗验重按平台和字段类型隔离：Android 依次选择 `oaid/oaid_md5 → gaid/aaid → android_id/android_idmd5 → imei/imei_md5`；iOS 依次选择 `idfa/idfa_md5 → caid → idfv`。建议显式传 `os`。没有可用设备号时仍可靠保存事实并执行事件 ID 幂等，设备窗决策记为 `SKIPPED_NO_DEVICE`。

### 3.3 事件类型

| 发送值 | 规范含义 |
|---|---|
| `show` / `impression` | 曝光 |
| `click` | 点击 |
| `activate` / `active` | 激活 |
| `evoke` / `launch` | 唤起 |
| `login` | 登录 |
| `register` | 注册 |
| `day1retention` / `next_day_retention` / `next_stay` | 次日留存 |
| `retention` / `recall` | 留存/召回 |
| `purchase` / `payment` / `pay` | 付费 |
| `reactivation` / `lahuo` / `lahui` / `huifang` | 再激活/拉活/拉回/回访 |
| `new` / `customer_acquisition` | 新客 |
| `visit` / `visits` | 访问 |
| `authentication` / `authenticate` | 认证 |
| `order` / `place_order` | 下单 |
| `firstreactivation` / `first_reactivation` / `evoke_first` | 首次再激活/首唤 |
| `completeorder` / `complete_order` / `finish_order` | 完成订单 |

接口接受这些统一事件，不代表当前绑定的上游会转发全部事件。例如 Coco 仅消费 CLICK，`TEMPLATE_GET` 消费 SHOW/CLICK；不支持的事件仍可作为 SCX 事实保存，但不会创建该上游的 feedback 投递任务。

## 4. Feedback 签名

### 4.1 签名原文

```text
UPPERCASE_METHOD + "\n"
+ RAW_PATH + "\n"
+ X_TIMESTAMP + "\n"
+ X_NONCE + "\n"
+ lowercase_hex_sha256(RAW_REQUEST_BODY)
```

然后：

```text
X-Sign = lowercase_hex_hmac_sha256(channelSecret, canonicalString)
```

`RAW_PATH`：

- 包含 `/ocpx`，例如 `/ocpx/open-api/ocpx/v1/channels/demo/feedback`。
- 不包含 scheme、host、端口和 query。
- 必须使用反向代理转发前客户端实际请求的原始 path。

### 4.2 Python 签名调用示例

仓库提供 `docs/广告平台设计/uat/sign_request.py`。先把请求 JSON 保存为文件，再生成三项动态 Header：

```bash
SCX_CHANNEL_SECRET='沙箱密钥' \
python3 sign_request.py \
  POST \
  /ocpx/open-api/ocpx/v1/channels/demo/feedback \
  feedback.json
```

脚本只输出 `X-Timestamp`、`X-Nonce` 和 `X-Sign`，不会发送请求。生产系统应在自己的后端代码中实现同一算法，并保护密钥。

### 4.3 常见签名错误

- 用解析后再序列化的 JSON 计算摘要，而不是实际发送的 Body。
- path 漏掉 `/ocpx`，或者错误地加入域名/query。
- 使用大写 HEX、Base64 或 SHA-1。
- 签名后 HTTP SDK 又改变 JSON 空白、换行或字符编码。
- 秒/毫秒与服务器时间偏差超过窗口。
- 超时重试时复用 nonce。重试应保持 `event_id`，但生成新时间戳、nonce 和签名。

## 5. Feedback 响应与重试

成功或幂等响应：

```json
{
  "eventId": "channel-event-001",
  "status": "ACCEPTED",
  "receivedAt": "2026-08-03T09:00:00+08:00"
}
```

| HTTP | `status`/含义 | 下游建议 |
|---|---|---|
| 202 | `ACCEPTED`，已可靠写入接入队列 | 记录成功；不代表 MySQL 已落库、设备窗有效或上游已接收 |
| 200 | `DUPLICATE`，同 ID 同载荷已接收 | 按成功处理 |
| 400 | JSON、字段、事件或路由参数无效 | 修正数据，不要盲目重试 |
| 401/403 | 凭据、时间、签名或权限错误 | 停止重试并检查配置 |
| 404 | 没有当前渠道可用的已发布路由 | 联系 SCX 对接人员 |
| 409 | nonce 重放，或同事件 ID 对应不同载荷 | nonce 重放可换 nonce；载荷冲突必须人工排查 |
| 429 | 当前渠道 QPS 超限 | 指数退避并加入随机抖动 |
| 503 | Redis、防重放或副本确认暂不可用 | 使用同一事件 ID、全新 nonce 重试 |

同一事件 ID 同载荷重试返回 200；同一事件 ID 不同载荷返回 409。不同事件 ID 在同一监测路由和设备窗口内仍返回 202，后到事实异步标记为 `DEDUPLICATED`，不计费、不向上游投递。窗口使用 SCX 服务端 `received_at`，不使用客户端 `event_time`；每条监测链接默认 3600 秒，可配置 0–604800 秒，0 只关闭设备窗验重。

通用错误体：

```json
{
  "code": 400,
  "message": "monitor_id or task_id is required"
}
```

推荐重试 503、429、连接失败和超时；建议退避 1s、2s、5s、10s、30s 并增加随机抖动。重试时不得修改业务载荷或事件 ID。

## 6. 下游 Callback 接口要求

下游提供一个公网 HTTPS 地址，例如：

```text
POST https://callback.partner.example/ocpx/scx
Content-Type: application/json
X-Client-Id: {channelCode}
X-Key-Version: 1
X-Timestamp: 1785744000000
X-Nonce: 7858c055c6754ee688873e11b9c17088
X-Sign: {HMAC-SHA256}
Idempotency-Key: {callback-event-id}
```

请求体：

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

字段说明：

| 字段 | 说明 |
|---|---|
| `event_id` | callback 全局幂等 ID，与 `Idempotency-Key` 相同 |
| `source_event_id` | 最初 feedback 的 `event_id`，用于业务关联 |
| `event` | 大写规范事件类型 |
| `action_time` | 上游转化发生时间，RFC 3339/ISO-8601 |
| `amount` | 已确认单位后的规范金额；未确认时为 `null` |
| `amount_raw` | 上游金额原始字符串，仅供审计，不能直接扣费 |
| `clickid` | 下游最初提供的点击 ID |
| `monitor_id` | 接收原 feedback 时固化的路由 ID |
| `channel_id` | SCX 内部渠道 ID |
| `ext` | 扩展诊断字段，业务逻辑不得依赖未约定字段 |

callback 事件值包括：`ACTIVATE`、`REGISTER`、`NEXT_DAY_RETENTION`、`RETENTION`、`PAYMENT`、`REACTIVATION`、`CUSTOMER_ACQUISITION`、`FIRST_REACTIVATION`、`COMPLETE_ORDER`。

### 6.1 下游处理顺序

1. 读取原始 path、原始 Body 和签名 Header。
2. 检查时间窗和 nonce。
3. 使用 `X-Key-Version` 对应密钥验签。
4. 校验 `Idempotency-Key == body.event_id`。
5. 在同一事务内插入以 `event_id` 唯一约束的业务记录。
6. 新增或已存在相同事件均返回 HTTP 2xx。
7. 只有持久化失败才返回非 2xx。

SCX 对 callback 使用与 feedback 完全相同的签名算法，但 METHOD 和 RAW_PATH 使用下游 callback 的实际值。下游验签必须基于收到的原始 Body。

联调时可把收到的 callback 原始 Body 保存为 `callback.json`，使用随契约包提供的脚本验证固定向量：

```bash
SCX_CHANNEL_SECRET='沙箱密钥' \
python3 verify_request.py \
  POST \
  /ocpx/scx \
  callback.json \
  '{X-Timestamp原值}' \
  '{X-Nonce原值}' \
  '{X-Sign原值}'
```

脚本仅用于联调。生产接收服务还必须独立校验时间窗、nonce 防重放、密钥版本与 `Idempotency-Key`。

### 6.2 callback 成功与重试

- 任意 HTTP 2xx 视为成功，响应体不作业务判断。
- 非 2xx、连接失败或超时会按 1/2/5/10 分钟重试。
- 最多尝试 5 次；之后进入死亡任务，由受权管理员审计后人工重放。
- 每次重试保持 `Idempotency-Key/event_id` 不变，但会生成新的时间戳、nonce 和签名。
- 下游接口应在完成可靠持久化后尽快返回，不要同步等待后续耗时业务。

如果下游必须使用表单、私有签名或特殊业务成功响应，双方需要在联调前确认专用 `DownstreamAdapter`，不能让下游自行假设标准接口支持这些变体。只需要普通 HTTPS GET query 回传时可选择 V73 `TEMPLATE_GET_DOWNSTREAM`。

### 6.3 通用 GET 回传模板

选择 `TEMPLATE_GET_DOWNSTREAM` 时，feedback 入口和 HMAC 规则不变；只有 SCX 向渠道回传转化的请求改为 GET。监测链接的 `downstream_callback_url` 必须是固定 HTTPS 域名下的宏模板，并至少包含 `__EVENT__` 和 `__EVENT_ID__`：

```text
https://callback.partner.example/ocpx?event=__EVENT__&event_id=__EVENT_ID__&clickid=__CLICKID__&ts=__TS__
```

支持宏：`__EVENT__`、`__EVENT_ID__`、`__SOURCE_EVENT_ID__`、`__CLICKID__`、`__MONITOR_ID__`、`__CHANNEL_ID__`、`__AMOUNT__`、`__AMOUNT_RAW__`、`__TS__`。每个值按 query 参数编码，未知或未解析宏会拒绝发布/投递。HTTP 任意 2xx 视为成功，`event_id` 必须作为渠道端幂等唯一键。该通用适配器不提供渠道私有 query 签名；有此要求时必须开发专用适配器。

## 7. 密钥轮换

当前 v1 的 feedback 入口只接受路由当前发布的密钥版本，不提供新旧版本并行接收窗口；双方必须约定切换时刻：

1. 下游预置新密钥，但仍使用旧版本发送 feedback。
2. 双方在沙箱验证新版本签名固定向量。
3. 清空或记录旧版本待重试 feedback，约定生产切换时间。
4. SCX 发布新 `X-Key-Version` 后，下游立即切换 feedback 发送版本。
5. 对切换前已接收事件，SCX callback 继续使用事件路由快照中的旧版本；下游必须保留旧密钥，按 callback 的 `X-Key-Version` 验签。
6. 超过 callback 引用、任务重试和人工重放保留窗口后，经双方确认删除旧密钥。

禁止直接覆盖密钥而不递增版本。需要 feedback 新旧密钥并行接收时，必须先扩展渠道密钥历史表和验签逻辑，不能仅靠文档约定。

## 8. 联调验收清单

### 8.1 Feedback

- [ ] 正常 CLICK 返回 202。
- [ ] 同事件 ID、同载荷、不同 nonce 返回 200 `DUPLICATE`。
- [ ] 同事件 ID、不同载荷返回 409。
- [ ] 相同 nonce 重放返回 409。
- [ ] 错误签名、错误密钥版本、过期时间戳被拒绝。
- [ ] 未发布/不属于当前渠道的路由被拒绝。
- [ ] 429/503/超时使用同一事件 ID 正确重试。
- [ ] Android/iOS 最小设备字段分别完成真实上游宏替换与投递 UAT。
- [ ] Android/iOS 字段优先级、同设备窗口并发竞争、窗口过期和无设备号降级均符合预期。
- [ ] 设备窗 `DEDUPLICATED` 事实不产生计费流水或上游任务。

### 8.2 Callback

- [ ] 下游正确验签并可靠持久化后返回 2xx。
- [ ] 重复 callback 只产生一次业务效果。
- [ ] 下游超时后 SCX 重试，`event_id` 保持不变。
- [ ] 新旧密钥版本均按轮换窗口正确处理。
- [ ] 激活、注册、留存、付费等已签约事件映射正确。
- [ ] `amount_raw` 未确认单位时不被当作实际扣费金额。
- [ ] 日志不记录密钥、完整设备标识或完整 callback Body。

双方应记录每个用例的请求 ID、时间、预期、实际结果和签字结论。沙箱 Mock 成功不代表生产上游已经验收。

## 9. 版本兼容与变更通知

- `schema_version=1.0` 内只允许增加可选字段，不改变既有字段语义。
- 新增必填字段、签名原文或事件语义必须升级主版本并给出迁移窗口。
- 下游解析 callback 时应忽略 `ext` 中未知字段，但顶层字段按 JSON Schema 校验。
- SCX 会通过 `X-Key-Version` 完成密钥轮换，不通过改变签名算法制造隐式不兼容。

机器可读文件：

- `contracts/openapi.yaml`：feedback API 与 callback webhook。
- `contracts/feedback-req-v1.schema.json`：feedback JSON Schema。
- `contracts/callback-req-v1.schema.json`：callback JSON Schema。
- `contracts/scx-ocpx-v1.postman_collection.json`：Postman 联调集合。
- `uat/sign_request.py` / `uat/verify_request.py`：签名生成与 callback 验签工具。

## 10. 生产放行条件

下游生产接入必须满足：

- 双方完成本清单并签字。
- 生产域名、证书、白名单、密钥版本和时钟同步已验证。
- callback 持久化、幂等、限流和监控告警已上线。
- SCX 已完成目标数据库迁移、Redis AOF/副本、故障恢复和容量门槛。
- SCX 与真实上游完成成功语义、鉴权、金额单位和重试 UAT。
- 先单路由灰度，完成原始转化、应回传、实际回传、扣量与失败任务对账，再扩大流量。
