# ADR-003：RTA 主动决策解锁门禁（Proposed / Not Accepted）

- 状态：**Proposed / Not Accepted**
- 日期：2026-08-10
- 关联需求/RFC：S11、[09 RTA 管理](../ocpx-aerim-模块实现规格/09-RTA管理.md)、ADR-001、ADR-002
- 决策者：架构 / 产品 / 安全 / 供应商 / 运维；具体人员均为 `TBD / Decision required`

> **不解锁声明：本文件只是待评审决策包，不是 Accepted ADR，不替代 ADR-001，也不解除 S11 Blocked。不得据此启用 RTA ACTIVE、非 OFF RouteVersion、真实外呼、connect-check、shadow、供应商 UAT 或生产流量。四层 OFF 门禁保持生效。**

## 背景与约束

当前 Accepted ADR-001 将 RTA 固定为 `OFF`。现有源码只提供管理 API、发布和数据面的 fail-closed 前置证据，不提供主动决策能力。S11 只有在协议、安全、隐私、容量、成本、Secret 生命周期和 UAT 等外部决策形成 Accepted ADR 后，才可另行授权实施。

本草案将待决问题整理为可签署清单。除已经由权威规格锁定的“不相互代填、Secret 不回显、默认拒绝、事实可追溯、分阶段授权”外，任何业务值、域名、协议字段、费用或 SLA 均保持 `TBD / Decision required`。

## 非目标与当前强制行为

- 不建立 RTA Profile、统计、主动决策、网络客户端或供应商 Adapter。
- 不录入或推断真实域名、IP、路径、Secret、价格、容量或成功码。
- 不执行连接检查、shadow、Mock 供应商闭环、真实 UAT 或生产验证。
- UI 保持锁定，管理 API 拒绝 ACTIVE，发布器强制 OFF，数据面拒绝非 OFF 快照且不外呼。
- 当前不存在 RTA Secret 读取面；未来 Secret 也只能创建/轮换，不能通过详情、日志、审计或异常回显。

## 1. 地址与所有权语义决策

以下三个概念必须分别决策、分别校验、分别版本化，禁止浏览器或服务端用其中一个字段自动补另一个字段。

| 决策 ID | 概念 | 必须签署的语义 | 所有权/来源 | 当前状态 |
| --- | --- | --- | --- | --- |
| `RTA-ADDR-01` | `serviceAddress` | 决策服务入口、作用域、是否随 Profile/MediaAccount/RouteVersion 冻结 | `TBD / Decision required` | 未决定 |
| `RTA-ADDR-02` | `api` | 是协议版本、资源标识、路径模板还是其它供应商字段 | `TBD / Decision required` | 未决定 |
| `RTA-ADDR-03` | `globalDomain` | 平台入口、回调域、供应商域或仅展示元数据的准确含义 | `TBD / Decision required` | 未决定 |
| `RTA-ADDR-04` | 三者关系 | 是否允许引用、组合或派生；任何允许关系的方向和版本规则 | `TBD / Decision required` | 未决定；默认互不代填 |
| `RTA-ADDR-05` | 配置责任 | 谁提供、谁复核、谁写入目标配置、谁批准变更 | `TBD / Decision required` | 未决定 |

所需附件：字段所有权图、数据流图、样例的完全脱敏版本、配置变更和回滚流程。附件编号及负责人均为 `TBD / Decision required`。

## 2. 供应商协议与防重决策

| 决策 ID | 必须锁定的事项 | 待提供证据 | 责任方 | 当前状态 |
| --- | --- | --- | --- | --- |
| `RTA-PROTO-01` | 协议名称、版本、兼容期和废弃策略 | 正式供应商协议及版本通知机制 | 供应商 / 架构 | `TBD / Decision required` |
| `RTA-PROTO-02` | method、path、content type、字符集和最大报文 | 正式接口规范 | 供应商 | `TBD / Decision required` |
| `RTA-PROTO-03` | 请求 schema、必填/可选字段、单位和枚举 | 机器可校验 schema 与脱敏样例 | 产品 / 供应商 | `TBD / Decision required` |
| `RTA-PROTO-04` | 响应 schema、业务码、allow/deny/abstain 语义 | 正式响应契约 | 产品 / 供应商 | `TBD / Decision required` |
| `RTA-AUTH-01` | 认证方式和签名覆盖范围 | 安全评审过的认证规范 | 安全 / 供应商 | `TBD / Decision required` |
| `RTA-AUTH-02` | timestamp 时钟窗、时钟源和偏差处置 | 防重放规范 | 安全 / 运维 | `TBD / Decision required` |
| `RTA-AUTH-03` | nonce 生命周期、唯一粒度和重放响应 | 防重放与存储容量评估 | 安全 / 架构 | `TBD / Decision required` |
| `RTA-AUTH-04` | requestId/idempotency key 生成方、作用域、TTL | 幂等与重复请求合同 | 架构 / 供应商 | `TBD / Decision required` |

## 3. 成功、错误与故障模式决策

| 决策 ID | 决策问题 | 必须明确的内容 | 当前状态 |
| --- | --- | --- | --- |
| `RTA-OUTCOME-01` | 什么是请求成功 | 网络成功、协议成功、业务成功分别定义；HTTP 2xx 不得自动等同业务成功 | `TBD / Decision required` |
| `RTA-OUTCOME-02` | 决策结果 | allow、deny、abstain、invalid、unknown 的稳定枚举及业务影响 | `TBD / Decision required` |
| `RTA-ERROR-01` | 错误分类 | 认证、协议、参数、限流、超时、供应商内部错误和本地安全拒绝的映射 | `TBD / Decision required` |
| `RTA-TIMEOUT-01` | 超时 | connect、response、total deadline 及预算扣减方式 | `TBD / Decision required` |
| `RTA-RETRY-01` | 重试 | 是否允许、哪些错误允许、次数、退避、是否跨 deadline；默认不得重试 | `TBD / Decision required` |
| `RTA-CB-01` | 熔断 | 窗口、阈值、半开探测、作用域和恢复条件 | `TBD / Decision required` |
| `RTA-DEGRADE-01` | 降级/安全旁路 | 超时、熔断、配置缺失时如何处理投递、计费和统计 | `TBD / Decision required` |
| `RTA-SHADOW-01` | 是否允许 shadow | 法律依据、字段、比例、隔离、成本、退出门槛；未签署前禁止 | `TBD / Decision required` |

## 4. 数据最小化、隐私与合规决策

| 决策 ID | 必须决策的范围 | 当前状态 |
| --- | --- | --- |
| `RTA-PRIV-01` | 请求/响应字段逐项必要性、目的、数据主体和敏感等级 | `TBD / Decision required` |
| `RTA-PRIV-02` | device、广告标识、IP、URL 参数等字段的裁剪、散列或加密方式 | `TBD / Decision required` |
| `RTA-PRIV-03` | 传输加密、静态加密、密钥所有权和解密权限 | `TBD / Decision required` |
| `RTA-PRIV-04` | 应用日志、访问日志、指标、审计和诊断证据的字段级脱敏规则 | `TBD / Decision required` |
| `RTA-PRIV-05` | 请求事实、响应事实、失败证据和统计的保留期限与删除证明 | `TBD / Decision required` |
| `RTA-PRIV-06` | 数据所在地、跨境、供应商子处理方和法律依据 | `TBD / Decision required` |
| `RTA-PRIV-07` | 数据主体请求、事故响应和监管审计接口 | `TBD / Decision required` |

未签署前的默认行为是最小化且不外发；日志、统计和普通响应不得记录原始请求/响应或 Secret。

## 5. 容量、成本与服务水平决策

| 决策 ID | 决策项 | 当前状态 |
| --- | --- | --- |
| `RTA-CAP-01` | 峰值/持续 QPS、突发模型、并发上限和租户/账号/Profile 频控 | `TBD / Decision required` |
| `RTA-CAP-02` | 连接池、线程/事件循环、队列和资源隔离预算 | `TBD / Decision required` |
| `RTA-COST-01` | 调用单价、阶梯、最低消费、失败/重试/shadow 是否计费和预算责任人 | `TBD / Decision required` |
| `RTA-SLA-01` | 供应商 SLA、支持时段、故障通知和赔付 | `TBD / Decision required` |
| `RTA-SLO-01` | 平台可用性、P50/P95/P99、错误率和数据完整性 SLO | `TBD / Decision required` |
| `RTA-MON-01` | 指标、告警阈值、值班路由、仪表板和证据保留 | `TBD / Decision required` |
| `RTA-DR-01` | 故障演练、容量验证、供应商不可用和区域故障预案 | `TBD / Decision required` |

## 6. Secret 生命周期决策

| 决策 ID | 必须签署的控制 | 当前状态 |
| --- | --- | --- |
| `RTA-SEC-01` | Secret 创建者、审批者、保管边界和安全交付通道 | `TBD / Decision required` |
| `RTA-SEC-02` | 加密设施、credential revision、盲索引需求和访问授权 | `TBD / Decision required` |
| `RTA-SEC-03` | 定期/紧急轮换周期、双人复核和生效证据 | `TBD / Decision required` |
| `RTA-SEC-04` | 旧 revision 重叠窗口、冻结 RouteVersion 的兼容和退役条件 | `TBD / Decision required` |
| `RTA-SEC-05` | 吊销传播、缓存失效、实例确认和失败恢复 | `TBD / Decision required` |
| `RTA-SEC-06` | 泄露分级、停流、轮换、取证、通知和复盘责任 | `TBD / Decision required` |
| `RTA-SEC-07` | 创建/轮换/吊销/读取尝试审计字段与保留期 | `TBD / Decision required` |

固定安全不变量：Secret 只写不回显，不进入浏览器持久化、普通 DTO、日志、异常、审计正文或发布快照明文。

## 7. 事实、统计与可追溯决策

| 决策 ID | 必须锁定的口径 | 当前状态 |
| --- | --- | --- |
| `RTA-FACT-01` | 请求事实、决策事实、重试 attempt 与最终 outcome 的不可变关系 | `TBD / Decision required` |
| `RTA-FACT-02` | requestId/Profile/RouteVersion/credential revision 的幂等和关联键 | `TBD / Decision required` |
| `RTA-FACT-03` | eventTime/receivedTime/timezone、迟到和回补规则 | `TBD / Decision required` |
| `RTA-STAT-01` | DAY/HOUR 请求量、成功量、成功率的精确定义和指标版本 | `TBD / Decision required` |
| `RTA-STAT-02` | timeout/reject/error/shadow 是否进入六列统计及如何另列质量指标 | `TBD / Decision required` |
| `RTA-TRACE-01` | traceId/correlationId、审计、发布快照和供应商证据的追溯链 | `TBD / Decision required` |
| `RTA-RECON-01` | SCX 与供应商请求/计费/结果对账周期、差异阈值和责任 | `TBD / Decision required` |

## 8. UAT 入口、退出与证据决策

| 决策 ID | 必须提交的产物 | 当前状态 |
| --- | --- | --- |
| `RTA-UAT-01` | 隔离环境、供应商测试账号/域名的安全交付和网络授权 | `TBD / Decision required` |
| `RTA-UAT-02` | 正常、拒绝、认证失败、超时、限流、重放、轮换和熔断用例 | `TBD / Decision required` |
| `RTA-UAT-03` | 字段最小化、日志脱敏、保留/删除和跨境合规证据 | `TBD / Decision required` |
| `RTA-UAT-04` | 容量、延迟、错误率、成本和供应商对账证据 | `TBD / Decision required` |
| `RTA-UAT-05` | 退出条件、缺陷分级、复测要求和签字模板 | `TBD / Decision required` |

UAT 必须在 ADR Accepted 且另有明确外部授权后执行；本草案不得作为 UAT 授权。

## 9. 分阶段启用、回滚与四层门禁

| 阶段 | 进入条件 | 允许动作 | 退出/回滚证据 | 当前状态 |
| --- | --- | --- | --- | --- |
| `G0 Proposed` | 本草案评审中 | 仅文档评审 | 继续四层 OFF | 当前阶段 |
| `G1 Accepted design` | 全部必填决策完成、全角色签署、状态经独立评审改为 Accepted | 仅授权后续源码任务；不自动允许外呼 | 实现计划和风险复核 | `TBD / Decision required` |
| `G2 Disabled implementation` | 源码/迁移/安全测试通过 | 只允许 DISABLED/Profile 与 fail-closed 验证 | 回到纯 OFF 版本 | `TBD / Decision required` |
| `G3 Controlled UAT` | 外部测试授权、隔离凭据、隐私与容量门禁通过 | 仅批准的供应商 UAT | 停止 worker、吊销测试凭据、RouteVersion 回 OFF | `TBD / Decision required` |
| `G4 Shadow` | `RTA-SHADOW-01` 明确允许并另行授权 | 仅签署比例/对象/窗口 | 新 RouteVersion OFF、停止外呼 | `TBD / Decision required` |
| `G5 Limited active` | UAT 退出、容量/成本/SLO/值班/回滚全部签署 | 仅明确对象和流量上限 | 新 RouteVersion OFF、吊销或回切 revision | `TBD / Decision required` |
| `G6 Expansion` | 限量阶段复盘通过并取得生产变更授权 | 仅批准范围 | 按已演练 Runbook 回滚 | `TBD / Decision required` |

四层门禁解除规则：UI、管理 API、发布器、数据面必须逐层有独立测试和变更授权；不能因为其中一层实现或本 ADR 变为 Accepted 就自动解除其它层。任何阶段回滚均发布新的 `OFF` RouteVersion，不原地改写历史快照。

## 候选方案

| 方案 | 优点 | 缺点 | 风险/成本 |
| --- | --- | --- | --- |
| 保持永久 OFF | 无第三方主动决策和隐私暴露 | 不提供 RTA 业务能力 | 业务机会成本为 `TBD / Decision required` |
| 同步主动决策 | 可在请求 deadline 内获得供应商结果 | 延迟、容量、费用和故障耦合 | 协议及所有数值均为 `TBD / Decision required` |
| 先 shadow 后主动 | 可先比较结果 | shadow 仍是真实外呼并产生隐私与费用 | 是否允许为 `TBD / Decision required`；未决定前禁止 |

本草案不选择候选方案。

## 影响

- 业务、数据、API、消息、安全、性能、运维和成本影响均依赖上述签署结果。
- 若未来 Accepted，只授权进入独立实施评审，不代表目标环境、供应商 UAT或生产已验收。
- 若不接受，ADR-001 的 RTA `OFF` 约束和当前 fail-closed 源码保持不变。

## Accepted 门槛与责任签署

以下条件必须全部满足，才可通过独立评审把状态改为 Accepted：

- [ ] `RTA-ADDR-*` 至 `RTA-UAT-*` 所有必填项已从 `TBD / Decision required` 变为明确决策，并附可审计来源。
- [ ] 正式供应商协议、schema、安全与隐私评审、成本/SLA、容量计划和 UAT 计划已作为受控附件登记。
- [ ] 四层门禁分阶段解除、回滚、值班和事故响应已获得责任人批准。
- [ ] 没有真实域名、Secret 或个人信息进入本文、Git、普通工单或评审输出。
- [ ] ADR 状态通过独立评审变更；仅编辑正文或勾选清单不得视为 Accepted。

| 责任角色 | 必须签署的范围 | 姓名 | 日期 | 签署/结论 |
| --- | --- | --- | --- | --- |
| 架构负责人 | 数据面边界、协议、快照、幂等、失败与阶段门禁 | `TBD / Decision required` | TBD | 未签署 |
| 产品负责人 | 业务价值、成功口径、结果影响、统计和灰度范围 | `TBD / Decision required` | TBD | 未签署 |
| 安全/隐私负责人 | 认证、Secret、字段最小化、SSRF/跨境、保留和事故响应 | `TBD / Decision required` | TBD | 未签署 |
| 供应商负责人 | 正式协议、支持版本、容量、SLA、费用、错误和 UAT | `TBD / Decision required` | TBD | 未签署 |
| 运维/SRE 负责人 | 配置交付、容量、监控、值班、回滚和故障演练 | `TBD / Decision required` | TBD | 未签署 |

## 迁移、回滚与验证

本草案不授权迁移或配置变更。未来实现必须使用新的前向迁移并单独验证；回滚不得删除事实或改写旧 RouteVersion。目标迁移、供应商 UAT 和生产变更均需要新的明确授权。

## 被替代关系

当前：不替代任何 Accepted ADR。若未来经独立评审 Accepted，只能替代 ADR-001 中“RTA 永远固定 OFF”的局部约束；ADR-001 的独立数据面、不可变快照和可靠事实边界继续有效。具体替代措辞仍为 `TBD / Decision required`。
