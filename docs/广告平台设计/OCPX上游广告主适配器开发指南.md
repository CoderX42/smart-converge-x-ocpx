# SCX OCPX V2 上游广告主 Adapter 开发指南

## Adapter 职责

SCX 参考 `sgyz-ocpx` 的 DSP 协议分流，但不在业务服务中按广告主 ID 写分支。上游合作方实现 `UpstreamAdapter`，只负责：

- 声明支持的事件和协议能力；
- 从 `UpstreamDeliveryContext` 构造广告主请求；
- 根据 HTTP 与业务响应判断成功；
- 从广告主 Callback 提取签名 `ext` 并归一化事件；
- 构造广告主要求的 Callback 成功/失败响应。

Adapter 不写数据库、不保存 Secret、不自行创建线程或调用 `subscribe()/block()`。当前统一层同步执行 HTTP，请求失败直接返回 502，不提供自动重试。

## 运行上下文

`UpstreamDeliveryContext` 包含：

- `partner`：从四张业务表读取的 `OcpxPartnerContext`；
- `feedback`：媒体的 `FeedbackReqV2`；
- `upstreamEventCode`：当前最小版本直接使用 Feedback 事件码；
- `callbackUrl`：带短期签名 `ext` 的 SCX 回调地址；
- `account/secret`：仅当次请求使用的 Engine 配置值。

Adapter 必须使用 `partner.bidUrl()`、合作方协议字段和 `callbackUrl` 构造请求。账号和 Secret 不得进入 URL 日志、异常信息、流程日志或业务表。

## 实现要点

1. `code()` 使用稳定的大写 ASCII，并与广告主表的 `adapterCode` 一致。
2. `supports()` 明确拒绝不支持的事件。
3. 每个 URL 参数单独编码，未解析必填宏时失败关闭。
4. `evaluateFeedbackResponse()` 同时检查 HTTP 和合作方业务码，并返回不含敏感数据的说明。
5. `callbackToken()` 从合作方参数中取出 SCX 生成的 `ext`。
6. `parseCallback()` 优先使用合作方稳定的 conversion/order ID，明确时间单位、时区和金额单位。
7. `callbackSuccess()` / `callbackFailure()` 遵守合作方协议，不能统一假设任意 2xx 都代表业务成功。

## 配置与启用

1. 实现并注册 Adapter Bean。
2. 广告主保存 Adapter 代码、上游 URL、宏、host 白名单和 `credentialConfigKey + version`。
3. 在 Engine `application-dev.yml` / `application-prod.yml` 的 `scx.ocpx.partner-credentials.advertisers.<configKey>` 写字面量 `version/account/secret`。
4. 重新打包并由宝塔重启 Engine。
5. 创建广告主所属推广链接、媒体和 ACTIVE 监测任务。

配置键缺失、版本不匹配、事件不支持、URL 安全校验失败或合作方业务拒绝都会使当前请求失败；不会产生后台重试任务。
