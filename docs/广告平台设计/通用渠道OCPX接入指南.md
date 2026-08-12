# 通用媒体 OCPX 接入指南

当前通用媒体接入统一使用：

- Feedback：`POST /open-api/ocpx/v2/media/{mediaCode}/feedback`
- 公共监测地址：`GET /c/{taskCode}`
- 下游回传：媒体表配置的 `callbackUrl`

运营人员先创建广告主及其推广链接，再创建媒体，最后用监测任务连接三者。任务创建即 ACTIVE；暂停后 Feedback、Callback 和公共监测地址都不可用。

媒体可选择 NONE、Bearer TOKEN 或 HMAC SIGNATURE 鉴权。SCX 同步把 Feedback 转发给广告主，上游接受后返回 202；网络或业务失败返回 502。广告主 Callback 同样同步回传媒体。所有进入外发阶段的结果写入 `scx_ocpx_flow_log`，报表直接按该表聚合。

当前不包含事件映射、扣量、账务、队列、自动重试、DLQ 或路由发布。完整字段、签名原文和响应码以 [OCPX下游渠道API接入文档](OCPX下游渠道API接入文档.md) 与 `contracts/openapi.yaml` 为准。
