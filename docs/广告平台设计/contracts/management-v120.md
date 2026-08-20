# 引力上游协议契约 V120

## 管理配置

- 广告主协议新增 `GRAVITY`；该协议不使用账号、Token、凭据引用、广告主级请求地址或宏参数。
- `GRAVITY` 推广链接固定使用 `platform=3`（Android+IOS），保存引力 HTTPS 基础地址和固定查询参数；不得保存 `idfa`、`oaid`、`android_id`、`imei`、`ip`、`ua`、`callback` 动态参数或 `{...}` 宏。
- 协议白名单登记精确域名 `backend.gravity-engine.com`；空白名单时 Engine 拒绝所有 GRAVITY 出站请求。

## 点击与 Callback

- 仅 `CLK` 投递引力。iOS 使用 `idfa/ip/ua/callback`；Android 使用至少一个 `oaid/android_id/imei`，并附带 `ip/ua/callback`。`android_id` 来自 Feedback 的 `androidid`。
- `{idfa}` 是有效宏名；引力示例中的 `{iafa}` 视为笔误，不进入 SCX 契约。
- `GET /ocpx/gravity/callback?ctx=...` 使用认证加密上下文恢复路由，并固定生成 `ACTIVATE`。成功、重复或扣量返回 HTTP 200 和 `{"code":200,"result":"success"}`；非法上下文或处理失败返回 HTTP 400 和 `{"code":400,"result":"failed"}`。
- 引力 HTTP 2xx 只表示监测接口已接受，不代表归因成功；没有明确幂等协议时不自动重试。

## 启用边界

2026-08-20 已取得引力确认：“Callback 原样访问且归因成功代表激活”。GRAVITY 不使用静态启用开关；只有广告主显式选择 `GRAVITY` 且精确域名白名单非空时才允许投递，清空白名单即可热停止。仍须完成目标库 V120 审批与迁移、重新打包部署，以及分别覆盖 iOS/Android 的引力 UAT，才能配置真实广告主并小流量启用。源码、本地测试或 Mock 成功不代表目标库迁移、引力验收或生产接入完成。
