# 监测任务真实点击测试契约 V112

## 边界

- 只生成并执行 `event=CLK`。默认 `REAL_MEDIA` 模式下，测试 Feedback 的 `callback_url` 必须来自当前任务已有的真实媒体 Feedback 快照；页面也提供显式 `SIMULATED_MEDIA` 模式，由服务端固定生成 Engine `/test-callback` 以模拟媒体最后一跳。两种模式都禁止浏览器传入任意目标 URL。
- `REAL_MEDIA` 预览未指定快照时，服务端选择当前任务最近一条包含真实公网 `callback_url` 的快照；执行时重新校验并绑定预览返回的 `feedbackLogId`。没有真实快照时阻断，不猜测 `uri`、`show_url`、`clk_url` 或广告主旧字段。`SIMULATED_MEDIA` 不查询快照，固定使用本次测试身份对应的 Engine 受控接收器。
- 所有接口同时要求 `ocpx:monitor-task:test` 和真实 `ROLE_ADMIN` 身份。测试可能形成真实点击、上游流量、点击或目标转化扣费和媒体扣量；`SIMULATED_MEDIA` 只替代媒体最后一跳接收，不会把其它链路变成沙箱。用户确认是最后一道发送确认。
- `event/mId/tId/aId/lId/callback_url` 由服务端按任务关系和模式固定。执行接口不接受任意 callback URL；真实模式只接受预览返回的快照主键，模拟模式只接受本次服务端生成的测试身份。
- Engine 地址固定读取 `scx.ocpx.admin.tracking.engine-base-url`；禁止重定向，连接超时 3 秒、读取超时 5 秒，响应正文最多保留 4096 字节。
- `REAL_MEDIA` 的 `callback_url` 是媒体某次真实请求携带的具体地址，可能含媒体侧一次性 clickid/token。若媒体要求每次点击使用新回调参数，应先让媒体提供可复用的 UAT 回调地址或模板；系统不会把历史地址伪装成新的媒体模板。`SIMULATED_MEDIA` 不接受 `feedbackLogId`，仅使用固定 Engine 地址和本次服务端生成的测试身份。

## API

### `POST /ocpx/monitor-tasks/{id}/test-links:generate`

请求可带 `mediaCallbackMode`（默认 `REAL_MEDIA`）和 `feedbackLogId` 指定真实 Feedback 快照；省略 `feedbackLogId` 时取当前任务最近一条有效快照。`SIMULATED_MEDIA` 不需要也不能指定快照。`parameters` 省略时，默认勾选当前媒体宏与广告主上游模板都可传递的设备参数，以及双方都支持的 IP、UA、机型和时间戳。`parameterValues` 用于提交编辑后的参数值。

返回新的 `SCX-UAT-{yyyyMMddHHmmss}-{12位小写十六进制}` 测试 ID、用于绑定预览与执行的随机 `callbackToken`、真实模式下的 `callbackFeedbackLogId`、媒体 Callback 地址、完整链接、配置检查、阻断项、警告、全链路预计扣费和当前余额。`callbackToken` 用于请求绑定和兼容校验；模拟模式还用它构造固定 Engine Callback 目标地址。预检失败时 `executable=false`、`testUrl=""`，不输出可复制的失败链接。

### `POST /ocpx/monitor-tasks/{id}/test-links:execute`

请求携带 `requestId`、`os`、`callbackToken`、`mediaCallbackMode`、可选的 `feedbackLogId` 和参数值。真实模式服务端重新查询该快照并校验任务四段关系及公网 URL 安全策略；模拟模式只重建固定 Engine Callback。若同任务、同测试 ID 的 `CLK/FEEDBACK` 事实已存在，返回 `alreadyExecuted=true`，不再次调用 Engine。

### `GET /ocpx/monitor-tasks/{id}/test-results/{requestId}`

只查询既有事实、账务和流程日志，观察真实或模拟媒体最后一跳 `MEDIA_DELIVERED`、`MEDIA_HTTP_FAILED`、`MEDIA_URL_REJECTED` 或 `MEDIA_INTERNAL_ERROR`。模拟模式的成功表示受控测试接收器已收到最后一跳；真实模式的 `COMPLETED` 只在真实媒体 HTTP 200/204 已记录后返回。HTTP 2xx 仍只证明网络接口响应，真实媒体业务入库需媒体侧核对。

时间线包含 `ENGINE_INGRESS / BILLING / UPSTREAM / ADVERTISER_CALLBACK / CALLBACK_BILLING / MEDIA_DEDUCTION / DOWNSTREAM_CALLBACK`。

## 参数规则

- 自动且不可取消：`request_id`、`clickid`、`os`、真实 `callback_url`。
- 固定路由：`event=CLK`、`mId/tId/aId/lId`。
- Android/iOS 设备值、MD5 一致性、平台限制、上游可转发身份和余额按服务端预检规则执行；未选参数不会进入最终 Query。
- URL 只接受安全的 HTTP/HTTPS 公网地址，拒绝 Engine 内部 `/test-callback`、内网、环回、链路本地、用户信息、危险协议、非法编码和未替换宏。

## 数据与审计

不新增测试历史表，不重放历史事实。真实 Callback 的出站结果复用 `scx_ocpx_flow_log` 的 `MEDIA_*` 结论，原始设备值、令牌和完整地址不写入应用日志。自动化测试使用 Mock Engine；本契约本身不代表已经执行生产点击或真实合作方 UAT。
