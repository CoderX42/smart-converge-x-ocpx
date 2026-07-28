# SCX OCPX Codex 总 Task 清单

> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`
>
> 更新日期：2026-07-28
>
> 用途：给 Codex 按依赖顺序领取和实现项目 Task。每个 Task 必须单独形成合同、代码、测试、运行证据和文档交接。

## 1. 当前执行位置

Claude/Codex 已完成 R1-00 本地代码切片及生产 V43–V47/候选发布，但还没有完成生产四角色、
凭据轮换、消息恢复和第三方验收。当前不要直接进入
`ARCH-01` 或真实 `BASE-04`。

最近一次状态：

- V48 媒体与对象 Scope 管理切片已完成代码、契约、前端和隔离测试；目标环境迁移与四角色生产验收仍待授权窗口。

- P0 本地可信边界已收口：安全拒绝审计单链路、prod 必填配置早期校验、敏感详情掩码、FIX-2/FIX-3
  均已完成定向验证。
- `make prod-secret-injection-check`、契约校验、相关 Maven 测试和前端类型检查已通过。
- 2026-07-28 已在确认后的生产机 `42.81.102.181` 完成备份、V44–V47、配置环境变量化、
  `fac5ba4` JAR/前端发布和健康检查；V43 已存在且校验和一致。
- 生产只有管理员账号，四角色浏览器/API/`sys_log` 矩阵仍缺运营、分析员和无范围账号。
- 生产没有可用 RocketMQ；两条既有 Outbox 保持 `RETRYING/attempts=1`，发布器已关闭以防
  继续重试。历史凭据轮换、真实第三方、故障恢复与 20k/40k 容量门禁尚未签收。
- 当前工作树存在历史 JAR、测试报告、日志、`.turbo` 和前端构建产物；均只标记为生成物，本次不删除，
  也不能混入实现证据。
- `scx-commercial`、`scx-finance`、`scx-inventory`、`scx-creative`、`scx-campaign` 尚无业务源码实现。

## 2. Codex 领取规则

1. 一次只领取一个 Task；先检查 `git status`、用户修改、现有代码和测试。
2. 未满足依赖不得实现后续 Task；静态通过不能替代真实请求、数据库、浏览器、MQ 或第三方 UAT。
3. 不得 reset、checkout、clean、stash、rebase、提交、推送、部署或覆盖用户已有修改，除非用户明确授权。
4. 数据库只允许前向迁移；不得改写已执行迁移、绕过校验和或破坏性清理。
5. 每个 Task 必须同步代码、迁移、契约、权限、测试、运行证据和 Runbook。
6. 生产凭据、真实资金、真实媒体、公开协议和不可恢复数据操作必须单独取得授权。
7. Task 状态只能使用：已实现并验证、已实现但未运行验证、部分完成、未开始、外部依赖阻塞、目标环境待验收、生产容量未验收。

完整的多 Agent 文件所有权、Task Prompt 和交付格式见：
[Claude Code 多 Agent 最小任务实施手册](Claude-Code多Agent最小任务实施手册.md)。Codex 可复用其 Task Prompt，
只需把主协调 Agent 替换为 Codex 当前会话。

## 3. 当前收口 Task

这些 Task 必须在 R1-00 关闭前完成或明确阻塞。

| Task ID | 目标 | 依赖 | 当前状态 |
|---|---|---|---|
| FIX-SECRET-CHECK-DEV | 修复 dev 可选占位符与生产密钥检查器兼容性；不降低 prod 检查 | 无 | 已实现并验证 |
| IMP-2 | `TrackingProperties` 加 `@Validated`/`@NotBlank`，缺少两个 Tracking 密钥时启动失败 | FIX-SECRET-CHECK-DEV | 已实现并验证 |
| IMP-3 | prod 早期校验数据库、Redis、RocketMQ、XXL-JOB、登录加密和 Tracking 密钥 | IMP-2 | 已实现并验证 |
| FIX-REPORTING-PRIVACY | click/campaign/creative 只返回前六位掩码，设备与 IP 只返回存在标记 | 无 | 已实现并验证 |
| FIX-1 | Sa-Token/Scope 拒绝写入 `sys_log`，保留脱敏审计 | BASE-02-FIX | 已实现并验证（隔离 MySQL；目标矩阵待 P1） |
| BASE-02-FIX2 | 统一 Scope/权限拒绝语义，核查 OP 权限码和 404 API 语义 | FIX-1 | 已实现并验证 |
| WORKTREE-ARTIFACT-AUDIT | 识别历史 JAR、报告、日志、`.turbo`、`dist` 等生成物，不删除 | 无 | 已完成清点 |

完成以上收口后，才可以把 R1-00 判断为“本地证据完整”；目标环境和第三方依赖仍需单独验收。

## 4. R1-00 基础可信边界

| Task ID | 最小功能 | 依赖 | 状态 |
|---|---|---|---|
| BASE-01 | V43–V48 前向迁移、重复执行、校验和、菜单和双 Worker 竞争 | 环境授权 | V48 本地空库/升级验证通过；生产待迁移 |
| BASE-02 | 管理员/运营/分析员/无范围用户 Scope 回归及拒绝审计 | BASE-01 | 本地完成；生产缺三类验收账号 |
| BASE-02-FIX | 修复 MediaController 列表和详情越权 | BASE-02 | 已实现并验证 |
| BASE-03 | 历史凭据扫描、分类、轮换清单 | BASE-02-FIX | 本地静态完成，人工轮换阻塞 |
| BASE-03-FIX | 当前树凭据分类和修复建议 | BASE-03 | 部分完成 |
| BASE-03-FIX-MERGE | 合并配置占位、删除弱默认、扩展密钥检查器 | BASE-03-FIX + 用户授权 | 已实现并完成生产运行时门禁；人工轮换待执行 |
| IMP-1 | prod 必填配置 fail-fast 验证 | BASE-03-FIX-MERGE | 已由 IMP-2/IMP-3 收口并验证 |
| BASE-04 | 真实第三方沙箱 v1.1 签名、Scope、nonce、回执、Postback、轮换 UAT | BASE-02、BASE-03、外部授权 | 外部依赖阻塞 |
| BASE-05 | RocketMQ/Outbox/Redis/Callback 故障恢复和最终对账 | 独立测试环境 | 脚本和本地 Redis 预检已实现；生产/独立恢复验收未开始 |

## 5. R1-01 模块骨架

R1-01 只有在 R1-00 的本地收口、目标环境条件和安全边界明确后才可开始。

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| ARCH-01 | `scx-commercial` 模块、POM、包结构、占位测试 | R1-00 |
| ARCH-02 | `scx-finance` 模块和只读 Commercial Port | ARCH-01 |
| ARCH-03 | `scx-inventory` 模块和 Subject/Delivery Port | R1-00 |
| ARCH-04 | `scx-creative` 模块和对象存储 Port | ARCH-03 |
| ARCH-05 | `scx-campaign` 模块和稳定领域 Port | ARCH-01～04 |
| ARCH-06 | 父 POM、Boot 装配、权限占位、依赖无环验收 | ARCH-01～05 |

## 6. R1-02 商业主数据

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| COM-01 | Organization/Advertiser 创建、查询和组织 Scope | ARCH-01 |
| COM-02 | 最多二级 AgencyRelation，防环和越级 | COM-01 |
| COM-03 | AdAccount 创建、归属和账户树 | COM-02 |
| COM-04 | QualificationCase/Document/ReviewLog 状态机 | COM-03 |
| COM-05 | AgreementTemplate/Instance 版本化合同 | COM-03 |
| COM-06 | DataAuthorization 和 PublishingEligibility Port | COM-04、COM-05 |

## 7. R1-03/R1-04 虚拟财务沙箱

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| FIN-01 | CNY 财务科目和 FinAccount | ARCH-02、COM-03 |
| FIN-02 | 不可变双分录 FinJournal/FinEntry | FIN-01 |
| FIN-03 | 虚拟充值和赠送 | FIN-02 |
| FIN-04 | CreditLine 授信、使用和到期 | FIN-02 |
| FIN-05 | BudgetReservation 预留、部分释放、全部释放 | FIN-03、FIN-04 |
| FIN-06 | estimated/confirmed spend、差额释放和冲正 | FIN-05 |
| FIN-07 | available 余额和 LedgerBalanceSnapshot | FIN-06 |
| FIN-08 | 财务幂等、并发和不变量测试 | FIN-02～07 |
| FIN-09 | 版本化返点规则和差额重算 | FIN-08 |
| FIN-10 | RefundCase 经办/审批和账本冲正 | FIN-08 |
| FIN-11 | InvoiceApplication/Item 模拟开票流程 | FIN-08 |
| FIN-12 | ReconciliationRun 和差异明细 | FIN-09～11 |
| FIN-13 | ClosingPeriod 关账和 AdjustmentJournal | FIN-12 |

## 8. R1-05 库存和媒体能力

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| INV-01 | MediaAccount 创建、状态和 Scope | ARCH-03 |
| INV-02 | Placement/PlacementFormat 和八类广告位 | INV-01 |
| INV-03 | MediaCapabilityMatrix 版本化能力 | INV-02 |
| INV-04 | PlacementQualitySnapshot 质量水位 | INV-02 |
| INV-05 | MediaAdapterConfig、Secret Reference 和 SSRF 防护 | INV-03 |

## 9. R1-06 素材、创意和落地页

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| CRE-01 | CreativeAsset/AssetBinary 元数据和不可变对象引用 | ARCH-04、INV-03 |
| CRE-02 | MIME、文件头、大小、哈希和扫描 Port | CRE-01 |
| CRE-03 | 素材审核状态机和不可变 ReviewLog | CRE-02 |
| CRE-04 | CreativeAssetRevision 发布、暂停和引用 | CRE-03 |
| CRE-05 | LandingPage 版本化 Schema、白名单和 URL 安全 | CRE-03 |
| CRE-06 | CreativeBinding 与媒体能力校验 | CRE-04、CRE-05 |

## 10. R1-07 Campaign 投放控制面

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| CAM-01 | Campaign/CampaignRevision | ARCH-05、COM-06 |
| CAM-02 | AdGroup/AdGroupRevision | CAM-01 |
| CAM-03 | Ad/AdRevision/CreativeBinding 引用 | CAM-02、CRE-06 |
| CAM-04 | TargetingRevision 安全 AST | CAM-02、INV-03 |
| CAM-05 | OptimizationGoal/BudgetPlan，支持 CPM/CPC/MEDIA_MANAGED_OCPC | CAM-01、FIN-05 |
| CAM-06 | 发布前主体、余额、素材、媒体、事件综合验证器 | CAM-03～05 |
| CAM-07 | Campaign 状态机和 PublishingOperation | CAM-06 |

## 11. R1-08 通用沙箱媒体 Adapter

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| ADP-01 | MediaAdapter Port、内部 DTO、稳定错误码和防腐层 | CAM-07、INV-05 |
| ADP-02 | GenericSandbox 幂等发布和外部 ID 映射 | ADP-01 |
| ADP-03 | 状态同步和增量游标 | ADP-02 |
| ADP-04 | 报表/账单拉取、迟到和重复批次 | ADP-02 |
| ADP-05 | 429/5xx/超时/部分成功/漂移故障注入 | ADP-03、ADP-04 |

## 12. R1-09 事件目录和黄金链路

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| EVT-01 | EventDefinition 和事件版本目录 | CAM-01 |
| EVT-02 | BusinessKey/Validity/Value/Optimization 规则 | EVT-01 |
| EVT-03 | FORM_SUBMIT v1.1 canonical、业务去重和有效性 | EVT-02 |
| EVT-04 | Last Click 7d、Asia/Shanghai 规则版本和重算 | EVT-03 |
| EVT-05 | 归因后幂等 Postback、HMAC、重试和 DLQ | EVT-04、ADP-02 |
| EVT-06 | 虚拟消耗和 target CPA 50 CNY 对账 | EVT-05、FIN-06 |

## 13. R1-10 报表、实验和导出

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| RPT-01 | 广告层级日/小时只读投影 | EVT-06 |
| RPT-02 | REQUEST→FORM_SUBMIT 漏斗 | RPT-01 |
| RPT-03 | 消耗、CPA、返点、退款、可开票指标 | RPT-01、FIN-13 |
| RPT-04 | 固定水位、版本、输入哈希的 ReportRun | RPT-02、RPT-03 |
| RPT-05 | HMAC internalDeviceKey 稳定实验分桶 | RPT-04 |
| RPT-06 | Scope 快照、重新鉴权、过期、脱敏导出 | RPT-04 |

## 14. R1-11 前端运营闭环

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| UI-01 | 广告主、代理和账户树页面 | COM-03 |
| UI-02 | 资质、合同、数据授权页面 | COM-06 |
| UI-03 | 财务账户、充值、授信、预留页面 | FIN-08 |
| UI-04 | 返点、退款、发票、对账、月结页面 | FIN-13 |
| UI-05 | 媒体账户、广告位、能力、质量页面 | INV-05 |
| UI-06 | 素材库、落地页和绑定页面 | CRE-06 |
| UI-07 | Campaign/AdGroup/Ad 编辑和发布校验 | CAM-07 |
| UI-08 | 发布记录、沙箱状态、故障重试页面 | ADP-05 |
| UI-09 | 事件目录、漏斗、实验页面 | EVT-06、RPT-05 |
| UI-10 | 投放/财务报表和安全导出页面 | RPT-06 |

## 15. R1-12 完整验收

| Task ID | 最小功能 | 依赖 |
|---|---|---|
| ACC-01 | 八角色权限、Scope、直接路由/API 矩阵 | UI-01～UI-10 |
| ACC-02 | 28 步 FORM_SUBMIT 黄金 E2E | ACC-01 |
| ACC-03 | Redis/MQ/Outbox/Adapter 故障恢复 | ACC-02 |
| ACC-04 | 隐私、密钥、日志、审计和导出验收 | ACC-02 |
| ACC-05 | 独立目标环境 R1 签收和 Go/No-Go | ACC-01～ACC-04 |

## 16. R2–R9 智能化和生产路线

### R2：模型 Shadow

`ML-01` Feature Registry；`ML-02` Dataset Snapshot/point-in-time join；`ML-03` LR/GBDT+LR
TrainingRun；`ML-04` ModelVersion/Artifact/Evaluation；`ML-05` Shadow 在线推理。

### R3：受控智能出价

`BID-01` Decision API/回退；`BID-02` CTR/CVR 推理和校准；`BID-03` oCPC/oCPM、cap、floor；
`BID-04` PID/Pacing；`BID-05` 预算保护、Kill Switch、1%→5%→20% Canary。

### R4：DCO、Lookalike、扩量

`DCO-01` 合规创意组合；`DCO-02` Bandit、探索预算和创意疲劳；`AUD-01` Lookalike 数据授权和评估；
`AUD-02` 严格定向、受控扩量和 Holdout。

### R5：智能诊断和建议

`DIAG-01` 诊断信号；`DIAG-02` 证据链根因诊断；`DIAG-03` 出价/定向/素材/预算建议；
`DIAG-04` INSIGHT_ONLY 到 FULL_AUTO 的审批状态机。

### R6：深度归因

`ATTR-01` MTA、时间衰减、位置模型；`ATTR-02` Markov/Shapley 分析；`ATTR-03` SKAdNetwork/第三方
聚合 Adapter；`ATTR-04` 增量实验和 Holdout。

### R7：强化学习与 AIGC

`RL-01` propensity、离线回放和安全 Action Space 门禁；`RL-02` RL Shadow；`AIGC-01` 统一
ModelGateway；`AIGC-02` 事实、版权、内容和人工审核。

### R8：真实财务

`REALFIN-01` 支付/银行/税务/媒体账单 ADR；`REALFIN-02` 单外部系统 Adapter；`REALFIN-03` 隔离 UAT、
双人审批、审计、备份和回滚。

### R9：生产容量

`CAP-01` 多实例和独立基础设施；`CAP-02` 全链路对账器；`CAP-03` 20,000 QPS 稳态；`CAP-04`
40,000 QPS 突发和故障恢复；`CAP-05` 不可篡改容量报告和 Go/No-Go。

## 17. Codex 每个 Task 的最低交付物

- 任务合同和文件所有权表。
- 代码、数据库前向迁移、API/事件契约。
- 权限、对象 Scope、隐私和审计。
- 后端测试、前端测试、并发/幂等/失败测试。
- 真实请求或明确说明“未运行态验证”。
- MySQL、Redis、RocketMQ、Outbox 事实对账（适用时）。
- Runbook、MVP/ToDo/技术设计状态更新。
- 命令、退出码、环境、证据路径、开放风险和下一 Task。
- `git diff --stat`、`git diff --check`；日志和构建产物不得伪装成代码变更。

## 18. Codex 当前第一条领取指令

当前本地 P0 已完成，下一任务必须在外部依赖和授权满足后领取：

```text
FIX-4（目标环境 V43–V47 + 四角色/API/审计）
→ BASE-04（真实第三方 v1.1 UAT）
→ BASE-05（独立故障恢复 + 20k/40k 容量）
→ ARCH-01（仅在上述门禁全部签收后）
```

未完成 R1-12 前，不领取 R2–R9；未完成 R1-00 收口前，不开始 ARCH-01。
