# SCX OCPX 本地 JMeter 不丢数压测

本目录只操作 Compose 项目 `scx-ocpx-jmeter`、本地端口 `13316/16389/16390/18081/19999` 和一次性数据库 `smart_converge_x`。它不会加载 `scx-ocpx-engine` 的 `dev` profile，也不会访问默认开发环境中的外部 MySQL、Redis 或真实上下游。

## 一键执行

```bash
bash tools/performance/jmeter/run-local.sh
```

默认顺序是：构建引擎、启动隔离 MySQL/Redis 主从、应用全部前向迁移、写入合成路由、功能批次、故障批次、Callback 10% 批次，最后按 `100/300/600/1000 QPS × 300 秒` 和 `2000/5000/10000/20000 QPS × 60 秒` 逐档执行。任何一档出现非预期状态、JMeter 客户端错误、Redis 无法排空或 MySQL/DLQ 对账不闭合都会停止升档。

调试时可缩短档位，但这种结果不能当作计划中正式本地批次：

```bash
SCX_STAGES="100:30 300:30" bash tools/performance/jmeter/run-local.sh
```

结果保存在 `tools/performance/jmeter/results/<run-id>/`：JTL、逐档摘要、资源快照、MySQL/Redis 对账和引擎/Mock 日志。脚本退出时默认删除本次命名的容器和数据卷；设置 `SCX_KEEP_STACK=true` 可临时保留，随后必须显式执行：

```bash
bash tools/performance/jmeter/stop-local-stack.sh
```

运行时随机生成的渠道 HMAC、设备 HMAC、AES-GCM 和 callback token 密钥仅存在于 `mktemp -d` 创建、权限 700 的目录内；具体文件权限为 600。JTL、日志、摘要和对账输出不保存密钥或设备原值。

## 口径

- HTTP 202：Redis Stream 已写入并满足 `WAIT 1`，不代表 MySQL 已落库或上游已成功。
- HTTP 200：同事件 ID、同规范化载荷的幂等重试。
- HTTP 409：nonce 重放或同事件 ID 不同载荷。
- “不丢”只针对 HTTP 已明确受理的唯一事件：`202 唯一事件 = MySQL Feedback 事实 + 接入 DLQ`，且最终 Redis `pending=0, lag=0`。
- 设备窗口重复仍属于 Feedback 事实，但决策为 `DEDUPLICATED`，不得产生计费或上游任务。
- 本机 Docker 资源是 4 CPU / 约 8 GB。即使 20,000 QPS 的 60 秒突发通过，也只记录为“本地突发达到”，不是生产容量验收。

