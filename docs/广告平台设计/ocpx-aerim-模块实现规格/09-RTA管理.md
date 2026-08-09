# 09 RTA 管理

> 状态：**Blocked**
> 现场页面：`/rta`
> 阻塞原因：Accepted ADR-001 与当前实现约束有效 RTA 模式为 `OFF`；主动决策必须先有替代 ADR

## 1. 参考页面

`PAGE-RTA-LIST` 包含关键词搜索、新增、列表和分页。现场列包含名称、今日概况、RTA ID、操作；行操作可见编辑、日报、时报，另有配置图标。

RTA 列表含业务配置名称和统计值，未将无法可靠裁净的完整画面保存到 Git；字段与弹层由本轮现场文本记录。

| ID | 作用 | 触发/流程 | 结果 | 证据 |
|---|---|---|---|---|
| `BTN-RTA-SEARCH` | 查询 | view 权限 | 列表刷新 | 现场实测 |
| `BTN-RTA-ADD` | 新增 | add 权限 | 打开新增抽屉 | 现场实测 |
| `BTN-RTA-EDIT` | 编辑 | edit + scope | 详情抽屉；Secret 不回显 | 现场可见，未提交 |
| `BTN-RTA-CONFIG` | 全局/账户配置 | config 权限 | 配置抽屉 | 现场打开；包含域名/Secret，未截图 |
| `BTN-RTA-DAILY` | 日报 | report view | 打开统计抽屉 | 现场打开只读 |
| `BTN-RTA-HOURLY` | 时报 | report view | 打开统计抽屉 | 现场可见 |
| `BTN-RTA-STATUS` | 启停 | status 权限 | 目标版本当前强制禁用 | 列状态可见，未执行 |

## 2. 弹层规格

### 2.1 新增/编辑 `POP-RTA-FORM`

现场字段：RTA 服务地址、服务密钥、名称、文档、API、媒体、状态，并有返回/X/确定/取消。

目标字段映射：

| 字段 | 规则 |
|---|---|
| 名称/code | 必填且稳定唯一 |
| 服务地址 | 仅 HTTPS、域名白名单、防 SSRF；读取掩码 |
| Secret | 只写不回显；轮换独立权限 |
| 文档 | 受控文档版本引用 |
| API/Adapter | 选择注册的 `RtaDecisionPort` 实现，不输入任意类名 |
| MediaAccount | 可选或必填由目标协议决定；受对象范围 |
| 超时 | 替代 ADR 必须给出上限；不得由浏览器任意放大 |
| 失败模式 | `FAIL_OPEN/FAIL_CLOSE/BYPASS`，必须由 ADR 锁定默认 |
| 状态 | 当前只允许 `DISABLED`；ACTIVE 提交返回阻塞错误 |

### 2.2 全局配置 `POP-RTA-CONFIG`

现场出现 RTA 域名与加密 Secret。SCX 不应把全局 Secret 下发浏览器；页面只显示域名掩码、credential revision、轮换时间和 configured 状态。保存产生新 revision，并要求受影响 Task 重新发布。

### 2.3 数据统计 `POP-RTA-STATS`

现场为“数据统计”抽屉，含日期范围、搜索、表格和分页；列为日期、名称、RTA ID、请求数、成功数、成功率。日报/时报共享组件，通过 `grain=DAY/HOUR` 切换。关闭、取消筛选不产生业务副作用；本轮未导出。

## 3. SCX 当前能力与决策门禁

`SCX仓库事实`：运行链存在 `RtaDecisionPort` 扩展边界，但当前模式硬锁 `OFF`。因此：

- 可以评审页面、表、接口和模拟测试设计；
- 可以保持 `RtaDecisionPort` 空实现/旁路测试；
- 不得创建 ACTIVE 配置、让 Task 提交 RTA、发布非 OFF 快照或对真实流量发请求；
- UI 隐藏/禁用不是唯一门禁，API、发布校验和数据面都必须拒绝。

解除阻塞的替代 ADR 至少决定：业务价值和合规、请求/响应协议、超时与失败模式、容量、重试是否允许、隐私字段、费用与 SLA、灰度/旁路、停流、统计口径和供应商 UAT。

## 4. 前端目标

路由 `views/adx/rta/index.vue`，API `src/api/adx/rta.ts`。Blocked 期间：列表可用于展示已存在的禁用配置或设计占位；新增/编辑的“启用”锁定并显示 ADR 原因；Task 的 RTA 开关同样锁定。

组件：`RtaProfileTable`、`RtaProfileDrawer`、`RtaCredentialRotateDialog`、`RtaStatsDrawer`、`RtaDecisionGateAlert`。统计请求取消旧查询；日期范围遵循报表通用组件。

## 5. 候选 API（替代 ADR 接受后才生效）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/adx/rta-profiles` | 分页/下拉；Blocked 时只读 |
| POST | `/adx/rta-profiles` | 幂等创建，强制 DISABLED |
| GET/PUT | `/adx/rta-profiles/{id}` | 详情/版本更新 |
| POST | `/adx/rta-profiles/{id}/credentials:rotate` | 独立权限 |
| POST | `/adx/rta-profiles/{id}/status` | 当前 `ACTIVE` 返回 `ADX_RTA_BLOCKED_BY_ADR` |
| GET | `/adx/rta-profiles/{id}/stats?grain=HOUR` | 统一决策事实聚合 |
| POST | `/adx/rta-profiles/{id}/connectivity:check` | 仅替代 ADR 后通过受控 worker |

不得提供“任意 URL + 任意 body”的 RTA 测试接口；调试复用 ADR-002 的受控工作台并绑定已保存配置。

## 6. 候选数据模型

`scx_adx_rta_profile(id, code, name, adapter_key, endpoint, credential_ref, credential_revision, document_id, media_account_id, timeout_ms, failure_mode, status, config_version, audit fields)`。

`scx_ocpx_rta_decision_fact(id, request_fact_id, route_id, route_version, task_id, profile_id, request_id, decision, reason_code, latency_ms, http_status, error_class, credential_revision, occurred_at)`；唯一 `(profile_id, request_id)` 或与入口事实组合的业务唯一键。

Secret 不在决策事实；请求/响应原文受控、脱敏且限期保存。统计来自 decision fact，不由 RTA 管理页临时请求供应商。

## 7. 运行与补偿边界（未来）

- RTA 是同步限时决策，不得对同一请求自动多次重试导致重复费用/不一致；是否单次重试须由协议明确。
- 超时按 RouteSnapshot 冻结的 failureMode 处理并落事实；不得运行时读当前 Profile 改策略。
- 熔断/半开按 profile + instance 观测，但业务决策事实仍进 MySQL。
- 旁路灰度只记录 shadow decision，不影响投递/计费；比较结果进入监控。
- RTA 拒绝后是否计费/上游投递必须由发布策略明确，不能由页面名称推断。

## 8. 权限、安全与观测

权限：`adx:rta:view/add/edit/status/credential/report/debug` 分离。Profile 按 MediaAccount/Channel scope；Secret 轮换需二次确认和审计。

指标（未来）：请求率、成功/拒绝/超时、P50/P95/P99、熔断、旁路差异、失败模式触发、credential revision 分布。Blocked 期间必须有 `rta_non_off_publish_rejected_total`，任何非零尝试告警。

## 9. 验收用例与解除阻塞门槛

| 层级 | Blocked 期间必测 |
|---|---|
| UI | 状态启用不可用且解释原因；Task RTA 同步锁定；Secret 不回显 |
| API | 创建强制 DISABLED；启用返回固定错误；范围外不可读 |
| DB | 无 ACTIVE profile；无 PUBLISHED route 的 rtaMode 非 OFF |
| 发布 | UI 篡改、直接 API、历史请求重放都不能发布非 OFF |
| 数据面 | 即使快照被异常注入非 OFF，也 fail-safe 拒绝/旁路并告警 |

替代 ADR 接受后还需补：供应商 Mock 合同、超时/熔断、shadow 对比、真实 UAT、容量和隐私评审；完成前不得把本模块标为可上线。
