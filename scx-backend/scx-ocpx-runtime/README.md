# scx-ocpx-runtime

`scx-ocpx-runtime` 是 SCX 最小 OCPX 数据面。它由独立的 `scx-ocpx-engine` 装配，也可随管理端应用加载，负责同步完成 Feedback 和 Callback 双向协议转换。

## 当前职责

- 从四张业务主表直接解析广告主、推广链接、媒体和监测任务关系。
- 校验媒体 TOKEN 或 HMAC 请求，以及配置键和凭据版本。
- 通过 `UpstreamAdapter` 将媒体 Feedback 同步转发给广告主。
- 使用无数据库状态的短期签名 `ext` 关联广告主 Callback。
- 通过 `DownstreamAdapter` 将广告主 Callback 同步回传给媒体。
- 为每次已进入外发阶段的 Feedback/Callback 写入一条脱敏流程日志。
- 直接按启用的监测任务和推广链接解析 `/c/{taskCode}` 跳转。

## 当前不包含

- Redis Stream、RocketMQ、投递任务、租约、自动重试、DLQ 和人工重放。
- 路由发布、路由快照、实例 ACK、事件字典和事件映射。
- 点击去重、扣量、CPC/CPA 账务、充值和报表事实表。
- 请求/响应正文持久化。流程日志只保存业务维度、事件标识、结果、HTTP 状态、耗时和截断后的说明。

## 数据流

```text
下游媒体 Feedback
  -> 根据 mediaCode + monitor_task_id 查询四实体关系
  -> 鉴权并由上游 Adapter 组装请求
  -> 同步请求上游广告主
  -> 写入 scx_ocpx_flow_log

上游广告主 Callback
  -> 校验签名 ext 并查询四实体关系
  -> 上游 Adapter 解析事件
  -> 下游 Adapter 组装请求并同步回传媒体
  -> 写入 scx_ocpx_flow_log
```

运行参数由 `scx-ocpx-engine/src/main/resources/application-*.yml` 与共享的 `ocpx-protocol-crypto.yml` 提供。合作方账号和 Secret 只存在于 profile 配置的 `partner-credentials` 中，修改后必须重新打包并由宝塔重启 Engine。
