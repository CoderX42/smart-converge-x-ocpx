# 91 SCX 实施依赖与验收矩阵

> 状态：Review
> 目的：按数据依赖交付纵向切片，避免先堆页面、后补权限/数据/运行链
> 本包只给出实施规格，不代表下列切片已经完成

## 1. 实施原则

每个切片只有同时完成以下证据才可标 Done：

```text
Accepted 需求/决策
  + 前向 migration 与 current schema/verify 同步
  + API/Schema/权限码与对象范围
  + web-ele 页面/异常态/可访问性
  + 单元/契约/集成/权限测试
  + 可观测指标与失败补偿
  + 本地或目标环境运行证据（按阶段）
```

源码存在、单测存在、页面打开、本地构建通过、目标库迁移完成、第三方 UAT、生产发布是不同证据层，状态必须分别记录。

## 2. 推荐纵向切片

| Slice | 目标 | 前置 | 主要改动 | 完成证据 | 状态 |
|---|---|---|---|---|---|
| `S0` | 术语、权限与对象范围基线 | Accepted ADR-001/002 | scope/revision 表、API、权限码、服务端过滤、菜单约定 | 范围越权测试、缓存版本失效、菜单/按钮/API 一致 | **Source + local gates complete (2026-08-10); target pending** |
| `S1` | Advertiser 接入资料收口 | S0 | V82 + current schema/verifier；正式 advertiser API/UI；资料/凭据 revision | Secret 不回显、独立权限/S0 scope、受管资料、并发/审计、依赖路由待重发 | **Source + local gates complete; target/UAT/prod pending** |
| `S2` | Product 主数据 | S0/S1 | V83 Product/Policy/Logo/Audit + 可空 AppLink productId + API/UI/事件默认策略 | 幂等/唯一/409/归档引用/S0 范围/受管 Logo/路由 revision | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S3` | MediaAccount / MediaCredential | S0 | V84 + current schema/verifier；Channel 分层、业务账户、外部凭据 revision、内部负责人、Route 冻结字段 | 账户 CRUD、Secret 只写、owner 分权、MEDIA_ACCOUNT scope、CONFIG_ONLY 预检 | **Source + local gates complete; target/UAT/prod pending** |
| `S4` | AppLink 结构化 | S1/S2 | V85 AppLink/audit/route 冻结字段 + 正式 API/UI | URL 安全、显式存量映射、引用保护、路由待重发 | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S5` | Task 运营聚合 | S2/S3/S4 | V86 Task/audit + Monitor 显式可空关联；正式 `/ocpx/tasks` API 与 Element Plus UI | 三对象 scope、幂等/409/归档引用、级联/曝光/去重、保存不发布、RTA OFF、旧 Monitor 不猜测 | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S6` | Event 模型收口 | S5 + 当前本地在研评审 | V87 四层真相、正式 API/UI、Task 动态绑定、不可变快照与纯函数策略合同 | 迁移空库/升级/重放/反例、范围/409/Adapter、确定性采样与 Callback 隔离合同 | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S7` | Monitor 发布与运行维度 | S5/S6 | 宏字典、HTTPS 监测入口、新维度、快照、Outbox、实例分发 | URL 宏验证、发布幂等、故障恢复、DRAFT 不承流、版本追踪 | **Source + local gates complete; target/UAT/prod pending** |
| `S8` | 统一小时事实与报表 | S7 | 事实扩维、groupBy API、五页 + Task 时/日/链接抽屉、双导出 | 跨视图/Dashboard 对账、回补、字段差异、权限/性能 | **Source + local gates complete; target/UAT/prod pending** |
| `S9` | Dashboard | S8 | 指标卡/趋势/asOf/三排行；V90 范围索引与权限收口 | 与报表一致、局部失败、缓存范围/水位隔离 | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S10` | 运行中心补偿体验 | S7 | V91 不变事实/失败/attempt/可恢复命令、Task 三抽屉、DLQ、预览/确认、复用 S7 停流 | 空库/升级/重放/反例、scope/脱敏/幂等/二次认证、claim/lease/fake processor | **Source + local gates complete (2026-08-10); target/UAT/prod pending** |
| `S11` | RTA 主动决策 | Accepted 替代 ADR + S7 | Profile/全局设置/credential revision/事实/决策/六列 DAY/HOUR 统计；区分服务地址、api 与全局域名 | 新增默认强制关、Secret 全链路不回显、四层 OFF 门禁、shadow/容量/故障模式/供应商 UAT | **Blocked；[ADR-003](../adr/ADR-003-RTA主动决策解锁门禁.md) 仅 Proposed / Not Accepted** |
| `S12` | DP 转换 | Accepted 供应商 RFC/ADR | Adapter/SSRF/幂等/UI | 安全测试、正式协议、隐私/TTL/UAT | **Blocked；[RFC-001](../adr/RFC-001-DP供应商协议与安全门禁.md) 仅 Proposed / Not Accepted** |

S2 与 S3 可并行，但共享的 scope、审计、存储和错误约定必须先由 S0/S1 固定。S8 前必须先保证运行事实具备完整维度，否则报表页面只会掩盖数据缺口。

S0 的“local gates complete”仅表示当前工作树源码、迁移契约、一次性 MySQL 8.4 空库/升级副本、撤权后误重放不回授、后端测试、前端类型/单测/规则/构建等本地证据通过。目标数据库未应用 V81，目标服务与浏览器尚未验收，也未发布生产；进入 S1 可以依赖该源码契约，但上线结论仍受 `DB-G5` 约束。

S1 当前源码复用 `ProtocolCrypto` 与现有受管对象存储，不建立平行凭据/密钥体系；创建/详情/轮换响应只返回 configured/revision。Advertiser 配置、credential 或 current document 变化只给已发布 Monitor 标记待重发，不修改既有 URL/状态/发布版本；显式发布时再冻结精确 revision。MySQL 8.4 已完成 current schema 空库、pre-V82 升级、V82 重放与加密/撤权状态不回授验证；`make backend-test`、契约检查、前端类型/lint/297 单测/Element Plus 生产构建和 diff 检查通过。目标数据库、目标服务、浏览器多角色、真实对象存储/上游平台、第三方恶意文件检测和生产仍未验收，不能据此判定上线。

S2 当前源码在 `scx-ocpx-admin` 内新增独立 Product 聚合，继承 S0 Advertiser 对象范围，并复用 S1 已固定的受管存储与待重发约定。V83 不自动按包名或名称回填存量 AppLink，只预留可空 `product_id` 给 S4 人工确认后启用；Product/事件策略变化只标记已发布路由待重发，旧快照不变，显式发布冻结 Product config/event-policy revision。Logo 普通 DTO 不包含对象键，业务或事务提交失败会补偿删除新对象，新 Logo 提交成功后才清理旧对象。本地 MySQL 8.4 current schema 空库、pre-V83 升级、V83 重放、唯一约束反例与 verifier 已通过；后端全量测试、契约检查、前端类型/lint/301 单测/Element Plus 生产构建已通过。目标数据库、目标服务、多角色浏览器、真实对象存储、AppLink/Task 联动、第三方 UAT 和生产均未验收。

S3 当前源码新增 V84 的 MediaAccount/MediaCredential/revision/owner/audit，并将 Monitor 关系保持可空且不自动回填；S5 必须由业务显式选择关系。登录标识 blind index 与 Secret 加密复用现有 ProtocolCrypto 密钥材料，无新增密钥/环境变量路径；凭据请求不进入请求体系统日志，普通响应只含掩码、configured 与 revision。S0 MEDIA_ACCOUNT 范围已在后端交集过滤、授权选项/影响摘要与前端动态行正式启用。配置变化只标记依赖路由待重发，显式发布冻结 account/credential 精确 revision，旧快照不变。连接检查固定 `CONFIG_ONLY/networkCalled=false`，不解密或访问媒体网络；真实连通性仍待第三方 UAT。本地 MySQL 8.4 current schema 空库、pre-V84 升级、V84 重放、唯一约束/凭据 revision 反例、Monitor 不自动回填与 verifier 已通过；`make backend-test`、迁移/契约检查、前端类型/lint/305 项单测/Element Plus 生产构建与 `git diff --check` 已通过。目标数据库、目标服务、多角色浏览器、真实媒体平台和生产均未验收。

S4 当前源码新增 V85 的结构化 AppLink、变更审计和 Route 精确冻结字段；存量链接统一保持 `LEGACY_PENDING_PRODUCT/DISABLED`，不猜测 Product 归属，且只允许显式映射到原 Advertiser 下的 ACTIVE Product。正式 API/UI 复用 S0 Advertiser 派生范围，覆盖唯一性、幂等、乐观锁、归档/恢复、引用保护、待补 Product 映射及全状态界面。URL 预检只做确定性语法、主机、userinfo、本地/私网/保留地址、宏和规范化检查，固定 `networkCalled=false`，不解析主机名或访问 URL；普通响应只返回掩码。AppLink/Product 变化只标记依赖路由待重发，不改旧发布快照；显式发布才冻结 Product/AppLink config revision 与最终结构化 URL。本地 MySQL 8.4 current schema 空库、pre-V85 升级、V85 重放、存量不猜测反例与 verifier 已通过；后端全量测试、迁移/契约检查、前端类型/lint/309 项单测/Element Plus 生产构建和 diff 检查已通过。目标数据库、目标服务、多角色浏览器、真实 URL/第三方 UAT 和生产均未验收。

S5 当前源码新增 V86 Task/audit 与 Monitor 显式关联契约；旧 Monitor 保持 `task_id=NULL/LEGACY_UNMAPPED`，不根据 URL、名称或现有外键猜测 Product/MediaAccount/Task。正式 DTO/API/UI 只保存 DRAFT/READY 配置，冻结 MediaCredential secret revision；Advertiser、Channel、MediaAccount 三类对象范围服务端求交，父子对象、负责人和 ACTIVE/READY 状态均重新校验。保存不调用既有 Monitor publish，不生成承流版本或 Outbox，不改旧发布快照；`ACTIVE/PAUSED` 状态固定 409，RTA 固定 OFF。Element Plus 页面复用现有 SCX 设计语言并将发布/报告/联调/点击/转化禁用为后续切片。MySQL 8.4 current schema 空库、pre-V86 升级、V86 重放、旧 Monitor 不猜测和约束反例已通过；`make backend-test`、迁移/契约检查、Java 定向格式、前端类型/lint/313 项单测/Element Plus 生产构建、配置占位与响应字段安全检查、`git diff --check` 均已通过。目标数据库、目标服务、多角色浏览器、第三方 UAT 和生产均未验收。

S6 当前源码用 V87 在既有事件表上收口 EventDefinition→ChannelEventMapping→TaskEventBinding→RouteEventSnapshot 四层真相，不创建平行事件体系。旧 definition/mapping/snapshot 保持 `LEGACY_UNMAPPED/LEGACY_V80` 且 canonical 字段为空，不按中文名、externalCode 或 URL 推断。Task binding 只接受当前 MediaAccount 派生 Channel/Adapter 的 ACTIVE mapping，服务端应用 Advertiser∧Channel∧MediaAccount scope；比例统一 0..10000 bp，限额固定自然日/Asia/Shanghai，采样算法固定 `SHA256_MOD_10000_V1`。Task 保存只改草稿和待重发标记；snapshot preview 明示未持久化且无 Outbox，正式发布仍属于 S7。Callback snapshot 0 默认隔离，只有显式 legacy adapter 才兼容；本轮只验证纯函数、防重和未知 code 合同，没有真实外发、计费或重放。MySQL 8.4 current schema 空库、pre-V87 升级、V87 重放、legacy 不猜测、canonical NULL/比例越界和 snapshot 不可变反例通过；`make backend-test`、契约检查、Java 定向格式、前端类型/lint/318 单测/Element Plus 生产构建通过。目标数据库未应用 V87，目标服务、多角色浏览器、第三方 UAT 和生产均未验收。

S7 当前源码新增 V88 的不可变 RouteVersion、发布记录、运行接受状态、Distribution/InstanceAck、pause command 与可恢复 Outbox claim 维度；存量 Monitor/Event 不猜测 Task 或 routeCode。publish-check 使用短期签名 token 和精确 revision fingerprint，默认字面量 `.invalid` tracking host 可见 FAIL；无 FAIL、权限/三对象范围仍有效且 Task version 未变化才可确认。发布事务冻结 S0–S6 主数据/credential/event/macro 配置并写 Outbox/初始分发，结果只到 PUBLISHED/PENDING；运行实例先以数据库扫描恢复/Redis 加速信号获取精确版本，验证不可变快照后写幂等 ACK，达标才 ACTIVE，跌破门槛退回待分发，部分失败为 DEGRADED。runtime 只读加密不可变 schema 3 snapshot，未知、DRAFT、DENIED/PAUSED、SUPERSEDED/过期或无 schema 2 事件快照的 routeCode 均拒绝。Outbox/实例 apply worker 在默认配置中关闭，未连接目标 Redis/RocketMQ/实例；停流源码要求 task+monitor 权限、三对象范围、二次认证、预览 token 和幂等命令，恢复必须发布新版本。本地 MySQL 8.4 已通过 current schema 空库、pre-V88 升级、V88 双重执行、verifier、RTA 约束和 RouteVersion 不可变反例；`make backend-test`（OCPX 101、runtime 75、boot 24）、契约检查、Java 定向格式、前端类型/lint/319 单测/Element Plus 生产构建通过。目标数据库未应用 V88，目标服务、多角色浏览器、真实实例 ACK、第三方 UAT 和生产均未验收，生产 No-Go。

## 3. SCX 模块落点

| 能力 | 后端所有权 | 前端路径 | 禁止做法 |
|---|---|---|---|
| 用户/角色/菜单/密码 | `scx-auth/scx-system` | `views/system` / 现有 auth | 新建 OCPX 用户/密码表 |
| 主数据/Task/发布管理 | `scx-ocpx-admin` | `views/ocpx/*` | 每个页面建 Maven 模块 |
| 机器契约 | `scx-ocpx-contract` | 无直接 UI | 浏览器持有签名 Secret |
| 高并发接入/投递 | `scx-ocpx-runtime/engine` | 运行中心只查管理 API | 放回 `scx-boot` 管理线程池 |
| Dashboard/Reports | `scx-ocpx-admin` 统一查询服务 | `views/ocpx/dashboard/report` | 五页各写一套口径 |
| RTA 管理/统计 | 管理元数据归 `scx-ocpx-admin`，未来机器决策归 `scx-ocpx-runtime/engine` | `views/ocpx/rta` | 在 `scx-boot` 外呼、浏览器持有/回读 Secret、把参考默认开启照搬为目标默认 |
| 调试/补偿 | ADR-002 工作台 + runtime | `views/ocpx/runtime` | 任意代理/复制投放路径 |

## 4. 切片级交付矩阵

| Slice | Migration/Data | API/Contract | UI | Permission/Scope | Async/Failure | Tests |
|---|---|---|---|---|---|---|
| S0 | `scx_ocpx_user_scope` + revision、活动唯一/有效期 | scope 整体读写/impact/options | 用户行 OCPX scope 抽屉 | function + object intersection；最后 ALL/MANAGE 保护 | DB revision 每次校验 + 本机/Redis 提交后失效 | 越权 ID、有效期、并发 version、缓存旧值、迁移契约 |
| S1 | `V82` advertiser/document/credential/change-audit + route frozen revision | 正式 list/detail/create/update/status/rotate/docs；报表选项最小投影 | Element Plus 广告主页、表单/凭据/资料/账务抽屉和完整状态 | function + S0 Advertiser scope；credential/document 独立权；二次认证 | 受管上传失败清理、轮换重叠、待重发不改旧快照 | 幂等/409、Secret 响应缺失、URL/文件/引用、迁移/重放 |
| S2 | V83 product/policy/logo/audit；AppLink 可空 productId；Route frozen revision | `/ocpx/products` + options/logo/archive/restore | Product 列表/抽屉完整状态、事件动态行、受管 Logo | 四功能权限 + S0 Advertiser scope | 新对象失败补偿删除；旧对象 after-commit 清理；已发布路由只标待重发 | 幂等/唯一/409/归档引用/事件重复/Logo 补偿/迁移空库升级重放/前端状态 |
| S3 | V84 media_account/credential/revision/owner/audit；Monitor 可空关系与冻结字段 | `/ocpx/media-accounts` + credentials/owners/config-only preflight | 媒体账号列表/抽屉、凭据、负责人、配置预检完整状态 | Channel ∧ MEDIA_ACCOUNT scope + credential/owner/debug 独立权 + 二次认证 | 现有 ProtocolCrypto；Secret revision；变化只标待重发；不改旧快照 | 不回显/盲索引唯一/409/引用保护/范围越权/预检不联网/迁移空库升级重放 |
| S4 | V85 app_link/audit；存量待补映射；Route 冻结字段 | `/ocpx/app-links` list/detail/options/create/update/mapping/status/archive/restore/url:validate | Element Plus 链接页全状态、掩码 URL、字段联动 | 六功能权限 + S0 Advertiser 派生范围 | 无网络 URL policy；变化只待重发；旧快照不变 | 幂等/唯一/409/引用/越权/SSRF 字面量/不泄漏/冻结/空库升级重放/前端状态 |
| S5 | V86 task/audit + monitor FK/索引/legacy mapping | `/ocpx/tasks` list/detail/options/create/update/status/archive/restore | 两行筛选、全状态、条件抽屉、dirty/409、后续动作禁用 | Advertiser∧Channel∧MediaAccount scope；负责人按 sys_user ID | 保存只草稿/待重发；无 publish/version/Outbox/snapshot 改写 | 幂等/唯一/409/范围、级联、曝光/大点击、0..86400、归档引用、RTA OFF、空库升级重放 |
| S6 | V87 演进 definition/mapping + task binding/audit + immutable route snapshot；legacy 显式不映射 | `/ocpx/events`、`/ocpx/event-mappings`、Task bindings/options/references/status/snapshot preview | 三页签字典页 + Task 动态事件行、继承来源、全状态/dirty/409；发布禁用 | Event 全局只读；mapping Channel scope；Task Advertiser∧Channel∧MediaAccount scope | 保存仅草稿/待重发；SHA-256 确定性合同；未知 code/默认 legacy 隔离；无真实 callback/Outbox/计费 | 唯一/409/停用引用/Adapter/四层无孤儿/快照不可变/越权/确定性重放/防重/空库升级重放/前端状态 |
| S7 | V88 immutable route version/publish record/runtime state/distribution/instance ack/pause + lease outbox | publish-check/token、幂等 publish/result、versions/distribution/copy/pause；routeCode runtime gate | 发布检查、ACK/DEGRADED/FAILED、版本历史、授权复制、二次认证停流 | task/monitor publish/view/copy/status 分离 + Advertiser∧Channel∧MediaAccount scope | worker 默认关闭；claim/lease/DB 扫描恢复；Redis 仅加速信号；快照验证后 ACK，达标 ACTIVE/跌破退回；地址 overlap/deny | 宏/HTTPS/routeCode、幂等/TOCTOU、不可变、outbox、ACK/分裂、PAUSED/未知拒绝、空库升级重放、前端全状态 |
| S8 | `V89` 冻结维度、贡献/游标、不可变 adjustment、导出任务/审计 | 统一 hourly/options + 普通/审减异步导出 | 五视图 + 固定 taskId 的时/日/链接共享抽屉 | view/export/deduction/sensitive 分离；三对象/Task scope | 聚合/导出 worker 默认关闭；同一函数增量/授权回补 | 空库/升级/重放/反例、零分母、跨视图口径、晚到幂等、越权与导出恢复 |
| S9 | 无新事实；V90 三对象范围查询索引 + 规范权限迁移 | 单 summary/options，内部只用 S8 unified service | 五卡/DAY 趋势/三排行/asOf/freshness | `ocpx:dashboard:view` + 三对象 scope；敏感指标仍分权 | 45 秒有界缓存；user/scope revision/query/metric version/watermark 键；故障实时降级 | 报表对账、零分母、越权、跨用户缓存、水位旁路、局部失败、空库/升级/重放/EXPLAIN |
| S10 | V91 runtime fact/failure/replay request/attempt/audit；旧行 UNMAPPED | overview/facts/clicks/conversions/DLQ/timeline/replay preview-confirm-status；stop 复用 S7 | 运行中心四页签、三个 Task 固定范围抽屉、时间线/预览/排队状态 | view/detail/dlq/replay/emergency-stop 分离；Advertiser∧Channel∧MediaAccount/Task scope；二次认证 | replay worker 字面量默认关闭；DB claim/lease/退避/terminal；默认 processor 不联网 | 不变事实/脱敏/幂等/token 篡改与过期/范围/claim 竞争/空库升级重放/前端全状态 |
| S11 | rta_profile/global_setting/credential_revision/decision_fact/hourly_stat；默认 DISABLED | profile/settings/credential rotate/stats；ACTIVE 固定 409 | 列表、新增/编辑、全局配置、DAY/HOUR 共用六列统计；状态锁定 OFF | view/edit/config/credential/report/debug 分权 + MediaAccount scope | 发布冻结 revision；非 OFF 快照拒绝；未来决策事实聚合 | 原密钥永不回显、SSRF、幂等/并发、四层 OFF、六列与 0 分母、供应商合同/UAT |

## 5. 数据联通验收

### 5.1 配置链

| 编号 | 场景 | 预期 |
|---|---|---|
| `IT-CONFIG-001` | 创建 Advertiser→Product→AppLink，以及 Channel→MediaAccount→MediaCredential，再由 Task 关联 | 每级下拉只见同 scope ACTIVE 对象；Task 冻结一致的账户和 credential revision |
| `IT-CONFIG-002` | Product 改事件默认 | 关联 Task/Route 标待重发，已发布快照不变 |
| `IT-CONFIG-003` | AppLink 改 redirect | 新发布版本才生效，旧在途请求仍用旧版本 |
| `IT-CONFIG-004` | Media credential 轮换 | 新 revision 可发布；旧版本按重叠策略运行/过期 |
| `IT-CONFIG-005` | 归档被活动 Task 引用对象 | 409 + 引用摘要，不发生半归档 |
| `IT-CONFIG-006` | Task 开启曝光监测/大点击、切换 MediaAccount | 条件字段出现并逐项校验；切换账户清空不兼容事件映射；保存只形成草稿 |
| `IT-CONFIG-007` | Task 配置上游事件、渠道事件、比例、限额、超量回传 | 绑定唯一、比例/限额语义明确；发布冻结完全值 |
| `IT-CONFIG-008` | 打开 RTA 新增/编辑/全局配置 | 新增参考态虽为开启，SCX 状态固定 DISABLED；详情/配置只返回 configured/revision，浏览器永不收到原密钥 |
| `IT-CONFIG-009` | 配置 RTA 服务地址、api、全局域名 | 三者独立建模和校验；协议语义未决前不得相互代填或触发外呼 |

### 5.2 发布链

| 编号 | 场景 | 预期 |
|---|---|---|
| `IT-PUBLISH-001` | 完整 Task 发布 | 事务写快照/事件快照/Outbox，version 单调 |
| `IT-PUBLISH-002` | 重复 publishRequestId | 返回同一结果，不多版本 |
| `IT-PUBLISH-003` | Redis 通知失败 | 管理面保留 PUBLISHED/DISTRIBUTING，Outbox 重试 |
| `IT-PUBLISH-004` | 部分实例加载失败 | 显示 DEGRADED/实例版本，不错误显示全量生效 |
| `IT-PUBLISH-005` | DRAFT route 收到请求 | 数据面拒绝，不回读主表拼配置 |
| `IT-PUBLISH-006` | 生成/轮换任务监测地址 | 仅 HTTPS 和宏白名单；routeCode 不可枚举；旧地址按重叠策略停用 |
| `IT-PUBLISH-007` | 地址有未知/重复/缺失宏 | 发布校验逐项 FAIL，不生成承流版本 |
| `IT-PUBLISH-008` | UI 篡改、直接 API 或历史重放尝试发布非 OFF RTA | 管理 API、发布器和数据面全部拒绝；PUBLISHED 快照有效模式仍为 OFF 并告警 |

### 5.3 运行与报表链

| 编号 | 场景 | 预期 |
|---|---|---|
| `IT-RUNTIME-001` | 同 Feedback 重放 | 只一份最终事实/计费/上游业务副作用 |
| `IT-RUNTIME-002` | Callback 重复 | 不重复限额、扣量、下游投递 |
| `IT-RUNTIME-003` | Worker 崩溃 | Pending 可认领，幂等保持 |
| `IT-RUNTIME-004` | 投递到上限 | DLQ 可查；预览后授权重放生成新事实 |
| `IT-RUNTIME-005` | 打开 Task 联调/点击/转化抽屉 | 打开本身无外发；taskId 不可跨 scope；设备/URL/clickId 默认脱敏 |
| `IT-RUNTIME-006` | Callback 命中比例、任务限额、链接限额和审减 | 原始事件不变；采样/两层超量/审减/媒体响应各有独立事实并可追踪 |
| `IT-RUNTIME-007` | Blocked 期间异常注入非 OFF RTA 快照 | 数据面拒绝加载或安全旁路，不向真实 endpoint 发请求，阻断指标递增 |
| `IT-REPORT-001` | 同范围同时间五页分组 | 合计与 TIME 总表一致 |
| `IT-REPORT-002` | Dashboard 指标 | 与 hourly 同口径、asOf 和 scope 一致 |
| `IT-REPORT-003` | 晚到 Callback 回补 | 原小时更新且不重复；freshness/finality 变化 |
| `IT-REPORT-004` | 审减 | 原始事实不变；净值与 adjustment 可对账 |
| `IT-REPORT-005` | 同一 Task 的时报、日报、链接日报 | 字段清单、grain 和 groupBy 正确；共同指标与报表中心完全一致；日报独有注册付费率不被其他视图擅自补列 |
| `IT-REPORT-006` | 普通导出与审减导出 | 独立权限、同 scope/筛选/指标版本；异步任务可审计且过期不可下载 |
| `IT-REPORT-007` | RTA 日报/时报查询 | DAY/HOUR 均固定“日期、名称、rtaId、请求量、成功量、成功率”六列；响应携带 grain/bucket/timezone，0 分母返回 null/-- |

## 6. 权限验收矩阵

| Case | 菜单 | 列表/下拉 | 详情 | 写入 | 导出/正文 |
|---|---|---|---|---|---|
| 无功能权限 | 不下发 | 403 | 403 | 403 | 403 |
| 有功能、无对象范围 | 可按产品策略显示或提示空权限 | 空/403 且不泄露总数 | 泛化拒绝 | 拒绝 | 拒绝 |
| 单 Advertiser VIEW | 可见只读菜单 | 仅该范围 | 仅该范围 | 无 edit 拒绝 | 依独立权限 |
| OPERATE 无 publish | 可编辑 Task 草稿 | 范围内 | 范围内 | CRUD 允许 | publish 拒绝 |
| Report export | 报表可见 | 范围内 | 范围内 | 无业务写 | 导出按同 scope |
| Runtime replay | 运行菜单 | 事实范围内 | 正文仍需独立权 | 只可命令式补偿 | 完整审计 |

所有越权测试都覆盖“猜 ID、修改 body 关联 ID、导出、总数/汇总、缓存命中、历史 URL、并发权限变化”。

## 7. 迁移与回滚门禁

| Gate | 必须满足 |
|---|---|
| `DB-G0` | 新前向 SQL 不修改已执行迁移；current schema/required tables/verify 同步 |
| `DB-G1` | 新 FK/逻辑关联先可空回填；孤儿/跨广告主/重复报告为 0 |
| `DB-G2` | 双写与对账有指标；收紧非空前完成目标库预览 |
| `DB-G3` | 应用 N/N-1 兼容；RouteSnapshot 有 schemaVersion |
| `DB-G4` | 回滚不删新表/版本；通过 feature flag/旧读路径/新版本回切 |
| `DB-G5` | 目标数据库应用前备份、预览、明确授权；本地门禁不代表生产完成 |

## 8. 质量门禁

按实际变更范围运行：

- 后端：相关模块单元/契约测试，最终 `make backend-test`；
- 前端：目标测试、`pnpm check:type`、`pnpm lint`、`pnpm test:unit`、生产构建；
- 契约：正式 OpenAPI/Schema 变更后 `make contract-check`；
- 数据库：schema/migration contract、空库和升级副本校验；
- 安全：gitleaks/Secret 扫描、SSRF、对象越权和 PII 日志测试；
- 运行：Redis pending 恢复、Outbox、DLQ、重复消息、实例版本分裂；
- 报表：事实→小时→页面/导出/Dashboard 自动对账。

已知仓库级基线错误与本切片引入错误分开报告，不能用前者掩盖后者，也不能因局部通过声称全仓或生产完成。

## 9. Blocked 决策表

| 模块 | 缺失决策/证据 | 解除人/产物 | 未解除前强制行为 |
|---|---|---|---|
| RTA | 服务地址/api/全局域名语义、成功口径、协议、失败模式、容量、隐私、费用、密钥生命周期、UAT、替代 ADR；[ADR-003](../adr/ADR-003-RTA主动决策解锁门禁.md) 仍为 Proposed | 架构/产品/安全/供应商/运维共同签署并形成 Accepted ADR | 参考新增默认开启不得照搬；Secret 只写不回显；UI 锁定、API 拒绝 ACTIVE、发布/数据面强制 OFF、连通检查不外呼 |
| DP | 淘宝/京东正式协议、授权、SSRF、保留、幂等、对账；[RFC-001](../adr/RFC-001-DP供应商协议与安全门禁.md) 仍为 Proposed | 产品/安全/架构/供应商/运维共同签署并形成 Accepted RFC 或 ADR | 无生产菜单、无外呼、convert 返回 Blocked |
| Event 本地在研 | 迁移/接口/快照评审、稳定主线状态 | 当前工作负责人 + 合并证据 | 只标本地在研，后续模块不可假定稳定 |

## 10. 交付状态模板

每个 Slice 交接时填写：

```text
Slice:
Source/branch/commit:
Migration state (source / local DB / target DB):
API contract state:
Frontend state:
Permission/object-scope evidence:
Unit/contract/integration/build results:
Runtime/UAT/production evidence:
Known baseline failures:
Open blockers:
Go/No-Go:
```

页面级验收入口回链到 [90-页面按钮弹窗覆盖矩阵](90-页面按钮弹窗覆盖矩阵.md)，领域不变量回链到 [00-总体领域模型与跨模块数据流](00-总体领域模型与跨模块数据流.md)。

## 11. S8 交付状态（2026-08-10）

```text
Slice: S8 统一小时事实与报表
Source/branch/commit: 当前隔离 worktree；累计 S0-S8 均未暂存、未提交、未推送
Migration state: V89/current schema/verifier/required tables/contract 已同步；仅隔离 MySQL 8.4 验证，目标未应用
API contract state: unified hourly/options、普通/审减异步导出、任务状态/取消/下载已实现
Frontend state: TIME/PRODUCT/LINK/TASK/MEDIA 五视图与 Task 三类报表抽屉已实现；S9/S10 由后续累计切片独立实现
Permission/object-scope evidence: view/export/deduction/sensitive 分权；Advertiser∧Channel∧MediaAccount/Task 在查询、候选、总数、汇总、导出和下载重新求交
Runtime/UAT/production evidence: worker 默认关闭；无目标 Redis/MQ/存储、无真实事件/回补/导出/下载；第三方 UAT 与生产均未验收
Open blockers: 目标 V89 预览/备份/应用、冻结维度双写质量、历史 UNMAPPED 人工治理、真实数据对账/性能、多角色浏览器和对象存储 UAT
Go/No-Go: Source/Local Gate only；Target/UAT/Production No-Go
```

本地夹具聚合、mock storage 边界和受控 EXPLAIN 只能证明源码合同与局部门禁，不能称为目标报表对账、真实导出或实例生效。

本地门禁结果：`make backend-test` 通过（OCPX 110、OCPX runtime 75、boot/迁移合同 27；engine 3 中 1 项外部 MySQL 并发验收按既有条件跳过）；前端 `check:type`、lint（0 error，4 个既有 warning）、323 项单测和 Element Plus 生产构建通过；`contract-check`、Java 定向格式、`git diff --check`、运行配置占位/敏感 DTO/网络副作用扫描均通过。隔离 MySQL 8.4 的空库、pre-S8 升级、V89 重放、约束反例、完整聚合 SQL、导出幂等键、verifier 与受控 EXPLAIN 均通过；测试容器完成后清理。V81–V88 哈希与 S8 开始前记录一致。

## 12. S9 交付状态（2026-08-10）

```text
Slice: S9 Dashboard 经营总览
Source/branch/commit: 当前 detached HEAD 隔离 worktree；累计 S0-S9 未暂存、未提交、未推送
Migration state: V90/current schema/verifier/contract 已同步；不新增 Dashboard 事实，只补三对象范围索引和 dashboard:view 权限迁移；目标未应用
API contract state: /ocpx/dashboard/summary|options；卡片、DAY 趋势、Product/Task/Media 排行均复用 S8 unified service 与 S8_V1 字典
Frontend state: Element Plus 五卡/趋势/三排行/筛选/刷新；Skeleton、两类空、全局/局部错、403、三类 freshness、小屏与横向滚动已实现；S10 由独立运行中心切片实现，不改 Dashboard 合同
Permission/object-scope evidence: dashboard:view 与 report:view 分离；一次请求冻结 Advertiser∧Channel∧MediaAccount scope；options/summary/ranking/cache 同 snapshot/revision
Runtime/UAT/production evidence: 仅隔离 MySQL、mock/fake 与源码门禁；无目标 Redis/服务/事实、无第三方调用、真实事件/回补/导出/下载/计费；UAT/生产未验收
Open blockers: 目标 V90 备份/预览/应用、真实数据与五页对账、目标规模 P95/缓存命中、多角色浏览器/小屏/可访问性 UAT、历史 UNMAPPED 质量
Go/No-Go: Source/Local Gate only；Target/UAT/Production No-Go
```

隔离 MySQL 8.4 已验证 current schema 空库、pre-S9 旧权限前向迁移、V90 重放、无 Dashboard 专用事实表、对象范围反例和合成 5,003 行事实的受控 EXPLAIN；新索引从全表扫描收敛到 `idx_scx_ocpx_dashboard_scope` 范围扫描。失活的旧 Dashboard Service/Mapper 独立 SQL 与公式测试已删除，主链只保留 S8 统一查询。Dashboard/兼容报表定向后端 10 项和前端模型/API 18 项通过；全量 `make backend-test` 通过（OCPX 112、OCPX runtime 75、boot/迁移合同 29，engine 3 项中 1 项既有外部 MySQL 条件跳过），前端 type、lint（0 error、4 个既有 warning）、323 项单测和 Element Plus 生产构建通过。`contract-check`、Java 定向格式、配置占位/敏感 DTO/网络副作用扫描与 `git diff --check` 通过；V81–V89 哈希保持不变，staged=0。本地夹具、进程内 fake cache 和 EXPLAIN 不代表目标经营数据、线上缓存或生产性能。

## 13. S10 交付状态（2026-08-10）

```text
Slice: S10 运行中心与失败补偿
Source/branch/commit: 当前 detached HEAD 隔离 worktree；累计 S0-S10 未暂存、未提交、未推送
Migration state: V91/current schema/required tables/verifier/contract 已同步；目标数据库未应用
API contract state: runtime overview/facts/clicks/conversions/DLQ/timeline、固定 Task integration facts、replay preview/confirm/status；emergency stop 复用 S7
Frontend state: Element Plus 运行中心四页签 + attempt timeline + replay preview/confirm；Task 行联调/点击/转化三类只读抽屉
Permission/object-scope evidence: canonical runtime 权限分离；列表/total/详情/options/command 全部按 Advertiser∧Channel∧MediaAccount/Task scope 重新求交
Worker state: replay worker 字面量默认关闭，默认 processor unavailable；HTTP confirm 只排队，不直接外发
Privacy state: DTO/log 只含掩码/摘要/hash；无原始 credential/header/callback body/完整手机号/IP/deviceId/签名/第三方敏感响应
Runtime/UAT/production evidence: 仅隔离 MySQL、fake processor/fake secondary auth/源码门禁；无真实 replay/stop/callback/计费/第三方访问
Open blockers: 目标 V91 备份/预览/应用、历史 UNMAPPED 质量、真实实例恢复与规模性能、多角色浏览器、协议/隐私批准后的正文/导出策略、第三方 UAT
Go/No-Go: Source/Local Gate only；Target/UAT/Production No-Go
```

隔离 MySQL 8.4 的 current 空库、pre-S10 升级、V91 重放、撤权后误重放不回授、不变/唯一/冻结维度反例、claim 竞争与受控 EXPLAIN 已验证；一次性容器在门禁结束后清理。`make backend-test` 通过（OCPX 119、OCPX runtime 76、boot/迁移合同 31；engine 3 项中 1 项既有外部 MySQL 条件验收跳过）；前端 `check:type`、lint（0 error，4 个累计基线 warning）、326 项单测和 Element Plus 生产构建通过。契约检查、S10 Java 定向格式、配置占位/敏感响应/网络客户端/双停流真相扫描及 `git diff --check` 通过。fake processor、夹具 DLQ、worker 关闭和本地 EXPLAIN 不能称为真实补偿、目标性能或实例生效。

## 14. S11/S12 fail-closed 审计状态（2026-08-10）

```text
Slice: S11/S12 最终阻塞与 fail-closed 审计
Source/branch/commit: 当前 detached HEAD 隔离 worktree；累计 S0-S10 受保护，新增变更仍未暂存、未提交、未推送
Migration state: 无新持久真相，无 V92；V81-V91 未修改
RTA state: Task/兼容 Monitor API 仅 OFF；发布检查/冻结/DB 约束强制 OFF；RouteSnapshot 构造/反序列化拒绝非 OFF；DisabledRtaDecisionPort 无网络依赖
DP state: 无菜单、前端路由、DTO、Service、Adapter、表或网络客户端；/ocpx/tools/dp/convert 仅返回 409 Blocked
Secret state: 当前没有 RTA Profile/全局设置 API 或 Secret 读取面；未新增 Secret 字段、日志或连接检查
Cross-boundary audit: Accepted ADR-002 自定义 HTTPS 调试是独立既有能力，不构成 DP 实现；供应商域名确定后仍需产品/架构/安全决定其对 DP 域名的隔离策略，未决前不得用于 DP 转换/UAT
Runtime/UAT/production evidence: 仅源码扫描与本地测试；无真实 RTA/DP 请求、供应商 UAT、目标服务或生产验收
Go/No-Go: S11/S12 继续 Blocked；Target/UAT/Production No-Go
```

本次最小修复只封闭异常非 OFF 快照和提供无载荷、无网络的 DP Blocked 响应，没有创建 RTA ACTIVE/profile/stats、DP Adapter/UI/菜单或任何外联能力。解除 S11 仍需架构/产品/安全接受替代 ADR 并完成供应商 UAT；解除 S12 仍需产品/安全接受供应商 RFC/ADR，并由架构/安全明确 ADR-002 调试工作台对未来 DP 供应商域名的隔离规则。

第二次 Blocked 审计新增 [ADR-003 RTA 主动决策解锁门禁](../adr/ADR-003-RTA主动决策解锁门禁.md) 与 [RFC-001 DP 供应商协议与安全门禁](../adr/RFC-001-DP供应商协议与安全门禁.md) 两份 **Proposed / Not Accepted** 决策准备文件。两份文件都不改变当前源码、迁移、菜单、路由或网络边界；在责任人完成全部 `TBD / Decision required`、独立评审将状态改为 Accepted 并另行取得实现/UAT/生产授权前，S11/S12 继续 Blocked，Target/UAT/Production No-Go。
