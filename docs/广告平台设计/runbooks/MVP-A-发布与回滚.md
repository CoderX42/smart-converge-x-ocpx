# MVP-A 发布、回滚与数据核验手册

## 适用范围与停止条件

本手册适用于 SCX MVP-A 的受控发布环境，覆盖应用包、数据库迁移、配置核对、运行态检查和前向修复。它不授权执行真实媒体回调、重放、删除、数据清洗、自动回滚或变更生产凭据。

以下任一情况立即停止发布：目标库/主机无法确认、备份失败、SQL 文件 SHA-256 与已审批发布包不一致、实际结构不满足前置条件、数据库校验失败、健康检查失败、Outbox/DLQ 出现持续增长，或发布负责人未确认回滚窗口。

## 发布前检查

1. 记录版本、提交、不可变镜像摘要、发布窗口、负责人和前一稳定版本；使用 `templates/发布清单模板.md` 留档。
2. 在构建工作区完成：

   ```bash
   make backend-test
   make frontend-check
   make frontend-build
   make contract-check
   make schema-ledger-check
   make prod-secret-injection-check
   git diff --check
   ```

3. 仅通过受控部署环境提供生产数据源、Redis、RocketMQ、签名密钥和 AES 登录密钥。`application-prod.yml` 还要求 `SCX_TRACKING_CHANNEL_MASTER_KEY` 与 `SCX_TRACKING_DEVICE_HMAC_KEY`；部署脚本要求 MySQL 连接参数和 XXL-JOB token 显式注入，不提供目标地址、账号或密码默认值；Nacos 还要求 `NACOS_DB_URL`、`NACOS_DB_USERNAME`、`NACOS_DB_PASSWORD`、`NACOS_AUTH_TOKEN_SECRET`。Nginx 管理面板 token 只允许放在请求头，不能放入查询参数或日志。先运行 `make prod-secret-injection-check` 确认配置和部署脚本未回退为内联值。不得从 Git、终端历史、浏览器或 Runbook 复制凭据；任何曾进入版本历史的值都必须在外部完成轮换，删除工作树中的明文不能替代轮换。
4. 确认生产 profile 关闭 sandbox 回传目标，外部出网 allowlist、告警接收人和 M6 指标采集已由负责人复核。`/actuator` 仅应在内部监控网络可访问，不能经前端公网代理暴露。
5. 确认本次前向 SQL 已纳入发布包，记录 Git 提交和文件 SHA-256，并在发布工作区执行 `make schema-ledger-check` 与 `git diff --check`；项目不依赖 Docker/Compose 冷启动数据库。数据库迁移账本已退役，禁止把它作为执行条件或验收证据。

## 数据库备份与升级

1. 使用受控数据库会话确认目标，而不是假设默认库：

   ```sql
   SELECT DATABASE(), @@hostname;
   SELECT table_name, engine, table_collation
   FROM information_schema.tables
   WHERE table_schema = DATABASE()
   ORDER BY table_name;
   ```

2. 由已授权的运维会话对本次受影响表做可恢复导出。当前 V43–V46 会修改审计字段、渠道客户端、当前渠道凭据和系统菜单；导出至少覆盖 `sys_log`、`sys_menu`、`sys_role_menu`、`scx_channel_client`、`scx_channel_credential`。使用 `--single-transaction --routines --triggers --no-tablespaces`，保存到受控备份位置并核验文件非空；不在命令、日志或本文件写入口令。
3. 在确认备份成功后，由已授权人员显式执行发布清单中唯一批准的前向 SQL。`init-database.sh` 只允许空库初始化，禁止用于已有库升级。执行前必须再次核对：

   ```text
   目标主机 / 数据库
   Git 提交
   SQL 相对路径
   SQL SHA-256
   审批人 / 执行人 / 变更窗口
   ```

4. 升级完成后运行只读校验，并确认本次结构后置条件：

   ```bash
   ./verify-database.sh
   ```

   ```sql
   SELECT column_name
   FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'scx_channel_client'
     AND column_name = 'allowed_scopes';
   ```

## 应用发布与运行态核验

1. 以不可变镜像/包启动新实例，先保持旧版本可回切；不要用旧 JVM 验证新 Prometheus 指标或新路由。
2. 在内部网络检查健康端点和 Prometheus 指标端点；确认版本、数据库、Redis、RocketMQ 连接健康，且 `scx_callback_queue`、`scx_outbox_queue` 的状态标签可采集。
3. 只在隔离 sandbox 完成业务冒烟：登录、媒体范围、客户/产品、追踪链接、监测任务、签名 CLICK/ACTIVATE/REGISTER/PAY、去重/归因、回传、日报、导出和 Outbox 投递。生产发布不得把真实媒体或真实回调当作验收输入。
4. 对比发布前后的真实结构、约束、关键表行数、最近日报快照和 Outbox/DLQ 状态；任何差异都记录在发布清单并交由负责人判断。

## 回滚与前向修复

SCX 的业务迁移是前向版本化 DDL；本手册不提供自动反向 SQL。

1. 若新应用尚未写入新结构，先停止扩大流量并回切到前一稳定应用包；保留新版本日志、监控与迁移证据。
2. 若仅应用代码失败且数据库兼容，优先回切应用包，不改动已执行 SQL 或业务数据。
3. 若前向 SQL 已执行或新字段已被使用，禁止直接执行反向 DDL 或手工回填未知数据。根据已确认根因新增 `V{n}__*.sql` 前向修复，并在隔离库演练后发布；任何 SQL 均不得使用已退役的数据库迁移账本。
4. 仅在发布负责人明确批准、应用已停止写入且已验证备份可用时，才由 DBA 按备份恢复受影响表；恢复后必须复跑数据库校验、健康检查、业务冒烟与队列核验。

## 本轮状态

本手册和迁移包一致性检查已就绪，尚未对目标发布环境执行备份、升级、密钥轮换、流量切换、容量演练或回滚演练。完成后必须立即更新 `MVP开发规划.md` 的执行快照与本轮完成增量，附命令、环境、结果和证据位置。
