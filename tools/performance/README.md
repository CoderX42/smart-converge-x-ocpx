# OCPX 容量验收

`ocpx-feedback.k6.js` 只用于获授权的隔离压测环境，默认按 20,000 accepted feedback QPS 持续 30 分钟，并强制以下门槛：

- HTTP 失败率不高于 0.1%；
- 检查成功率不低于 99.9%；
- p95 不高于 50ms，p99 不高于 100ms；
- 每次请求使用唯一事件 ID、nonce 和真实 HMAC 签名，只接受 HTTP 202。

密钥只在压测机上临时注入 k6 进程，不得写入脚本、命令历史、Git 或报告：

```bash
read -s OCPX_CHANNEL_SECRET
export OCPX_CHANNEL_SECRET
k6 run \
  -e OCPX_TARGET=https://load.example/ocpx/open-api/ocpx/v1/channels/demo/feedback \
  -e OCPX_CHANNEL_CODE=demo \
  -e OCPX_MONITOR_ID=published-monitor-id \
  -e OCPX_CHANNEL_SECRET \
  tools/performance/ocpx-feedback.k6.js
unset OCPX_CHANNEL_SECRET
```

压测后还必须独立完成：

1. 对账 `202 accepted = MySQL 事实 + 已处理任务 + DLQ`；
2. 压测中终止一台 `scx-ocpx-engine`，验证另一台继续接入并最终接管 Pending；
3. 保存 k6、Redis、MySQL 和应用监控证据，不得以模拟 Coco 的容量结果代替真实 Coco UAT。
