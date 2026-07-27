# SCX OCPX 系统总任务清单（2026-07-27 快照）

> 文档状态：长期任务索引（可被任何 agent CLI 消费：Claude Code / Codex / Cursor / etc.）
> 更新时间：2026-07-27
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`
> 用途：完整 R1-00 / R1 / R2-R9 任务列表 + 当前实现进度 + 依赖 + 启动步骤
> 严格脱敏：不输出真实凭据值

---

## 1. 进度总览

| 阶段 | 范围 | 状态 |
|---|---|---|
| **M0–M6 (MVP-A)** | 主数据、追踪、归因、回传、报表、运营 | ✅ 已实现并验证（[MVP交付说明.md](../../MVP交付说明.md)） |
| **R1-00 前置闭环** | 媒体 Scope / 端点权限 / 凭据时钟 / 菜单融合 | ✅ 已实现并验证（BASE-01 ~ BASE-03-RUNTIME-GATE） |
| **R1-00 本地收口** | 凭据注入 / 审计 / 隐私 / 配置 fail-fast | ✅ 已实现并验证（目标环境、第三方和容量仍待签收） |
| **R1 ARCH** | 5 个新 Maven 模块骨架 | ❌ 未开始（需 BASE-01~05 全部签收） |
| **R1 COM/FIN/INV/CRE/CAM** | 商业 / 财务 / 库存 / 素材 / Campaign | ❌ 未开始 |
| **R1 ADP/EVT/RPT/UI/ACC** | Adapter / 事件 / 报表 / 前端 / 验收 | ❌ 未开始 |
| **R2–R9** | 模型 Shadow / 智能出价 / DCO / 诊断 / 深度归因 / RL / AIGC / 真实财务 / 容量 | ❌ 未开始（远期） |

---

## 2. 已完成（含全部证据路径）

### 2.1 M0–M6 (MVP-A)

| 任务 | 状态 | 证据 |
|---|---|---|
| M1 主数据 | ✅ | scx-delivery 模块 + 媒体对象授权 |
| M2 追踪链接 | ✅ | scx-delivery/tracking-link + 事件映射版本化 |
| M3 监测任务 | ✅ | scx-delivery/tracking-task + 状态机 |
| M4 事件归因回传 | ✅ | scx-tracking 沙箱闭环 |
| M5 报表 | ✅ | scx-reporting + 异步导出 |
| M6 可观测性 | ✅ | [runbooks/M6-可观测性与故障演练.md](./M6-可观测性与故障演练.md) |

### 2.2 R1-00 前置闭环

| Task ID | 状态 | Runbook |
|---|---|---|
| BASE-01（V43–V46 隔离 MySQL 验证） | ✅ 已实现并验证 | [BASE-01-2026-07-27-V43-V46-verification.md](./BASE-01-2026-07-27-V43-V46-verification.md) |
| BASE-02（4 角色 Scope 真实回归） | ⚠️ 部分完成（已发现 P0 越权并修复） | [BASE-02-2026-07-27-scope-regression.md](./BASE-02-2026-07-27-scope-regression.md) |
| BASE-02-FIX（修复 MediaController 越权） | ✅ 已实现并验证 | [BASE-02-FIX-2026-07-27-media-scope.md](./BASE-02-FIX-2026-07-27-media-scope.md) |
| BASE-03（凭据轮换清单） | ✅ 已实现并验证 | [BASE-03-2026-07-27-credential-rotation.md](./BASE-03-2026-07-27-credential-rotation.md) |
| BASE-03-FIX（仓库凭据风险审计） | ✅ 已实现并验证 | [BASE-03-FIX-2026-07-27-credential-cleanup.md](./BASE-03-FIX-2026-07-27-credential-cleanup.md) |
| BASE-03-FIX-MERGE（5 类文件实际修复，用户授权 16:30） | ✅ 已实现并验证 | [§0 合并执行摘要](./BASE-03-FIX-2026-07-27-credential-cleanup.md#0-base-03-fix-merge-合并执行摘要) |
| BASE-03-RUNTIME-GATE（7/7 prod profile fail-closed） | ✅ 已实现并验证 | [BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md](./BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md) |
| L0 生产发布与 V43–V47 | ⛔ 主机身份不清已停止；未执行线上 SQL/部署 | [L0 执行记录](./L0-2026-07-27-production-gate-execution.md) |
| L0 四角色浏览器/API/审计 | ⚠️ 隔离环境通过，生产账号与安全媒体待注入 | [L0 执行记录 §3](./L0-2026-07-27-production-gate-execution.md#3-隔离数据库与四角色证据) |
| BASE-05 / CAP-01~05 | ⚠️ 仅 Redis 本机预检；独立环境和 20k/40k 未执行 | [L0 执行记录 §4](./L0-2026-07-27-production-gate-execution.md#4-故障恢复与容量边界) |
| FIX-SECRET-CHECK-DEV（check 脚本 dev/prod 兼容） | ✅ 已实现并验证（用户 2026-07-27 21:30 授权） | [FIX-SECRET-CHECK-DEV-2026-07-27-checker-compat.md](./FIX-SECRET-CHECK-DEV-2026-07-27-checker-compat.md) |
| IMP-2（TrackingProperties 配置绑定校验） | ✅ 已实现并验证（Spring 配置绑定范围） | [IMP-2-2026-07-27-tracking-properties-validation.md](./IMP-2-2026-07-27-tracking-properties-validation.md) |
| FIX-1（拒绝访问安全审计） | ✅ 已实现并验证（单元与构建范围） | [FIX-1-2026-07-27-security-denial-audit.md](./FIX-1-2026-07-27-security-denial-audit.md) |
| FIX-2（渠道凭据角色权限闭环） | ✅ 已实现并验证（隔离 MySQL） | [FIX-2-2026-07-27-operator-channel-client-write.md](./FIX-2-2026-07-27-operator-channel-client-write.md) |
| FIX-3（上游客户空分页语义） | ✅ 已实现并验证（控制器回归测试范围） | [FIX-3-2026-07-27-upstream-customer-empty-page.md](./FIX-3-2026-07-27-upstream-customer-empty-page.md) |
| P0（本地可信边界收口） | ✅ 已实现并验证（代码、单元、契约和本地构建范围） | [P0-2026-07-27-local-trust-boundary-closure.md](./P0-2026-07-27-local-trust-boundary-closure.md) |

### 2.3 工作树当前状态（2026-07-27 21:30）

**已修改（M 状态保留用户原状）**：

- 32 个 M 文件：用户原 32 个修改保持原状
- 1 个 BASE-02-FIX 增量：scx-backend/scx-subject/.../MediaController.java (+39/-4)
- 1 个 BASE-03-FIX-MERGE 增量：scripts/check-prod-secret-injection.sh (+75/-10)
- 1 个 BASE-03-FIX-MERGE 增量：scripts/deploy/install.sh (+27/-19)
- 1 个 BASE-03-FIX-MERGE 增量：scx-backend/db/nacos/application.properties (+1/-1)
- 5 个 BASE-03-FIX-MERGE 新增/修改：xxl-job-admin/.../application.properties + 3 sample
- 1 个新增单测：scx-backend/scx-subject/src/test/.../MediaScopeServiceTest.java

**新增 runbook**（?? 状态）：

```
docs/广告平台设计/runbooks/
├── PROGRESS-2026-07-27.md                          # 长期进度
├── CONTINUE-PROMPT-2026-07-27.md                  # 跨会话 prompt
├── SYSTEM-TASKS-2026-07-27.md                      # 本文件（任务总清单）
├── BASE-01/02/02-FIX/03/03-FIX/03-RUNTIME-GATE-*.md
├── IMP-1-2026-07-27-fail-on-unresolved.md
└── FIX-SECRET-CHECK-DEV-2026-07-27-checker-compat.md
```

---

## 3. 改进项（建议优先领取，依赖最少）

| Task ID | 目标 | 范围 | 依赖 | 状态 |
|---|---|---|---|---|
| **IMP-1** | prod 必填配置 fail-fast | scx-boot + prod 配置 | BASE-03-RUNTIME-GATE ✅ | ✅ 已由 IMP-2/IMP-3 收口并验证 |
| **IMP-2** | `TrackingProperties` 加 `@Validated + @NotBlank` | scx-tracking/src/main/java/.../config/TrackingProperties.java | IMP-1 | ✅ 已实现并验证（Spring 配置绑定范围） |
| **IMP-3** | 数据源、Redis、RocketMQ、XXL-JOB、登录加密和 Tracking 密钥早期校验 | scx-boot + common-job | IMP-2 | ✅ 已实现并验证 |
| **FIX-REPORTING-PRIVACY** | 点击敏感详情仅返回业务 ID 掩码和设备/IP 存在标记 | reporting + OpenAPI + web-ele | 无 | ✅ 已实现并验证 |
| **FIX-1** | 补 `BaseSecurityAuditListener` 写 sys_log | scx-common-security 新增监听器 | BASE-02-FIX ✅ | ✅ 已实现并验证（单元与构建范围） |
| **FIX-2** | 核查 OP 角色 `ad:channel-client:write` 权限码绑定 | scx-system 角色绑定 + 单测 | BASE-02 ✅ | ✅ 已实现并验证（隔离 MySQL） |
| **FIX-3** | `/boot/ad/upstream-customers` "data=null 但 code=404" 语义核查 | scx-delivery | BASE-02 ✅ | ✅ 已实现并验证（控制器回归测试范围） |
| **FIX-4** | 浏览器多角色回归（Playwright） | 前端 + E2E | 四角色现有账号、授权/未授权媒体和安全测试媒体 | 用例已实现；目标环境账号与媒体参数待注入 |
| **CI-GITLEAKS** | `gitleaks/gitleaks-action@v2` | `.github/workflows/ci.yml` | 无 | ✅ 已存在；不重复实现 |

---

## 4. 外部依赖阻塞（领取前必须解除）

| Task | 阻塞项 | 负责人 |
|---|---|---|
| **BASE-04** | 真实第三方沙箱授权 + v1.1 文档 + 测试凭据 | 商务 / 第三方 |
| **BASE-05** | 独立测试环境（多实例 MySQL/Redis/RocketMQ）+ 分布式发压 | 运维 / DBA |
| **ACC-05** | 独立目标环境 R1 签收 | 负责人 / 客户 |
| **CAP-01~05** | 20k/40k QPS 容量门禁 | 运维 / DBA |

---

## 5. R1 模块骨架（ARCH-01 ~ ARCH-06，需 BASE-01~05 全部签收）

| Task ID | 模块 | 依赖 | 状态 |
|---|---|---|---|
| ARCH-01 | scx-commercial | BASE-01~05 | ❌ |
| ARCH-02 | scx-finance | ARCH-01 | ❌ |
| ARCH-03 | scx-inventory | BASE-01~05 | ❌ |
| ARCH-04 | scx-creative | ARCH-03 | ❌ |
| ARCH-05 | scx-campaign | ARCH-01~04 | ❌ |
| ARCH-06 | 单向依赖 / 启动装配 / 占位权限验收 | ARCH-01~05 | ❌ |

---

## 6. R1 业务模块

### 6.1 COM-01 ~ COM-06 商业主数据

| Task ID | 目标 | 依赖 | 状态 |
|---|---|---|---|
| COM-01 | Organization / Advertiser 创建 + 范围查询 | ARCH-01 | ❌ |
| COM-02 | 最多二级 AgencyRelation | COM-01 | ❌ |
| COM-03 | AdAccount 创建 + 归属 + 树 | COM-02 | ❌ |
| COM-04 | QualificationCase 状态机 + 不可变审核日志 | COM-03 | ❌ |
| COM-05 | AgreementTemplate/Instance | COM-03 | ❌ |
| COM-06 | DataAuthorization + PublishingEligibilityPort | COM-04 + COM-05 | ❌ |

### 6.2 FIN-01 ~ FIN-13 财务

详见 [Claude-Code多Agent最小任务实施手册 §7 R1 Task Prompt - FIN-01~13](../../Claude-Code多Agent最小任务实施手册.md)

### 6.3 INV-01 ~ INV-05 媒体库存 / CRE-01 ~ CRE-06 素材 / CAM-01 ~ CAM-07 Campaign

详见同上 R1 Task Prompt 索引。

### 6.4 ADP-01 ~ ADP-05 媒体 Adapter / EVT-01 ~ EVT-06 事件 / RPT-01 ~ RPT-06 报表

详见同上。

---

## 7. R1 前端与验收

| Task ID | 范围 | 依赖 | 状态 |
|---|---|---|---|
| UI-01 ~ UI-10 | 10 个前端运营页面 | 各对应 API | ❌ |
| ACC-01 | 8 角色权限矩阵自动化验收 | UI-01~10 | ❌ |
| ACC-02 | FORM_SUBMIT 黄金 E2E | ACC-01 | ❌ |
| ACC-03 | Redis/MQ/Outbox/Adapter 故障恢复 | ACC-02 | ❌ |
| ACC-04 | 隐私 / 密钥 / 日志 / 审计 | ACC-02 | ❌ |
| ACC-05 | 独立目标环境 R1 签收 | ACC-01~04 | ❌ |

---

## 8. R2–R9 远期

| 阶段 | Task | 目标 | 状态 |
|---|---|---|---|
| R2 | ML-01~ML-05 | Feature Registry / Dataset Snapshot / TrainingRun / ModelVersion / Shadow | ❌ |
| R3 | BID-01~BID-05 | Decision API / CTR-CVR / oCPC-oCPM / PID Pacing / Kill Switch | ❌ |
| R4 | DCO-01~02 + AUD-01~02 | DCO Bandit / Lookalike | ❌ |
| R5 | DIAG-01~DIAG-04 | 诊断信号 / 根因 / 建议 / 审批状态机 | ❌ |
| R6 | ATTR-01~ATTR-04 | MTA / Markov-Shapley / SKAdNetwork / 实验设计 | ❌ |
| R7 | RL-01~02 + AIGC-01~02 | 强化学习 / AIGC 接入 | ❌ |
| R8 | REALFIN-01~REALFIN-03 | 真实支付接入（必须 ADR/RFC 审批） | ❌ |
| R9 | CAP-01~CAP-05 | 20k/40k QPS 容量门禁 | ❌ |

---

## 9. 领取下一任务建议（按依赖顺序）

```
优先级 1（无外部依赖，已完成）：
  IMP-2  →  TrackingProperties @Validated + @NotBlank
  FIX-1  →  BaseSecurityAuditListener 写 sys_log
  FIX-2  →  OP 角色 ad:channel-client:write 权限码核查
  FIX-3  →  /boot/ad/upstream-customers 404 语义核查

优先级 2（外部依赖）：
  BASE-04 → 真实第三方沙箱 v1.1 UAT
  BASE-05 → RocketMQ/Outbox 故障恢复
  IMP-3  →  CI gitleaks 集成

优先级 3（需 R1-00 全闭环签收）：
  ARCH-01 ~ ARCH-06 → 5 个新 Maven 模块骨架
  COM-01 ~ COM-06 / FIN-01 ~ FIN-13 / INV-01 ~ INV-05
  CRE-01 ~ CRE-06 / CAM-01 ~ CAM-07
  ADP-01 ~ ADP-05 / EVT-01 ~ EVT-06
  RPT-01 ~ RPT-06 / UI-01 ~ UI-10
  ACC-01 ~ ACC-05

优先级 4（远期）：
  R2 ML / R3 BID / R4 DCO+AUD / R5 DIAG
  R6 ATTR / R7 RL+AIGC / R8 REALFIN / R9 CAP
```

---

## 10. 在 Codex CLI 中启动

> Codex CLI 是 OpenAI 的代码生成工具，与 Claude Code 略有差异：
> - Codex 使用 sandboxed shell + agent loop
> - Codex 没有 hooks（`lefthook.yml` 在 Codex 环境中不生效）
> - Codex 通过 `~/.codex/config.toml` 配置
> - Codex subagent 通过 `codex agent` 子命令

### 10.1 启动步骤

```bash
# 1. 安装 Codex CLI（如未安装）
npm install -g @openai/codex

# 2. 切换工作区
cd /Users/karl/code/yw-tech/smart-converge-x

# 3. 设置环境（Codex 需 OpenAI API key）
export OPENAI_API_KEY=sk-xxx

# 4. 启动 Codex（交互模式）
codex

# 5. 在 Codex 中粘贴以下 prompt
```

### 10.2 Codex 启动 Prompt

```text
你是 SCX OCPX 项目的代码实现 agent。工作区：/Users/karl/code/yw-tech/smart-converge-x

【上下文】
R1-00 前置闭环已完成（2026-07-27）：
- BASE-01 ~ BASE-03-RUNTIME-GATE 全部通过
- 详见 docs/广告平台设计/runbooks/SYSTEM-TASKS-2026-07-27.md（总任务清单）
- 详见 docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md（进度）
- 详见 docs/广告平台设计/runbooks/CONTINUE-PROMPT-2026-07-27.md（继续 prompt）

【建议优先领取（无外部依赖）】
1. IMP-2: TrackingProperties 加 @Validated + @NotBlank
2. FIX-1: BaseSecurityAuditListener 写 sys_log
3. FIX-2: OP 角色 ad:channel-client:write 权限码核查
4. FIX-3: /boot/ad/upstream-customers 404 语义核查

【硬规则】
- 一次只领取一个 Task
- 不 reset/clean/stash/rebase/覆盖/删除用户已有修改（32 个 M 文件 + 5 个 BASE-02-FIX/MERGE 增量文件）
- DB 只前向迁移；不修改 V*__*.sql
- 不输出/记录/写入真实凭据（仅 SHA-256 前 8 + 长度）
- 菜单可见性≠授权；后端权限码 + 对象 Scope 必须同时成立
- HTTP 202≠成功；继续核对 RawEvent/归因/Callback/Outbox/MQ/Redis/报表
- 涉及公开协议/真实资金/生产/隐私/不可恢复操作时暂停请求授权
- 静态门禁：make prod-secret-injection-check + bash -n scripts/deploy/*.sh + py_compile scx-nginx-panel.py + git diff --check

【第一步】
1. git status --short && git diff --stat
2. 读 docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md
3. 读 docs/广告平台设计/runbooks/SYSTEM-TASKS-2026-07-27.md
4. 读 docs/广告平台设计/runbooks/CONTINUE-PROMPT-2026-07-27.md
5. 问用户领取哪个 Task

【任务开始必输出】
Task ID / 业务目标 / 范围内 / 范围外 / 依赖 / 当前代码基线 / 当前工作树风险 / 文件所有权 / 数据库迁移 / API/事件契约 / 权限与隐私 / 失败与降级 / 测试范围 / 完成条件

【任务完成必输出】
状态 / 业务闭环 / 变更 / 验证证据 / 质量与维护性 / 边界 / 工作树 / 下一任务
```

### 10.3 Codex 与 Claude Code 关键差异

| 维度 | Claude Code | Codex CLI |
|---|---|---|
| 模型 | Claude (Anthropic) | GPT (OpenAI) |
| 配置 | `~/.claude/` + AGENTS.md | `~/.codex/` + `config.toml` |
| 多 agent | 主 Agent + Task agent | subagent via `codex agent` |
| Hooks | lefthook.yml + PreToolUse/PostToolUse | 无 hooks（用 sandbox 配置） |
| Plan mode | EnterPlanMode | `--plan` flag |
| 模型切换 | `/model` 命令 | `model` config in `config.toml` |
| 输出格式 | GitHub-flavored markdown | Plain text + diff |
| 工具调用 | Read/Edit/Write/Bash/Grep | Read/Edit/Bash/Grep 类似 |
| 上下文压缩 | 自动 | 需显式 `codex compact` |

---

## 11. 元数据

- 创建日期：2026-07-27
- 关联文档：[PROGRESS-2026-07-27.md](./PROGRESS-2026-07-27.md), [CONTINUE-PROMPT-2026-07-27.md](./CONTINUE-PROMPT-2026-07-27.md)
- 适用范围：跨 agent CLI（Claude Code / Codex / Cursor / Aider）
- 严格脱敏：本文档不输出真实凭据值
- 维护说明：每完成一个任务更新 §2.2 / §3 状态
