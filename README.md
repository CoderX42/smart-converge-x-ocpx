# Smart Converge X

> 瑶知智汇广告平台（SCX）—— 单企业效果广告投放与归因管理平台。

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-42b883)](https://vuejs.org/)
[![MySQL 8.x](https://img.shields.io/badge/MySQL-8.x-orange)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey)]()

---

## 目录

- [项目概述](#项目概述)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
  - [环境要求](#环境要求)
  - [安装步骤](#安装步骤)
  - [配置说明](#配置说明)
  - [启动验证](#启动验证)
- [使用示例](#使用示例)
- [API 接口说明](#api-接口说明)
- [研发文档](#研发文档)
- [贡献指南](#贡献指南)
- [许可证](#许可证)
- [联系方式](#联系方式)

---

## 项目概述

**Smart Converge X（SCX，中文名：瑶知智汇广告平台）** 是面向单一企业及其内部业务线建设的广告投放与效果归因平台。平台连接广告主需求与媒体流量，覆盖投放配置、流量分发、事件采集、归因优化、报表、财务对账和风控等全链路能力。

**核心目标：**

- 在可控成本下提升广告主转化效果。
- 保证媒体、广告主与平台三方的数据可核验、可追溯。
- 为运营、财务、审核、风控等角色提供统一工作台。

**范围边界：**

- 一期以效果广告为主，支持 CPM/CPC/CPA/OCPX 投放、媒体事件接入、归因、报表、对账和结算。
- 二期加入 OpenRTB 实时竞价与自动出价能力。
- 首版不建设对外售卖的 SaaS 多租户能力，也不包含 PMP、保量和跨域数据合作。

---

## 核心功能

| 模块 | 关键能力 |
| --- | --- |
| 账户与组织 | 平台、广告主、代理商、媒体账户；角色、权限、资质与审计。 |
| 资金与合同 | 余额、授信、冻结、充值、合同、计费规则、账单、对账、结算、发票。 |
| 媒体与库存 | 媒体应用/站点、广告位、广告格式、尺寸、流量标签、底价和库存状态。 |
| 投放管理 | 活动、计划、单元、广告、创意、落地页、预算、出价、排期和状态流转。 |
| 定向与频控 | 地域、时段、设备、系统、网络、媒体、广告位、人群、黑白名单、频次。 |
| 素材审核 | 素材上传、病毒/尺寸检查、人工审核、驳回原因、版本和审核留痕。 |
| 事件与归因 | 展示、点击、激活、注册、下单、付费等事件；去重、归因窗口、归因模型。 |
| OCPX 优化 | 目标 CPA/ROI、冷启动、学习期、转化价值、自动出价建议、异常停投。 |
| 数据报表 | 实时监控、日结报表、漏斗、多维下钻、数据导出、数据延迟标识。 |
| 风控与质量 | 无效流量识别、设备/IP 异常、媒体质量评分、反作弊、品牌安全和申诉。 |
| 开放平台 | API 密钥、签名、IP 白名单、Webhook、回调重试、接口版本与沙箱。 |
| 运维治理 | 配置中心、灰度、限流、链路追踪、告警、重放、数据修复和灾备。 |

---

## 技术栈

| 层级 | 选型 | 说明 |
| --- | --- | --- |
| 后端 | Java 17 + Spring Boot 3.x + MyBatis-Plus | 单体管理面与业务服务。 |
| 前端 | TypeScript + Vue 3 | 后台管理 Web。 |
| OLTP 数据库 | MySQL 8.x | 业务主库。 |
| 缓存/计数 | Redis | 预算预占、频控、限流、会话。 |
| 事件总线 | Apache RocketMQ | 展示、点击、转化等事件异步处理。 |
| 分析库 | Apache Druid | 报表、漏斗、多维分析。 |
| 对象存储 | 阿里云 OSS | 素材、归档日志。 |
| 契约规范 | OpenAPI / AsyncAPI / JSON Schema | 接口与消息契约。 |
| 运行方式 | JVM + Node.js | 后端直接运行 JAR/Maven，前端直接运行 Vite；MySQL、Redis、RocketMQ 使用外部服务。 |

---

## 项目结构

```text
smart-converge-x/
├── scx-backend/                 # 单体后端（Java 17 / Spring Boot）
│   ├── scx-auth/                # 认证授权模块
│   ├── scx-system/              # 系统管理模块（用户、角色、菜单、部门、字典、日志）
│   ├── scx-common/              # 公共依赖与工具
│   ├── scx-subject/             # 广告主体与对象级授权（首个业务纵切片）
│   ├── scx-boot/                # 广告平台业务启动入口
│   └── db/smart-converge-x/      # 数据库初始化脚本
├── scx-fronted/                 # 后台管理 Web（Vue 3 / TypeScript）
│   ├── apps/                     # 应用入口
│   ├── packages/                 # 共享包（组件、请求、状态、工具等）
│   └── scripts/                  # 构建与部署脚本
├── docs/                         # 项目文档
│   ├── README.md                 # 文档中心入口
│   └── 广告平台设计/              # 研发设计文档、ADR、契约、数据字典
├── .github/workflows/            # CI 质量门
├── scripts/                      # 宝塔同步、打包、发布与运行态检查入口
├── tools/quality/                # 与部署无关的质量门脚本
└── README.md                     # 本文件
```

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+ / pnpm 8+
- MySQL 8.x
- Redis 7.x
- Apache RocketMQ 5.x
- 可访问的 MySQL 8.x、Redis 7.x、Apache RocketMQ 5.x 服务

### 安装步骤

#### 1. 准备连接变量

```bash
cp .env.example .env.local
set -a
source .env.local
set +a
```

开发环境使用 `dev` profile，连接变量由 `.env.local` 或部署平台注入；数据库迁移由 `scx-backend/db/smart-converge-x/init-database.sh` 执行。

#### 2. 初始化/升级数据库

```bash
make db-migrate
make db-verify
```

#### 3. 编译并启动后端

```bash
make backend-build
cd scx-backend
java -jar scx-boot/target/scx-boot.jar --spring.profiles.active=dev
```

#### 4. 安装并启动前端

```bash
cd scx-fronted
pnpm install
pnpm dev:ele
```

### 配置说明

后端核心配置文件位于：

```text
scx-backend/scx-boot/src/main/resources/application.yml
```

关键配置项：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `server.port` | 后端服务端口 | `9999` |
| `spring.datasource.url` | MySQL 连接地址 | 由 `SCX_DATASOURCE_URL` 注入 |
| `spring.datasource.username` | MySQL 用户名 | 由 `SCX_DATASOURCE_USERNAME` 注入 |
| `spring.datasource.password` | MySQL 密码 | 由 `SCX_DATASOURCE_PASSWORD` 注入 |
| `spring.data.redis.host` | Redis 地址 | 由 `SCX_REDIS_HOST` 注入 |
| `spring.data.redis.port` | Redis 端口 | `SCX_REDIS_PORT`（默认 `6379`） |
| `rocketmq.name-server` | RocketMQ NameServer | 由部署环境注入 |

生产环境请勿将密码、密钥、证书直接写入仓库，应通过环境变量或配置中心注入。

### 启动验证

#### 启动后端

```bash
cd scx-backend
java -jar scx-boot/target/scx-boot.jar
```

健康检查：

```bash
curl http://localhost:9999/boot/actuator/health
```

#### 启动前端

```bash
cd scx-fronted
pnpm dev:ele
```

默认访问地址：`http://localhost:3000`

---

## 使用示例

### 已交付的首个业务切片

1. 使用初始化脚本中的管理员登录后台。
2. 进入【广告主体 / 主体列表】查看主体。
3. 服务端会同时校验菜单权限与 `scx_user_subject_scope` 对象授权；非管理员只会得到已授权主体，详情接口对未授权对象返回统一失败结果。

> 详细操作步骤与接口样例见 [docs/广告平台设计/20-从零到一开发实施手册.md](docs/广告平台设计/20-从零到一开发实施手册.md)。

---

## API 接口说明

平台对外提供两类接口：

| 接口类型 | 说明 | 入口 |
| --- | --- | --- |
| 管理接口 | 供后台管理 Web 调用；当前已提供系统管理和广告主体只读能力。 | `scx-backend` 各模块 Controller |
| 事件接入接口 | 供媒体和广告主回传展示、点击、转化事件。 | `POST /v1/events`（规划中） |

### 接口认证

- 管理接口：Sa-Token 会话认证与服务端权限校验。
- 机器接口：尚未交付；API Key 和服务器到服务器的签名在 M1-05 实现，禁止将任何签名密钥交给浏览器。

### 接口文档

启动后端后，Swagger 文档地址：

```text
http://localhost:9999/boot/swagger-ui/index.html
```

完整 OpenAPI 契约草案见 [docs/广告平台设计/contracts/](docs/广告平台设计/contracts/)。

---

## 研发文档

完整的研发设计、架构决策、迭代计划位于：

```text
docs/广告平台设计/
```

必读入口：

- [00-愿景范围与文档治理](docs/广告平台设计/00-愿景范围与文档治理.md)
- [01-产品需求与范围](docs/广告平台设计/01-产品需求与范围.md)
- [02-业务规则与流程](docs/广告平台设计/02-业务规则与流程.md)
- [03-系统架构与模块](docs/广告平台设计/03-系统架构与模块.md)
- [06-Agent里程碑与迭代治理](docs/广告平台设计/06-Agent里程碑与迭代治理.md)
- [研发 Backlog](docs/广告平台设计/backlog.md)

---

## 贡献指南

1. 所有代码变更必须关联 Backlog 或需求追踪矩阵条目。
2. 新增功能需补充单元测试、契约测试或接口测试。
3. 敏感配置（密码、密钥、证书）不得提交到仓库。
4. 提交前运行本地编译与代码检查：

```bash
# 后端
cd scx-backend
mvn -pl scx-boot -am clean compile

# 前端
cd scx-fronted
pnpm lint
pnpm check:type
```

5. 架构或产品重大变更需先提交 ADR 或 RFC，经评审后落地。

---

## 许可证

本项目为专有软件（Proprietary），未经授权不得复制、分发或用于商业用途。

---

## 联系方式

- 项目仓库：`ssh://git@117.72.57.4:2222/SmartConvergeX/smart-converge-x.git`
- 研发文档中心：[docs/广告平台设计/README.md](docs/广告平台设计/README.md)
- 问题与需求：请在内部项目管理工具中提交 Issue 或联系产品负责人。

---

> **注意：** 本项目当前处于 M0 研发基线阶段，广告业务功能正在建设中。功能实现状态以 Backlog、需求追踪矩阵和真实测试证据为准，文档中的“规划能力”不代表已交付。
