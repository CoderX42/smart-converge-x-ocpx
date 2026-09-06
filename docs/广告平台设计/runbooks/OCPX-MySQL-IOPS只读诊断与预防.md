# OCPX MySQL IOPS 只读诊断与预防 Runbook

## 目的与边界

本 Runbook 针对火山引擎 RDS MySQL Primary 的 IOPS 使用率告警。IOPS 使用率是实例当前 I/O 请求量相对规格上限的比例；扩容磁盘容量不会自动提高 IOPS 上限，必须单独核对实例规格中的 `MaxIops`、吞吐上限和 I/O 延迟。

本 Runbook 只做只读采集：不执行 `COUNT(*)` 扫描业务大表、不执行 `EXPLAIN ANALYZE`、不执行 `FLUSH`、不修改全局参数、不删除数据、不重放队列。诊断账号只需要读取目标库状态、Performance Schema 和 `information_schema` 元数据。

脚本路径：[scripts/ops/ocpx-mysql-diagnostic.sh](../../../scripts/ops/ocpx-mysql-diagnostic.sh)。脚本默认输出 TSV，包含数据库身份、限定变量、两次状态差值、表/索引 I/O 聚合、Top SQL digest、表大小估算、索引元数据、长事务摘要和连接状态摘要；不打印连接密码或完整进程 SQL。

## 现场采集

先从火山引擎控制台导出告警窗口（至少告警前后各 30 分钟）：Primary 节点、`IOPS使用率`、读/写 IOPS、吞吐量、I/O latency、CPU、内存、连接数、磁盘空间、binlog/redo 相关指标、实例规格 `MaxIops`。保留导出时间和时区。只看 15 分钟平均值无法判断是读放大、写放大、后台刷脏、锁等待还是规格上限过小。

在能访问 RDS 的安全运维机执行以下命令，认证通过 MySQL 客户端配置文件或 login-path 提供：

```bash
# 推荐：权限最小化的 defaults-extra-file；文件权限应为 600
bash scripts/ops/ocpx-mysql-diagnostic.sh \
  --defaults-extra-file /secure/mysql/ocpx-readonly.cnf \
  --database smart_converge_x --interval 15 > /secure/evidence/ocpx-mysql-$(date -u +%Y%m%dT%H%M%SZ).tsv

# 或使用 mysql_config_editor 保存的 login-path
bash scripts/ops/ocpx-mysql-diagnostic.sh \
  --login-path ocpx-readonly --database smart_converge_x --interval 15 \
  > /secure/evidence/ocpx-mysql-$(date -u +%Y%m%dT%H%M%SZ).tsv
```

采集文件应与控制台同一时间窗口保存。`interval` 必须为 1–60 秒；默认 15 秒。若 Performance Schema 被关闭或某个汇总表不可用，记录报错并补充火山引擎 SQL 洞察/慢查询导出，不要为了采集而在线启用或重启实例。

## 证据判读

### 先确认连接到了正确的 Primary

`[identity]` 中应同时核对 `hostname`、`server_uuid`、版本、数据库名和 `read_only`。本次现场目标应是 `mysql-e379be1ffea9-mb9c1-0`、MySQL 8.0.43-34、`smart_converge_x`、`read_only=0`；若不一致，立即停止，把结果标记为无效环境证据。

### 用两次差值估算当前负载

`[status_delta]` 的 `Innodb_data_reads`、`Innodb_data_writes` 是采样窗口内的累计差值；除以 `interval_seconds` 可得近似每秒操作量。将其与控制台读/写 IOPS、吞吐和延迟对齐：

- 写 IOPS 和 `Innodb_data_writes` 同步升高：优先检查事实/账务/小时汇总写入、流程日志、批量清理、重试和 binlog/redo 刷盘。
- 读 IOPS 和 `Innodb_buffer_pool_reads` 升高，同时 `buffer_pool_read_requests` 很高：优先检查冷数据扫描、缺索引和报表/Callback 查询。
- `Innodb_log_waits`、`Innodb_os_log_pending_fsyncs` 或锁等待增加：说明 redo/fsync 或事务争用可能成为瓶颈；不要直接把 `innodb_io_capacity` 调大掩盖根因。
- `Created_tmp_disk_tables` 相对 `Created_tmp_tables` 占比上升：检查排序、分组和临时表是否落盘。
- `Threads_running` 长时间高于稳定基线而 `Threads_connected` 未接近上限：优先检查慢 SQL、锁等待和连接池持有时间。

这些计数器是累计状态，单次采集只用于相对变化；更换参数、重启或实例切换后应重新建立基线。

### Top SQL 与表/索引 I/O

`[top_statement_digests]` 按总耗时排序，重点查看 `rows_examined`、`tmp_disk_tables`、`no_index_used`、平均耗时和调用次数。将排名前 30 的 digest 与火山引擎慢查询样本、应用日志中的接口阶段关联；不要仅凭总耗时为 SQL 建索引。

`[table_io_waits]`、`[index_io_waits]` 用于确定 `scx_ocpx_%` 表的读写热点。`<NO_INDEX>` 读操作、写操作远高于预期或等待时间异常时，再对对应真实 SQL 做脱敏 `EXPLAIN`（只读、低峰、单条），确认过滤列顺序和排序是否可用索引。`[index_metadata]` 只证明索引存在，不证明优化器实际选择它。

当前源码的主实时写路径是 `scx_ocpx_event_fact`、`scx_ocpx_billing_entry` 和 `scx_ocpx_hourly_metric`；旧 `scx_ocpx_log_*`、`scx_ocpx_report_mat*` 和兼容队列属于历史/兼容路径。V125 已在源码中默认关闭兼容报表多写、限制投递并发和清理批次，但目标库迁移、生产 JAR 发布、重启及 24 小时 IOPS 验收仍需单独提供证据。

### 连接、锁与批处理

`[transactions]` 只输出事务状态和锁定/修改行数，不输出事务 SQL；发现长时间 `RUNNING`、`trx_rows_locked` 持续增长或 `wait_started` 非空时，结合应用 trace 和 DBA 授权的锁图定位。`[processlist_summary]` 仅按命令/状态聚合，避免把请求参数写入诊断文件。

应用侧同时核对：Boot/Engine 实际 JAR SHA-256、进程启动时间、加载 profile、`ocpx.compatibility-report.enabled`、`ocpx.flow-log.success-sample-rate`、上下游线程池 core/max/queue、清理 cron/批量参数，以及 Redis 兼容队列深度。源码单测通过、Actuator 健康为 UP 或前端超时下降，都不能替代数据库 IOPS 证据。

Engine 已提供内部 Prometheus 端点 `/ocpx/actuator/prometheus`；生产进程绑定 `127.0.0.1:10000`，公网 Nginx 不应放行 actuator 路径。由本机 Prometheus 或安全采集器抓取调度队列、清理批次和应用计时指标，并与 RDS 时间轴对齐。

## 分阶段处理方案

### P0：告警窗口内止损（只读确认后再做已授权开关）

1. 先固定告警窗口证据、RDS 规格与 `MaxIops`，区分容量、IOPS、吞吐、延迟和 CPU/内存；磁盘扩容不能作为 IOPS 优化结论。
2. 核对是否仍运行旧 JAR 或 `ocpx.compatibility-report.enabled=true`。若确认旧兼容队列是写放大来源，按发布流程准备切换到已审查的 V125 包；不要直接编辑源文件或仅重启旧 JAR。
3. 对正在增长的慢 SQL/清理任务，先限流或暂停对应**已授权**非核心作业，并观察 15 分钟读写 IOPS、延迟、`Innodb_log_waits`、锁等待和 Callback P95；不得删除事实、账务或小时指标。

### P1：低峰发布 V125/V126（需备份、审批和回滚点）

1. 核对生产 JAR SHA-256、Git 提交、目标数据库身份和现有索引；备份受影响表或完成云备份可恢复性确认。
2. 对 V125 三个清理索引和 V126 Callback 索引在目标库执行前做 `EXPLAIN`/在线 DDL 能力审核；确认 `information_schema.statistics` 不重复后再按低峰窗口执行前向 SQL。不要修改已执行的增量文件，不依赖退役迁移账本。
3. 发布并重启 Boot/Engine，使用宝塔脚本的健康检查确认新 PID 与 `/boot/actuator/health`、`/ocpx/actuator/health`；检查启动日志中的配置和 JAR 时间。
4. 发布后观察至少 2 小时及一个完整 24 小时周期：RDS IOPS、读写 IOPS、吞吐、延迟、CPU/内存、连接、redo/锁等待、Top SQL、Callback P95/499/5xx、小时汇总延迟、Redis 队列深度和清理批次。任一关键指标恶化应按回滚点恢复，并保留对账证据。

### P2：长期预防

- 建立 1 分钟采集、15 分钟/1 小时聚合和 30 天趋势；保留 Top digest、表/索引 I/O、锁等待、连接池和 RDS IOPS/latency 的同一时间轴。
- 建议分层阈值（需以稳定基线和规格复核，均为建议，不是本次实测结论）：15 分钟 IOPS 使用率 ≥70% 预警，≥80% 严重；连续 3 个 5 分钟窗口触发升级。若 latency、`Innodb_log_waits`、锁等待或错误率先恶化，应提前升级，即使 IOPS 尚未到 80%。
- 每次发布前做生产量级的只读 `EXPLAIN`、索引存在性检查和写放大评审；禁止无证据增加覆盖索引，避免索引本身放大写 IOPS。
- 为兼容队列、清理任务、投递线程池和 RTA 流建立低基数指标；队列深度、失败/重试、清理删除行数和批次耗时必须可告警。失败必须有限重试并退避，不能形成热循环。
- 制定月度容量复盘：按峰值 QPS、每请求事实/账务/汇总写次数、binlog/redo 增长、表索引增长和 RDS MaxIops 计算余量；余量不足时优先减少写放大或拆分读流量，再评估升级实例规格/只读副本。

## 验收标准

优化只有在以下证据全部齐全时才可标记为“线上恢复”：目标 JAR 与配置已生效、V125/V126 索引在目标库可查、Top SQL/锁/redo 根因有记录、连续 2 小时及 24 小时周期 IOPS 使用率稳定低于严重阈值、Callback P95/错误率/小时指标延迟无回归、并完成关键事实/账务/小时汇总对账。源码、单元测试、本地 Prometheus 或单次健康检查只能标记为“发布准备”或“隔离验证”。
