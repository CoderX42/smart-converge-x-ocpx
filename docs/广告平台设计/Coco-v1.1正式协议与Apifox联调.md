# Coco v1.1 与 SCX 最小链路联调

当前 `COCO` 上游 Adapter 只支持 CLICK。媒体 Feedback 经四实体关系解析后同步请求 Coco；SCX 在请求中放入带签名 `ext` 的 Callback 地址。Coco Callback 到达后，SCX 同步回传当前监测任务关联媒体的 `callbackUrl`。

## 上游请求

广告主的 `bidUrl` 可使用 Adapter 支持的宏，例如 `__CALLBACK__`、`__CLICKID__`、`__EVENTID__`。账号和 Secret 从 Engine `partner-credentials.advertisers.<configKey>` 读取，只用于当次请求，不进入数据库或流程日志。

Coco 响应必须同时满足 HTTP 和正式协议业务 code。HTTP 非 2xx、非法正文或业务拒绝都会使 Feedback 返回 502，并写一条 FAILED 流程日志；当前版本不会后台自动重试。

## Callback

Coco 必须原样回传 SCX 生成的 `ext`。Adapter 解析 `action_type`、转化 ID、时间和金额后生成 `CallbackReqV2`，同步请求媒体。媒体成功后返回 Coco 协议成功响应，失败时返回 Adapter 定义的失败响应并写 FAILED 日志。

## Apifox 联调顺序

1. 创建并启用广告主、推广链接、媒体和监测任务。
2. 在 Engine profile 中配置广告主和媒体凭据，重新打包并由宝塔重启。
3. 用 `contracts/scx-ocpx-v2.postman_collection.json` 调用 Feedback。
4. 从 Coco Mock 捕获 Callback URL，并使用其中的真实 `ext` 触发 Callback。
5. 在“流程日志”按事件 ID 核对 FEEDBACK/CALLBACK，在报表中核对成功/失败数量。

真实 Coco 域名、字段、业务 code、Callback 重试规则、容量和 UAT 仍须与合作方确认。
