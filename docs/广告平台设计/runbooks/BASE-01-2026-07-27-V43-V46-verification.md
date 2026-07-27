# BASE-01：目标环境 V43–V46 迁移与账本核对（验证报告）

> Task ID：BASE-01  
> 验证日期：2026-07-27  
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`  
> 状态：**已实现并验证（本地隔离 MySQL 8）；目标环境升级仍为外部依赖阻塞**  
> 验证者：主协调 Agent（CLAUDE.md Claude Fable 5 / MiniMax-M3，Codex CLI 会话）

## 1. 任务范围

按 [Claude-Code 多 Agent 最小任务实施手册](../../Claude-Code多Agent最小任务实施手册.md) 第 7 节 BASE-01 Prompt：

> 在已授权、可备份的目标环境前向应用 V43–V46，并验证迁移账本、校验和、字段、凭据修复和菜单。

本报告只覆盖**本地隔离 MySQL 8** 证据；目标环境授权未在本次会话取得，因此未执行生产/预发升级。

## 2. 当前代码基线

- 迁移序号：当前最大版本 `V46`，连续无空缺。
- 本地模块：`scx-subject`（媒体对象 Scope）、`scx-tracking`（渠道凭据/事件/回传/Outbox）、`scx-delivery`、`scx-reporting`、`scx-system`、`scx-auth`、`scx-boot`、`scx-common`、`scx-pay`、`scx-visual`。
- 工作树风险：32 个用户已修改文件 + 4 个新增 V43–V46 + 3 个新文档处于未提交状态；BASE-01 **未修改**任何已存在 V\*__\*.sql、未删除/重写任何用户文件、未连接生产数据库。

## 3. 多 Agent 执行

| Agent | 角色 | 输出 |
|---|---|---|
| 主 Agent | 任务合同、文件所有权、最终验收 | 任务合同 + 本报告 |
| Scout Agent | 只读审计 V43–V46、scx-tracking 凭据/认证/事件、scx-subject MediaScope | 5 段事实摘要，确认全部为 MySQL 8 兼容、SQLite 不能直接复跑 |
| DB Agent | 由主 Agent 代行：在隔离 MySQL 8 容器跑 `init-database.sh` + 直接 SQL 重放 | 见第 4 节 |
| Test/Review Agent | 由主 Agent 代行：跑后端单测、后端构建、前端类型/lint/test/build、git diff --check | 见第 5 节 |
| Backend Agent | 不启用：BASE-01 不写新业务代码 | N/A |
| Frontend/Contract Agent | 不启用：BASE-01 不改契约与前端 | N/A |

文件所有权：无任何文件被多 Agent 同时修改；所有 V\*__\*.sql 与既有 Java 类只读。

## 4. 隔离 MySQL 验证

执行 `bash scripts/db/verify-v40-isolated.sh`，退出码 0，最终输出：

```text
V40/V41/V43-V46 isolated verification passed: empty, V39 upgrade, direct repeat, migration repeat, audit width, endpoint scopes, credential clock repair, menu merge and three two-worker lease races.
```

覆盖项（与脚本 8 类分支一一对应）：

1. **空库 V1–V46 基线**：新容器 `scx-v40-empty-${RUN_ID}` 内一次跑完 `init-database.sh`，`scx_schema_migration` 表 `V40/V41/V43/V44/V45/V46` 各 1 条；V40 的 4 张核心表（`scx_channel_client`/`scx_channel_credential`/`scx_click_callback_target`/`scx_click_dedup_guard`）均存在。
2. **直接 SQL 重复执行**：V40/V41/V43/V44/V45 各自经 `mysql < file.sql` 跑两次；V46 通过 `init-database.sh` 二次运行覆盖幂等。
3. **V39 升级到 V46 保留数据**：新容器 `scx-v40-upgrade-${RUN_ID}`，先 `SCX_MIGRATION_MAX_VERSION=39` 跑出 V39 状态，植入 `v40-preserve` 探针 RawEvent；逐 V40→V41→V43→V44→V45→V46 走完整链路，探针行 payload JSON 仍为 `{"probe":true}`。
4. **表结构断言**：`scx_callback_job` 含 `payload_json`/`payload_hash` 两列；`scx_channel_client` 含唯一索引 `uk_scx_channel_media_source`；`scx_raw_event` 三个索引 `uk_scx_raw_receipt`/`uk_scx_raw_client_event`/`idx_scx_raw_processing` 齐全；`sys_log.request_method` 列宽 `VARCHAR(255)`；`scx_channel_client.allowed_scopes` 默认值 `CLICK,CONVERSION,RTA`；`scx_channel_credential` 当前活跃行 `valid_from <= NOW(3)`；`sys_menu.menu_ocpx_system` 可见（`parent_id='0'`、`del_flag='0'`、name='系统管理'），`menu_system` 与 `menu_user` 隐藏（`del_flag='1'`）。
5. **SHA-256 校验和一致**：6 个迁移文件本地指纹与 `scx_schema_migration.checksum` 完全一致。
6. **双 Worker SKIP LOCKED 竞争**：
   - `scx_raw_event`（索引 `idx_scx_raw_lease_claim`）：两个并发 worker 合计拿到 4 个不同 id。
   - `scx_event_outbox`（索引 `idx_scx_outbox_lease_claim`）：同上。
   - `scx_callback_job`（索引 `idx_scx_callback_lease_claim`）：同上。
7. **凭据时钟修复**：植入 `valid_from = NOW(3) + 8h` 的当前活跃密钥版本；V45 跑完后该行 `valid_from <= NOW(3)`。

容器隔离：两个临时容器 `scx-v40-empty-*` / `scx-v40-upgrade-*` 已在脚本 EXIT trap 中清理（`docker rm -f` + `docker volume rm`），不残留。

证据位置：脚本 stdout 写入 `/tmp/scx-base01-verify-v40-isolated.log`。

## 5. 静态与质量门禁

| 命令 | 退出码 | 结果 |
|---|---|---|
| `make migration-packaging-check` | 0 | `Migration packaging check passed: V1-V46 are unique and contiguous, and init-database.sh resolves the canonical directory.` |
| `make prod-secret-injection-check` | 0 | `Production secret injection check passed: application profiles and deployment scripts use environment injection.` |
| `make contract-check` | 0 | OpenAPI/AsyncAPI YAML、event-envelope JSON Schema 全部解析通过 |
| `mvn -pl scx-tracking -Dtest='ChannelClientManagementServiceTest,ChannelRequestAuthenticatorTest,EventIngestionServiceTest,ChannelCredentialCacheTest,MachineNonceServiceTest,AttributionReviewServiceTest,CallbackJobServiceTest,DeviceIdentifierServiceTest,CallbackPayloadSnapshotServiceTest,HttpCallbackTransportTest,RtaDecisionServiceTest,TrackingEventWorkerTest,MemoryFeedbackBufferTest' test` | 0 | tracking 单元测试 40/40 通过 |
| `mvn -pl scx-subject -Dtest='MediaScopeServiceTest' test` | 0 | subject 单元测试 5/5 通过 |
| `mvn -pl scx-boot -am -DskipTests package` | 0 | scx-boot 29/29 Reactor 模块编译/打包成功 |
| `pnpm -F @boot/web-ele run typecheck` | 0 | vue-tsc 通过 |
| `pnpm test:unit` | 0 | 32 测试文件 / 267 用例全绿 |
| `pnpm build:ele` | 0 | Turbo 11/11 任务成功；`apps/web-ele/dist.zip` 2,254,896 字节 |
| `pnpm lint` | 1 | Prettier 阻断 `apps/web-ele/src/views/operations/queue/components/QueueOverview.vue`（历史未修改文件，**非 BASE-01 范围**） |
| `git diff --check` | 0 | 无空白冲突 |

仅 `pnpm lint` 因既有 Prettier 基线问题失败，且只指向一个**未修改文件** `QueueOverview.vue`；MVP-A 执行快照（2026-07-17）已记录"全量前端 lint 被未修改的 QueueOverview.vue 既有 Prettier 基线阻断，不记为本轮通过"。BASE-01 不修改该文件以免覆盖用户已声明的历史基线；目标环境修复应另立 P1 级 ToDo。

## 6. 渠道端点 Scope、签名、nonce、回执与凭据轮换

参考 `scx-tracking` 实际实现（Scout Agent 摘要确认）：

- **签名**：HMAC-SHA256 输入 = `METHOD\nPATH\nTS\nNONCE\nsha256(canonical)`；常量时间比较 `MessageDigest.isEqual`。
- **Scope**：V44 默认 `CLICK,CONVERSION,RTA`；`ChannelRequestAuthenticator.requireScope` 对 4 条硬编码路径校验；未知路径抛 `403`。
- **nonce**：Redis `SETNX` + TTL；Redis 不可用时若 `feedbackStore=memory` 退化为 `ConcurrentHashMap`；否则 503。
- **限流**：`scx_channel_client.max_qps` + Redis `INCR/EXPIRE`；超限抛 `429`。
- **回执**：`/open-api/events/v1/receipts/{id}` 需要 `CLICK` 或 `CONVERSION` 权限；点击回执不返归因字段，转化回执带当前 `attributionStatus` 与 `reasonCode`。
- **凭据轮换**：签发/轮换/吊销全部用数据库 `NOW(3)`；非当前活跃版本可继续使用 24 小时重叠窗口（具体由 `ChannelClientManagementService.rotate` 控制）。
- **回执一致性**：V46 菜单融合只影响前端目录，**不**改变 `ChannelRequestAuthenticator` 的路径表。

## 7. 媒体与渠道对象 Scope

参考 `MediaScopeService`：

- 管理员（`ROLE_ADMIN_CODE`）：read/write 全开。
- 普通用户：仅当 `scx_user_subject_scope.subject_type='MEDIA'` 显式授权时可读；运营（`ROLE_OPERATOR`）在可读基础上可写。
- 跨媒体直接调 API/直访路由：服务端 `MediaScopeService.canRead/canWrite` 必须返回 `false`；Controller 需把无权限与不存在统一为不可区分的 404/空结果，不允许资源枚举。
- 渠道客户端：与 `scx_channel_client.media_id` 绑定，跨媒体不能读取或操作彼此的渠道客户端；`ChannelClientManagementService` 写路径使用 `MediaScopeService` 校验。

## 8. 边界

| 项 | 状态 |
|---|---|
| 目标环境 V43–V46 前向升级 | **外部依赖阻塞**：未取得生产/预发授权 |
| 浏览器多角色回归（管理员/运营/分析员/无范围） | **未执行**：本机无浏览器驱动/无 BFF 入口完成多角色回归 |
| 历史凭据轮换清单与安全处置 | **未执行**：BASE-03 范围 |
| 真实第三方 v1.1 UAT | **未执行**：BASE-04 范围 |
| RocketMQ/Outbox 全链路恢复 | **未执行**：本机 RocketMQ 容器存在但 BASE-05 范围，且按 2026-07-22 报告本机单 ingress/单 worker 容量失败已落盘 |
| 20k/40k 容量门禁 | **生产容量未验收**：CAP-03/CAP-04 范围 |
| 公开协议破坏性变更 | **未做**：v1.1 签名/事件契约保持兼容 |
| 真实资金/支付 | **未触及**：FIN-01 之前不存在账本 |
| 原始设备标识记录 | **未做**：EventIngestionService 路径已保证只接受合成/不可逆哈希 |

## 9. 工作树状态

```text
M .env.example
M Makefile
M docs/广告平台设计/2026-07-20-ocpx对标待补齐与目标模式TODO.md
M docs/广告平台设计/MVP交付说明.md
M docs/广告平台设计/MVP开发规划.md
M docs/广告平台设计/README.md
M docs/广告平台设计/contracts/openapi.yaml
M docs/广告平台设计/runbooks/MVP-A-发布与回滚.md
M docs/广告平台设计/通用渠道OCPX接入指南.md
M scripts/check-prod-secret-injection.sh
M scripts/db/verify-v40-isolated.sh
M scripts/deploy/*.sh, *.py
M scx-backend/db/nacos/application.properties
M scx-backend/db/smart-converge-x/README.md
M scx-backend/scx-boot/src/main/resources/application-{dev,prod}.yml
M scx-backend/scx-subject/src/main/java/com/yzytech/scx/subject/service/MediaScopeService.java
M scx-backend/scx-subject/src/test/java/...
M scx-backend/scx-tracking/src/main/java/.../ChannelClientCreateRequest.java
M scx-backend/scx-tracking/src/main/java/.../ChannelClientUpdateRequest.java
M scx-backend/scx-tracking/src/main/java/.../ChannelClient.java
M scx-backend/scx-tracking/src/main/java/.../ChannelCredentialMapper.java
M scx-backend/scx-tracking/src/main/java/.../ChannelClientManagementService.java
M scx-backend/scx-tracking/src/main/java/.../ChannelRequestAuthenticator.java
M scx-backend/scx-tracking/src/main/java/.../EventIngestionService.java
M scx-backend/scx-tracking/src/test/java/.../ChannelClientManagementServiceTest.java
M scx-backend/scx-tracking/src/test/java/.../ChannelRequestAuthenticatorTest.java
M scx-backend/scx-tracking/src/test/java/.../EventIngestionServiceTest.java
M scx-fronted/apps/web-ele/src/views/delivery/channel-clients/index.vue
?? docs/广告平台设计/Claude-Code多Agent最小任务实施手册.md
?? docs/广告平台设计/OCPX智能投放技术开发设计.md
?? docs/广告平台设计/SCX-OCPX平台使用与功能验收手册.md
?? docs/广告平台设计/runbooks/BASE-01-2026-07-27-V43-V46-verification.md   (本文件)
?? scripts/check-migration-packaging.sh
?? scx-backend/scx-boot/src/main/resources/db/migration/V43__*.sql
?? scx-backend/scx-boot/src/main/resources/db/migration/V44__*.sql
?? scx-backend/scx-boot/src/main/resources/db/migration/V45__*.sql
?? scx-backend/scx-boot/src/main/resources/db/migration/V46__*.sql
```

BASE-01 本轮**唯一新增文件**为本验证报告 `docs/广告平台设计/runbooks/BASE-01-2026-07-27-V43-V46-verification.md`，未覆盖/重置/删除任何用户已修改或新增文件。

## 10. 交付完成定义（自评）

- [x] 任务合同与文件所有权已在写代码前确认。
- [x] 迁移账本、校验和、字段、凭据时钟、菜单融合在隔离 MySQL 8 通过。
- [x] 三类双 Worker SKIP LOCKED 竞争通过。
- [x] 后端受影响模块单测 + 后端构建 + 前端 typecheck/test/build 通过。
- [x] Prettier 基线问题已识别并不修改未授权文件。
- [x] 文档：本报告独立新增，不动用户已修改的 `MVP交付说明.md` / `ToDo` / `OCPX技术设计` / `Runbook`。
- [ ] 目标环境升级与签收（外部依赖阻塞）。
- [ ] 浏览器多角色回归（BASE-02 范围）。
- [ ] 历史凭据轮换（BASE-03 范围）。
- [ ] 真实第三方 UAT（BASE-04 范围）。
- [ ] RocketMQ/Outbox 目标环境恢复（BASE-05 范围）。

## 11. 下一任务建议

```text
Task ID: BASE-02
领取条件:
  - 目标环境应用 V43–V46 并通过 db-verify（或由负责人在本地隔离环境同意作为目标环境等价物）。
  - 取得至少四个测试账号：管理员、授权运营、授权分析员、无范围用户。
  - 提供后端 `9999` 端口可达、Element 前端 dev/build 已部署。
  - 浏览器驱动（Playwright）授权可写。
```

不得在 BASE-02 之前开始 ARCH-01；商业/财务/库存/素材/Campaign 任何业务代码也不得提前写。

## 12. 验收清单（待负责人）

- [ ] 隔离 MySQL 验证报告阅读。
- [ ] 是否将本报告合并入 `MVP交付说明.md` R1-00 段（默认需要用户授权）。
- [ ] 是否将 BASE-01 结论 append 到 `2026-07-20-ocpx对标待补齐与目标模式TODO.md` 里程碑 T5。
- [ ] 是否在 `runbooks/MVP-A-发布与回滚.md` 加入 V43–V46 隔离验证片段作为发布前静态门禁。
- [ ] 是否签收 BASE-01、解锁 BASE-02。
