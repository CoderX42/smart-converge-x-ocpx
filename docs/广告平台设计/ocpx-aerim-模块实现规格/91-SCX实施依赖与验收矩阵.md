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
| `S0` | 术语、权限与对象范围基线 | Accepted ADR-001/002 | scope 表/API、权限码、服务端过滤、菜单约定 | 范围越权测试、缓存失效、菜单/按钮/API 一致 | Ready for design |
| `S1` | Advertiser 接入资料收口 | S0 | 复用 advertiser，资料版本/凭据 revision | Secret 不回显、资料扫描/权限、依赖路由待重发 | Ready for design |
| `S2` | Product 主数据 | S0/S1 | Product 表/API/UI/事件默认策略 | CRUD、唯一、归档引用、范围、Logo | Ready for implementation after review |
| `S3` | MediaAccount / MediaCredential | S0 | Channel 分层、业务账户、外部凭据 revision、内部负责人 | 账户 CRUD、Secret 只写、owner 分权、连接检查边界 | Ready for implementation after review |
| `S4` | AppLink 结构化 | S1/S2 | productId、类型、redirect/download、归档 | URL 安全、级联、存量回填、路由待重发 | Depends S2 |
| `S5` | Task 运营聚合 | S2/S3/S4 | Task 表/API/UI、运营、点击/曝光 AppLink、MediaAccount、去重、状态与 Monitor 关联 | CRUD/级联/条件显隐/保存不发布/RTA OFF/旧 Monitor 映射 | Depends S2–S4 |
| `S6` | Event 模型收口 | S5 + 当前本地在研评审 | 定义/映射/绑定；比例、限额、超量；快照契约化 | 迁移/契约/确定性采样/发布/Callback 集成 | In progress locally, not stable |
| `S7` | Monitor 发布与运行维度 | S5/S6 | 宏字典、HTTPS 监测入口、新维度、快照、Outbox、实例分发 | URL 宏验证、发布幂等、故障恢复、DRAFT 不承流、版本追踪 | Depends S5/S6 |
| `S8` | 统一小时事实与报表 | S7 | 事实扩维、groupBy API、五页 + Task 时/日/链接抽屉、双导出 | 跨视图/Dashboard 对账、回补、字段差异、权限/性能 | Depends S7 |
| `S9` | Dashboard | S8 | 指标卡/趋势/asOf | 与报表一致、局部失败、缓存范围 | Depends S8 |
| `S10` | 运行中心补偿体验 | S7 | Task 联调/点击/转化抽屉、事实图、DLQ、预览/重放/停流 | 明细血缘/脱敏/不变事实、命令幂等、二次授权、演练 | Can overlap S8 |
| `S11` | RTA 主动决策 | 替代 ADR + S7 | Profile/全局设置/credential revision/事实/决策/六列 DAY/HOUR 统计；区分服务地址、api 与全局域名 | 新增默认强制关、Secret 全链路不回显、四层 OFF 门禁、shadow/容量/故障模式/供应商 UAT | **Blocked** |
| `S12` | DP 转换 | 供应商 RFC/ADR | Adapter/SSRF/幂等/UI | 安全测试、正式协议、隐私/TTL/UAT | **Blocked** |

S2 与 S3 可并行，但共享的 scope、审计、存储和错误约定必须先由 S0/S1 固定。S8 前必须先保证运行事实具备完整维度，否则报表页面只会掩盖数据缺口。

## 3. SCX 模块落点

| 能力 | 后端所有权 | 前端路径 | 禁止做法 |
|---|---|---|---|
| 用户/角色/菜单/密码 | `scx-auth/scx-system` | `views/system` / 现有 auth | 新建 ADX 用户/密码表 |
| 主数据/Task/发布管理 | `scx-adx` | `views/adx/*` | 每个页面建 Maven 模块 |
| 机器契约 | `scx-ocpx-contract` | 无直接 UI | 浏览器持有签名 Secret |
| 高并发接入/投递 | `scx-ocpx-runtime/engine` | 运行中心只查管理 API | 放回 `scx-boot` 管理线程池 |
| Dashboard/Reports | `scx-adx` 统一查询服务 | `views/adx/dashboard/report` | 五页各写一套口径 |
| RTA 管理/统计 | 管理元数据归 `scx-adx`，未来机器决策归 `scx-ocpx-runtime/engine` | `views/adx/rta` | 在 `scx-boot` 外呼、浏览器持有/回读 Secret、把参考默认开启照搬为目标默认 |
| 调试/补偿 | ADR-002 工作台 + runtime | `views/adx/runtime` | 任意代理/复制投放路径 |

## 4. 切片级交付矩阵

| Slice | Migration/Data | API/Contract | UI | Permission/Scope | Async/Failure | Tests |
|---|---|---|---|---|---|---|
| S0 | `scx_adx_user_scope`、唯一/有效期 | scope 整体读写/impact | 用户 ADX scope 页签 | function + object intersection | 缓存失效重试 | 越权 ID、角色变化、缓存旧值 |
| S1 | document/version、credential revision | advertiser 扩展/rotate/docs | 广告主/资料抽屉 | credential/document 独立权 | 上传清理、轮换重叠 | Secret/URL/文件/引用 |
| S2 | product/policy | `/adx/products` | Product 表/抽屉 | advertiser scope | Logo 临时清理 | CRUD/唯一/归档/事件行 |
| S3 | media_account/credential/owner | `/adx/media-accounts` + credentials/owners | 媒体账户、渠道用户凭据、内部负责人 | Channel/Account scope + credential 独立权 | Secret revision/连接检查 worker | 凭据不回显、owner 分权、Adapter、状态 |
| S4 | app_link 新列/回填 | 扩展 `/adx/app-links` | 条件链接表单 | product/advertiser scope | URL validation | redirect 矩阵、SSRF、归档 |
| S5 | task + monitor FK/索引 | `/adx/tasks` | Task 两行筛选、条件抽屉 | 多对象交集 | 保存不发布 | 级联、曝光/大点击、去重、版本、状态、RTA OFF |
| S6 | event 四层表/快照 | events/mappings/bindings | 字典/上游↔渠道事件/比例/限额/超量 | Channel/Task scope | 确定性采样/限额/隔离/重放 | callback/映射/快照/防重 |
| S7 | route version/outbox 新维度 | monitor publish/distribution + macro contract | 发布检查/地址复制/版本状态 | publish/copy/status 分离 | Outbox/热更/地址轮换/紧急停流 | 宏白名单、幂等、崩溃、版本分裂 |
| S8 | hourly fact 新粒度/索引 | unified hourly/export | 五视图 + Task 时/日/链接共享组件 | 查询/普通导出/审减导出/敏感列 | 聚合回补/导出 job | 字段清单/口径/性能/晚到/权限 |
| S9 | 无新事实 | summary + hourly | 卡/趋势/asOf | dashboard + scope | cache invalidation | 报表一致/局部失败 |
| S10 | replay command/关联维度 | integration/click/conversion/facts/DLQ/replay/stop | 三个 Task 明细抽屉 + 时间线/预览/命令 | 正文/复制/导出/高风险操作独立授权 | DLQ/重放/停流 | 明细血缘/不变事实/二次认证/演练 |
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
| RTA | 服务地址/api/全局域名语义、成功口径、协议、失败模式、容量、隐私、费用、密钥生命周期、UAT、替代 ADR | 架构/产品/安全 Accepted ADR | 参考新增默认开启不得照搬；Secret 只写不回显；UI 锁定、API 拒绝 ACTIVE、发布/数据面强制 OFF、连通检查不外呼 |
| DP | 淘宝/京东正式协议、授权、SSRF、保留、幂等、对账 | 产品/安全 RFC 或 ADR | 无生产菜单、无外呼、convert 返回 Blocked |
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
