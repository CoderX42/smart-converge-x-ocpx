# SCX OCPX 本机容量门禁结论

- 时间：2026-07-22 14:27:20Z，固定镜像 `grafana/k6:0.49.0`。
- 机器：Apple M3 Pro，12 逻辑核，18 GiB；单 ingress JVM `-Xms512m -Xmx1g`，单 worker JVM `-Xms512m -Xmx1g`。
- 阶段：2,000 QPS 预热 1 分钟、20,000 QPS 持续 5 分钟、40,000 QPS 突发 30 秒、2,000 QPS 恢复 1 分钟。
- 结果：1,244,021 请求，平均 2,591 req/s，5,873,994 dropped iterations，错误率 92.99%。ingress/RTA p95 为 2,743.96/2,658.11 ms；本次 k6 0.49 导出的 JSON 未包含精确 p99，但两项 p99 阈值均明确失败，脚本已补 `summaryTrendStats` 供下次输出精确 p99。
- 40k 边界：出现 `cannot assign requested address`，发压端临时端口耗尽；本结果不能推断服务端 40k 极限。
- 恢复：Feedback Stream lag/Pending/DLQ 约 7 分钟归零；本次 runId 的 78,813 条事件与 k6 成功事件检查精确一致，约 9 分钟全部进入 `PROCESSED`；8,381 条 RTA 日志也与 k6 成功 RTA 检查精确一致，DLQ 为 0。
- 未闭环：本轮明确关闭 RocketMQ Outbox publisher，`scx_event_outbox` 留有 158,551 条 `PENDING`，因此不构成隔离 RocketMQ/Outbox 恢复验收。
- 判定：本机容量门禁失败，不降低阈值，不标记 20,000 QPS 达标。专用多实例环境必须使用独立 RocketMQ 和分布式发压重新执行。
