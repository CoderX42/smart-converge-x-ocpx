# OCPX 容量门禁

`run-ocpx-capacity.sh` 固定使用 `grafana/k6:0.49.0`，不依赖主机安装 k6。运行前必须准备可丢弃的
隔离环境，并分别启动 ingress 与 worker 进程。脚本不会创建、重置或清理数据库。

容量流量包含 2,000 QPS 预热、20,000 QPS 持续 5 分钟、40,000 QPS 突发 30 秒和恢复阶段；
主流量每次生成高基数 SHA-256 设备值，另有固定设备的点击验重并发场景。任何阈值失败都保留
`artifacts/perf/<UTC时间>/` 原始报告，不降低门槛。

运行前记录 ingress/worker 实例数、JVM、连接池、MySQL、Redis 与 RocketMQ 参数。运行后补充
实际吞吐、dropped iterations、p99、错误率、MySQL/Redis 指标、PEL/DLQ、数据库对账、重复
Postback 和积压恢复时间。本机结果只能标记为本机容量证据。
