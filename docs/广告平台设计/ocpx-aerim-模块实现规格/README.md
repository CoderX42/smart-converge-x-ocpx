# OCPX 参考系统到 SCX 的模块化实现规格

> 状态：Draft / Review
> 观察日期：2026-08-09（Asia/Shanghai）
> 参考目标：`https://ocpx.aerim.cn`
> 账号范围：本轮授权账号当前可见菜单；凭证、人员与业务数据均不入库、不入图
> 交付性质：现场观察 + SCX 实现草案；不覆盖 OCPX 基线、Accepted ADR 或正式契约

## 1. 用途与边界

本包将参考系统的页面行为转换为可在 SCX 中评审、拆分和验收的模块实现规格，重点回答四个问题：

1. 页面上有哪些布局、字段、按钮、弹层、状态与条件联动；
2. 每个业务对象由谁拥有，如何与上下游对象联通；
3. SCX 当前能力可以复用什么、缺什么、与目标设计冲突在哪里；
4. 一个模块如何从数据库、接口、前端、权限、异步链路一直验收到可交付。

本包不声称能够从浏览器证明参考系统的接口、数据库、消息队列、算法或部署实现。所有保存、禁用、重置密码、导出、退出登录、DP 转换以及真实外发动作均未执行；对应行为只能标为界面意图或待验证。

## 2. 证据标签

| 标签 | 含义 | 可用于何种结论 |
|---|---|---|
| `现场实测` | 2026-08-09 当前浏览器会话中实际导航、展开或切换 | 页面存在、可见字段、只读交互与条件显隐 |
| `界面文案推断` | 根据按钮、提示或字段名称推断 | 预期用途；不能证明提交后的服务端流程 |
| `SCX仓库事实` | 当前受版本控制的 SCX 源码、契约或 Accepted ADR | 当前可复用能力与约束 |
| `SCX当前工作树-未提交` | 当前用户工作树中的本地在研内容 | 只能说明本地现状，不能视为稳定主线能力 |
| `SCX目标设计` | 本包提出、待评审的目标方案 | 后续 RFC、ADR、契约和迭代输入 |
| `未验证` | 因副作用、权限、空数据或安全边界未执行 | 实施前必须补证据或明确决策 |

结论冲突时，按“Accepted ADR / 正式契约 → 当前稳定源码 → 本地未提交源码 → 目标设计 → 参考界面推断”的顺序处理。参考页面不会反向覆盖 SCX 已接受的架构决定。

## 3. 文档导航

| 文档 | 范围 | 状态 |
|---|---|---|
| [00-总体领域模型与跨模块数据流](00-总体领域模型与跨模块数据流.md) | 术语、实体关系、所有权、配置发布、运行链路、指标血缘 | Review |
| [01-登录与全局框架](01-登录与全局框架.md) | 登录、会话、侧栏、面包屑、账号菜单、通用状态 | Draft |
| [02-首页仪表盘](02-首页仪表盘.md) | 指标卡、趋势图、统一报表投影 | Draft |
| [03-上游广告主与接入资料](03-上游广告主与接入资料.md) | 广告主、适配器、接入文档与密钥 | Draft |
| [04-产品管理](04-产品管理.md) | 产品主数据、产品事件策略、归档 | Draft |
| [05-链接管理](05-链接管理.md) | AppLink、类型、跳转、下载地址、事件映射 | Draft |
| [06-媒体渠道与媒体账户](06-媒体渠道与媒体账户.md) | Channel、Adapter、MediaAccount、外部 MediaCredential 与内部负责人 | Draft |
| [07-任务与监测路由发布](07-任务与监测路由发布.md) | Task、MonitorRoute、草稿校验与发布快照 | Draft |
| [08-事件字典与事件映射](08-事件字典与事件映射.md) | 事件定义、渠道映射、任务绑定、发布快照 | Draft / 本地在研依赖 |
| [10-报表中心](10-报表中心.md) | 五类汇总、统一粒度、指标口径、导出 | Draft |
| [11-用户权限与个人中心](11-用户权限与个人中心.md) | `scx-system` 账号、RBAC、对象数据范围 | Draft |
| [12-DP转换工具](12-DP转换工具.md) | 淘宝/京东转换适配器与 SSRF 边界 | Blocked |
| [13-运行中心与失败补偿](13-运行中心与失败补偿.md) | 接入、队列、投递、DLQ、重放与可观测 | Draft |
| [90-页面按钮弹窗覆盖矩阵](90-页面按钮弹窗覆盖矩阵.md) | 页面、控件、弹层、截图、模块双向追踪 | Review |
| [91-SCX实施依赖与验收矩阵](91-SCX实施依赖与验收矩阵.md) | 纵向切片、依赖、权限、迁移、门禁 | Review |

## 4. 模块状态与边界

| 模块 | 参考面 | SCX 可复用 | 主要缺口 | 目标状态 |
|---|---|---|---|---|
| 登录/全局框架 | 登录、菜单、账号菜单、分页 | `scx-auth`、`scx-system`、服务端菜单路由 | OCPX 对象数据范围与页面级异常态统一 | 可实施 |
| 仪表盘 | 5 个指标卡、趋势 | 统一小时事实与报表服务 | 目标指标口径、同比基准、权限过滤 | 报表后实施 |
| 广告主 | 由链接/任务筛选间接可见 | `OcpxAdvertiser`、适配器、密钥治理 | 接入资料对象存储引用和能力声明 | 可实施 |
| 产品 | 产品列表、抽屉、事件限量 | 无独立产品聚合 | Product 表、归属、版本、事件策略 | 优先实施 |
| 链接 | 链接列表与条件表单 | `OcpxAppLink` | `productId`、下载/跳转/归档结构化 | 产品后实施 |
| 媒体 | 媒体渠道、渠道用户邮箱/密码、账户数量 | `OcpxChannel`、Adapter、`sys_user` | `MediaAccount`、外部 `MediaCredential`、内部负责人/对象范围分层 | 产品并行实施 |
| 任务/路由 | 两行筛选、点击/曝光/去重、事件比例/限额/超量、监测地址、联调/点击/转化、三类报表入口 | `OcpxMonitorLink`、发布服务、Outbox | `Task` 运营聚合、宏契约、冻结依赖版本、保存与发布分离 | 链接/媒体后实施 |
| 事件 | 产品/链接事件映射 | 当前工作树存在事件定义/映射/快照在研代码 | 主线评审、契约稳定、迁移确认 | 先收口现有在研工作 |
| 报表 | 数据/产品/链接/任务/媒体汇总 | 小时事实、报表接口 | 新维度、统一分组、口径字典 | 路由链路后实施 |
| 用户权限 | 用户、重置、禁用、个人中心 | `scx-system` / Sa-Token | OCPX 对象范围关联 | 可与主数据并行 |
| DP | 淘宝/京东页签 | 无正式产品边界 | 协议、所有权、隐私、SSRF、幂等 RFC | Blocked |
| 运行中心 | 运行审计、DLQ、调试工作台 | 独立 OCPX 数据面、ADR-001/002 | 新维度观测与安全补偿操作 | 随路由扩展 |

## 5. 推荐实施顺序

```text
身份/RBAC/对象范围 + 术语映射
                ↓
       Advertiser / Product / MediaAccount
                ↓
              AppLink
                ↓
        Task + MonitorRoute 发布
                ↓
       事件映射 + 发布快照收口
                ↓
    运行链路维度补齐 + 统一小时事实
                ↓
          Reports → Dashboard

DP：供应商协议与安全 RFC 接受后独立实施
```

每个纵向切片必须同时包含：前向迁移、后端接口、前端页面、权限与对象范围、契约/单元/集成测试、失败补偿与证据。不得以“页面已显示”代替业务闭环验收。

## 6. 通用实现约定

- 前端：新业务页放在 `scx-fronted/apps/web-ele/src/views/ocpx/`；继续使用服务端菜单与权限驱动路由。API 按 `product/link/media/task/report/dp` 拆分，同时保留现有 `ocpx.ts` 兼容导出。
- 后端：管理面归 `scx-ocpx-admin`，身份与菜单归 `scx-auth/scx-system`；高并发机器数据面当前未实现，重建时不得按页面新建 Maven 模块。
- API：管理端统一 `Result<T>`，列表统一 `page/current/size/total/records`；创建接口支持 `Idempotency-Key`，更新接口携带 `version` 或 `If-Match`；冲突返回 `409`。
- 数据：MySQL 是主数据、版本、Outbox 和最终事实的记录系统；Redis 仅作可靠队列、幂等、短期计数、缓存和配置失效通知。
- 发布：运行依赖必须经过 `DRAFT → 校验 → 不可变快照 → PUBLISHED → Outbox → 数据面热更新`；未发布配置不得承载流量。
- 删除：有下游引用的主数据禁止物理删除；优先 `ARCHIVED/DISABLED`。数据库变更只允许新增前向迁移并同步当前态 schema 与验证脚本。
- 安全：密码、Secret、Token 只写不回显；URL 做 scheme/host/IP/重定向链校验；设备标识、手机号、邮箱、客户名称、业务链接和报表样例不得进入截图或普通日志。
- 时间与比率：统一 `Asia/Shanghai`；日期区间明确定义闭区间/半开区间；分母为 0 时返回 `null`，前端显示 `--`，不得显示误导性的 `0%`。

## 7. 脱敏证据目录

截图仅用于证明布局与字段，不包含账号、密码、手机号、邮箱、客户名称、业务链接、密钥、ID 或报表实时数值。

| 证据 | 内容 |
|---|---|
| [SHOT-GLOBAL-01](assets/2026-08-09/SHOT-GLOBAL-01-sidebar-redacted.png) | 全局侧栏与菜单分组 |
| [SHOT-PRODUCT-01](assets/2026-08-09/SHOT-PRODUCT-01-nav-selected.png) | 产品管理菜单选中态；抽屉字段以现场记录为证 |
| [SHOT-PRODUCT-02](assets/2026-08-09/SHOT-PRODUCT-02-add-drawer-sanitized.png) | 用户提供的新增产品抽屉经 AI 辅助脱敏裁切；不含账号和业务列表 |
| [SHOT-PRODUCT-03](assets/2026-08-09/SHOT-PRODUCT-03-event-options.png) | 用户提供的事件下拉、多行映射、限量与删除图标安全裁图 |
| [SHOT-LINK-01](assets/2026-08-09/SHOT-LINK-01-add-drawer-upper-sanitized.png) | 用户提供的新增链接抽屉上半段经 AI 辅助脱敏裁切；证明三项必填归属、类型和重定向字段 |
| [SHOT-LINK-02](assets/2026-08-09/SHOT-LINK-02-add-drawer-lower-sanitized.png) | 新增链接抽屉下半段脱敏裁切；证明链接地址为必填粘贴字段及归档默认态 |
| [SHOT-LINK-03](assets/2026-08-09/SHOT-LINK-03-edit-event-section-sanitized.png) | 编辑链接下半段脱敏裁切；链接内容已遮盖，证明编辑态事件/单价/限量区域 |
| [SHOT-MEDIA-01](assets/2026-08-09/SHOT-MEDIA-01-nav-selected.png) | 媒体管理菜单选中态；抽屉字段以现场记录为证 |
| [SHOT-MEDIA-02](assets/2026-08-09/SHOT-MEDIA-02-list-sanitized.png) | 媒体列表经 AI 辅助整行遮盖；保留筛选、表头、渠道用户加号和“点击添加用户”提示 |
| [SHOT-MEDIA-03](assets/2026-08-09/SHOT-MEDIA-03-add-drawer-sanitized.png) | 新增媒体渠道抽屉脱敏裁切；证明媒体文档/名称必填和 Channel 未标星 |
| [SHOT-MEDIA-04](assets/2026-08-09/SHOT-MEDIA-04-edit-drawer-sanitized.png) | 编辑媒体渠道抽屉脱敏裁切；三个业务值均替换为“已脱敏” |
| [SHOT-MEDIA-05](assets/2026-08-09/SHOT-MEDIA-05-channel-user-add-sanitized.png) | 新增渠道用户抽屉脱敏裁切；父媒体名已替换，证明邮箱/密码必填与描述字段 |
| [SHOT-TASK-01](assets/2026-08-09/SHOT-TASK-01-list-sanitized.png) | 任务列表 AI 辅助脱敏；保留两行筛选、表头、监测地址复制区和七个行动作 |
| [SHOT-TASK-02](assets/2026-08-09/SHOT-TASK-02-add-drawer-upper-sanitized.png) | 新增任务上半段脱敏裁切；证明任务名/运营、点击链接、曝光、媒体与去重字段 |
| [SHOT-TASK-03](assets/2026-08-09/SHOT-TASK-03-exposure-event-mapping-sanitized.png) | 曝光与大点击条件区、媒体选择和事件映射行脱敏证据 |
| [SHOT-TASK-04](assets/2026-08-09/SHOT-TASK-04-event-status-lower-sanitized.png) | 事件比例/限量/超量、描述、归档、状态、确定/取消的下半段证据 |
| [SHOT-TASK-05](assets/2026-08-09/SHOT-TASK-05-hourly-summary-sanitized.png) | 小时汇总抽屉脱敏证据；保留日期、双导出、列设置和小时指标表结构 |
| [SHOT-TASK-06](assets/2026-08-09/SHOT-TASK-06-daily-summary-sanitized.png) | 日汇总抽屉脱敏证据；保留默认仅看有点击、双导出、指标表和分页 |
| [SHOT-TASK-07](assets/2026-08-09/SHOT-TASK-07-link-summary-sanitized.png) | 链接汇总抽屉脱敏证据；保留链接/日期/dataView、双导出和分页 |
| [SHOT-TASK-08](assets/2026-08-09/SHOT-TASK-08-integration-detail-sanitized.png) | 媒体联调详情脱敏证据；产品/包名/下载地址均已替换，保留复制图标和空态 |
| [SHOT-TASK-09](assets/2026-08-09/SHOT-TASK-09-click-detail-sanitized.png) | 点击详情抽屉脱敏证据；第三方浮层已移除，保留筛选、表头、空态和分页 |
| [SHOT-TASK-10](assets/2026-08-09/SHOT-TASK-10-conversion-detail-sanitized.png) | 转化详情抽屉脱敏证据；数据行已遮盖，保留筛选、导出、表头和分页 |
| [SHOT-REPORT-01](assets/2026-08-09/SHOT-REPORT-01-task-summary-filter-table.png) | 任务汇总筛选与双导出按钮 |
| [SHOT-USER-01](assets/2026-08-09/SHOT-USER-01-nav-selected.png) | 用户管理菜单选中态 |
| [SHOT-SETTING-01](assets/2026-08-09/SHOT-SETTING-01-page-header.png) | 用户中心页面标题区域 |
| [SHOT-DP-01](assets/2026-08-09/SHOT-DP-01-taobao-tab.png) | 淘宝转换页签 |
| [SHOT-DP-02](assets/2026-08-09/SHOT-DP-02-jd-tab.png) | 京东转换页签 |

完整列表、编辑抽屉和统计页可能同时出现账号、客户名、业务链接或实时指标，因此未把无法可靠裁净的原图保存到 Git；只保留安全原始裁图或明确标注的 AI 辅助脱敏裁切，相应字段同时以现场文本记录复核。

## 8. 评审门禁

- 任一“已实现”结论必须给出代码、迁移、测试和运行证据，不能引用本包自行证明。
- DP 开工前必须有供应商协议、域名白名单、数据保留和审计 RFC/ADR。
- 本包若与 Accepted ADR 或正式契约冲突，先更新本包状态为 Blocked，不能绕过决策直接编码。
