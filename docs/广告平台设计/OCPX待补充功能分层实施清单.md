# SCX OCPX 待补充功能分层实施清单

> 文档状态：执行清单
>
> 基线日期：2026-07-27
>
> 当前迁移头：`V48__add_media_scope_management_permissions.sql`（代码已实现；目标环境待迁移）
>
> 适用范围：从当前“追踪、归因、回传、RTA、基础报表研发沙箱”补齐为媒体托管
> OCPX 平台，再逐步进入自有智能出价能力。

## 1. 使用规则

1. 本文按能力层级整理缺口，不以页面存在、接口返回 `202` 或单元测试通过代替业务闭环验收。
2. 一次实施只领取一个可独立验证的纵向切片。后端、前端、契约、数据库迁移、线上数据升级、
   运行验证和文档属于同一切片的交付物。
3. 只要任务涉及表结构、菜单权限、字典、初始化数据、历史数据回填或索引，就必须新增
   `V{n}__*.sql` 前向迁移。当前 V48 已落地；后续候选从 V49 起，开工时仍必须重新扫描迁移目录，
   不能提前占号。
4. 不修改、删除或重命名已经执行的迁移，不手工伪造 `scx_schema_migration`，不绕过文件名和
   SHA-256 校验。
5. 用户要求：线上数据需要执行的 SQL 在对应功能实现阶段一并执行。该要求纳入完成定义；
   但每次执行前仍必须确认目标库身份、受影响表、可恢复备份、变更窗口和发布授权。
6. 线上只执行仓库中已经完成隔离验证的同一份前向迁移。禁止先在线上执行临时 SQL、事后再补
   migration。
7. 如果目标环境、备份、权限或变更窗口未满足，任务状态只能写为“代码已实现，目标环境待
   迁移”，不能写成“完成”或“已上线”。
8. 真实支付、自动开票、真实媒体密钥、隐私口径、模型自动控制预算等高风险能力，需要对应
   ADR/RFC 和明确授权。

## 2. 分层总览

| 层级 | 当前基础 | 主要待补充能力 | 对应阶段 | 优先级 |
|---|---|---|---|---|
| L0 基础可信与上线前置层 | 权限、渠道签名、迁移账本、本地可靠入口 | 目标环境迁移、多角色回归、真实渠道 UAT、MQ/Redis 恢复 | R1-00 | P0 |
| L1 商业基础层 | 客户、产品、合作方等运营主数据 | 广告主/代理商/广告账户、资质、合同、数据授权 | R1-01～R1-02 | P0 |
| L2 财务与对账层 | 通用支付模板，无广告资金账本 | 虚拟账户、双分录、充值、授信、冻结、返点、退款、发票、月结 | R1-03～R1-04 | P0/P1 |
| L3 资源管理层 | 媒体、渠道、链接、接入文档 | 媒体账户、流量位、素材、创意审核、落地页、创意绑定 | R1-05～R1-06 | P0 |
| L4 数据基建层 | 点击/转化、设备哈希、Last Click、回传、RTA | 事件目录、FORM_SUBMIT、曝光/落地事件、规则版本、反作弊 | R1-08～R1-09 | P0 |
| L5 投放执行层 | 监测任务，不是 Campaign | Campaign/AdGroup/Ad、预算、出价、定向、发布、媒体同步、频控 | R1-07～R1-09 | P0 |
| L6 算法引擎层 | 无自有模型和自动出价 | CTR/CVR、Shadow、oCPC、PID/Pacing、Lookalike、DCO、RL | R2～R7 | P1/P2 |
| L7 报表与诊断层 | 事件计数、漏斗、日报、导出 | 消耗、真实 CPA/ROI、归因分析、差异对账、诊断和建议 | R1-10、R5～R6 | P0/P1 |
| L8 生产治理层 | 本地测试、监控和 Runbook | SLA、容量、容灾、值班、审计签收、生产 Go/No-Go | R1-12、R9 | P0 |

## 3. L0 基础可信与上线前置层

### 3.1 待补充功能

- [ ] 在目标环境应用并核对当前未发布迁移，确认迁移账本、菜单、角色权限和历史数据一致。
- [ ] 管理员、运营、分析员、无媒体范围用户完成浏览器和直调 API 权限矩阵。
- [ ] 完成历史凭据轮换、安全处置和凭据缓存失效验证。
- [ ] 真实第三方渠道签收 v1.1 签名、端点权限、回执、Callback 和轮换。
- [ ] Redis 停启、Pending reclaim、DLQ、Callback、Outbox、RocketMQ 全链路恢复。
- [ ] 拒绝访问、凭据操作、人工重放、配置修改等高风险操作全部落审计日志。

### 3.2 数据库与线上 SQL

- 涉及对象：`scx_schema_migration`、`sys_menu`、`sys_role_menu`、`sys_log`、
  `scx_user_subject_scope`、`scx_channel_client`、`scx_channel_credential`。
- 线上动作：先只读核对迁移账本和业务数据，再备份受影响表，通过
  `init-database.sh` 应用尚未登记的迁移。
- 验收：迁移记录、脚本名、校验和、菜单权限、四角色数据范围和关键行数全部一致。

## 4. L1 商业基础层

### 4.1 广告主、代理商与广告账户

- [ ] `Organization` / `Advertiser` 主体及对象范围。
- [ ] 直客、一级代理、最多一层子代理关系，禁止循环和越级挂靠。
- [ ] `AdAccount` 广告账户、所属客户、媒体账户映射和账户树。
- [ ] 主体、代理关系、广告账户启用/停用/归档状态机及不可变操作历史。

### 4.2 资质、合同与数据授权

- [ ] 资质申请、资质文件、审核、驳回、补件、过期和复审。
- [ ] 合同模板、合同实例、版本、生效区间和状态。
- [ ] 数据处理授权、设备标识授权、渠道授权和发布资格判断。
- [ ] 未通过资质、合同或数据授权的账户禁止进入投放发布。

### 4.3 数据库与线上 SQL

- 建议对象：`scx_organization`、`scx_agency_relation`、`scx_ad_account`、
  `scx_qualification_case`、`scx_qualification_document`、`scx_qualification_review_log`、
  `scx_agreement_template`、`scx_agreement_instance`、`scx_data_authorization`。
- 每个状态对象必须保存版本、操作者、原因和生效时间；启用数据不允许原地覆盖。
- 线上迁移需要同时完成表/索引/唯一约束、菜单权限、角色授权和已有客户数据的受控映射。
- 历史客户不能凭名称模糊合并；必须以确认过的业务编码建立映射，无法确认的记录进入待处理表。

## 5. L2 财务与对账层

### 5.1 虚拟财务沙箱

- [ ] CNY 财务科目、虚拟账户和账户状态。
- [ ] 不可变双分录 `FinJournal` / `FinEntry`，每笔业务借贷合计为零。
- [ ] 虚拟充值、赠送、授信额度、额度占用和到期。
- [ ] 预算预留、部分释放、全部释放、预估消耗、确认消耗和冲正。
- [ ] 可用余额、账本余额快照、幂等键、并发保护和不变量校验。

### 5.2 商务与结算

- [ ] 版本化返点规则、返点差额重算。
- [ ] 退款申请、经办/审批分离、账本冲正。
- [ ] 模拟开票申请、开票项目和状态流。
- [ ] 媒体账单、平台账本、投放消耗三方对账。
- [ ] 月结期间、关账、受控调账和差异说明。

### 5.3 数据库与线上 SQL

- 建议对象：`scx_fin_account`、`scx_fin_journal`、`scx_fin_entry`、
  `scx_credit_line`、`scx_budget_reservation`、`scx_spend_fact`、
  `scx_ledger_balance_snapshot`、`scx_rebate_rule`、`scx_refund_case`、
  `scx_invoice_application`、`scx_reconciliation_run`、`scx_closing_period`。
- 财务历史只追加，不通过 `UPDATE` 覆盖原始流水；纠错使用冲正和新分录。
- 线上首次迁移只建虚拟财务沙箱，不自动导入通用 `scx-pay` 交易数据。
- 需要导入期初余额时，必须提供逐账户来源、双分录平衡校验、导入批次号和复核签字。
- 真实充值、支付、开票和结算留到 R8；未完成 ADR/RFC 前不得执行真实财务数据迁移。

## 6. L3 资源管理层

### 6.1 媒体库存

- [ ] 媒体账户授权与状态。
- [ ] 开屏、信息流、激励视频、Banner、搜索、贴片等流量位和版位格式。
- [ ] 媒体支持的素材规格、定向、出价和回传能力矩阵。
- [ ] 流量位质量水位、可用状态、地域/设备限制和屏蔽规则。
- [ ] 媒体 Adapter 配置只保存 Secret Reference，不保存明文密钥。

### 6.2 素材、创意与落地页

- [ ] 图片、视频、标题、CTA 等素材元数据和不可变文件引用。
- [ ] MIME、文件头、大小、哈希、病毒/内容扫描。
- [ ] 素材审核、驳回原因、复审和不可变审核记录。
- [ ] 素材版本、创意组合、发布/暂停和引用影响检查。
- [ ] 落地页版本化 Schema、URL 白名单、跳转安全和发布记录。
- [ ] 微信/抖音/支付宝小程序跳转能力通过独立 Adapter 接入。

### 6.3 数据库与线上 SQL

- 建议对象：`scx_media_account`、`scx_placement`、`scx_placement_format`、
  `scx_media_capability_revision`、`scx_placement_quality_snapshot`、
  `scx_creative_asset`、`scx_asset_binary`、`scx_creative_review_log`、
  `scx_creative_revision`、`scx_landing_page_revision`、`scx_creative_binding`。
- 对象存储文件不直接写入数据库；数据库只保存不可变对象引用、哈希和安全状态。
- 线上迁移必须为已有媒体、渠道和文档建立显式映射；不能按显示名称自动关联。
- 新的素材/媒体菜单、权限码和角色授权必须由同一前向迁移落地。

## 7. L4 数据基建层

### 7.1 事件与设备体系

- [ ] 建立版本化 `EventDefinition`，明确曝光、请求、点击、落地、表单、注册、付费等事件。
- [ ] 正式支持 `FORM_SUBMIT`，定义 `leadId`、业务键、有效性、价值和优化资格。
- [ ] 补齐通用渠道曝光和落地事件契约；SDK、JS Pixel 另立安全和隐私任务。
- [ ] 继续保持设备标识外部 SHA-256、内部 HMAC；不得保存原始设备 ID。
- [ ] Cookie、UnionID、手机号等新标识必须先明确采集授权、盐值、用途、保留期和删除流程。

### 7.2 归因、去重与反作弊

- [ ] Last Click 默认 7 天，支持配置 D1/D7/D30 窗口和规则版本。
- [ ] 转化重算追加 revision，不覆盖历史结果。
- [ ] 事件迟到、时钟偏移、重复、乱序和撤销规则。
- [ ] IP/设备频率、设备异常、批量模式和黑名单等规则型反作弊。
- [ ] 反作弊判定保留原因码、证据引用和人工复核，不直接删除 Raw Event。
- [ ] MTA、SKAdNetwork 和第三方聚合归因留到 R6。

### 7.3 回传与可靠性

- [ ] 归因成功后按媒体 Adapter 生成幂等 Postback。
- [ ] 回传签名、稳定幂等键、重试、DLQ、重放和结果对账。
- [ ] Raw Event、归因、Callback、Outbox、MQ、Redis Pending/DLQ 可按事件 ID 串联追溯。

### 7.4 数据库与线上 SQL

- 建议对象：`scx_event_definition`、`scx_event_rule_revision`、
  `scx_form_submit_identity`、`scx_attribution_rule_revision`、
  `scx_attribution_recompute_run`、`scx_fraud_rule_revision`、
  `scx_fraud_decision`、`scx_media_postback_job`。
- 新事件类型需要同时更新 OpenAPI、JSON Schema、数据库约束、事件映射种子和报表投影。
- 历史 `CUSTOM` 表单数据只有在业务键、来源和口径可确认时才能迁移为 `FORM_SUBMIT`；
  无法确认的历史事实保持原值并通过映射表解释。
- 大表回填采用“新增可空字段/新表 → 分批回填 → 对账 → 后续迁移收紧约束”，避免一次长事务。

## 8. L5 投放执行层

### 8.1 Campaign 控制面

- [ ] `Campaign` / `AdGroup` / `Ad` 及各自不可变 revision。
- [ ] 推广目的、优化目标、预算、排期、出价模式和状态机。
- [ ] CPM、CPC 和 `MEDIA_MANAGED_OCPC`；第一阶段不建设自有 RTB/DSP。
- [ ] 地域、性别、年龄、设备、时段、兴趣和自定义人群定向 AST。
- [ ] 创意绑定、落地页绑定、媒体能力校验和发布前综合校验。
- [ ] 草稿、待审核、已发布、暂停、完成、驳回、归档及操作历史。

### 8.2 媒体 Adapter 与执行同步

- [ ] 幂等发布、外部对象 ID 映射、部分成功处理。
- [ ] 媒体审核状态、投放状态和配置漂移同步。
- [ ] 消耗/账单拉取、迟到批次、重复批次和差异修正。
- [ ] 429、5xx、超时和媒体降级的稳定错误码、重试和人工处理。
- [ ] 曝光、点击和转化频控；转化去重不能代替曝光频控。

### 8.3 数据库与线上 SQL

- 建议对象：`scx_campaign`、`scx_campaign_revision`、`scx_ad_group`、
  `scx_ad_group_revision`、`scx_ad`、`scx_ad_revision`、
  `scx_targeting_revision`、`scx_optimization_goal`、`scx_budget_plan`、
  `scx_publishing_operation`、`scx_external_object_mapping`、
  `scx_media_sync_cursor`、`scx_media_cost_batch`。
- 启用中的投放配置不能原地覆盖；修改生成新 revision。
- 线上迁移应先建立草稿对象和映射表，再开放发布权限；不能把现有监测任务直接认定为已发布
  Campaign。
- 对已有追踪任务的关联只做可逆映射，不修改原始任务、事件和归因历史。

## 9. L6 算法引擎层

### 9.1 模型与智能出价

- [ ] Feature Registry、特征权限和 point-in-time 数据集。
- [ ] TrainingRun、ModelVersion、模型制品、评估和回滚。
- [ ] CTR/CVR 模型、校准度、AUC/GAUC 和数据漂移监控。
- [ ] Shadow 推理，不影响真实流量。
- [ ] oCPC/oCPM 决策 API、目标 CPA、cap/floor 和安全回退。
- [ ] PID/Pacing、预算保护、Kill Switch 和 1%→5%→20% Canary。

### 9.2 人群、创意与高级优化

- [ ] Lookalike 数据授权、种子人群、评估和 Holdout。
- [ ] 自动扩量与严格定向边界。
- [ ] DCO 合规组合、Bandit 探索和创意疲劳。
- [ ] 强化学习离线回放、安全 Action Space 和 Shadow。
- [ ] AIGC 统一 ModelGateway、版权/事实/内容审核和人工确认。

### 9.3 数据库与线上 SQL

- 建议对象：`scx_feature_definition`、`scx_dataset_snapshot`、
  `scx_training_run`、`scx_model_version`、`scx_prediction_log`,
  `scx_bid_decision`、`scx_pacing_state`、`scx_experiment`、
  `scx_experiment_assignment`、`scx_model_monitor_snapshot`。
- 训练数据和模型制品存对象存储；数据库保存版本、哈希、权限和审计元数据。
- 首次线上迁移只允许 Shadow 数据，不得自动打开智能出价。
- 从 Shadow 切换到真实决策必须通过独立配置、审批、Canary 和 Kill Switch，不能通过 SQL
  默认置为启用。

## 10. L7 报表与诊断层

### 10.1 投放与商业报表

- [ ] Campaign/AdGroup/Ad 日报和真正的小时投影。
- [ ] 展示、点击、CTR、转化、CVR、消耗、CPA、收入、ROI/ROAS。
- [ ] 预估消耗、确认消耗、返点、退款、可开票和月结指标。
- [ ] `REQUEST → IMPRESSION → CLICK → LANDING → FORM_SUBMIT` 漏斗。
- [ ] 媒体账单、平台账本、投放事实和回传结果差异解释。
- [ ] 固定输入水位、规则版本和输入哈希的可重现 ReportRun。

### 10.2 诊断与建议

- [ ] 掉量、成本撞线、预算不足、定向过窄、素材疲劳和回传异常信号。
- [ ] 根因诊断必须附数据证据和置信度。
- [ ] 出价、预算、定向和素材建议。
- [ ] `INSIGHT_ONLY → REQUIRE_APPROVAL → LIMITED_AUTO → FULL_AUTO` 状态机。
- [ ] MTA、Markov/Shapley、SKAN 和增量实验留到 R6。

### 10.3 数据库与线上 SQL

- 建议对象：`scx_delivery_fact_hourly`、`scx_delivery_fact_daily`、
  `scx_cost_fact`、`scx_finance_report_daily`、`scx_funnel_daily`、
  `scx_reconciliation_difference`、`scx_diagnostic_signal`、
  `scx_diagnostic_finding`、`scx_optimization_recommendation`。
- 当前报表历史不覆盖；新投影通过新 ReportRun 重算，旧快照保留。
- 首次线上回填必须固定截止时间、规则版本、时区和数据行数，并保存回填批次。
- 报表迁移后需要对 Raw Event、归因、Callback、Outbox、媒体成本和账本逐层对账。

## 11. L8 生产治理层

### 11.1 上线与运行能力

- [ ] 独立目标环境、生产拓扑和最小权限账号。
- [ ] MySQL/Redis/RocketMQ 多实例恢复演练和 RPO/RTO。
- [ ] 指标、日志、Trace、告警、值班接收器和升级路径。
- [ ] 发布、回切、前向修复、备份恢复和数据核验演练。
- [ ] 第三方 UAT、权限矩阵、隐私、密钥、审计和导出签收。
- [ ] 持续 20,000 QPS、突发 40,000 QPS、全链路无损对账。

### 11.2 数据库与线上 SQL

- 所有环境使用同一迁移包；生产不得维护单独 SQL 分支。
- 每次上线保存迁移前后账本、关键表行数、校验和、耗时、锁等待和验证结果。
- 容量所需索引必须基于真实查询计划和压测证据新增，不能凭字段猜测批量建索引。
- 迁移失败后禁止删除账本或执行未知反向 SQL；使用新的前向修复迁移。

## 12. 实施顺序

### 阶段 A：关闭可信边界

`L0 → BASE-04/BASE-05 → R1-00 签收`

完成目标环境迁移、多角色权限、真实第三方 UAT、故障恢复和容量环境准备。

### 阶段 B：形成媒体托管 OCPX 完整业务链

`L1 商业 → L2 虚拟财务 → L3 资源 → L5 Campaign → L4 FORM_SUBMIT/归因/回传
→ L7 成本与 CPA 报表 → L8 R1 验收`

此阶段目标是：

```text
广告主/账户/资质/合同
  → 虚拟资金与预算预留
  → 媒体位/素材/落地页
  → Campaign/AdGroup/Ad
  → 媒体托管 oCPC 发布
  → CLICK/FORM_SUBMIT
  → Last Click 7d
  → Postback
  → 消耗/CPA/账本/差异报表
```

### 阶段 C：引入自有智能优化

`L6 R2 Shadow → R3 受控智能出价 → R4 DCO/Lookalike → R5 诊断`

Shadow 期间不得影响真实流量；真实出价必须有 Canary、预算保护和 Kill Switch。

### 阶段 D：高级能力和生产规模

`R6 深度归因 → R7 RL/AIGC → R8 真实财务 → R9 生产容量`

真实财务和自动化策略均需要单独 ADR/RFC、审批、UAT 和生产放行。

## 13. 每个实现任务的线上 SQL 强制流程

### 13.1 开工前

1. 执行 `git status --short`，保护用户已有修改。
2. 重新扫描迁移目录，确认当前最大版本和未提交迁移。
3. 对目标库执行只读核验：库身份、`scx_schema_migration`、目标表结构、关键行数和数据分布。
4. 明确本任务新增表、字段、索引、菜单、权限、字典、种子和历史回填。
5. 明确锁表风险、预计耗时、兼容窗口、停止条件和前向修复方案。

### 13.2 实现与隔离验证

1. 新增一个或多个前向 `V{n}__*.sql`，DDL/DML 可重复或由迁移账本可靠保护。
2. 同步修改实体、Mapper、服务、API、前端、契约、测试和数据库校验脚本。
3. 在可丢弃 MySQL 8 完成：
   - 空库初始化；
   - 当前生产基线到新版本升级；
   - 重复执行；
   - 校验和检查；
   - 代表数据保留；
   - 并发/部分失败恢复；
   - 历史回填前后对账。
4. 通过相关后端、前端、契约、迁移包和 `git diff --check` 门禁。

### 13.3 同一实施阶段执行线上迁移

1. 再次确认目标主机、库名和只允许执行本任务迁移。
2. 对受影响表和 `scx_schema_migration` 创建可恢复备份并确认文件非空。
3. 在批准的变更窗口通过现有迁移器执行：

   ```bash
   cd scx-backend/db/smart-converge-x
   SCX_SKIP_BASELINE=true SCX_MIGRATION_BASELINE_VERSION=auto ./init-database.sh
   ```

4. 不在命令或文档中写入口令；凭据只从受控环境注入。
5. 执行后核对：
   - 新迁移版本、文件名和 SHA-256；
   - 新表/字段/索引；
   - 菜单、角色、权限和字典；
   - 回填总数、成功数、待处理数；
   - 外键/唯一约束；
   - Raw Event、归因、Callback、Outbox、MQ、报表和账本；
   - 应用健康、错误日志和队列积压。
6. 完成真实 API/浏览器业务冒烟，并记录环境、命令、结果、限制和验收人。

### 13.4 必须停止的情况

- 目标库身份不明确或连接指向未知环境。
- 备份失败、不可读取或未覆盖受影响表。
- 迁移账本与文件名/校验和不一致。
- 线上结构与预期基线不一致。
- 出现锁等待、连接中断、部分成功但账本未登记或关键数据对账失败。
- Outbox/DLQ/错误率持续上升。
- 需要删除数据、反向 DDL、改写已执行迁移或扩大任务范围。

停止后先做只读诊断；不得盲目重跑、手写迁移账本或修改历史 migration。

## 14. 单个任务完成定义

只有同时满足以下条件，任务才能标记“已实现并验收”：

- [ ] 功能、权限、对象范围和失败语义完成。
- [ ] 数据库前向迁移、索引、菜单权限、字典和必要历史回填完成。
- [ ] API/消息契约与代码一致。
- [ ] 单元、集成、前端、契约、迁移和关键 E2E 通过。
- [ ] 隔离 MySQL 空库、升级、重复、校验和和数据对账通过。
- [ ] 对应线上 SQL 已在已确认目标环境执行并完成迁移后核验。
- [ ] 线上 API/浏览器业务冒烟通过。
- [ ] 监控、审计、回传、Outbox、MQ 和报表无异常积压。
- [ ] 文档记录环境、命令、结果、边界、验收人和下一任务。

若线上迁移或运行验收尚未执行，最终状态必须写为：

> 已实现并通过本地/隔离验证；目标环境 SQL 与运行验收待执行。
