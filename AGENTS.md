# Smart Converge X（SCX）Agent 工程指南

> 本文件面向不熟悉本项目的 AI 编程 Agent。阅读前默认你已了解：这是一个**单企业效果广告投放与归因管理平台**（中文名：瑶知智汇广告平台），仓库同时包含 Java 单体后端与 Vue 3 管理前台。

---

## 1. 项目概述

- **项目代号**：`Smart Converge X`，简称 `SCX`。
- **产品定位**：面向单一企业内部业务线的精简广告服务管理平台，功能面收敛为媒体（渠道）、广告主、推广链接、监测任务、扣量规则、充值/余额（SGYZ 对齐极简版：Redis 单值余额，无账务流水表，不含点击计费/CPC）、报表中心与权限管理；汽车线索域已退出。
- **当前阶段**：OCPX 重构基线；原商业、财务/支付、媒体资源、创意、投放、监测归因、旧报表和运营中心已退出构建。
- **权威文档入口**：`docs/广告平台设计/README.md`，其中规定了阅读顺序、文档权威级别（L0 基线 / L1 规范 / L2 执行 / L3 模板）以及 Agent 迭代治理规则。
- **工程组织**：
  - `scx-backend/`：Java 单体后端，Maven 多模块。
  - `scx-fronted/`：Vue 3 管理后台，pnpm workspace + Turborepo 单仓库。
  - `docs/`：研发文档。当前 `docs/广告平台设计/` 下实际存在 `README.md`、`MVP开发规划.md`、`竞品调研-ocpx.aerim.cn.md`、`2026-07-15-ocpx-aerim-功能审计报告.md` 与 `templates/`；契约目录 `contracts/`、数据字典目录 `data-dictionary/`、`adr/` 等尚未填充。
  - `tools/quality/check-contracts.sh`：OpenAPI / AsyncAPI / JSON Schema 契约校验脚本。
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

应用连接信息写在 profile 配置文件中（**直写，无 env 占位**），见 **§6.4**：

- 开发：`scx-backend/scx-boot/src/main/resources/application-dev.yml`
- 生产：`scx-backend/scx-boot/src/main/resources/application-prod.yml`
- XXL-JOB：`xxl-job/xxl-job-admin/src/main/resources/application.properties`

数据库初始化（可选，需本机 mysql 客户端能连上 yml 中的库）：

```bash
# 若脚本仍读取 MYSQL_*，可临时 export 与 yml 一致的值后执行
make db-migrate
make db-verify
```

> MySQL、Redis、RocketMQ 由环境外部管理；**应用侧**通过 yml/properties 直连，不依赖 `/etc/scx/scx.env`。

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
# 等价于：bash ./tools/quality/check-contracts.sh
```

> `tools/quality/check-contracts.sh` 校验 `docs/广告平台设计/contracts/` 下的 YAML/JSON 契约文件。

---

## 5. 代码组织与模块划分

### 5.1 后端（`scx-backend/`）

Maven 聚合坐标：`com.scx:scx:2.1.2`，默认 profile `boot`。Java 包根：`com.yzytech.scx`。

| 模块 | 说明 |
| --- | --- |
| `scx-boot` | 单体应用启动入口，`ScxApplication` 位于 `com.yzytech.scx`。 |
| `scx-auth` | 登录、验证码、Sa-Token 认证。 |
| `scx-system` | 用户、角色、菜单、部门、字典、日志；拆分为 `scx-system-api` + `scx-system-biz`。 |
| `scx-common` | 公共能力子模块群：`core`、`security`、`mybatis`、`redis`、`swagger`、`storage`、`excel`、`log-buffer`、`log`、`job`、`websocket`、`dubbo`、`sentinel`、`seata`、`sms`、`idempotent`、`datasource`、`logistics`。`log-buffer` 仅提供进程内有界脱敏日志缓冲，不包含系统日志落库自动配置。 |
| `scx-ocpx-core` | OCPX 管理面与数据面共享的五个 MyBatis-Plus 实体（广告主、推广链接、媒体、监测任务、扣量规则）及最小契约；不包含 Spring 运行组件。 |
| `scx-ocpx-admin` | 精简广告管理业务：五实体 CRUD、扣量规则（写库后同步 Redis 扣量比例）、广告主充值/余额（Redis 单值累加，无流水表）、流程日志查询、Dashboard、时段报表与 CSV 导出。 |
| `scx-ocpx-engine` | 独立 OCPX 数据面应用；实现通用 Feedback/Callback GET 闭环，默认监听 `10000/ocpx`；另含小米（`/mi/feedback` 入站 + 签名回传）与 KIMI（`/kimi/callback` POST 入站 + 投递）adapterCode 适配、logback 分文件滚动日志、Feedback 日明细每日清理任务。 |
| `scx-visual` | `scx-generator` 代码生成 + `scx-monitor` Spring Boot Admin。 |

典型包结构（以 `scx-ocpx-admin` 为例）：

```text
com.yzytech.scx.ocpx.admin
├── controller/      # @RestController + @SaCheckPermission + Swagger
├── service/         # 渠道目录、账户与报表业务服务
├── mapper/          # MyBatis-Plus BaseMapper + 自定义 XML
└── resources/mapper/*.xml
```

OCPX 的五个 `@TableName` 实体统一位于 `scx-ocpx-core` 的
`com.yzytech.scx.ocpx.entity` 包（含 `OcpxDeductionRule`）；Admin 与 Engine 分别依赖 Core，Boot 不依赖 Engine。

数据库脚本约定：

- 空库入口：`scx-backend/db/smart-converge-x/smart_converge_x_baseline.sql`，其中统一加载系统、XXL-JOB 与 `db/schema/scx_current_schema.sql` 当前态结构。
- `init-database.sh` 只允许空库初始化，禁止扫描或自动执行 `db/migration`。
- 未来已有库变更必须新增一份前向增量 SQL，并在同一变更中同步 `db/schema/scx_current_schema.sql`、`schema-required-tables.sh`、校验脚本和契约测试。
- 已执行的增量 SQL 不得修改或重放；生产应用前必须备份、预览并取得明确授权。
- `scx_schema_migration` 迁移账本已于 2026-08-11 明确退役。当前态 schema 和未来新增 SQL 均不得创建、查询、写入、更新、删除或以任何方式依赖该账本；版本化文件名只用于代码审查和发布包识别。
- 已有环境若仍保留该历史表，只允许把它视为可选遗留对象；不得用其内容判断结构版本，也不得因本规则自动删除。删除存量表必须另行备份、预览并取得明确生产授权。
- 已有库发布证据改为：目标身份、变更前备份、Git 提交、SQL 文件 SHA-256、人工审批/执行记录、`information_schema` 结构后置条件、关键数据对账和 `verify-database.sh` 结果。提交前运行 `make schema-ledger-check`，阻止后续业务 SQL 重新引用退役账本。

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
- 仓库中的示例/默认凭据仅用于本机联调；真实生产密钥由负责人在部署机 yml 中维护，避免无必要地扩大分发面。

### 6.4 配置规范（Agent 强制遵守）

> **现行规范（2026-07 起）：应用运行时配置采用「配置文件直写」，不使用 Spring 环境变量占位符，也不依赖 `/etc/scx/scx.env` 等 env 文件注入。**

#### 必须遵守

1. **禁止**在下列文件中使用 `${SCX_*}`、`${ROCKETMQ_*}`、`${XXL_JOB_*}`、`${XXL_SSO_*}` 等**运行时环境变量占位符**（含 `${VAR:default}` 形式）：
   - `scx-backend/scx-boot/src/main/resources/application-dev.yml`
   - `scx-backend/scx-boot/src/main/resources/application-prod.yml`
   - `xxl-job/xxl-job-admin/src/main/resources/application.properties`
   - `xxl-job/**/executor*/**/application.properties` 与 `xxl-job-executor.properties`（样例执行器）
2. **禁止**新增「生产启动必须读取 `/etc/scx/scx.env` 或 `EnvironmentFile` 才有配置」的路径；宝塔 / `java -jar` 必须能只靠 profile + yml/properties 启动。
3. **禁止**恢复或新增类似 `ProdRequiredConfigurationEnvironmentPostProcessor` 的「缺少 SCX_* 环境变量就 fail-closed」逻辑（该类应保持 no-op；勿再强制 env）。
4. 新增配置项时：**直接写在对应 profile 的 yml / properties 中**，使用字面量（host、url、password、token 等）。
5. 建议在 yml 中保留 `spring.config.fail-on-unresolved-placeholders: false`，避免历史占位残留阻断启动。
6. 修改配置后须**重新打包**（配置打进 jar），再重启进程；仅改源文件不打包不生效。

#### 允许的例外（不要扩大范围）

- **Maven / 构建期**属性：`pom.xml` 中的 `${project.version}`、`${spring-boot.version}` 等。
- **Logback 内部**路径变量：`${log.path}`、`${LOG_HOME:-...}`（日志框架惯例，不是业务密钥注入）。
- **Docker Compose / 外部编排**样例中的 `${MYSQL_PATH}` 等（仅 docker 文档/样例；不作为 scx-boot / xxl-job-admin 的启动必选）。
- **Nacos 独立组件**配置（`scx-backend/db/nacos/`）若仍使用占位，不自动套用到 scx-boot；除非任务明确要求改 Nacos。

#### 部署与宝塔

- 线上标准：**宝塔 + bash**（`scripts/bt-deploy/00` / `01` / `03`）。
- 代码目录默认：`/opt/smart-converge-x`；前端站点根：`/www/wwwroot/scx`。
- 改库连接 / Redis / 密钥：编辑仓库内 yml 或服务器上同源路径 → `bash scripts/bt-deploy/01-deploy.sh --no-pull backend`（或全量 `03`）→ 宝塔重启 Java 项目。
- scx-boot 与 xxl-job 的 `accessToken` 等关联项必须在**各自配置文件中字面量一致**。

#### Agent 自检清单（改配置相关 PR 前）

```text
[ ] application-dev.yml / application-prod.yml 无 ${SCX_*} / ${ROCKETMQ_*} 业务占位
[ ] xxl-job-admin application.properties 无 ${XXL_JOB_*} / ${XXL_SSO_*} 业务占位
[ ] 未重新引入「缺少 env 则拒绝启动」的 EnvironmentPostProcessor 强制逻辑
[ ] 未把启动流程改回「必须 source /etc/scx/scx.env」
[ ] 新增配置项已写入对应 yml/properties 字面量，并说明需重新打包
```

---

## 7. 测试策略

### 7.1 后端

- **单元测试**：JUnit 5 + Mockito + AssertJ，位于各模块 `src/test/java`。
- **示例**：`scx-ocpx-admin/src/test/java/.../OcpxAdvertiserServiceTest.java`、`OcpxDeductionRuleServiceTest.java`、`OcpxUnifiedReportServiceTest.java`。
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

- 使用 Maven / `scripts/bt-deploy` 构建 `scx-boot` JAR，并由宝塔 Java 项目管理器以 prod profile 启动。
- **连接与密钥写在 `application-prod.yml`（及 xxl-job `application.properties`）**，打包进 jar；**不要**再设计为必须 env 注入（见 **§6.4**）。
- 应用默认监听 `9999`，context-path `/boot`。
- 宝塔：Java 项目配置 JAR 为 `.../build/backend/scx-boot.jar`，由宝塔 Java 项目管理器重启。

### 8.2 前端部署

- 构建产物：`pnpm build:ele` 或 `scripts/bt-deploy/01-deploy.sh` 同步到宝塔站点根（默认 `/www/wwwroot/scx`）。
- 站点 / SSL / 伪静态由**宝塔**管理；伪静态模板：`scripts/bt-deploy/templates/nginx-scx-baota-rewrite.conf`。

### 8.3 生产部署注意事项

- 修改 `application-prod.yml` / xxl-job properties 后必须**重新打包并重启**。
- 配置文件中的密码会进入 jar：限制仓库与服务器访问权限；勿把含真实生产密钥的 diff 发到公开渠道。
- 首次登录后必须修改默认管理员密码（默认账号 `system` / `123456`）。
- 日常发布：`scripts/bt-deploy/00-sync_repos.sh`、`03-deploy_all.sh`，然后在宝塔 Java 项目管理器重启。

---

## 9. 安全与合规

- **认证授权**：Sa-Token 会话 + 菜单/按钮权限码；OCPX 使用 `ocpx:*` 权限组并复用 `scx-system` 的账号和角色管理。
- **机器接口签名**：`api.sign.enabled` 默认为 `false`，规划中的事件接入等机器接口后续需启用签名，**禁止将签名密钥下发到浏览器**。
- **敏感配置**：`.gitleaks.toml` 配置了默认规则与允许列表；CI 使用 `gitleaks/gitleaks-action@v2` 扫描密钥泄露。
- **配置存放方式**：业务运行配置**直写**在 `application-*.yml` / xxl-job `application.properties`（见 **§6.4**）；不要把「必须 env 注入」当成新规范重新引入。
- **数据脱敏**：`scx-common-core` 提供 `@Desensitization` 及手机号、身份证、姓名、密码脱敏实现。
- **默认数据**：数据库脚本不再携带真实支付证书、线上数据源、小程序配置等敏感种子。

---

## 10. Agent 必读与工作流程

1. **先读文档再写代码**：
   - 必读：本文件 **§6.4 配置规范**、`docs/广告平台设计/README.md`、`docs/广告平台设计/MVP开发规划.md`。
   - 后续按 `README.md` 中规划的阅读顺序补齐其他专题文档。
2. **不能仅凭任务标题生成代码**；实现状态以真实测试证据与现有代码为准，不要把规划能力描述为已交付。
3. **提交前运行**：
   - `make backend-test`
   - `cd scx-fronted && pnpm check:type && pnpm lint && pnpm test:unit`
   - `make contract-check`（契约目录补齐后纳入门禁）
4. **变更粒度**：一个提交/PR 围绕一个明确的 MVP 任务；新增功能补测试；重大变更先写 ADR。
5. **不要做的事**：
   - **不要**在 scx-boot / xxl-job 运行配置中重新引入 `${ENV}` 占位或强制 `/etc/scx/scx.env`。
   - 不要把规划能力描述为已交付。
   - 不要修改无关的代码风格或进行大范围重命名。
   - 不要把签名密钥下发到浏览器。

---

## 11. 关键文件速查

| 用途 | 路径 |
| --- | --- |
| 根 Makefile | `Makefile` |
| 后端主 POM | `scx-backend/pom.xml` |
| 后端启动模块 | `scx-backend/scx-boot/pom.xml` |
| 后端主配置 | `scx-backend/scx-boot/src/main/resources/application.yml` |
| 后端开发配置（直写） | `scx-backend/scx-boot/src/main/resources/application-dev.yml` |
| 后端生产配置（直写） | `scx-backend/scx-boot/src/main/resources/application-prod.yml` |
| XXL-JOB 配置（直写） | `xxl-job/xxl-job-admin/src/main/resources/application.properties` |
| 宝塔运维脚本 | `scripts/bt-deploy/`（00 同步 / 01 打包 / 02 管理 / 03 一键 / 04 推送） |
| 配置规范（Agent） | 本文件 **§6.4** |
| 后端启动/部署 | `scripts/bt-deploy/`、宝塔 Java 项目管理器、`application-*.yml` |
| 数据库基线脚本 | `scx-backend/db/smart-converge-x/` |
| 当前态业务初始化 SQL | `scx-backend/scx-boot/src/main/resources/db/schema/scx_current_schema.sql` |
| 未来前向增量 SQL | `scx-backend/scx-boot/src/main/resources/db/migration/`（不自动执行） |
| 前端根 package | `scx-fronted/package.json` |
| 前端 workspace | `scx-fronted/pnpm-workspace.yaml` |
| 前端 Turborepo | `scx-fronted/turbo.json` |
| 前端默认应用 | `scx-fronted/apps/web-ele/` |
| 前端 ESLint | `scx-fronted/eslint.config.mjs` → `@boot/eslint-config` |
| 前端 Prettier | `scx-fronted/.prettierrc.mjs` → `@boot/prettier-config` |
| 前端 Stylelint | `scx-fronted/stylelint.config.mjs` → `@boot/stylelint-config` |
| 前端 Nginx | `scx-fronted/scripts/deploy/scx-fronted.conf` |
| 契约校验脚本 | `tools/quality/check-contracts.sh` |
| 密钥扫描配置 | `.gitleaks.toml` |
