# 监测任务完整点击测试契约 V112

## 边界

- 只生成并执行 `event=CLK`；测试 Feedback 自动携带不可编辑的 `callback_url`，目标固定为当前 Engine 的 `/test-callback` 受控接收端。
- 所有接口同时要求 `ocpx:monitor-task:test` 和真实 `ROLE_ADMIN` 身份。
- `event/mId/tId/aId/lId/callback_url` 由服务端按监测任务关系固定，执行接口只回传随机测试令牌，不接受目标 URL。
- Engine 地址固定读取 `scx.ocpx.admin.tracking.engine-base-url`；禁止重定向，连接超时 3 秒、读取超时 5 秒，响应正文最多保留 4096 字节。
- 执行可能形成真实点击、上游流量、点击或目标转化扣费和媒体扣量。上游 HTTP 200 只代表点击接口已响应；只有广告主真实发送 Callback 且媒体扣量放行后，测试接收端才会形成最后一跳证据。
- 测试接收端成功证明 SCX 正式 Callback 链路的最后一跳可达，不代表真实媒体已经业务入库；真实媒体地址仍通过“联调”功能验证。
- 公网 Nginx 只精确开放 `GET /ocpx/test-callback`，其余未知 `/ocpx/**` 继续返回 404；公开路由不替代 Controller 的测试令牌、PASSED 事实与 HMAC 签名校验。

## API

### `POST /ocpx/monitor-tasks/{id}/test-links:generate`

请求：

```json
{
  "os": "ANDROID",
  "parameters": ["oaid", "oaid_md5", "ip", "ua", "model", "ts"]
}
```

`parameters` 省略时，默认勾选当前媒体宏与广告主上游模板都可传递的设备参数，以及双方都支持的 IP、UA、机型和时间戳。返回 `SCX-UAT-{yyyyMMddHHmmss}-{12位小写十六进制}` 测试 ID、32 位随机 `callbackToken`、完整链接、逐项配置检查、阻断项、警告、全链路预计扣费和当前余额。完整链接中的 `callback_url` 已按 Query 规则编码。

### `POST /ocpx/monitor-tasks/{id}/test-links:execute`

请求：

```json
{
  "requestId": "SCX-UAT-20260817183000-abcdef123456",
  "os": "ANDROID",
  "callbackToken": "0123456789abcdef0123456789abcdef",
  "parameters": {
    "oaid": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

服务端重新校验 OS、平台、参数格式、关联 MD5、测试令牌、上游可转发设备标识和余额，再按任务关系重建链接及测试接收地址。`request_id` 与 `clickid` 都使用测试 ID。若同任务、同测试 ID 的 `CLK/FEEDBACK` 事实已存在，返回 `alreadyExecuted=true`，不再次调用 Engine；Engine 事实唯一键继续承担并发与网络重试下的最终幂等边界。

### `GET /ocpx/monitor-tasks/{id}/test-results/{requestId}`

只查询既有 `scx_ocpx_event_fact` 与 `scx_ocpx_flow_log`，不新增测试历史表。返回：

- 入站事实与 `POSTED / NOT_APPLICABLE / REJECTED_INSUFFICIENT_BALANCE`；
- 实际消费金额；
- 上游点击流程状态、HTTP 状态、稳定消息和耗时；
- 广告主 Callback 事实、转化计费、金额、媒体扣量判定与比例；
- SCX 测试接收器是否实际收到正式下游投递；
- `ENGINE_INGRESS / BILLING / UPSTREAM / ADVERTISER_CALLBACK / CALLBACK_BILLING / MEDIA_DEDUCTION / DOWNSTREAM_CALLBACK` 时间线；
- `WAITING / REJECTED / UPSTREAM_PENDING / UPSTREAM_RESPONDED / UPSTREAM_FAILED / CALLBACK_PROCESSING / CALLBACK_FAILED / CALLBACK_DEDUCTED / DOWNSTREAM_PENDING / COMPLETED` 总状态。

## 参数规则

- 自动且不可取消：`request_id`、`clickid`、`os`、`callback_url`。
- 固定路由：`event=CLK`、`mId/tId/aId/lId`。
- Android 随机值包含有效 Luhn 校验位 IMEI、小写十六进制 OAID、16 位 Android ID、AAID、Android UA/机型和毫秒时间戳。
- iOS 随机值包含大写 UUID IDFA、iOS UA/机型和毫秒时间戳。
- 同时选择原始值和 MD5 时，执行前必须验证二者一致；未选参数不进入最终 Query，不发送宏占位符。
- Android 至少有一个可被上游模板转发的 Android 设备标识；iOS 至少能转发 IDFA 或其哈希语义，否则阻断。
- Android/iOS 单端任务锁定平台，双端任务允许选择，快应用阻断。
- 默认 IP 使用 RFC 5737 TEST-NET-3 `203.0.113.0/24`，管理员可改成合作方认可的测试 IP。

## 数据与审计

系统不建立独立测试表，也不在操作日志、异常或服务日志记录完整测试 URL、测试令牌和设备值。管理端只在当前授权会话中展示生成值；事实、账务和流程详情仍以现有表为准。

`/test-callback` 只接受格式正确的测试 ID、128 位随机令牌、服务端路由关系、公开事件编号，以及 Engine 正式下游投递使用服务端密钥生成的 HMAC-SHA256 签名；签名不会出现在原测试 Feedback 中，手工打开 URL 或通过“联调”发送均不能伪造完整闭环。接收端还要求相同路由与事件已经存在 `deduction_decision=PASSED` 的 Callback 事实。接收成功以确定性主键幂等写入 `scx_ocpx_flow_log`，只保存令牌参与计算的 SHA-256 指纹，不保存令牌、签名或完整地址。自动化测试必须使用本机 Mock Engine，不得访问真实合作方。
