# 监测任务点击测试契约 V112

## 边界

- 只生成并执行 `event=CLK`；不生成展示、Callback 或 `callback_url`。
- 所有接口同时要求 `ocpx:monitor-task:test` 和真实 `ROLE_ADMIN` 身份。
- `event/mId/tId/aId/lId` 由服务端按监测任务关系固定，执行接口不接受目标 URL。
- Engine 地址固定读取 `scx.ocpx.admin.tracking.engine-base-url`；禁止重定向，连接超时 3 秒、读取超时 5 秒，响应正文最多保留 4096 字节。
- 执行可能形成真实点击、上游流量和点击扣费。上游 HTTP 200 只代表接口已响应，不代表合作方已经入库。

## API

### `POST /ocpx/monitor-tasks/{id}/test-links:generate`

请求：

```json
{
  "os": "ANDROID",
  "parameters": ["oaid", "oaid_md5", "ip", "ua", "model", "ts"]
}
```

`parameters` 省略时，默认勾选当前媒体宏与广告主上游模板都可传递的设备参数，以及双方都支持的 IP、UA、机型和时间戳。返回 `SCX-UAT-{yyyyMMddHHmmss}-{12位小写十六进制}` 测试 ID、完整链接、逐项配置检查、阻断项、警告、预计扣费和当前余额。

### `POST /ocpx/monitor-tasks/{id}/test-links:execute`

请求：

```json
{
  "requestId": "SCX-UAT-20260817183000-abcdef123456",
  "os": "ANDROID",
  "parameters": {
    "oaid": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  }
}
```

服务端重新校验 OS、平台、参数格式、关联 MD5、上游可转发设备标识和余额，再按任务关系重建链接。`request_id` 与 `clickid` 都使用测试 ID。若同任务、同测试 ID 的 `CLK/FEEDBACK` 事实已存在，返回 `alreadyExecuted=true`，不再次调用 Engine；Engine 事实唯一键继续承担并发与网络重试下的最终幂等边界。

### `GET /ocpx/monitor-tasks/{id}/test-results/{requestId}`

只查询既有 `scx_ocpx_event_fact` 与 `scx_ocpx_flow_log`，不新增测试历史表。返回：

- 入站事实与 `POSTED / NOT_APPLICABLE / REJECTED_INSUFFICIENT_BALANCE`；
- 实际消费金额；
- 上游流程状态、HTTP 状态、稳定消息和耗时；
- `ENGINE_INGRESS / BILLING / UPSTREAM` 时间线；
- `WAITING / REJECTED / UPSTREAM_PENDING / UPSTREAM_RESPONDED / UPSTREAM_FAILED` 总状态。

## 参数规则

- 自动且不可取消：`request_id`、`clickid`、`os`。
- 固定路由：`event=CLK`、`mId/tId/aId/lId`。
- Android 随机值包含有效 Luhn 校验位 IMEI、小写十六进制 OAID、16 位 Android ID、AAID、Android UA/机型和毫秒时间戳。
- iOS 随机值包含大写 UUID IDFA、iOS UA/机型和毫秒时间戳。
- 同时选择原始值和 MD5 时，执行前必须验证二者一致；未选参数不进入最终 Query，不发送宏占位符。
- Android 至少有一个可被上游模板转发的 Android 设备标识；iOS 至少能转发 IDFA 或其哈希语义，否则阻断。
- Android/iOS 单端任务锁定平台，双端任务允许选择，快应用阻断。
- 默认 IP 使用 RFC 5737 TEST-NET-3 `203.0.113.0/24`，管理员可改成合作方认可的测试 IP。

## 数据与审计

系统不建立独立测试表，也不在操作日志、异常或服务日志记录完整测试 URL 和设备值。管理端只在当前授权会话中展示生成值；事实、账务和流程详情仍以现有表为准。自动化测试必须使用本机 Mock Engine，不得访问真实合作方。
