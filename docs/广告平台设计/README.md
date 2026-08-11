# Smart Converge X 广告平台研发文档中心

本目录是 **Smart Converge X（SCX，瑶知智汇广告平台）** 当前有效的产品与研发文档入口。平台面向单一企业内部业务线；文档中的规划能力不等于已实现能力。

## 1. Agent 必读顺序

新加入的研发人员或 Agent 必须依次阅读：

1. 仓库根目录 `AGENTS.md`。
2. [OCPX 重构实现说明](OCPX管理面实现说明.md)。
3. 实现对应业务模块前阅读 [OCPX 参考系统到 SCX 的模块化实现规格](ocpx-aerim-模块实现规格/README.md)；该包只使用 2026-08-09 当前浏览器会话证据，并受当前 OCPX 基线、Accepted ADR 与正式契约约束。
4. [MVP 开发规划（历史执行基线）](MVP开发规划.md)。
5. [OCPX 智能投放技术开发设计](OCPX智能投放技术开发设计.md)。
6. 阅读 [OCPX 双向转发引擎实现说明](OCPX双向转发引擎实现说明.md) 了解 V70-V74 的当前代码事实。
7. 需要对照旧 `sgyz-ocpx` 时阅读 [sgyz-ocpx 基础能力兼容基线](sgyz-ocpx基础能力兼容基线.md)。
8. 涉及充值或 CPC 时阅读 [OCPX 点击计费与充值账务说明](OCPX点击计费与充值账务说明.md)。
9. 需要追溯 V70 前沙箱时再读 [SCX OCPX 平台使用与功能验收手册](SCX-OCPX平台使用与功能验收手册.md)。
10. 使用 Claude Code 多 Agent 实施时阅读
   [Claude Code 多 Agent 最小任务实施手册](Claude-Code多Agent最小任务实施手册.md)。
11. 使用 Codex 实施时阅读 [Codex OCPX 总 Task 清单](Codex-OCPX-总Task清单.md)。
12. [2026-07-15 功能审计报告](2026-07-15-ocpx-aerim-功能审计报告.md)。
13. [OCPX 对标待补齐与目标模式 ToDo](2026-07-20-ocpx对标待补齐与目标模式TODO.md)。
14. 需要追溯早期观察时再读[竞品调研](竞品调研-ocpx.aerim.cn.md)。
15. 面向下游渠道交付时使用[OCPX 下游渠道 API 接入文档](OCPX下游渠道API接入文档.md)。
16. 新增 Coco 类上游时阅读[OCPX 上游广告主适配器开发指南](OCPX上游广告主适配器开发指南.md)。
17. 平台内部联调与故障排查时阅读[通用渠道 OCPX 接入指南](通用渠道OCPX接入指南.md)。
18. Coco 联调时阅读 [Coco v1.1 正式协议与 Apifox 联调](Coco-v1.1正式协议与Apifox联调.md)。
19. 开工和交接时使用 [Agent 与交付模板](templates/README.md)。

当前工作树中仍不存在旧导航曾引用的 00–22 专题文档、Backlog、状态页和数据字典；`adr/` 已包含 Accepted ADR-001/002，以及不具决策效力的 Proposed/Not Accepted ADR-003 与 RFC-001，MVP 契约已补充在 `contracts/`。Agent 不得根据已删除文件的标题臆造规则，也不得把 Proposed 文件当成 Accepted 决策或绕过 Accepted ADR。

## 2. 当前有效文档

| 文档 | 用途 | 权威边界 |
|---|---|---|
| [OCPX 重构实现说明](OCPX管理面实现说明.md) | 当前目标菜单、前后端契约、表结构与旧域退役门禁 | 当前实现基线；优先于历史 OCPX 扩展规划 |
| [OCPX 参考系统到 SCX 的模块化实现规格](ocpx-aerim-模块实现规格/README.md) | 总体数据联通、分模块全栈规格、页面证据与实施验收矩阵 | 现场观察 + SCX Draft/Review 实现规格；不证明参考系统后端，不覆盖 OCPX 基线、Accepted ADR 或当前契约 |
| [OCPX 双向转发引擎实现说明](OCPX双向转发引擎实现说明.md) | 独立 scx-ocpx-engine、双向通用适配器、可靠队列、CPC 和验收边界 | V70-V74 源码实现；目标环境迁移、真实外发、容量与 UAT 未执行 |
| [Coco v1.1 正式协议与 Apifox 联调](Coco-v1.1正式协议与Apifox联调.md) | Coco 宏、成功码、callback、FIRST_ORDER、Mock 与运行中心核验 | V74 源码契约；真实 Coco UAT 前仍为 No-Go |
| [OCPX 点击计费与充值账务说明](OCPX点击计费与充值账务说明.md) | 充值幂等、CLICK 扣费、余额不足、流水和生产门禁 | 源码契约；目标库迁移、真实余额与流量对账未执行 |
| [sgyz-ocpx 基础能力兼容基线](sgyz-ocpx基础能力兼容基线.md) | 旧系统核心流程、SCX 覆盖矩阵、接口映射和专用适配器边界 | 基础流程基线；不表示全部旧媒体/DSP 私有协议已兼容 |
| [ADR-001 独立数据面](adr/ADR-001-OCPX双向转发独立数据面.md) | 管理面与高并发数据面拆分决策 | Accepted |
| [ADR-002 接口调试与双端日志](adr/ADR-002-系统内置接口调试与双端日志.md) | 已接入链路、自定义 HTTPS、一次性授权、调试关联与双端有界日志 | Accepted；V76 源码实现，目标环境与真实链路验收待执行 |
| [ADR-004 数据库变更不使用迁移账本](adr/ADR-004-数据库变更不使用迁移账本.md) | 当前态空库 schema、显式前向 SQL、数据库外发布证据与账本退役边界 | Accepted；现有账本表仅作可选遗留，不读取、不补写，删除需单独授权 |
| [ADR-003 RTA 主动决策解锁门禁](adr/ADR-003-RTA主动决策解锁门禁.md) | S11 协议、安全、隐私、容量、Secret、事实、UAT 与分阶段门禁签署清单 | **Proposed / Not Accepted**；不解除 Blocked，不授权 ACTIVE、外呼、shadow 或 UAT |
| [RFC-001 DP 供应商协议与安全门禁](adr/RFC-001-DP供应商协议与安全门禁.md) | 淘宝/京东协议待件、SSRF、隐私、幂等、调试隔离、费用、对账与 UAT 签署清单 | **Proposed / Not Accepted**；不解除 Blocked，不授权菜单、Adapter、convert、外呼或 UAT |
| [OCPX 下游渠道 API 接入文档](OCPX下游渠道API接入文档.md) | 可交付给下游研发的 Feedback、Callback、签名、幂等、重试和 UAT 规范 | Contract v1；实际域名、渠道编码、路由和密钥单独安全交付 |
| [OCPX 上游广告主适配器开发指南](OCPX上游广告主适配器开发指南.md) | 新增 Coco 类上游的协议确认、SPI 实现、注册、测试与灰度流程 | 研发规范；第三方正式协议和真实 UAT 决定生产成功语义 |
| [MVP 开发规划](MVP开发规划.md) | MVP 范围、Agent 合同、架构/数据方案、任务队列、验收与开放决策 | Accepted；第 15 节默认方案已接受 |
| [OCPX 智能投放技术开发设计](OCPX智能投放技术开发设计.md) | 完整 OCPX 目标边界、锁定决策、模块与 R1–R9 依赖 | 产品方向 Accepted；按任务增量实现 |
| [SCX OCPX 平台使用与功能验收手册](SCX-OCPX平台使用与功能验收手册.md) | V70 前 OCPX 沙箱能力与验收记录 | 历史基线，不是当前运行契约 |
| [Claude Code 多 Agent 最小任务实施手册](Claude-Code多Agent最小任务实施手册.md) | Claude Code 会话总控、多 Agent 文件所有权、R1–R9 最小 Task 与逐项 Prompt | 执行方案；实现状态仍以代码和验收证据为准 |
| [Codex OCPX 总 Task 清单](Codex-OCPX-总Task清单.md) | Codex 当前收口 Task、R1–R9 总任务、依赖顺序和当前领取指令 | 执行清单；实现状态仍以代码和验收证据为准 |
| [MVP 交付说明](MVP交付说明.md) | 当前实现范围、验证结果、数据库升级与运行态验收入口 | 代码完成；运行态验收待执行 |
| `contracts/` | 沙箱事件 OpenAPI、异步消息 AsyncAPI、事件信封 JSON Schema | `make contract-check` 已启用 |
| [通用渠道 OCPX 接入指南](通用渠道OCPX接入指南.md) | v1.1 签名、设备字段、Feedback/Callback、错误码、联调与容量验收边界 | 本机迁移、Redis 恢复及容量失败证据已落盘；浏览器多角色、第三方 UAT 与专用环境 2 万 QPS 尚未验收 |
| [MVP-A 发布、回滚与数据核验手册](runbooks/MVP-A-发布与回滚.md) | 发布前检查、迁移包一致性、数据库备份/升级、回滚边界与运行态核验 | Runbook 已就绪；目标环境演练待执行 |
| [功能审计报告](2026-07-15-ocpx-aerim-功能审计报告.md) | 竞品全路由、交互和安全边界的较新证据 | 参考输入，不证明竞品后端实现，也不替代 SCX 决策 |
| [OCPX 对标待补齐与目标模式 ToDo](2026-07-20-ocpx对标待补齐与目标模式TODO.md) | 目标菜单、功能差距、分里程碑待办与验收条件 | Draft；对标实施输入，待负责人确认后进入迭代基线 |
| [竞品调研](竞品调研-ocpx.aerim.cn.md) | 较早的页面、字段和功能面盘点 | 参考输入；与审计或仓库事实冲突时取较新证据 |
| [Agent 与交付模板](templates/README.md) | 任务、交接、ADR、RFC、迁移、测试、发布和事故模板 | 格式约束，不决定业务范围 |

## 3. 当前状态

- 文档状态：OCPX 重构说明为当前实现基线；原 MVP/OCPX 文档保留为历史决策记录。
- 产品阶段：精简 OCPX 管理闭环已进入源码实现与本地门禁阶段。
- 已有代码：`scx-ocpx-admin` 承载管理面与充值账务；`scx-ocpx-contract`、`scx-ocpx-runtime` 与独立 `scx-ocpx-engine` 已形成 FeedbackReq → 上游适配 → callback → 下游适配双向代码闭环，并实现 CLICK 审计/CPC、Coco v1.1 业务成功码和运行请求审计。V70-V74 为待授权应用的前向迁移。
- 未交付：目标数据库迁移、生产部署、真实 Coco/下游 UAT、生产密钥轮换、Redis AOF/副本验收、故障演练与两实例 20,000 accepted QPS 容量门槛。
- 契约状态：`contracts/` 已切换为当前双向转发 OpenAPI、Redis Stream AsyncAPI、FeedbackReq/CallbackReq JSON Schema；历史 `/open-api/events/v1` 已明确退出当前契约。
- 状态更新时间：2026-08-07。

## 4. 事实与状态规则

1. 实现状态以当前代码、数据库、运行日志和本轮测试证据为准。
2. 目标设计以用户明确决定、Accepted ADR/RFC 和已评审规划为准；代码与目标冲突时记录实现漂移，不能让代码反向覆盖决策。
3. 测试文件存在、页面可打开或静态检查通过，不等于运行闭环已验证。
4. 任何“已验证”必须附环境、命令、结果与证据位置；任何“已验收”必须有验收人结论。
5. 不恢复或覆盖用户当前删除/修改的文档、广告主代码、迁移和配置，除非任务明确授权。
6. 数据库变更遵守 ADR-004：不得使用 `scx_schema_migration`；以显式前向 SQL、数据库外发布记录和真实结构后置条件作为证据。

## 4.1 运行配置规范（Agent 强制，与 AGENTS.md §6.4 一致）

自 2026-07 起，SCX **应用运行配置**采用配置文件**直写**，**禁止**依赖环境变量占位与 `/etc/scx/scx.env`：

| 规则 | 说明 |
| --- | --- |
| 禁止 `${SCX_*}` / `${XXL_JOB_*}` 等业务占位 | 写在 `application-dev.yml`、`application-prod.yml`、`xxl-job-admin` 的 `application.properties` |
| 禁止强制 env 启动 | 宝塔 / `java -jar` 只靠 profile + 配置文件即可启动 |
| 禁止恢复 env fail-closed | 勿恢复「缺少 SCX_* 则拒绝启动」的 EnvironmentPostProcessor 强制逻辑 |
| 改配置后须重新打包 | 配置打进 jar 后重启才生效 |

权威全文与 Agent 自检清单：**仓库根目录 `AGENTS.md`（或 `Agents.md`）第 6.4 节**。  
部署脚本说明：`scripts/bt-deploy/README.md`。
