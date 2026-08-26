# 人人视频 CPA 上游协议契约 V124

## 管理配置

- 广告主协议增加 `RENREN_VIDEO`，管理端显示“人人视频”。该协议不使用广告主级凭据、请求地址或宏参数，也不保存合作方 Bearer Token。
- 完整 `/ad-action/click` 点击模板保存在推广链接 `link_url`。每个产品分别创建 Android（平台 `1`）和 IOS（平台 `2`）链接，不接受 Android+IOS 混合平台。
- 模板必须包含商务分配的固定 `userId`、唯一参数名、`ip/os/ua/callback/requestId/clickId` 公共宏，以及 `__TS__` 毫秒或 `__TS_S__` 秒时间戳宏之一。Android 还必须包含 IMEI MD5、OAID 和 OAID MD5；IOS 至少包含 CAID、IDFA 或 IDFA MD5 之一。
- `RENREN_VIDEO` 使用管理员维护的精确域名白名单。V124 不写入任何默认域名；空白名单拒绝全部请求，可作为紧急停投开关。

## 数据面

- Engine 仅在媒体 `Feedback CLICK` 成功记账后发起一次人人视频 GET，不自动重试。模板同时支持 HTTP/HTTPS，但目标必须通过精确白名单、DNS 公网地址和 SSRF 检查。
- 原值存在而 MD5 缺失时生成小写 32 位 MD5；点击 ID 缺失时使用 `{requestId}_{clickTimestampMillis}`；`__PASS_THROUGH__` 仅来自推广链接 `linkExt`。
- 动态值只编码一次。HTTP 200 且 JSON `code=200` 才算成功；业务码 201–205、300 及其他响应均记录为稳定失败结论。
- 流程日志只留存安全端点、宏展开审计、HTTP 状态与脱敏响应预览，不保存完整请求 URL、设备原值或加密 Callback 上下文。

## Callback

`GET /ocpx/renren-video/callback?kid=...&ext=...&event=...` 使用现有 AES-GCM 密钥环恢复 Feedback 路由：

| `event` | SCX 事件 |
|---|---|
| `activate` | `ACTIVATE` |
| `register` | `REGISTER` |
| `leave` | `NEXT_STAY` |
| `pay` | `PAYMENT`，金额为空 |
| `first_activate` | `FIRST_ACTIVATION` |

首次成功、重复 Callback 和媒体扣量均返回空 HTTP 200；参数、事件或密文非法返回 400；内部处理失败返回 500。事实幂等、广告主计费、媒体扣量、报表与下游 Callback 继续复用 `scx_ocpx_event_fact` 现有闭环。

## 发布边界

V124 SQL 只扩展协议约束，不绑定广告主、不写 `userId`、不预置域名。目标库迁移、Boot/Engine/前端部署、正式模板录入、Android/iOS 真实点击和合作方/媒体 UAT 都需要独立审批与证据；源码、本地 Mock 或 HTTP 200 不代表第三方验收。
