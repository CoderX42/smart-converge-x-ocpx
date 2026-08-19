# 流程日志结果与响应预览契约 V117

## 详情内容

- `GET /ocpx/flow-logs/{id}/request-detail` 先返回流程日志自身的结果状态、上游 HTTP 状态、
  稳定业务结论、耗时、事件时间、事件 ID 和源事件 ID。
- 新产生的上游 HTTP 请求额外保存 `response_preview`：仅保存响应正文经常见凭据字段脱敏后的
  前 4096 个字符，用于识别合作方返回的业务码与说明。
- V117 以前的历史流程日志不回填响应预览；请求未到达上游或上游没有响应正文时该字段为空。
- 响应预览不等于合作方已完成业务处理。是否接受仍应结合 `result_status`、HTTP 状态、稳定结论码
  以及合作方后续 Callback 或对账证据判断。

## 安全与数据边界

- 仅拥有 `ocpx:monitor-task:integration` 的真实 `ROLE_ADMIN` 可以访问，响应继续使用
  `Cache-Control: no-store`。
- 流程日志不保存上游请求 URL、签名或凭据。响应预览会脱敏常见 token、secret、password、sign、
  signature、api_key 等字段并移除不安全控制字符；它不是任意响应数据的永久原文归档。
- 下游请求参数仍来自 `scx_ocpx_log_feedback` 异步规范化快照。快照尚未落库或已过期时，接口仍返回
  流程结果与响应预览，请求数据部分为空。

该接口只用于诊断，不执行重放、不发送 Callback，也不改变事实、计费、扣量或报表。
