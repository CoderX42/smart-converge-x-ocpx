# Smart Converge X MVP 开发规划

> 文档状态：Draft，作为后续开发 Agent 的任务导航与执行约束  
> 版本：0.2.0  
> 日期：2026-07-15  
> 输入：[功能审计报告](research/2026-07-15-ocpx-aerim-功能审计报告.md)、`竞品调研-ocpx.aerim.cn.md`、ADR-0001、ADR-0002、当前工程与 Backlog  
> 约束：竞品仅作为需求发现输入；与已接受 ADR、服务端授权、财务/合规开放决策冲突时，以 SCX 基线为准。

## 0. Agent 执行合同

本节优先供开发 Agent 阅读。Agent 不得仅根据某一张竞品页面直接编码，也不得把本文的候选表名、接口或工期当作已接受的跨领域决策。

### 0.1 每次启动必须阅读

开发 Agent 在修改代码前按顺序阅读：

1. 根目录 `AGENTS.md`。
2. `docs/广告平台设计/README.md` 与 `status/CURRENT.md`。
3. `06-Agent里程碑与迭代治理.md`、本文件、当前 Backlog 条目。
4. ADR-0001、ADR-0002 和所有状态为 Accepted 的相关 ADR。
5. 当前任务涉及的产品、业务、数据、权限、安全、契约和迁移文档。
6. 只检查任务影响范围内的代码、迁移、接口和测试，不默认重构工程。

### 0.2 Agent 一次只能领取一个任务

- 一个任务必须是可独立验收的纵向切片，建议 0.5–3 个工作日。
- 禁止用“完成 MVP-01”“完成整个投放模块”作为单个 Agent 任务。
- 开始前先输出：目标、不做、影响模块/表/API、业务规则来源、风险和验证命令。
- 未满足 Definition of Ready 时，只允许补充调研、设计或测试基线，不允许自行补齐产品决策。
- 完成后按 `templates/Agent交接模板.md` 写交接；没有证据不得标记“已验收”。

### 0.3 Agent 必须停下确认的情况

- 任务需要决定真实媒体、广告格式、归因窗口、计费、结算、数据保留或 PII 用途。
- 需要新增微服务、改变模块化单体、替换技术栈或变更公共鉴权模型。
- 需要删除/重写已执行迁移、历史数据、公开 API/消息字段或用户现有改动。
- 需要真实生产地址、密钥、设备标识、回调 payload、支付或广告下发。
- 当前工作树已有改动与任务文件重叠，无法安全保留。

### 0.4 Agent 禁止事项

- 不提交账号、密码、Token、证书、生产 URL、个人信息和浏览器取证文件。
- 不通过隐藏菜单、前端按钮或管理员角色绕过服务端对象授权。
- 不修改已执行 Flyway 文件；修复使用新的前向迁移。
- 不把 Redis、Druid 或报表聚合当业务最终事实源。
- 不顺手重构模板遗留模块，不扩大到任务之外的支付、商城、小程序等代码。
- 不执行真实禁用、回传、导出、支付、投放或生产数据修复。

### 0.5 标准验证命令

Agent 根据影响范围运行对应命令，并在交接中记录原始结果：

```bash
make dev-up
make db-verify
make contract-check
make backend-test
make backend-build
make frontend-check
make frontend-build
```

`make dev-reset` 会删除本地容器数据卷，除非任务明确需要空库验证且已说明影响，否则不得执行。若某命令因既有仓库问题失败，Agent 必须区分“本次回归”和“已存在问题”，不能省略或宣称通过。

### 0.6 Agent 输出格式

开工前：

```markdown
## 任务合同
- Task ID：
- 目标：
- 不做：
- 规则来源：
- 影响模块/表/API：
- 风险与阻塞：
- 验证命令：
```

交接时：

```markdown
## 交付结果
- 已完成：
- 未完成：
- 代码/迁移/契约/文档：
- 权限与审计：
- 测试命令和结果：
- 已知限制：
- 回滚或前向修复：
- 下一任务建议：
```

## 1. MVP 目标

用 1–2 人团队交付一个可在沙箱环境演示和验收的效果广告追踪最小闭环：

```text
主体与合作方 → 产品/接入文档 → 追踪链接 → 投放任务
→ 曝光/点击/转化事件 → 去重与基础归因
→ 任务/产品/链接/媒体汇总 → 异步导出
```

MVP 的成功标准不是复刻竞品页面，而是让每个业务对象都具备服务端归属校验、版本、状态、审计、契约、迁移和测试证据。

### 1.1 MVP 验收目标

1. 管理员可维护广告主、媒体主体、合作方、产品、接入文档和媒体渠道。
2. 运营可创建追踪链接和任务，配置事件映射、去重窗口、曝光监测和启停状态。
3. 沙箱可接收曝光、点击及转化事件；重复请求不产生重复事实。
4. 系统可完成确定性的最后点击归因，并记录未归因、超窗、重复和回传状态。
5. 用户只能查看授权主体范围内的数据；直接访问隐藏路由不能越权。
6. 控制台可按日期、任务、产品、链接和媒体查看聚合数据，并创建异步导出任务。
7. 迁移、OpenAPI/AsyncAPI、单元/集成/越权/E2E 测试和运行文档均可重复验证。

## 2. 输入材料取舍

| 输入结论 | 采用方式 |
|---|---|
| 产品、合作方、文档、链接、任务、媒体、报表构成竞品主链路 | 纳入 MVP 业务对象和页面 |
| 链接支持 Android/iOS/其他、重定向、归档和事件映射 | 纳入追踪链接最小模型 |
| 任务支持曝光监测、去重窗口、状态、后置 RTA | 前三项纳入 MVP；真实 RTA 调用延期 |
| 报表按任务、产品、链接、媒体和日期聚合 | 纳入 MVP；指标先限定基础漏斗 |
| 审减、超量、留存、授信等字段 | 仅保留扩展点；定义口径后再实现 |
| 竞品存在菜单隐藏但路由可直达 | 作为 SCX 服务端对象授权的反面验收用例 |
| 旧调研建议拆成多个独立服务 | 不采用；遵守模块化单体基线 |
| 旧调研中的真实指标量级和容量推断 | 不采用；等待 Q-006 的真实容量输入 |
| 旧调研推测租户、审批、结算能力 | 不作为已确认需求 |

## 3. MVP 范围

### 3.1 必须交付

| 领域 | MVP 能力 | 优先级 |
|---|---|---|
| IAM 与主体 | 用户、角色、广告主/媒体主体授权范围、操作审计 | P0 |
| 主数据 | 合作方、产品、媒体渠道、接入文档元数据 | P0 |
| 追踪链接 | CRUD、类型、跳转目标、归档、事件映射、版本 | P0 |
| 投放任务 | CRUD、链接/媒体关联、曝光开关、去重窗口、启停/归档 | P0 |
| 事件接入 | 曝光、点击、激活、注册、支付沙箱事件；签名、幂等、原始事实 | P0 |
| 基础归因 | 最后点击、归因窗口、重复/超窗/未归因判定 | P0 |
| 报表 | 仪表盘、任务/产品/链接/媒体日汇总、基础漏斗和比率 | P0 |
| 导出 | 日期校验、异步任务、OSS 产物、进度与过期 | P1 |
| 运维 | RocketMQ 重试/DLQ、任务监控、请求关联 ID、指标和告警 | P0 |

### 3.2 MVP 明确不做

- 真实资金充值、扣费、发票、媒体结算和对账。
- RTB/OpenRTB、自动出价、学习期、实验平台和模型训练。
- 真实生产 RTA 调用；只保留配置模型和模拟适配器。
- 代理商授信、返点、代投关系和“授信”报表口径。
- 审减审批、财务确认和复杂超量结算；仅记录原始判定字段。
- 淘宝/京东 DP 转换、通用短链平台、素材生产和完整 OSS 素材中心。
- SaaS 多租户售卖能力；当前是单企业平台下的主体数据范围隔离。
- 未经 Q-001/Q-002 确认的真实媒体协议和广告格式。

## 4. 角色与权限

| 角色 | MVP 可见范围 | 关键操作 |
|---|---|---|
| 平台管理员 | 全部主体与系统配置 | 用户/角色、主体授权、主数据、任务、审计 |
| 运营 | 被授权的广告主、媒体和任务 | 产品、链接、任务、报表、导出 |
| 广告主查看者 | 自己主体下的链接、任务和报表 | 只读查询与导出 |
| 媒体查看者 | 被授权媒体渠道和相关任务数据 | 只读任务、媒体汇总 |

权限模型采用 `RBAC + subject scope + resource ownership`：菜单和按钮只负责体验；Controller/Application Service 必须根据当前用户重新校验主体、资源归属和操作权限。高风险操作必须记录原因、操作者、时间和前后状态。

## 5. 功能需求

### 5.1 主体与主数据

| ID | 需求 | 核心规则 | 验收 |
|---|---|---|---|
| MVP-FR-001 | 广告主与媒体主体管理 | 延续 `scx-subject`；编码唯一；禁用后不可新建下游资源 | CRUD、分页、状态、越权测试、审计通过 |
| MVP-FR-002 | 合作方管理 | 合作方不等同广告主；支持正常/禁用 | 名称必填、搜索分页、禁用原因和审计 |
| MVP-FR-003 | 产品管理 | 产品必须归属广告主；禁用后不可新建链接 | 名称/编码唯一；主体范围校验 |
| MVP-FR-004 | 媒体渠道管理 | 渠道归属媒体主体，可关联接入文档 | 名称、编码、状态、媒体主体、文档引用完整 |
| MVP-FR-005 | 接入文档元数据 | 保存名称、类型、版本、OSS 对象引用或受控外链 | 不保存密钥；下载/预览需权限和签名 |

### 5.2 追踪链接

| ID | 需求 | 核心规则 | 验收 |
|---|---|---|---|
| MVP-FR-006 | 追踪链接 CRUD | 关联产品、合作方、文档；类型为 Android/iOS/Other | 服务端校验关联对象属于可访问主体 |
| MVP-FR-007 | 跳转配置 | 支持重定向、直达地址、下载地址和占位符规范化 | 非法协议、未知占位符和空必填项被拒绝 |
| MVP-FR-008 | 事件映射 | 内部标准事件映射到外部事件；配置版本化 | 激活/注册/支付等字典可扩展；历史任务不被覆盖 |
| MVP-FR-009 | 生命周期 | `DRAFT → ACTIVE → DISABLED → ARCHIVED` | 禁用停止新流量；归档保留历史查询；状态变更审计 |

### 5.3 投放任务

| ID | 需求 | 核心规则 | 验收 |
|---|---|---|---|
| MVP-FR-010 | 任务 CRUD | 关联追踪链接、媒体渠道和运营人 | 只能引用启用且有权限的对象 |
| MVP-FR-011 | 监测配置 | 点击监测必选；曝光监测可选；去重窗口最小为 0 秒 | 条件字段前后端一致校验 |
| MVP-FR-012 | 任务生命周期 | `DRAFT → ENABLED → PAUSED → ARCHIVED` | 暂停停止接收新业务事件；归档只读 |
| MVP-FR-013 | RTA 预留 | 保存版本化 RTA 配置，但只连接模拟适配器 | 真实外部地址和密钥不进入仓库 |

### 5.4 事件、归因与回传

| ID | 需求 | 核心规则 | 验收 |
|---|---|---|---|
| MVP-FR-014 | 事件接入 | 接收 impression/click/activate/register/pay | OpenAPI、签名、时间戳、防重放、限流测试通过 |
| MVP-FR-015 | 幂等与去重 | `source + event_id` 唯一；设备去重受任务窗口控制 | 并发重复事件只产生一条事实记录 |
| MVP-FR-016 | 最后点击归因 | 在配置窗口内选择同任务/设备最近有效点击 | 超窗、乱序、未归因均有明确原因码 |
| MVP-FR-017 | 异步回传 | 归因结果生成回传任务；指数退避、最大次数、DLQ | 重试不重复回传；支持人工重放并审计 |

### 5.5 报表与导出

| ID | 需求 | 核心规则 | 验收 |
|---|---|---|---|
| MVP-FR-018 | 仪表盘 | 今日曝光、点击、激活、注册、支付及趋势 | 显示数据时间、时区和是否为预估值 |
| MVP-FR-019 | 多维汇总 | 日期、任务、产品、链接、媒体渠道维度 | 曝光、点击、重复点击、转化量和基础比率口径一致 |
| MVP-FR-020 | 明细查询 | 点击与转化明细按主体授权，敏感标识默认脱敏 | 直接传他人资源 ID 返回 403/404，不泄漏存在性 |
| MVP-FR-021 | 异步导出 | 必选日期；限制跨度/行数；生成任务 ID | RocketMQ 消费、OSS 签名下载、过期删除和失败原因可见 |

## 6. 页面与路由规划

| SCX 菜单 | 页面 | 建议前端路径 | MVP 操作 |
|---|---|---|---|
| 工作台 | 效果概览 | `/dashboard/analytics` | 指标卡、趋势、数据时间 |
| 主体管理 | 广告主、媒体主体、合作方 | `/subject/*` | 查询、新增、编辑、启停 |
| 投放配置 | 产品、媒体渠道、接入文档 | `/delivery/product` 等 | CRUD、状态、关联选择 |
| 投放配置 | 追踪链接 | `/delivery/tracking-link` | 筛选、抽屉表单、事件映射 |
| 投放配置 | 投放任务 | `/delivery/task` | 筛选、条件表单、启停/归档 |
| 数据中心 | 任务/产品/链接/媒体汇总 | `/reporting/*` | 日期筛选、表格、汇总行、导出 |
| 数据中心 | 点击/转化明细 | `/reporting/event-detail` | 筛选、脱敏、授权查询 |
| 任务中心 | 导出与回传任务 | `/operations/jobs` | 进度、失败原因、重试/重放 |

页面统一采用查询表单 + 表格 + 抽屉，不复制竞品组件库。条件表单、事件映射和导出任务应抽成领域组件，避免“万能表单/万能表格”隐藏业务规则。

## 7. 模块与代码边界

保持模块化单体，不拆微服务：

```text
scx-backend/
├── scx-auth / scx-system       # 登录、RBAC、菜单、审计
├── scx-subject                 # 广告主、媒体主体、合作方与授权范围
├── scx-delivery                # 产品、渠道、文档、追踪链接、任务
├── scx-tracking                # 事件接入、原始事实、幂等、回传任务
├── scx-attribution             # 归因规则与结果
├── scx-reporting               # Druid 查询、仪表盘、导出任务
└── scx-boot                    # 统一启动入口，端口 9999

scx-fronted/apps/web-ele/src/
├── views/subject
├── views/delivery
├── views/reporting
├── views/operations
├── api/subject|delivery|tracking|reporting
└── components/domain
```

新增模块前先确认 ADR-0003；若该 ADR 仍为 Proposed，可先在现有模块中建立清晰包边界，不擅自改变跨模块技术决策。

## 8. MVP 数据模型

| 聚合/表候选 | 关键字段 | 所有者 |
|---|---|---|
| `scx_partner` | id、code、name、status、version、subject_id | subject |
| `scx_product` | id、advertiser_id、code、name、platform、status、version | delivery |
| `scx_media_channel` | id、media_id、code、name、status、document_id | delivery |
| `scx_integration_document` | id、owner_subject_id、name、type、version、object_key/external_url、status | delivery |
| `scx_tracking_link` | id、product_id、partner_id、type、redirect_enabled、targets、status、version | delivery |
| `scx_link_event_mapping` | link_id、internal_event、external_event、limit_value、version | delivery |
| `scx_delivery_task` | id、link_id、channel_id、dedup_window_sec、exposure_enabled、status、version | delivery |
| `scx_raw_event` | event_id、source、task_id、event_type、occurred_at、received_at、payload_ref、dedup_status | tracking |
| `scx_attribution_result` | conversion_event_id、click_event_id、rule_version、status、reason_code | attribution |
| `scx_callback_job` | attribution_id、target、status、attempts、next_retry_at、last_error | tracking |
| `scx_export_task` | requester_id、subject_scope、query_snapshot、status、object_key、expires_at | reporting |
| `scx_audit_log` | actor、action、resource_type/id、reason、before/after hash、request_id | system |

DDL 必须使用 Flyway；所有业务表包含创建/更新人、时间、乐观锁或版本字段。原始事件不可被更新为聚合值，报表和账务均不能反向修改事件事实。

## 9. API 与消息契约

### 9.1 管理 API

- `/boot/subjects/*`：延续现有主体接口。
- `/boot/partners`、`/boot/products`、`/boot/media-channels`、`/boot/integration-documents`。
- `/boot/tracking-links`、`/boot/delivery-tasks`。
- `/boot/reports/dashboard`、`/boot/reports/daily`、`/boot/export-tasks`。
- 列表统一 page/size/sort；写操作支持幂等键和乐观锁；状态变更使用独立动作接口并要求 reason。

### 9.2 事件 API

- `/events/v1/impressions`
- `/events/v1/clicks`
- `/events/v1/conversions`

事件信封至少包含 schema_version、event_id、source、task_code、event_type、occurred_at、device_key_hash 和 attributes。设备原值是否允许接收、如何加密和保留，必须等待 Q-007 关闭。

### 9.3 RocketMQ 主题候选

- `scx-event-ingested`
- `scx-attribution-requested`
- `scx-attribution-completed`
- `scx-callback-requested`
- `scx-export-requested`

Topic、Tag、Key、顺序性、重试和 DLQ 进入 AsyncAPI；不得只存在于 Java 常量中。

## 10. 技术实现基线

- 后端：Java 17、Spring Boot 3.x、MyBatis/Flyway，根包 `com.yzytech.scx`。
- 前端：现有 Vue 3 + TypeScript + Element Plus 应用，不引入 TinyVue。
- MySQL：配置、状态、权限、审计、原始事件索引和异步任务事实。
- Redis：短期幂等、设备去重窗口、限流和热点查询；不可作为最终事实源。
- RocketMQ：事件、归因、回传和导出异步解耦。
- Druid：日维度和多维报表；MVP 初期可先用 MySQL 验证口径，再以同一查询契约切换 Druid。
- OSS：接入文档和导出产物；数据库只保存 object key、元数据和校验哈希。
- API/消息/DDL：OpenAPI、AsyncAPI/JSON Schema、Flyway 同一纵向切片内提交。

## 11. 纵向切片与开发顺序

| 切片 | 范围 | 交付证据 | 预估 |
|---|---|---|---:|
| MVP-00 基线收口 | M0 启动、CI、Flyway、对象授权测试；清理当前接口路径不一致 | 本地启动、健康检查、CI、契约检查 | 3–5 天 |
| MVP-01 主数据闭环 | 合作方、产品、媒体渠道、文档元数据 | DDL、API、权限、列表/抽屉、E2E、审计 | 1.5–2 周 |
| MVP-02 链接闭环 | 追踪链接、跳转配置、事件映射、状态/版本 | 越权/校验/状态机测试，前端 E2E | 1.5–2 周 |
| MVP-03 任务闭环 | 任务、监测开关、去重窗口、启停/归档 | DDL、API、条件表单、并发与权限测试 | 1.5–2 周 |
| MVP-04 事件归因闭环 | 沙箱事件、幂等、最后点击归因、模拟回传 | 黄金样例、乱序/重复/超窗/DLQ 测试 | 2–3 周 |
| MVP-05 报表导出闭环 | 仪表盘、多维日报、明细脱敏、异步导出 | 口径测试、Druid/MySQL 对账、OSS 生命周期、E2E | 2–3 周 |
| MVP-06 上线准备 | 监控、限流、密钥轮换、Runbook、演练 | 安全/容量/故障演练和发布清单 | 1 周 |

1–2 人串行预估 10–14 周；若 Q-001、Q-006、Q-007 未关闭，只能完成沙箱 MVP，不能宣称生产可用。

## 12. 第一批可直接开工任务

### DEV-001：MVP 基线验证与修复

- 分拆为 `DEV-001A` 只读基线验证和 `DEV-001B` 经证据确认后的定向修复；不要在一次任务中混做全面修复。
- 校验后端测试/打包、前端类型检查/单测/生产构建、契约与迁移。
- 验证空库 Flyway 迁移、MySQL/Redis/RocketMQ 本地启动和 `/actuator/health`。
- 修复现有 subject 前后端路径、角色绕过和初始化脚本差异后再扩展业务。
- 输出测试报告和限制，不修改业务范围。

### DEV-002：合作方 + 产品纵向切片

- 实际领取时拆成 `DEV-002A 合作方后端`、`DEV-002B 合作方前端`、`DEV-002C 产品后端`、`DEV-002D 产品前端`；每项独立验收。
- 每项先写 Flyway、OpenAPI、权限策略和数据所有权，再实现服务端 CRUD 或前端契约接入。
- 前端实现分页列表、搜索、抽屉表单、状态变更原因和审计展示。
- 测试两个广告主/媒体主体之间的横向越权、禁用对象引用和并发版本冲突。

### DEV-003：媒体渠道 + 接入文档元数据

- 实际领取时拆成媒体渠道后端/前端、接入文档后端/前端四个任务。
- 文档只存 OSS object key 或受控外链；禁止写入凭据。
- 渠道必须绑定媒体主体，供后续任务选择。
- 下拉接口只返回当前用户可访问且启用的对象。

DEV-001 验收前，不建议并行开发事件归因或报表；否则会在错误的主体、路径和迁移基线上累积返工。

### Agent 任务队列（首批）

| Task ID | 单一目标 | 前置 | 主要证据 | 状态 |
|---|---|---|---|---|
| DEV-001A | 只读验证当前启动、构建、测试、契约、迁移和主体授权基线 | 无 | 命令结果、失败归因、文件/API 路径映射 | 已就绪 |
| DEV-001B | 仅修复 DEV-001A 确认的 P0 基线问题 | DEV-001A | 回归测试、最小差异、限制 | 待 DEV-001A |
| DEV-002A | 合作方后端 CRUD、状态、授权、审计 | DEV-001B；主体边界确认 | Flyway、OpenAPI、服务测试、越权测试 | 待前置 |
| DEV-002B | 合作方列表和抽屉表单 | DEV-002A | 类型检查、单测、生产构建、E2E | 待前置 |
| DEV-002C | 产品后端 CRUD、状态、广告主归属 | DEV-001B | Flyway、OpenAPI、状态与越权测试 | 待前置 |
| DEV-002D | 产品列表、筛选和抽屉表单 | DEV-002C | 类型检查、单测、生产构建、E2E | 待前置 |
| DEV-003A | 媒体渠道后端与媒体主体归属 | DEV-001B | Flyway、OpenAPI、授权测试 | 待前置 |
| DEV-003B | 媒体渠道前端 | DEV-003A | 前端质量门、E2E | 待前置 |
| DEV-003C | 接入文档元数据后端 | DEV-001B；OSS 策略明确 | Flyway、OpenAPI、对象授权与签名测试 | 待前置 |
| DEV-003D | 接入文档元数据前端 | DEV-003C | 前端质量门、E2E | 待前置 |

启动后续 Agent 时，优先使用类似指令：

> 阅读 `AGENTS.md` 和 `docs/广告平台设计/22-MVP开发规划.md`，领取 `DEV-001A`。先输出任务合同，只做只读基线验证，不修改代码；按文档记录每条命令结果和根因映射。

## 13. Definition of Done

每个业务切片同时满足：

- Flyway 安装与升级验证通过，无手工建表步骤。
- OpenAPI/AsyncAPI/JSON Schema 校验通过，错误码和示例齐全。
- 服务端主体归属与资源授权测试通过，菜单隐藏不作为授权。
- 创建、查询、更新、状态流转、非法输入、重复请求和并发冲突均有测试。
- 高风险写操作记录审计、原因、request_id 和前后状态摘要。
- 前端列表、筛选、表单校验、空态、加载、错误和权限状态完整。
- 相关后端构建、测试，前端类型检查、单测、生产构建通过。
- 文档、Backlog、需求追踪矩阵、当前状态同步更新。

## 14. 开工前必须确认的决策

| 决策 | 不确认时允许的工作 | 阻塞的工作 |
|---|---|---|
| Q-001 首批媒体/广告主/协议 | 沙箱事件和适配器接口 | 真实媒体接入、生产回传 |
| Q-002 广告格式 | 产品/链接基础元数据 | 创意、广告位、审核 |
| Q-006 容量 | 功能正确性和小规模压测 | 生产容量、Druid 分片和成本承诺 |
| Q-007 数据合规 | 哈希设备键和合成数据 | 接收真实设备标识、生产保留策略 |
| Q-004/Q-005/Q-008 财务 | 非金额事件与效果报表 | 扣费、充值、账单、结算 |

推荐立即开始 DEV-001；同时由产品确认 Q-001/Q-002，由合规确认 Q-007。DEV-002/003 可使用合成数据并行准备，但不能把候选需求标记为已验收。
