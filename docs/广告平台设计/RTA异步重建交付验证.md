# RTA 异步重建交付验证

日期：2026-09-08。Task ID：`RTA-ASYNC`。状态：源码完成，本地验证通过，待负责人业务验收。方案依据：[ADR-016](adr/ADR-016-RTA异步重建.md)。

## 交付结果

- RTA 管理拆分为广告主接入、媒体接入、任务绑定、请求记录、流程日志和统计报表。目录跳转第一个有权限的子页面，无子页面权限时隐藏目录；各页面可刷新直达、独立查询。
- 媒体配置直接关联现有 `mediaId`，每个媒体最多一份。名称读取现有主数据，凭据解析媒体身份；绑定继承监测任务的媒体及广告主归属。媒体页和任务页提供配置入口。
- `POST /ocpx/rta/requests` 返回 202 后，由上游独立请求 `/ocpx/rta/callbacks/{accessCode}`，再通知媒体。路径不含版本号。任务映射采用绑定快照，只有实际命中且仍有效的任务能通过曝光/点击门禁。
- `rtaRequestId` 使用 SCX 返回的 `relayRequestId`，事件自身请求 ID 独立保留。请求幂等、回调冲突、截止时间、通知重试与恢复由 Engine 和 Redis 处理；RTA 不产生计费。
- Engine 将脱敏阶段摘要写入 Redis Stream，Boot 的 XXL-JOB Handler 批量写库后 ACK；统计按累计版本快照落库，日志定期清理。没有应用内定时 SQL 落库任务。
- 删除旧 Profile 页面/API、同步 RTA 和专属适配链路，保留合作方非 RTA 点击、转化、计费及扣量流程。新 schema 只建新 RTA 表，已有旧表保留为遗留数据，应用停止读取。

主要代码位于 Core 的 `ocpx/rta/contract`、Engine 的 `ocpx/rta/relay`、Admin 的 `ocpx/admin/rta`，前端位于 `apps/web-ele/src/views/ocpx/rta`。协议、配置、任务注册、切换及回退步骤见 [通用异步 RTA 契约与切换说明](contracts/rta-async.md)。

## 本地验证证据

所有集成验证使用本机隔离服务或 Mock，没有连接应用 dev/prod 共享数据库。

- `make backend-test`、`make ocpx-engine-test`：通过。Engine 最终报告为 209 项，201 项通过、8 项既有条件测试跳过；新增 `RelayIntegrationTest` 7 项全部执行通过。
- `mvn -f scx-backend/pom.xml -pl scx-boot,scx-ocpx-engine -am clean verify`：通过；最终修改后再次 `verify` 通过。检查发布 JAR，包含新 Controller、独立实时调度器及 XXL-JOB Handler，未包含退役的 Controller、决策服务、旧配置索引和 admission store 类。
- `RtaPersistenceIntegrationTest`：在显式指定的本机临时 MySQL 和自启 Redis 上执行 3 项，全部通过。覆盖媒体唯一配置与数据范围、配置版本和改名展示、数据库故障后的 Pending 恢复与重放、死信隔离、日志清理、统计快照乱序及日报聚合。
- 前端 `pnpm check:type`：通过；`pnpm lint`：通过，0 个错误，3 条既有警告位于 `region-selector/index.vue` 和 `utils/util.ts`；`pnpm test:unit`：47 个文件、337 项全部通过。
- 页面 E2E：`pnpm --dir scx-fronted/apps/web-ele test:e2e rta-async.mocked.spec.ts`，Chromium 4 项通过。覆盖六个页面刷新直达、按权限选择目录默认页、现有媒体配置默认停用、请求详情关联日志。
- `make contract-check`、`make schema-ledger-check`、`git diff --check`：通过。

模拟 HTTP 链路覆盖上游回调早于受理响应、媒体签收、候选 A/B/C 只命中 A/C、B 及其他媒体无法借用放行结果、请求幂等冲突、终态竞争、超时和迟到回调、重启读队列、通知重试耗尽，以及签名篡改、防重放、AES AAD 和摘要脱敏。模拟器只存在于测试中。

本次日志证据保存在执行机器 `/tmp/scx-rta-verify-pass.log`、`/tmp/scx-rta-persistence-delivery.log`、`/tmp/scx-rta-types-complete.log`、`/tmp/scx-rta-lint-delivery.log`、`/tmp/scx-rta-unit-complete.log`、`/tmp/scx-rta-e2e-final.log`、`/tmp/scx-rta-contract-delivery.log`。这些是本地临时证据，不作为生产发布凭证。

## SQL 预演

前向文件：[V131__rebuild_async_rta.sql](../../scx-backend/scx-boot/src/main/resources/db/migration/V131__rebuild_async_rta.sql)。本次验证文件 SHA-256：`c056eadecac3e4cc93a452c19e6717634f04d04c6024e5b1ade78d9b2b144a74`。

本机临时数据库 `scx_rta_test_final` 预置旧 Profile、绑定及外键，再执行一次 V131：两类旧测试记录均保留，旧 RTA 外键已解除，五张新表及新外键存在，六个子菜单就绪。当前态结构与 `verify-database.sh` 校验通过。未修改或重放既有迁移，未使用退役迁移账本。

本机可用 MySQL 为 **9.6.0**，并非生产目标 MySQL 8.x；MySQL 8.x 上的发布预演仍须在目标版本隔离环境完成。此次本地结果不能替代目标环境后置校验。临时服务只监听本机，验收后关闭。

## 权限、配置与发布边界

机器接口使用合作方 HMAC 和防重放，签名密钥仅在服务端。设备报文仅加密短存 Redis，日志不保存设备、签名或密钥。配置编辑及查询沿用管理端权限和广告主数据范围，不自动扩大普通角色权限。

新接入默认停用。尚未执行生产 SQL、注册线上 XXL-JOB、发布或启用合作方配置。上线需先按切换说明完成目标备份与 SQL 审批、字面量配置及重新打包、Nginx 接入规则、三个 XXL-JOB 注册、上下游联合验收，再逐项启用。

与确认方案无主动范围变更。真实合作方适配、容量、生产放量和负责人验收尚未完成，属于单独验收范围。Redis 不可恢复丢失时，不能保证恢复全部已受理请求及未落库数据；需监测队列积压、回调超时、死信、任务失败和统计延迟。

回退按切换说明关闭新受理、处理既有请求与待落库数据，再停止进程。不得使用旧状态放行新请求，不删除新旧历史表。下一步为目标版本数据库预演和合作方联调。验收人、业务结论及生产发布日期待负责人记录。
