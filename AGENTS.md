# Smart Converge X（SCX）Agent 工程指南

> 本文件面向不熟悉本项目的 AI 编程 Agent。阅读前默认你已了解：这是一个**单企业效果广告投放与归因管理平台**（中文名：瑶知智汇广告平台），仓库同时包含 Java 单体后端与 Vue 3 管理前台。

---

## 1. 项目概述

- **项目代号**：`Smart Converge X`，简称 `SCX`。
- **产品定位**：面向单一企业内部业务线的广告投放、流量分发、事件归因、报表、财务对账与风控平台。
- **当前阶段**：M0 研发基线阶段，首个可运行的业务纵向切片是**广告主体（subject）/ 媒体主体只读能力**。
- **权威文档入口**：`docs/广告平台设计/README.md`，其中规定了阅读顺序、文档权威级别（L0 基线 / L1 规范 / L2 执行 / L3 模板）以及 Agent 迭代治理规则。
- **工程组织**：
  - `scx-backend/`：Java 单体后端，Maven 多模块。
  - `scx-fronted/`：Vue 3 管理后台，pnpm workspace + Turborepo 单仓库。
  - `docs/`：研发文档。当前 `docs/广告平台设计/` 下实际存在 `README.md`、`MVP开发规划.md`、`竞品调研-ocpx.aerim.cn.md`、`2026-07-15-ocpx-aerim-功能审计报告.md` 与 `templates/`；契约目录 `contracts/`、数据字典目录 `data-dictionary/`、`adr/` 等尚未填充。
  - `scripts/check-contracts.sh`：预留的 OpenAPI / AsyncAPI / JSON Schema 契约校验脚本，待契约目录补齐后启用。
  - `.boot-admin/`：项目模板初始化器，用于从本模板派生新项目，不参与运行时。

---

## 2. 技术栈与运行时架构

### 2.1 后端

| 层级 | 选型 | 说明 |
| --- | --- | --- |
| 语言 / JVM | Java 17 | JDK 17+，Temurin 推荐。 |
| 框架 | Spring Boot 3.5.9 | 单体启动入口在 `scx-boot`。 |
| ORM | MyBatis-Plus 3.5.15 | 实体用 `@TableName` + `@TableId(type = ASSIGN_ID)`。 |
| 权限 | Sa-Token 1.45.0 | 会话 + 权限码校验，配合 `scx-common-security`。 |
| 数据源 | dynamic-datasource + Druid | 主库 `master`，开发环境用 Druid 连接池。 |
| 缓存 / 锁 | Redis 7 + Redisson | 会话、验证码、预算/频控等。 |
| 消息队列 | Apache RocketMQ 5.x | 事件异步处理。 |
| 调度 | XXL-JOB 3.4.1 | 调度中心表在 `20xxl_job_schema.sql`。 |
| 监控 | Spring Boot Admin 3.5.6 | 位于 `scx-visual/scx-monitor`。 |
| 接口文档 | Knife4j / springdoc-openapi | 启动后访问 `http://localhost:9999/boot/swagger-ui.html`。 |
| 容器 | Undertow | `scx-boot` 排除 Tomcat，使用 Undertow。 |
| 其他常用库 | Lombok、Hutool、Fastjson2、Apache POI、阿里云 OSS / MinIO / 七牛 / 腾讯云 COS | 位于 `scx-common` 各子模块。 |

### 2.2 前端

| 层级 | 选型 | 说明 |
| --- | --- | --- |
| 框架 | Vue 3.5 + TypeScript 5.9 | Composition API。 |
| 构建 | Vite 6.4 | 应用配置在 `apps/web-ele/vite.config.mts` 等。 |
| 包管理 | pnpm 10.12.4 | 固定版本，`packageManager` 字段已声明。 |
| 仓库管理 | Turborepo 2.10 | 任务缓存配置见 `turbo.json`。 |
| UI 库 | Element Plus（默认）、Ant Design Vue、Naive UI | 三个应用变体，默认构建目标是 `web-ele`。 |
| 状态 | Pinia 3 + pinia-plugin-persistedstate | 用户、偏好设置等。 |
| 路由 | Vue Router 4.6 | 菜单/权限驱动路由。 |
| 样式 | TailwindCSS 3.4 + SCSS | `internal/tailwind-config` 提供共享配置。 |
| 测试 | Vitest 3.2 + happy-dom + @vue/test-utils | 单元测试入口 `pnpm test:unit`。 |
| E2E | Playwright（脚手架） | 当前未接入主 CI。 |

### 2.3 运行时拓扑

```text
┌─────────────────┐      /api proxy       ┌────────────────────────────┐
│  scx-fronted    │ ─────────────────────▶ │ scx-boot (port 9999, /boot)│
│  (nginx / Vite) │                        │  - Sa-Token session        │
└─────────────────┘                        │  - MyBatis-Plus + MySQL    │
                                           │  - Redis cache/lock        │
                                           │  - RocketMQ producer       │
                                           └────────────────────────────┘
                                                    │       │       │
                                                   MySQL  Redis  RocketMQ
```

- 后端默认监听 `9999`，Servlet context-path 为 `/boot`。
- 健康检查：`GET http://localhost:9999/boot/actuator/health`。
- 前端开发服务器将 `/api` 代理到 `http://localhost:9999`，生产 Nginx 配置位于 `scx-fronted/scripts/deploy/scx-fronted.conf`。

---

## 3. 环境要求

- **JDK**：17+
- **Maven**：3.9+
- **Node.js**：20.10.0+
- **pnpm**：10.12.4（建议 `corepack enable` 自动使用）
- **外部基础设施**：MySQL 8.x、Redis 7.x、RocketMQ 5.x（开发与线上使用同一套外部服务）
- **MySQL**：8.x
- **Redis**：7.x
- **RocketMQ**：5.x

---

## 4. 快速启动与常用命令

### 4.1 配置外部依赖服务

```bash
cp .env.example .env.local
set -a; source .env.local; set +a
make db-migrate
make db-verify
```

> MySQL、Redis、RocketMQ 由环境外部管理；仓库只负责应用启动和数据库前向迁移。

### 4.2 后端

```bash
# 全量编译并打包（跳过测试）
make backend-build

# 运行测试
make backend-test

# 手动在 scx-backend 目录执行
mvn -pl scx-boot -am -DskipTests package
java -jar scx-boot/target/scx-boot.jar

# 仅编译验证
mvn -pl scx-boot -am -DskipTests compile
```

### 4.3 前端

```bash
cd scx-fronted
corepack enable
pnpm install --frozen-lockfile

# 本地开发（推荐 Element Plus 版本）
pnpm dev:ele

# 其他变体
pnpm dev:antd
pnpm dev:naive

# 构建
pnpm build:ele          # 生产构建

# 代码质量
pnpm check:type         # TypeScript 类型检查
pnpm lint               # ESLint
pnpm test:unit          # Vitest 单元测试
pnpm check              # 全量：循环依赖、依赖、类型、cspell
```

### 4.4 数据库初始化与校验

```bash
# 初始化或升级（需要可访问外部 MySQL 和本地 MySQL 客户端）
make db-migrate

# 仅校验已有库
MYSQL_HOST=... MYSQL_USERNAME=... MYSQL_PASSWORD=... ./verify-database.sh

# Makefile 入口
make db-verify
```

### 4.5 契约校验（当前不可用）

```bash
make contract-check
# 等价于：bash ./scripts/check-contracts.sh
```

> **注意**：`scripts/check-contracts.sh` 的设计目标是校验 `docs/广告平台设计/contracts/` 下的 YAML/JSON 契约文件，但目前该目录在仓库中不存在，直接运行会失败。待契约文件补齐后即可启用。

---

## 5. 代码组织与模块划分

### 5.1 后端（`scx-backend/`）

Maven 聚合坐标：`com.scx:scx:2.1.2`，默认 profile `boot`。Java 包根：`com.yzytech.scx`。

| 模块 | 说明 |
| --- | --- |
| `scx-boot` | 单体应用启动入口，`ScxApplication` 位于 `com.yzytech.scx`。 |
| `scx-auth` | 登录、验证码、Sa-Token 认证。 |
| `scx-system` | 用户、角色、菜单、部门、字典、日志；拆分为 `scx-system-api` + `scx-system-biz`。 |
| `scx-common` | 公共能力子模块群：`core`、`security`、`mybatis`、`redis`、`swagger`、`storage`、`excel`、`log`、`job`、`websocket`、`dubbo`、`sentinel`、`seata`、`sms`、`idempotent`、`datasource`、`logistics`。 |
| `scx-pay` | 通用支付配置、交易、退款、回调；拆分为 `scx-pay-api` + `scx-pay-biz`。 |
| `scx-subject` | 首个业务切片：媒体主体与对象级授权（`scx_media`、`scx_user_subject_scope`）。 |
| `scx-visual` | `scx-generator` 代码生成 + `scx-monitor` Spring Boot Admin。 |

典型包结构（以 `scx-subject` 为例）：

```text
com.yzytech.scx.subject
├── controller/      # @RestController + @SaCheckPermission + Swagger
├── service/         # @Service，只读/写操作可分离（如 MediaReadService）
├── mapper/          # MyBatis-Plus BaseMapper + 自定义 XML
├── entity/          # @Data + @TableName
└── resources/mapper/*.xml
```

数据库脚本约定：

- 基线：`scx-backend/db/smart-converge-x/00schema.sql`、`10smart_converge_x_core.sql`、`20xxl_job_schema.sql`。
- 业务增量迁移：`scx-backend/scx-boot/src/main/resources/db/migration/V{序号}__*.sql`，当前由 `init-database.sh` 按文件名排序应用。

### 5.2 前端（`scx-fronted/`）

```text
scx-fronted/
├── apps/
│   ├── web-ele/        # Element Plus 管理后台（默认）
│   ├── web-antd/       # Ant Design Vue 变体
│   └── web-naive/      # Naive UI 变体
├── packages/
│   ├── @core/          # base / ui-kit / composables / preferences
│   ├── effects/        # access / common-ui / hooks / layouts / plugins / request
│   ├── constants/      # 常量
│   ├── icons/          # 图标
│   ├── locales/        # i18n
│   ├── preferences/    # 用户偏好
│   ├── stores/         # Pinia store
│   ├── styles/         # 共享样式
│   ├── types/          # 共享类型
│   └── utils/          # 工具函数
├── internal/
│   ├── lint-configs/   # @boot/eslint-config / prettier / stylelint / commitlint
│   ├── node-utils/     # 仓库脚本工具
│   ├── tailwind-config/# 共享 Tailwind 配置
│   ├── tsconfig/       # 共享 tsconfig
│   ├── vite-config/    # 共享 Vite 配置
│   ├── turbo-run/      # turbo 运行脚本
│   └── vsh/            # 项目级 CLI（lint / publint / code-workspace）
└── scripts/deploy/     # 前端静态构建与 Nginx 部署配置
```

Workspace 命名：业务包使用 `@boot/*`，核心基础包使用 `@boot-core/*`，与目录 `scx-fronted` 不冲突。

---

## 6. 代码风格与研发规范

### 6.1 Java 后端

- **格式化**：根 `pom.xml` 引入 `io.spring.javaformat:spring-javaformat-maven-plugin`。
- **实体**：使用 Lombok `@Data`，继承 `Model<T>`，主键用 `@TableId(type = IdType.ASSIGN_ID)`，表名用 `@TableName("scx_xxx")`。
- **接口返回**：Controller 统一返回 `com.yzytech.scx.common.core.util.Result<T>`。
- **权限**：Controller 方法加 `@SaCheckPermission("module:resource:action")`。
- **依赖注入**：推荐构造函数注入 + `@RequiredArgsConstructor`。
- **安全上下文**：通过 `com.yzytech.scx.common.security.util.SecurityUtils.getUser()` 获取当前用户。
- **日志 / 调试**：`application.yml` 中开启 `com.yzytech.scx.system.mapper` 与 `generator.mapper` 的 DEBUG 日志。

### 6.2 TypeScript / Vue 前端

- **Prettier**：`singleQuote: true`、`trailingComma: 'all'`、`printWidth: 80`、启用 `prettier-plugin-tailwindcss`。
- **ESLint**：`@boot/eslint-config`（基于 `@eslint/js`、typescript-eslint、vue、perfectionist、import-x、unicorn、regexp、vitest 等）。
- **Stylelint**：`stylelint-config-standard` + `stylelint-config-recess-order` + Tailwind/SCSS/Vue 规则。
- **Git Hook**：`lefthook.yml` 在 pre-commit 阶段自动对 staged 文件运行 Prettier、ESLint、Stylelint；post-merge 自动 `pnpm install`。
- **提交规范**：使用 `czg`（cz-git）+ `@boot/commitlint-config`，type 枚举：`feat/fix/perf/style/docs/test/refactor/build/ci/chore/revert/types/release`。

### 6.3 通用约定

- 新增功能应尽量关联 `docs/广告平台设计/MVP开发规划.md` 中的任务，并在需求明确后补充测试。
- 重大架构/产品变更需先写 ADR 或 RFC，评审后落地。ADR 目录：`docs/广告平台设计/adr/`（当前为空）。
- 不要在代码中直接提交生产密码、API 密钥、支付证书、OSS 密钥。

---

## 7. 测试策略

### 7.1 后端

- **单元测试**：JUnit 5 + Mockito + AssertJ，位于各模块 `src/test/java`。
- **示例**：`scx-subject/src/test/java/.../MediaControllerTest.java`、`MediaReadServiceTest.java`。
- **运行**：`make backend-test` 或 `cd scx-backend && mvn -pl scx-boot -am test`。
- **测试原则**：
  - 服务层优先测权限分支（管理员 vs 普通用户）。
  - Controller 测未授权/不存在场景时返回统一失败结果，避免泄露对象存在性。
  - 对静态工具类使用 `Mockito.mockStatic`。

### 7.2 前端

- **单元测试**：Vitest + happy-dom，配置 `vitest.config.ts`。
- **运行**：`pnpm test:unit`。
- **类型检查**：`pnpm check:type`。
- **E2E**：Playwright 已作为 devDependency，但当前未接入主 CI；需要时自行补充 `test:e2e`。

### 7.3 契约与数据库

- **契约校验**：`make contract-check` 的目标目录 `docs/广告平台设计/contracts/` 目前不存在，待补齐后启用。
- **数据库校验**：`make db-verify` 校验必需表、`system` 管理员角色映射、主体权限码是否存在。

---

## 8. 部署说明

### 8.1 后端部署

- 使用 Maven 构建 `scx-boot` JAR，并通过 `--spring.profiles.active=prod` 启动。
- MySQL、Redis、RocketMQ 地址和凭据由部署环境注入，应用默认监听 `9999`。

### 8.2 前端部署

- 构建产物：`pnpm build:ele` 输出 `apps/web-ele/dist`，由外部 Nginx 或静态文件服务托管。
- Nginx 配置：`scx-fronted/scripts/deploy/scx-fronted.conf`：
  - `/api` 反向代理到后端 `9999` 端口。
  - 直接返回 `403` 阻止 `/actuator`。
  - 添加 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`CSP` 等安全响应头。

### 8.3 生产部署注意事项

- 生产数据库密码、Redis 密码、RocketMQ 地址、支付密钥/证书、OSS 密钥等**必须**通过环境变量或配置中心注入。
- 后端可覆盖的环境变量示例：
  - `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL`
  - `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME`
  - `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD`
  - `SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PASSWORD`
  - `ROCKETMQ_NAME_SERVER`
- 首次登录后必须修改默认管理员密码（默认账号 `system` / `123456`）。

---

## 9. 安全与合规

- **认证授权**：Sa-Token 会话 + 菜单/按钮权限码；`scx-subject` 已实现对象级授权（`scx_user_subject_scope`）。
- **机器接口签名**：`api.sign.enabled` 默认为 `false`，规划中的事件接入等机器接口后续需启用签名，**禁止将签名密钥下发到浏览器**。
- **敏感配置**：`.gitleaks.toml` 配置了默认规则与允许列表；CI 使用 `gitleaks/gitleaks-action@v2` 扫描密钥泄露。
- **开发配置风险**：`application-dev.yml` 与 `application-prod.yml` 均只读取环境变量；真实凭据只允许存放在 Git 外部的 `.env.local` 或部署平台密钥管理中。
- **数据脱敏**：`scx-common-core` 提供 `@Desensitization` 及手机号、身份证、姓名、密码脱敏实现。
- **默认数据**：数据库脚本不再携带真实支付证书、线上数据源、小程序配置等敏感种子。

---

## 10. Agent 必读与工作流程

1. **先读文档再写代码**：
   - 必读：`docs/广告平台设计/README.md`、`docs/广告平台设计/MVP开发规划.md`。
   - 后续按 `README.md` 中规划的阅读顺序补齐其他专题文档（目前多数尚未创建）。
2. **不能仅凭任务标题生成代码**；实现状态以真实测试证据与现有代码为准，不要把规划能力描述为已交付。
3. **提交前运行**：
   - `make backend-test`
   - `cd scx-fronted && pnpm check:type && pnpm lint && pnpm test:unit`
   - `make contract-check`（当前因契约目录缺失会失败，补齐后再纳入门禁）
4. **变更粒度**：一个提交/PR 围绕一个明确的 MVP 任务；新增功能补测试；重大变更先写 ADR。
5. **不要做的事**：
   - 不要提交密码、密钥、证书、生产数据库地址。
   - 不要把规划能力描述为已交付。
   - 不要修改无关的代码风格或进行大范围重命名。

---

## 11. 关键文件速查

| 用途 | 路径 |
| --- | --- |
| 根 Makefile | `Makefile` |
| 后端主 POM | `scx-backend/pom.xml` |
| 后端启动模块 | `scx-backend/scx-boot/pom.xml` |
| 后端主配置 | `scx-backend/scx-boot/src/main/resources/application.yml` |
| 后端开发配置 | `scx-backend/scx-boot/src/main/resources/application-dev.yml` |
| 后端启动/部署 | `Makefile`、`scx-backend/scx-boot/src/main/resources/application-*.yml` |
| 数据库基线脚本 | `scx-backend/db/smart-converge-x/` |
| 业务增量 SQL | `scx-backend/scx-boot/src/main/resources/db/migration/` |
| 前端根 package | `scx-fronted/package.json` |
| 前端 workspace | `scx-fronted/pnpm-workspace.yaml` |
| 前端 Turborepo | `scx-fronted/turbo.json` |
| 前端默认应用 | `scx-fronted/apps/web-ele/` |
| 前端 ESLint | `scx-fronted/eslint.config.mjs` → `@boot/eslint-config` |
| 前端 Prettier | `scx-fronted/.prettierrc.mjs` → `@boot/prettier-config` |
| 前端 Stylelint | `scx-fronted/stylelint.config.mjs` → `@boot/stylelint-config` |
| 前端 Nginx | `scx-fronted/scripts/deploy/scx-fronted.conf` |
| 契约校验脚本 | `scripts/check-contracts.sh` |
| 密钥扫描配置 | `.gitleaks.toml` |
