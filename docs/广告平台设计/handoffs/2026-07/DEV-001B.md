# Agent 交接：DEV-001B

- 日期：2026-07-16
- Agent/负责人：Codex
- 状态：待验收
- 分支/提交/构建产物：当前工作区，未提交；`scx-backend/scx-boot/target/scx-boot.jar`

## 结果

- 已完成：修正主体前端 API 路径、登录角色编码、广告主/媒体对象读取响应语义；补齐直连 MySQL 初始化与只读校验；更新管理面 OpenAPI；直接对开发库应用 V1/V2 幂等迁移并验证。
- 未完成：未启动完整运行时做登录后的浏览器端到端验证；未处理仓库既有的前端 Stylelint 存量问题。
- 与原设计差异：按开发要求不使用 Docker；数据库迁移直接连接开发 MySQL 执行。未改变 ADR-0002 的技术栈或仓库结构。

## 变更清单

- 代码：前端固定请求 `/boot/subjects/advertisers` 与 `/boot/subjects/media`；登录用户角色集合从角色 ID 改为角色编码，权限查询仍使用角色 ID；主体详情无权访问或不存在时统一返回 HTTP 404。
- 数据库迁移：已备份主体表、主体菜单和角色菜单关系；已对开发库执行 `V1__subject_advertiser_read_model.sql`、`V2__subject_media_read_model.sql`；新增 `verify-database.sh`。
- API/消息契约：`management.openapi.yaml` 新增 4 个主体读取端点、Sa-Token 安全定义和响应模型。
- 配置/开关：未新增或提交凭据；`make db-verify` 改为只读验证，不再隐式初始化数据库。
- 文档：更新数据库直连初始化说明并新增本交接。

## 验证证据

| 环境 | 命令/操作 | 结果 | 报告/日志路径 | 时间 |
| --- | --- | --- | --- | --- |
| Java 17 | `make backend-test` | 通过，26 个 Reactor 模块成功 | Maven 控制台 | 2026-07-16 |
| Java 17 | 主体与登录角色定向测试 | 9 个测试通过，含对象范围和 404 语义 | Maven Surefire 控制台 | 2026-07-16 |
| Java 17 | `make backend-build` | 通过，后端 JAR 已生成 | `scx-backend/scx-boot/target/scx-boot.jar` | 2026-07-16 |
| Node.js | `pnpm check:type` | 通过 | Turbo/Vue TSC 控制台 | 2026-07-16 |
| Node.js | `pnpm test:unit` | 32 个测试文件、267 个测试通过 | Vitest 控制台 | 2026-07-16 |
| Node.js | `pnpm build:ele` | 通过，生产包已生成 | `scx-fronted/apps/web-ele/dist.zip` | 2026-07-16 |
| 契约 | `make contract-check` | OpenAPI、AsyncAPI、JSON Schema 全部通过语法校验 | 脚本控制台 | 2026-07-16 |
| 开发 MySQL | 直接执行 V1/V2 后运行 `verify-database.sh` | 通过；3 张主体表和 2 个管理员主体权限齐全 | 命令行校验结果 | 2026-07-16 |

## 风险、限制与回滚

- 已知限制：开发库当前广告主、媒体和用户主体范围数据均为空；需要录入验收数据后才能完成多角色端到端验证。仓库未建立 `flyway_schema_history`，当前开发配置仍禁用 Flyway，本次按现状直接执行幂等 SQL。
- 数据/资金影响：仅涉及主体表结构与菜单/角色权限，不涉及投放、预算、计费或结算数据。
- 回滚/前向修复：代码可按文件回退；数据库变更采用向前修复，不删除主体表。变更前 SQL 备份位于 `/tmp/scx-dev001b-20260716-RSIOQ9`。
- 后续任务：录入广告主、媒体和非管理员主体范围测试数据，启动应用并完成登录、菜单、列表、详情及越权访问验收；单独治理前端 Stylelint 存量。

## 验收

- 验收人：待用户确认
- 结论与日期：待验收
