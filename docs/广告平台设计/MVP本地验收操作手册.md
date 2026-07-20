# SCX MVP 本地启动与验收操作手册

## 适用范围

本手册只用于本机 Compose 环境。后端使用 `local` profile，连接 `127.0.0.1` 的 MySQL、Redis、RocketMQ 和 callback mock；不读取 `application-dev.yml` 的远程数据源，不连接线上数据库或线上 Redis。

## 1. 前置条件

- JDK 17 或更高版本
- Node.js 20 或更高版本、pnpm 10
- Docker Desktop 已启动
- 已在仓库根目录执行过 `pnpm install --frozen-lockfile`（首次使用前端时）

确认当前分支含 V29：

```bash
git branch --show-current
ls scx-backend/scx-boot/src/main/resources/db/migration/V29__reconcile_v28_schema_gap.sql
```

推荐分支为 `codex/scx-mvp-complete`。

## 2. 启动本地依赖与数据库

在仓库根目录执行：

```bash
make mvp-local-up
```

该命令会启动本机 MySQL、Redis、RocketMQ、MinIO、callback mock，并把本机 `smart_converge_x` 升级至当前迁移版本。它会写入本地 Docker 卷，但不会访问线上数据库。

确认数据库结构：

```bash
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USERNAME=root MYSQL_PASSWORD=scx_local_only make db-verify
```

预期输出包含：

```text
Smart Converge X database verification passed
```

## 3. 启动后端

新开一个终端：

```bash
cd scx-backend
mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=local
```

后端默认地址：`http://127.0.0.1:9999/boot`。

健康检查：

```bash
curl http://127.0.0.1:9999/boot/actuator/health
```

预期返回 `UP`。若 9999 已被占用，可临时使用：

```bash
mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=10099
```

此时后续前端命令需设置 `VITE_API_PROXY_TARGET=http://127.0.0.1:10099`。

## 4. 启动前端

再新开一个终端：

```bash
cd scx-fronted
pnpm dev:ele
```

浏览器打开：`http://127.0.0.1:5777`。

本地默认管理员：

- 用户名：`system`
- 密码：`123456`

仅用于本地 Compose 库；不要把该账号密码用于任何非本机环境。

## 5. 浏览器验收清单

登录后至少检查以下页面均能正常加载且非空白：

1. 上游客户、产品管理、媒体渠道、接入文档、渠道用户。
2. 追踪链接、短链与 DP 工具、监测任务、任务授权。
3. 转化审核、回传任务与领域事件发件箱、RTA 配置、平台配置。
4. 效果概览、日报、数据汇总、链接/产品/任务/媒体汇总、导出任务。

`make mvp-local-up` 会在 V30 自动创建 `sandbox`/`demo` 前缀的可识别基础数据：合作方、客户、产品、文档、渠道、渠道用户、链接、任务、事件映射、RTA 配置、事件/归因、回传、Outbox 和日报。V30 可重复执行，只更新这些固定演示 ID；不要手工把该迁移复制到非受控业务库。

## 6. 自动化验收

前端质量门禁：

```bash
cd scx-fronted
pnpm check:type
pnpm lint
pnpm test:unit
pnpm build:ele
```

后端与契约门禁：

```bash
make backend-test
make contract-check
make migration-packaging-check
make prod-secret-injection-check
```

运行本地浏览器 E2E（后端在 9999 时）：

```bash
cd scx-fronted
E2E_API_TARGET=http://127.0.0.1:9999 \
E2E_EVENT_API_BASE=http://127.0.0.1:9999 \
E2E_CALLBACK_MOCK_BASE=http://127.0.0.1:8089 \
E2E_EVENT_SIGNING_KEY=scx-local-only-signing-key \
E2E_USERNAME=system E2E_PASSWORD=123456 \
pnpm -F @boot/web-ele exec playwright test
```

Outbox 故障恢复用例需要临时停止并恢复**本机** RocketMQ broker；请按 [M6 可观测性与故障演练](runbooks/M6-可观测性与故障演练.md) 操作，不要对线上 broker 做该演练。

## 7. 常见问题

| 现象 | 处理方式 |
|---|---|
| `Migration history mismatch` | 确认在本机 Compose 库运行 `make mvp-db-migrate`，不要修改已登记迁移文件；当前迁移器已修复 Tab 解析。 |
| 数据库校验失败 | 先运行 `make mvp-local-up`，再执行本机 `db-verify`。不要对线上库使用本地初始化命令。 |
| 前端登录或接口 502 | 确认后端健康检查为 `UP`，并确认前端代理端口与后端一致。 |
| 回传或 Outbox 未完成 | 检查本机 RocketMQ 与 callback mock 容器运行状态；只使用 sandbox 目标。 |
| 页面没有演示数据 | 仅在可丢弃的本机库执行第 5 节 V25 演示种子。 |

## 8. 停止与清理

停止后端和前端可直接在各自终端按 `Ctrl+C`。

停止本地依赖但保留数据库：

```bash
make dev-down
```

`make dev-reset` 会删除本地 Docker 卷和全部本地数据；只有确认本机数据可丢弃时才执行，禁止用于线上环境。
