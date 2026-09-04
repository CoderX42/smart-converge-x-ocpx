# Mock RTA 通用框架契约 V128

## 数据面入口

Engine 提供 `POST /ocpx/rta`，接收 `rta-request-v1` 规范化 JSON。当前只注册
`MOCK` 适配器，不允许通过 Profile 配置任意 URL、密钥或供应商认证信息。Mock 支持
`ALLOW`、`DENY`、`ERROR`、外部任务 ID 和可选延迟。

`requestId` 必须为 1～128 个字符；请求体最多 64 KB，白名单 `attributes` 最多 8 KB。
路由主键必须属于同一监测任务。相同 `requestId` 的规范化请求重试返回相同结果，携带不同
请求内容返回 409。Redis 或 Stream 不可用时返回 503，绝不返回 `ALLOW`。

## Admission 与点击门禁

决策事实先写入 `r:ocpx:rta:decision-fact`，再写入
`rta:admission:{monitorTaskId}:{requestId}`，TTL 默认 300 秒。只有相同监测任务和
`requestId` 的点击才能通过门禁；缺失、过期、拒绝或路由不匹配返回 403，并且不创建点击事实、
不计费、不投递下游。`rta_enabled=0` 的旧任务完全绕过 RTA。

Stream 消费组 `scx-ocpx-rta-facts` 异步写入决策事实和小时指标，最多重试 3 次，随后写入
DLQ。小时指标中的 `allowRate` 是 `allowCount / requestCount`，分母为 0 时为空值；管理端
统计返回 `dataAsOf` 说明异步落库延迟。

## 管理端与发布边界

新增 `/ocpx/rta/profiles`、`/ocpx/rta/monitor-tasks/{taskId}`、`/ocpx/rta/decisions`
和 `/ocpx/rta/metrics`，继续使用 `AdvertiserScope` 与 `ocpx:rta:*` 权限。启用任务必须
绑定 ACTIVE Mock Profile 和至少一个外部任务；非 Mock 适配器直接拒绝。

V128 只新增源码、契约、Mock 和隔离环境验证。目标数据库迁移、生产重启、公网 Nginx 入口、
真实供应商调用和 ACTIVE 放量均不在本变更内。
