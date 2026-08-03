# Smart Converge X 广告平台研发文档中心

本目录是 **Smart Converge X（SCX，瑶知智汇广告平台）** 当前有效的产品与研发文档入口。平台面向单一企业内部业务线；文档中的规划能力不等于已实现能力。

## 1. Agent 必读顺序

新加入的研发人员或 Agent 必须依次阅读：

1. 仓库根目录 `AGENTS.md`。
2. [ADX 重构实现说明](ADX重构实现说明.md)。
3. [MVP 开发规划（历史执行基线）](MVP开发规划.md)。
4. [OCPX 智能投放技术开发设计](OCPX智能投放技术开发设计.md)。
5. [SCX OCPX 平台使用与功能验收手册](SCX-OCPX平台使用与功能验收手册.md)。
6. 使用 Claude Code 多 Agent 实施时阅读
   [Claude Code 多 Agent 最小任务实施手册](Claude-Code多Agent最小任务实施手册.md)。
7. 使用 Codex 实施时阅读 [Codex OCPX 总 Task 清单](Codex-OCPX-总Task清单.md)。
8. [2026-07-15 功能审计报告](2026-07-15-ocpx-aerim-功能审计报告.md)。
9. [OCPX 对标待补齐与目标模式 ToDo](2026-07-20-ocpx对标待补齐与目标模式TODO.md)。
10. 需要追溯早期观察时再读[竞品调研](竞品调研-ocpx.aerim.cn.md)。
11. 对接第三方渠道时阅读[通用渠道 OCPX 接入指南](通用渠道OCPX接入指南.md)。
12. 开工和交接时使用 [Agent 与交付模板](templates/README.md)。

当前工作树中不存在旧导航曾引用的 00–22 专题文档、Backlog、状态页、数据字典和已接受 ADR。MVP 契约已补充在 `contracts/`。Agent 不得根据已删除文件的标题臆造规则。

## 2. 当前有效文档

| 文档 | 用途 | 权威边界 |
|---|---|---|
| [ADX 重构实现说明](ADX重构实现说明.md) | 当前目标菜单、前后端契约、表结构与旧域退役门禁 | 当前实现基线；优先于历史 OCPX 扩展规划 |
| [MVP 开发规划](MVP开发规划.md) | MVP 范围、Agent 合同、架构/数据方案、任务队列、验收与开放决策 | Accepted；第 15 节默认方案已接受 |
| [OCPX 智能投放技术开发设计](OCPX智能投放技术开发设计.md) | 完整 OCPX 目标边界、锁定决策、模块与 R1–R9 依赖 | 产品方向 Accepted；按任务增量实现 |
| [SCX OCPX 平台使用与功能验收手册](SCX-OCPX平台使用与功能验收手册.md) | 当前能力、缺口、R1-00 操作与生产放行清单 | 当前代码事实与验收边界 |
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

- 文档状态：ADX 重构说明为当前实现基线；原 MVP/OCPX 文档保留为历史决策记录。
- 产品阶段：精简 ADX 管理闭环已进入源码实现与本地门禁阶段。
- 已有代码：`scx-adx` 统一承载渠道、广告主、应用链接、监测链接、扣量、报表和线索；账号与角色复用 `scx-system`。
- 未交付：目标数据库迁移、旧表物理退役、生产部署、真实报表数据接入、汽车之家任务凭据联调与浏览器多角色 UAT。
- 契约状态：`contracts/` 已包含 OpenAPI、AsyncAPI 与事件 JSON Schema，`make contract-check` 为有效质量门。
- 状态更新时间：2026-08-03。

## 4. 事实与状态规则

1. 实现状态以当前代码、数据库、运行日志和本轮测试证据为准。
2. 目标设计以用户明确决定、Accepted ADR/RFC 和已评审规划为准；代码与目标冲突时记录实现漂移，不能让代码反向覆盖决策。
3. 测试文件存在、页面可打开或静态检查通过，不等于运行闭环已验证。
4. 任何“已验证”必须附环境、命令、结果与证据位置；任何“已验收”必须有验收人结论。
5. 不恢复或覆盖用户当前删除/修改的文档、广告主代码、迁移和配置，除非任务明确授权。

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
