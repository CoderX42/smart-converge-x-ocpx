# RFC-001：DP 供应商协议与安全门禁（Proposed / Not Accepted）

- 状态：**Proposed / Not Accepted**
- 作者/日期：待指派 / 2026-08-10
- 关联需求/ADR/Backlog：S12、[12 DP 转换工具](../ocpx-aerim-模块实现规格/12-DP转换工具.md)、ADR-002
- 评审人：产品 / 安全 / 架构 / 淘宝供应商 / 京东供应商 / 运维；具体人员均为 `TBD / Decision required`

> **不解锁声明：本文件只是供应商与安全决策准备，不是 Accepted RFC/ADR，不解除 S12 Blocked。不得据此新增生产菜单、DP Adapter、任意 URL 代理、真实 convert、调试器代跑、供应商 UAT 或任何外呼。当前 `/ocpx/tools/dp/convert` 必须继续固定返回 Blocked。**

## 问题与目标

S12 缺少淘宝和京东正式协议、授权、固定网络边界、隐私/保留、幂等、费用、对账、错误和 UAT 决策。本 RFC 将两家供应商的待件分别登记，并定义 Accepted 前的安全门槛。

所有真实 endpoint、IP、凭据、签名材料、费用和 SLA 均不得写入本文件；相应未决项统一标记 `TBD / Decision required`，通过受控附件和责任签署解决。

## 非目标与当前强制行为

- 不实现供应商协议、Adapter、存储、菜单、前端页面或网络客户端。
- 不创建通用代理，不允许浏览器提供 method、headers、body、DNS、代理地址或目标 endpoint。
- 不使用 ADR-002 调试工作台代替 DP Adapter、协议验收或供应商 UAT。
- 不记录或输出完整输入 URL、结果 URL、授权正文、凭据或第三方敏感响应。
- 生产与目标环境保持 No-Go；任何 UAT 和外呼需要 RFC Accepted 后的独立明确授权。

## 1. 供应商正式协议待件登记

每个供应商必须独立提供和签署，不得把一家供应商的字段、成功码或安全策略推断给另一家。

| Provider | 正式协议/版本 | endpoint/method/path | schema/content type | auth/signature | 支持/变更通知 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 淘宝 | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | 未到件/未签署 |
| 京东 | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | `TBD / Decision required` | 未到件/未签署 |

### 1.1 每家供应商必须提供

| 决策 ID | 待件 | 淘宝 | 京东 |
| --- | --- | --- | --- |
| `DP-PROTO-01` | endpoint 所有权、环境、固定 host/path 和变更流程 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-PROTO-02` | method、协议版本、content type、字符集、请求/响应大小 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-PROTO-03` | 请求 schema、字段类型/单位/必填性、规范化规则 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-PROTO-04` | 响应 schema、结果链接规则、业务成功和错误码 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-AUTH-01` | 认证类型、credential 所有权和安全交付 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-AUTH-02` | 签名算法、覆盖字段、规范化、timestamp/nonce 和防重 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-VERSION-01` | 兼容窗口、废弃通知、灰度和回滚版本 | `TBD / Decision required` | `TBD / Decision required` |

## 2. 固定网络边界与 SSRF 决策

正式 allowlist 必须由供应商和安全通过受控渠道提供，不能来自用户输入、响应重定向或 DNS 临时结果。

| 决策 ID | 必须锁定的控制 | 当前状态 |
| --- | --- | --- |
| `DP-NET-01` | 每家供应商固定域名、IP/CIDR、端口、路径和环境白名单 | `TBD / Decision required` |
| `DP-NET-02` | scheme/TLS 版本、证书/主机名验证和可选固定信任策略 | `TBD / Decision required` |
| `DP-NET-03` | DNS 首次解析、连接前复核、连接地址固定和缓存 TTL，防 DNS rebinding | `TBD / Decision required` |
| `DP-NET-04` | 重定向策略；默认拒绝，若允许则逐跳重新验证 host/IP/port/path | `TBD / Decision required` |
| `DP-NET-05` | IPv4/IPv6 规范化及回环、私网、链路本地、保留、多播、映射地址拒绝 | `TBD / Decision required` |
| `DP-NET-06` | 云 metadata、容器/宿主机、集群服务、Unix socket 和代理环境隔离 | `TBD / Decision required` |
| `DP-NET-07` | URL userinfo、混淆编码、超长 URL、重复 host、非标准端口和解析歧义拒绝 | `TBD / Decision required` |
| `DP-NET-08` | 响应 content type/size、压缩炸弹、读取 deadline 和临时文件禁用 | `TBD / Decision required` |

Accepted 前默认拒绝所有供应商网络请求；即使未来允许，前端也不得覆盖 allowlist 或传任意网络参数。

## 3. 输入、输出与隐私最小化决策

| 决策 ID | 决策问题 | 淘宝 | 京东 |
| --- | --- | --- | --- |
| `DP-DATA-01` | 允许输入字段、用途、规范化和最大长度 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-DATA-02` | 输出字段、完整链接可见范围、复制和再次鉴权 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-DATA-03` | 输入/输出中授权参数、推广标识、账号或个人信息分类 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-DATA-04` | 请求/响应正文是否允许落库；默认不允许 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-DATA-05` | 日志、审计、指标仅可保存的摘要/hash/host/error 字段 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-RET-01` | 幂等记录、审计、缓存和诊断证据 TTL | `TBD / Decision required` | `TBD / Decision required` |
| `DP-RET-02` | 到期删除方式、失败重试、删除证明和备份处置 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-XBORDER-01` | 数据所在地、跨境、子处理方、法律依据和用户告知 | `TBD / Decision required` | `TBD / Decision required` |

普通 DTO、日志、错误和审计正文不得包含 credential、授权正文、完整输入/结果 URL 或供应商敏感响应。

## 4. 幂等、限流与失败恢复决策

| 决策 ID | 必须决定的事项 | 当前状态 |
| --- | --- | --- |
| `DP-IDEM-01` | idempotency key 生成方、作用域、载荷 hash、TTL 和同键异载荷冲突 | `TBD / Decision required` |
| `DP-IDEM-02` | 供应商是否原生幂等、requestId 查询和响应丢失后的重取 | `TBD / Decision required` |
| `DP-LIMIT-01` | 用户/provider/租户/IP 的速率、突发、日限额和拒绝语义 | `TBD / Decision required` |
| `DP-CONC-01` | 每家供应商并发、连接池、队列上限和隔离 | `TBD / Decision required` |
| `DP-TIMEOUT-01` | connect/read/total timeout 和客户端 deadline | `TBD / Decision required` |
| `DP-RETRY-01` | 哪些错误可重试、次数、退避、费用与幂等前提；默认不重试 | `TBD / Decision required` |
| `DP-ERROR-01` | 本地校验、认证、限流、供应商拒绝、超时、未知和响应无效的稳定映射 | `TBD / Decision required` |
| `DP-RECON-01` | 调用、费用、成功结果和 UNKNOWN 的对账周期、差异阈值和责任人 | `TBD / Decision required` |
| `DP-COMP-01` | 响应丢失、UNKNOWN、供应商成功但本地失败的查询/补偿；禁止盲重试 | `TBD / Decision required` |

## 5. 费用、SLA、容量与可观测决策

| 决策 ID | 决策项 | 淘宝 | 京东 |
| --- | --- | --- | --- |
| `DP-COST-01` | 单价、阶梯、失败/重试/查询是否收费、预算与超限 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-SLA-01` | 可用性、延迟、支持时段、故障通知和赔付 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-CAP-01` | 峰值/持续 QPS、突发、配额和扩容提前量 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-MON-01` | 请求/成功/拒绝/超时/P95/限流/SSRF/重定向/幂等指标 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-ALERT-01` | 告警阈值、值班、供应商升级路径和证据保留 | `TBD / Decision required` | `TBD / Decision required` |
| `DP-AUDIT-01` | 谁在何时为何 provider 发起转换、结果分类、traceId 和权限审计 | `TBD / Decision required` | `TBD / Decision required` |

## 6. ADR-002 调试工作台隔离决策

ADR-002 的受控自定义 HTTPS 调试是独立既有能力，不能成为 DP 的通用传输层或旁路。

| 决策 ID | 必须选择并签署的问题 | 当前状态 |
| --- | --- | --- |
| `DP-DEBUG-01` | 自定义调试是否强制拒绝未来 DP provider 域名/IP；若否，需给出更严格授权依据 | `TBD / Decision required` |
| `DP-DEBUG-02` | DP provider allowlist 与调试 allowlist 是否由不同配置、权限和审批链维护 | `TBD / Decision required` |
| `DP-DEBUG-03` | 调试器是否能携带 DP auth/signature；当前默认禁止 | `TBD / Decision required` |
| `DP-DEBUG-04` | 调试日志、请求/响应正文和 DP 隐私/TTL 如何物理与逻辑隔离 | `TBD / Decision required` |
| `DP-DEBUG-05` | 对 DP 域名的调试尝试如何拒绝、告警和审计 | `TBD / Decision required` |

固定不变量：**不可拿调试器代替 DP Adapter，不可把调试执行当成 DP 协议验收或供应商 UAT。** 在隔离策略 Accepted 并实现验证前，不得用调试工作台访问任何未来 DP 供应商域名。

## 7. UAT、灰度与回滚决策

| 决策 ID | 必须提交的产物 | 当前状态 |
| --- | --- | --- |
| `DP-UAT-01` | 每家供应商隔离环境、测试凭据的安全交付、测试数据和网络授权 | `TBD / Decision required` |
| `DP-UAT-02` | 正常/拒绝/认证/签名/重放/限流/超时/UNKNOWN/幂等用例 | `TBD / Decision required` |
| `DP-UAT-03` | SSRF、DNS rebinding、IPv4/IPv6、重定向、metadata 和响应大小安全证据 | `TBD / Decision required` |
| `DP-UAT-04` | 隐私、TTL 删除、审计、费用和对账证据 | `TBD / Decision required` |
| `DP-UAT-05` | UAT 退出标准、缺陷分级、供应商签字和复测要求 | `TBD / Decision required` |
| `DP-ROLLBACK-01` | 菜单/worker/adapter 停用、凭据吊销、在途请求和幂等结果的回滚 Runbook | `TBD / Decision required` |

阶段顺序建议仍需签署：`Proposed → Accepted design → disabled implementation → isolated UAT → limited production → expansion`。任何阶段进入条件、流量、时间窗和回滚阈值均为 `TBD / Decision required`；Accepted 只授权后续实现评审，不自动授权 UAT 或生产。

## 兼容、灰度与回滚

- 淘宝和京东 Adapter 必须独立版本化、隔离限流和故障域，不能用 provider 分支拼成通用任意代理。
- 协议版本变更、灰度范围、缓存兼容、幂等结果兼容和回滚窗口均为 `TBD / Decision required`。
- 回滚不得删除审计或修改既有结果；是否保留短期加密结果必须由 TTL/隐私决策签署。
- 本 RFC 不授权创建表、配置、菜单、网络客户端或供应商凭据。

## 测试与验收门槛

未来实现至少需要合同、SSRF、隐私、幂等、并发、超时、重试、费用、对账、失败恢复、权限、审计和供应商 UAT 证据。具体环境、数据规模、阈值和签署模板均为 `TBD / Decision required`。

本地 Mock、静态 schema 或 ADR-002 调试结果不得宣称供应商协议成立、真实转换成功或 UAT 完成。

## Accepted 门槛与责任签署

- [ ] 淘宝和京东各自正式协议、版本、auth/signature、allowlist、错误和 UAT 资料均登记受控附件；缺一家不得推断补齐该家。
- [ ] SSRF、隐私/TTL/删除、跨境、幂等、费用、容量、SLA、对账和回滚全部形成明确决策。
- [ ] ADR-002 对 DP 域名、权限、credential 和审计隔离已签署。
- [ ] 所有 `TBD / Decision required` 必填项已关闭或明确列为阻止实现的未决项。
- [ ] RFC 状态通过独立评审变更；正文存在或勾选清单不等于 Accepted。

| 责任角色 | 必须签署的范围 | 姓名 | 日期 | 签署/结论 |
| --- | --- | --- | --- | --- |
| 产品负责人 | 使用目的、输入输出、成功、费用、用户流程和生产范围 | `TBD / Decision required` | TBD | 未签署 |
| 安全/隐私负责人 | SSRF、认证、Secret、字段最小化、TTL、删除、跨境和调试隔离 | `TBD / Decision required` | TBD | 未签署 |
| 架构负责人 | Adapter 边界、幂等、失败恢复、版本、容量和审计 | `TBD / Decision required` | TBD | 未签署 |
| 淘宝供应商负责人 | 正式协议、allowlist、错误、费用、SLA 和 UAT | `TBD / Decision required` | TBD | 未签署 |
| 京东供应商负责人 | 正式协议、allowlist、错误、费用、SLA 和 UAT | `TBD / Decision required` | TBD | 未签署 |
| 运维/SRE 负责人 | 配置交付、网络、监控、值班、限流、回滚和故障演练 | `TBD / Decision required` | TBD | 未签署 |

## 开放问题

本文件中所有标记 `TBD / Decision required` 的项目均为开放问题。未关闭的必填项继续阻塞 S12；不得以参考页面、抓包猜测、历史代码、调试器请求或本地 Mock 替代正式供应商输入。
