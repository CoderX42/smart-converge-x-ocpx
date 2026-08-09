# SCX OCPX 本地 JMeter 压测报告

## 结论

- 本次通过的最高本地档位：100 QPS。
- 20,000 QPS 60 秒本地突发：未达到或未执行。
- 口径仅适用于本地 Docker 4 CPU / 约 8 GB 隔离环境；不构成生产 20,000 QPS 验收。
- 不丢数结论只覆盖 HTTP 已明确受理的唯一事件，并要求 MySQL 事实 + 接入 DLQ 闭合且 Redis pending/lag 最终为 0。

## Feedback 分档

| 目标 QPS | 时长(s) | 请求 | 实测 QPS | 202 比例 | p50 | p95 | p99 | 最大 pending | 最大 lag | 排空(s) | 事实 | DLQ |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 100 | 5 | 610 | 126.425 | 100.000% | 4 ms | 155 ms | 175 ms | 0 | 0 | 0 | 610 | 0 |

## 功能与故障证据

- 功能批次覆盖 Android/iOS 字段矩阵、同 ID 幂等、载荷冲突、设备窗口并发竞争、窗口过期和无设备降级。
- 故障批次覆盖 Redis 副本不足、Redis 主节点中断、MySQL 暂停、引擎重启 Pending 接管、提交后 ACK 前重放语义和非法 Stream 消息 DLQ。
- Callback 批次按有效 Feedback 的 10% 生成转化，并重复发送 callback；Mock 对每个固定幂等键按 500/500/200 返回。

详见 `functional-results.json`、`functional-reconcile.txt`、`fault-results.txt`、`callback-qps100/callback-reconcile.txt`、各档 `summary.json`、`resources.tsv` 和 `reconcile.txt`。

## 正式容量验收边界

正式 20,000 QPS 仍需独立压测机、至少两台数据面实例、分布式 JMeter、持续 30 分钟、实例故障接管及真实第三方 UAT。
