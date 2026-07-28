# L0 基础可信边界关闭执行记录（2026-07-27）

> 状态：本地验证、生产 V43–V47 与候选发布完成；生产四角色、凭据轮换、独立容量环境和真实第三方 UAT 尚未签收
> 发布分支：`codex/l0-production-gate`  
> 代码发布候选：`fac5ba4`  
> 原则：生产身份不清即停止；未执行线上 SQL 不标记完成

## 1. 当前结论

L0 尚未关闭，也不能标记为“仅受 BASE-04 阻塞”。

已完成：

- V1–V47 在空库应用、重复执行和校验通过。
- V47 同时补齐运营写/轮换与分析员只读权限。
- 四角色浏览器与直接 API 隔离回归 2/2 通过。
- 权限拒绝和对象 Scope 拒绝均形成精确、脱敏的失败审计。
- Redis Pending 持久化、`XAUTOCLAIM`、ACK 的本地一次性环境预检通过。
- 同一代码提交完成 30 模块后端测试、前端类型检查和生产构建。
- 发布 JAR、前端压缩包、迁移包及 SHA-256 清单已在本机生成。
- 2026-07-28 已在确认后的生产机 `42.81.102.181` 完成一致性备份、V44–V47
  前向迁移、配置环境变量化、候选 JAR/前端发布和健康检查；V43 已在此前登记且
  校验和一致。
- 生产渠道客户端已统一为 `CLICK,CONVERSION,RTA` Scope；管理员、运营、分析员
  的渠道权限种子与合并后的系统菜单已完成数据库核对。
- 生产无效渠道凭据请求返回业务码 `401`，Raw Event 总数保持不变；现有 5 条
  ACTIVE 凭据均无未来生效或已过期异常，2 个客户端均能关联当前 ACTIVE 版本。

未完成：

- 生产仅有管理员角色账号，没有运营、分析员和无范围验收账号；浏览器、直接 API、
  对象 Scope 与真实 `sys_log` 四角色矩阵尚未执行。
- 5 条历史 ACTIVE 凭据尚未与真实调用方协调双版本轮换、缓存失效和旧版本吊销。
- 当前生产没有可用 RocketMQ。发布后两条既有 Outbox 进入
  `RETRYING/attempts=1`，已关闭后台发布器以防继续重试；记录未删除、未改成成功。
- 首次发送失败时第三方 `RocketMQTemplate` 曾把完整消息对象写入受限错误日志；
  未在文档中保留其内容。生产已关闭发布器并增加该 logger 的 OFF 配置，后续恢复
  前还需完成既有日志受控轮换和隐私复核。
- 30 分钟监控观察、XXL-JOB 调度中心、告警接收和值班签收尚未完成。
- BASE-05 独立容量环境的 RocketMQ/Outbox 故障恢复、全链路对账、20k/40k 门禁未执行。
- BASE-04 真实第三方 UAT 资源未具备。

因此继续保持 `ARCH-01` 禁止启动，不宣称生产 OCPX 接入已验收。

## 2. 生产身份纠正与发布边界

2026-07-27 的首次执行把 `117.72.57.4` 当作生产目标，严格 SSH 校验发现主机
密钥变化后停止，未修改 `known_hosts`、未连接数据库，也未执行发布。2026-07-28
负责人明确纠正生产目标为 `root@42.81.102.181:22`。

```text
SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo
```

该指纹与本机已有可信记录一致，`StrictHostKeyChecking=yes` 连接成功；本轮没有
新增、删除或替换 `known_hosts`。生产仓库存在历史日志和未跟踪 V43，发布未在该
脏工作树执行构建或 Git 同步，而是使用本地验证过的 `fac5ba4` 独立制品。

发布前备份位于：

```text
/var/backups/scx/l0-20260728-121046
```

备份包含完整数据库一致性转储（71 张表）、原环境文件、systemd unit、旧 JAR、
旧前端和校验和；数据库压缩包已通过 `gzip -t`，全部备份文件通过 SHA-256 校验。

## 3. 隔离数据库与四角色证据

### 3.1 数据库

一次性 MySQL 8 容器使用独立端口，空库执行 `init-database.sh`：

- migration ledger：47 条，最高版本 V47；
- V1–V47 首次应用通过；
- 第二次执行通过，无重复结构或数据错误；
- `verify-database.sh` 通过；
- V47 验证：
  - `ROLE_OPERATOR`：渠道客户端写、凭据轮换；
  - `ROLE_ANALYST`：渠道菜单、渠道客户端只读。

### 3.2 四角色浏览器/API

用例：
`scx-fronted/apps/web-ele/e2e/l0-four-role-production-gate.spec.ts`

隔离账号只在一次性数据库中创建，密码由运行环境注入；Playwright trace 和
video 关闭，不保存 Token 或浏览器存储。结果：

```text
2 passed
15 skipped（非本次目标用例）
exit 0
```

覆盖：

- 管理员可见全部媒体；
- 运营仅可读写授权媒体；
- 分析员仅可读授权媒体；
- 无范围用户不能访问媒体和渠道资源；
- 未授权媒体与不存在媒体返回不可区分结果；
- 浏览器页面与直接 API 同时验证；
- 沙箱媒体完成创建、轮换、吊销、停用；
- 测试客户端最终为 `DISABLED`；
- 凭据 v1 最终为 `REVOKED`，v2 为 `ACTIVE`。

审计定向查询结果：

| 主体 | 请求 | 结果 | 审计 |
|---|---|---|---|
| 分析员 | `POST /boot/ad/channel-clients` | 权限拒绝 | 1 条、失败、`NOT_PERMISSION`、仅 `params=[]` |
| 无范围用户 | `POST /boot/ad/channel-clients` | 权限拒绝 | 1 条、失败、`NOT_PERMISSION`、仅 `params=[]` |
| 运营跨媒体 | `POST /boot/ad/channel-clients` | 对象 Scope 拒绝 | 1 条、失败、`BUSINESS_RESULT:404`、参数为空 |
| 运营安全媒体 | 创建/轮换/吊销/停用 | 成功 | 每个操作各有成功审计、参数为空 |

上述隔离四角色验收日志中没有密码、Token、渠道密钥、设备标识、媒体凭据或请求参数值。

## 4. 故障恢复与容量边界

本轮完成本机一次性 Redis 7 预检，并对生产 Redis 做只读核对：

- Redis 集成测试 5/5 通过；
- 重启后 Pending 保留；
- `XAUTOCLAIM` 和 ACK 通过。
- 生产 8 个 Feedback Stream、RTA Stream 及对应 DLQ 当前长度均为 0。
- 生产 Callback 为 1 条 `SUCCEEDED`，Raw Event 为 3 条 `ACCEPTED`，Outbox 为
  2 条 `SENT` 和 2 条 `RETRYING/attempts=1`。
- 生产没有 Nameserver/Broker 监听 `9876`；Outbox 发布器保持关闭，待 BASE-05
  独立环境恢复和全链路对账后再启用。

这不是 BASE-05 签收。当前没有可确认的独立容量环境目标地址、压测凭据和
环境授权，所以没有对本机或生产发起 20k/40k 压测，也没有执行 RocketMQ
中断、Worker 提交前/后崩溃、Callback 500/500/200 或全链路事实对账。

## 5. 构建与发布包

### 5.1 验证

| 门禁 | 结果 |
|---|---|
| `mvn -pl scx-boot -am test` | 30 模块成功，exit 0 |
| 前端类型检查 | exit 0 |
| 前端单测 | 32 文件、267 用例通过 |
| `pnpm build:ele` | 11/11 任务成功，exit 0 |
| 变更文件 ESLint | exit 0 |
| 契约校验 | exit 0 |
| 生产密钥注入检查 | exit 0 |
| 迁移打包检查 | exit 0 |
| 四角色 Playwright | 2/2 通过，exit 0 |

全仓 `pnpm lint` 仍被未改动的
`QueueOverview.vue` 既有 Prettier 漂移阻塞；本次变更文件的 ESLint 已通过，
且生产构建成功。本任务未顺带格式化该无关文件。

### 5.2 本机发布包

目录：`/tmp/scx-l0-artifacts-fac5ba4`

| 文件 | SHA-256 |
|---|---|
| `scx-boot-fac5ba4.jar` | `4d4e97fb8b5280591cbcfa052a8529057b6f95a4031bbdaab9da103d9e2df60c` |
| `web-ele-dist-fac5ba4.zip` | `5b267d62e8afcae8ef7f01c119ccc75294b9af7b6528088219477beb57c3ffd4` |
| `scx-db-migrations-fac5ba4.tar.gz` | `329a23b1c1a600d489641a6cd44d3665dada464ed05967a61b22bb485c3d397b` |

该目录已上传到生产备份目录下的 `release-fac5ba4/` 并再次通过 SHA-256、ZIP 和
TAR 完整性检查。迁移包包含 `init-database.sh`、`verify-database.sh`、baseline
和 V1–V47；生产当前 JAR 校验和与候选一致。

## 6. 恢复执行所需输入

生产：

1. 通过安全注入提供运营、分析员和无范围验收账号；
2. 指定共同授权媒体、未授权媒体和允许写入的 sandbox/测试媒体；
3. 确认历史凭据轮换负责人、真实调用方和双值/版本化切换窗口；
4. 提供可用 RocketMQ/XXL-JOB、告警接收负责人和 30 分钟观察窗口。

BASE-05：

1. 独立可丢弃环境的入口与授权；
2. MySQL、Redis、RocketMQ、应用实例和压测节点拓扑；
3. 压测凭据的安全注入位置；
4. 监控与对账数据源；
5. 故障注入和恢复窗口负责人。

BASE-04：

1. 真实第三方联系人；
2. 沙箱环境与一次性 Scope 凭据；
3. 双方 UAT 时间窗口；
4. 签收人和既有签收模板。

## 7. 下一次生产执行顺序

1. 准备运营、分析员和无范围账号，执行生产四角色浏览器/API/审计矩阵。
2. 与真实调用方协调 5 条历史凭据的轮换、缓存失效、旧版本吊销和回退窗口。
3. 部署独立 RocketMQ/XXL-JOB 与监控接收器，在受控窗口恢复两条 Outbox 并逐层
   对账 Raw Event、Outbox、MQ 和消费者事实。
4. 完成 30 分钟生产观察；异常积压、权限或审计泄密均保持 No-Go。
5. 独立执行 BASE-05；第三方资源到位后执行 BASE-04。

任何迁移部分执行、账本/校验和异常、越权、审计泄密或积压持续增长，均停止
后续步骤并采用新的前向修复，不修改已执行 migration，不手写账本。

## 8. 本轮脚本门禁（2026-07-28 增补）

为避免再次误连错误主机，生产验收脚本现在默认拒绝所有写入，必须由授权操作人在
受控窗口显式提供以下环境变量：

```text
L0_ALLOW_PRODUCTION_WRITE=I_HAVE_AUTHORIZATION
L0_TARGET_HOST=42.81.102.181
L0_SSH_FINGERPRINT=SHA256:rrH8nVzaA40JGueBrcH8Jq2tad//8FL0WbUEuHSOXZo
L0_SSH_FINGERPRINT_CONFIRMED=true
```

脚本不会执行 keyscan、`accept-new` 或修改 `known_hosts`；指纹必须先由基础设施负责人
带外确认。四角色脚本只在 `artifacts/acceptance/four-role-<runId>.json` 写入不含密钥、
令牌、请求参数和浏览器存储的元数据。失败时仍执行撤销 Scope、禁用临时账号和停用
第二媒体的清理，但不删除账号、媒体或审计记录。

RocketMQ 安装脚本仅接受本地 `rocketmq-all-5.3.1-bin-release.zip`，固定校验官方
SHA-256，默认不启动/enable 服务、不下载源码、不自动建 Topic；只有带 `--start` 的
受控窗口才会启动绑定 `127.0.0.1` 的 NameServer/Broker 并创建
`scx_tracking_events`。XXL-JOB 安装脚本仅接受构建机上传的 3.4.1 JAR 和显式
SHA-256，生产机禁止下载或 Maven 构建。Publisher、Scheduler 和 executor 必须按
恢复顺序逐项开启，任何 Outbox、Redis PEL/DLQ、Callback 或审计异常均立即停止并保持
No-Go。
