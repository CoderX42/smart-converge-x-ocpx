# SCX OCPX 双向转发引擎实现说明（V70-V73 / 双向适配与 CPC 账务）

## 1. 当前结论

本轮源码已经建立第一条完整代码路径：

```text
下游 FeedbackReqV1
  -> HMAC/Nonce/QPS 校验
  -> Redis Stream 64 分区可靠接入
  -> 批量事务写 MySQL 事实
  -> CLICK 按 OFF/AUDIT_ONLY/CPC 做幂等记账或真实扣费
  -> 余额允许时创建上游投递任务
  -> Coco CLICK 或 TEMPLATE_GET SHOW/CLICK 宏适配与异步投递
  -> 上游 callback(ext + 事件/时间/金额)
  -> MySQL 原始 callback 事实与固定扣量决策
  -> 标准 HMAC JSON POST 或通用 GET 宏模板异步投递下游
```

这代表代码闭环已实现，不代表生产闭环已验收。V70-V73 尚未在目标数据库执行；真实 Coco 成功响应、鉴权、沙箱地址和重试约定尚需官方文档/UAT；充值/CPC 尚未以目标库和真实流量对账；20,000 QPS 尚未重测。因此当前生产结论仍是 **No-Go**。

## 2. 模块与职责

| 模块 | 职责 | 关键入口 |
|---|---|---|
| `scx-ocpx-contract` | `FeedbackReqV1`、`CallbackReqV1`、规范事件、路由快照、上下游适配器 SPI、RTA/Billing Port | 不依赖 Web/数据库 |
| `scx-ocpx-runtime` | 接入、标准化、加密、队列、批量落库、回调、投递、重试、扣量、审计 | 被 `scx-rtb` 引用 |
| `scx-rtb` | 独立 WebFlux/Netty 应用、数据面健康检查 | `10000/rtb` |
| `scx-adx` | 草稿配置、发布校验、Outbox、运行概览、死亡任务重放 | `/boot/adx/*` |

## 3. 协议和方向

### 3.1 下游 feedback

`POST /rtb/open-api/ocpx/v1/channels/{channelCode}/feedback`

- 请求体兼容 snake_case `FeedbackReq`，以 `monitor_id` 或 `task_id` 定位已发布路由。
- 签名原文：`METHOD + "\n" + 原始PATH + "\n" + X-Timestamp + "\n" + X-Nonce + "\n" + sha256(原始请求体)`。
- 有 `event_id` 时直接作为幂等事件 ID；CLICK 可回退到 `clickid`；否则使用渠道、路由、事件与规范化 JSON 指纹生成。
- 新事件返回 202；同 ID 同载荷返回 200；同 ID 不同载荷或 nonce 重放返回 409；限流为 429；Redis 或副本确认不可用为 503。

### 3.2 上游 feedback

`CocoUpstreamAdapter` 只为 CLICK 创建 `FEEDBACK_UPSTREAM` 投递任务。`TemplateGetUpstreamAdapter` 为 SHOW/CLICK 提供通用 GET 宏模板，覆盖 `sgyz-ocpx` 常见请求、时间、路由、设备和 callback 宏别名。所有值逐个按查询参数编码；必填值缺失、未知宏或仍有未解析宏时拒绝投递。`__CALLBACK__`/`__CALLBACK_URL__` 中的 `ext` 是服务端生成的 HMAC 签名引用，不包含完整 FeedbackReq。

`TEMPLATE_GET` 不包含任何合作方私有签名或业务成功码。出现此类要求时必须新增专用 `UpstreamAdapter`。

### 3.3 上游 callback 与下游 callback

`GET /rtb/open-api/ocpx/v1/upstreams/{upstreamCode}/callback`。Coco 支持其既有事件；`TEMPLATE_GET` 另覆盖唤起、登录、访问、认证、下单等 `sgyz-ocpx` 基础事件词汇，并优先采用上游事件/转化/订单 ID 保证重试幂等。有效 callback 在响应 `code=0` 前，已经写入原始事实、唯一扣量决策和可选下游任务。重复 callback 也返回 `code=0`。

`STANDARD` 下游适配器投递 `POST CallbackReqV1`，带 `Idempotency-Key` 与同一套 HMAC 头。V73 的 `TEMPLATE_GET_DOWNSTREAM` 仍用标准 JSON/HMAC 接收渠道 feedback，但向渠道回传转化时把 `event/event_id/source_event_id/clickid/monitor_id/channel_id/amount/amount_raw/ts` 写入 HTTPS GET 宏模板。两个通用适配器都只把 HTTP 2xx 视为成功；私有签名、表单、加密或业务响应码仍须专用 `DownstreamAdapter`。

## 4. 可靠性与高并发

- Redis Stream 默认 64 分区。分区键使用 Redis Cluster hash tag，Lua 在同一槽内完成幂等检查与 XADD；生产默认要求 `WAIT 1` 副本确认。
- Consumer Group 每批最多 500 条；MySQL 事务提交后才 ACK。Pending 超时后跨实例 claim；达到最大投递次数进入 Redis 与 MySQL 双 DLQ。损坏信封单条隔离，不会拖累同批正常事件。
- MySQL 投递任务用 `FOR UPDATE SKIP LOCKED` 与 60 秒租约多实例抢占。失败按 1/2/5/10 分钟退避，第 5 次进入 `DEAD`；人工重放保留历史尝试记录。
- 外呼用 WebClient、3 秒超时、受限线程池、每适配器 Semaphore 隔离、有限队列、基础熔断和背压；不使用固定 500 线程或无限队列。
- `upstream-delivery-enabled` 与 `downstream-delivery-enabled` 是两个独立字面量开关，dev/prod 默认均为 `false`。首次部署可只接入落库；沙箱先开上游，再开下游影子/真实投递。改动配置后必须重新打包并重启。
- 路由配置启动时全量加载进不可变本地索引，管理面发布同事务写 Outbox，Redis Pub/Sub 触发原子重载，60 秒全量轮询兜底。无 `PUBLISHED` 路由直接拒绝。

## 5. 数据与隐私

V70 扩展渠道、广告主、监测链接、扣量规则和小时报表，并新增：事件事实、AES-GCM 协议快照、callback 引用、投递任务/尝试、扣量决策、接入 DLQ、配置 Outbox、RTA 决策日志和 Billing 账本。V72 把账本扩展为余额前后值、操作人和备注，并将广告主余额精度统一为 6 位小数。

原始设备标识只存在于接入边界和 7 天 AES-GCM 协议快照；事实表只保存版本化 HMAC 关联键。callback 引用同样 7 天有效。外呼错误只记录受控错误码/异常类型，响应体最多读取 4 KiB，日志不得输出完整 URL、设备标识、密钥或 callback 参数。生产禁止 HTTP、私网/环回/链路本地/多播解析地址和重定向。

## 6. 扣量、RTA 与 Billing

扣量比例限定为 0..1。Redis Lua 按 `路由 + 事件类型 + 路由版本 + 自然日` 原子维护原始/回传计数；MySQL 对 callback event 保存唯一决策，重试复用决策。Redis 故障默认放行并记录 `REDIS_UNAVAILABLE_FAIL_OPEN`，原始 callback 事实永不因扣量消失。

`RtaDecisionPort` 当前只有 Disabled 实现，发布服务拒绝任何非 `OFF` 路由。Billing 支持：`OFF` 不记账；`AUDIT_ONLY` 在 CLICK 首次落库时写 `RECORDED` 流水但不改余额；`CPC` 使用路由快照的 `unit_price` 和 CNY，在广告主余额行锁内按 CLICK `event_id` 幂等扣款并写 `POSTED` 流水。余额不足写 `REJECTED/INSUFFICIENT_BALANCE`，不扣款且阻止该点击向上游投递。成功 CPC 金额同步聚合到小时报表 `cost_amount`。转化 callback 的 `amount/amount_raw` 只用于转化审计，不触发点击扣费。

后台充值使用 `RECHARGE:{advertiserId}:{requestId}` 作为账务唯一键；余额更新与流水同事务。V72 没有实现退款/冲正接口，生产如需冲正必须先补独立的反向流水规则和审批审计，不得直接改余额。

## 7. 配置发布和管理端

渠道、广告主、监测链接或扣量规则发生变化时，相关路由自动回到 `DRAFT`。发布时校验：上下游启用、适配器注册状态、HMAC 密钥、双向 HTTPS 地址及精确域名白名单、必填的下游 callback、上游 callback 宏、下游 GET 必填宏、RTA/Billing 模式，以及 AUDIT_ONLY/CPC 单价大于 0。成功后增加 `config_version`、写 `published_at` 与配置 Outbox。

管理端新增适配器/鉴权、Coco 宏 URL、下游 callback、RTA/Billing、路由版本/发布按钮，以及 OCPX 运行中心的积压、成功、重试、死亡任务、DLQ、方向分布和人工重放。V70 默认只向 `ROLE_ADMIN` 授予路由发布和重放，`ROLE_OPERATOR` 仅可查看运行状态。密钥接口仍只回显是否已配置，不回传明文。

## 8. 上线顺序与门禁

1. 经授权备份并应用 V70-V73；V72 会删除汽车线索表，若需归档必须先导出。部署外发关闭的 `scx-rtb`，验证接入、Redis、批量落库和账务表。
2. 沙箱开启 Coco feedback，确认响应体/鉴权/金额单位/重试；再开启 callback 接收但不向下游外发。
3. 下游 callback 影子核对后，单一路由灰度真实回传。
4. 先用 AUDIT_ONLY 对账点击量与预估成本，再核对广告主初始余额并单路由启用 CPC；覆盖重复点击、余额不足、事务回滚和小时成本对账。随后完成 Redis/MySQL 故障恢复演练。
5. 在独立压测机使用 `tools/performance/ocpx-feedback.k6.js`，两台 8 核 16G 实例持续 30 分钟达到 20,000 accepted QPS，成功率至少 99.9%，入口 p95≤50ms、p99≤100ms，且 accepted 等于事实、已处理任务与 DLQ 总和；终止一实例后 Pending 必须接管且无丢失。
6. 真实 Coco 与首个下游签字 UAT 后，另行申请生产发布。

数据库迁移、真实外发、目标环境压测和生产部署均是独立授权边界，源码测试通过不能替代这些验收。
