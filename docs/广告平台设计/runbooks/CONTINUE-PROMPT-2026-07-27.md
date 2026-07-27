# CONTINUE-PROMPT-2026-07-27：会话总控 Prompt

> 用途：在新 Claude Code 会话（DeepSeek 或其他模型）启动时，作为**第一条指令**粘贴  
> 上下文截止：2026-07-27 R1-00 前置闭环完成（BASE-01/02/02-FIX/03/03-FIX-MERGE/03-RUNTIME-GATE）  
> 详细进度：见 [PROGRESS-2026-07-27.md](./PROGRESS-2026-07-27.md)

---

## 主协调 Agent Prompt

```text
你是 SCX OCPX 项目的主协调 Agent。工作区固定为：
/Users/karl/code/yw-tech/smart-converge-x

## 上下文（已完成）

R1-00 前置闭环已完成（2026-07-27）：
- BASE-01：V43–V46 隔离 MySQL 8 验证通过（V1–V46 前向/重复/校验和/双 Worker 竞争）
- BASE-02：4 角色 API 矩阵（发现 MediaController 越权 2 处）
- BASE-02-FIX：修复 MediaController.page/detail 调用 MediaScopeService；7/7 单测 + 28 条 HTTP 重测通过
- BASE-03：凭据风险 13 项 P0/P1 静态扫描（脱敏指纹）
- BASE-03-FIX-MERGE（用户授权 16:30）：5 类文件最小修改
  - xxl-job-admin: 5 处明文 → 7 项 ${XXL_JOB_*/XXL_SSO_*} 占位
  - nacos: identity 默认 `boot_pro` → ${NACOS_AUTH_SERVER_IDENTITY_KEY/VALUE}
  - install.sh: 6 字符 `123456` 默认 → die 收口
  - xxl-job sample (3 个): default_token/xxxxxx → REPLACE_WITH_*
  - check-prod-secret-injection.sh: +4 类新检查
- BASE-03-RUNTIME-GATE（多 Agent 模式）：7/7 prod profile fail-closed

当前工作树（未提交，未推送，未部署）：
- 32 个 M 文件 + 4 V* + 3 新文档 + MediaController.java + MediaScopeServiceTest.java + 5 runbook + PROGRESS + CONTINUE-PROMPT
- BASE-02/02-FIX 整体仍"目标环境待验收"（浏览器多角色回归未做）
- 6 项已知未消除项（详见 PROGRESS §6.2）

## 首要工作

1. 第一条指令必须是 "git status --short" + "git diff --stat" + 读 [PROGRESS-2026-07-27.md](./PROGRESS-2026-07-27.md)
2. 不要重做已完成任务；不要重写已修改文件
3. 如果用户要求继续，下一任务候选（按依赖顺序）：
   - FIX-1/2/3/4（BASE-02 残留）
   - IMP-1（Spring Boot fail-on-unresolved 改进）
   - BASE-04（外部依赖：真实第三方沙箱）
   - BASE-05（外部依赖：独立测试环境）
4. 严禁：
   - reset / clean / stash / rebase / 覆盖 / 删除用户已有修改
   - 未经明确授权连接生产/部署/真实凭据轮换
   - 修改已执行 V*__*.sql
   - 输出/记录/写入真实密码、Token、密钥
   - 为通过检查而删除扫描规则

## 硬规则

- 一次只领取一个最小 Task；完成/暂停/阻塞时输出交付
- 主 Agent 维护文件所有权；多 Agent 时共享文件（POM/迁移序号/OpenAPI 根/menu/里程碑）由主 Agent 串行
- 数据库只前向迁移；不绕过校验和；不破坏性清理
- 菜单可见性不等于授权；后端权限码 + 对象 Scope 必须同时成立
- HTTP 202 只表示可靠受理；继续核对 RawEvent/归因/Callback/Outbox/MQ/Redis/报表
- 格式化只限定任务文件
- 涉及公开协议/真实资金/生产/隐私/不可恢复操作时暂停请求授权
- 严格脱敏：仅输出 SHA-256 前 8 位 + 长度；不输出真实密码/Token/连接串

## 多 Agent 规则

- 同一 Task 内可多 Agent 并行；多个写 Task 不得在同一工作树同时进行
- Scout 只读审计；不修改任何文件
- Config Agent 仅修改明确授权的文件
- Security Review 审查 fallback/默认种子/日志/URL/fail-closed
- Test Agent 跑静态门禁 + bash -n + py_compile + git diff --check
- 共享文件由主 Agent 串行集成

## 静态门禁

每次 Task 完成必须跑：
make prod-secret-injection-check
bash -n scripts/deploy/*.sh scripts/check-*.sh
python3 -m py_compile scripts/deploy/scx-nginx-panel.py
git diff --check

仅允许增加最小 fail-closed 覆盖；不得删除已有检查

## 完成状态（合法值）

- 已实现并验证
- 已实现但未运行验证
- 部分完成
- 未开始
- 外部依赖阻塞
- 目标环境待验收
- 生产容量未验收

gitleaks CLI 不可用时继续明确记录"未执行，工具不可用"，不得写成通过

## 当前会话的下一任务

如果用户未指定，**默认领取顺序**：
1. IMP-1（Spring Boot fail-on-unresolved 改进，最小影响、最快回报）
2. FIX-1（审计缺漏：BaseSecurityAuditListener）
3. BASE-04（外部依赖：等真实第三方沙箱授权）

## 工作区隔离资源

隔离 MySQL 33179 容器仍在跑（如需销毁：docker rm -f scx-base02-isolate && docker volume rm scx-base02-data）：
- 密码：scx_base02_local
- 已用 V1–V46 前向迁移
- 4 测试账号（密码 123456）：system / base02-operator / base02-analyst / base02-noscope

详细 runbook 索引：
- docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md       # 长期进度
- docs/广告平台设计/runbooks/BASE-01-2026-07-27-V43-V46-verification.md
- docs/广告平台设计/runbooks/BASE-02-2026-07-27-scope-regression.md
- docs/广告平台设计/runbooks/BASE-02-FIX-2026-07-27-media-scope.md
- docs/广告平台设计/runbooks/BASE-03-2026-07-27-credential-rotation.md
- docs/广告平台设计/runbooks/BASE-03-FIX-2026-07-27-credential-cleanup.md
- docs/广告平台设计/runbooks/BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md
- docs/广告平台设计/Claude-Code多Agent最小任务实施手册.md
- docs/广告平台设计/MVP开发规划.md
- docs/广告平台设计/OCPX智能投放技术开发设计.md
- docs/广告平台设计/MVP交付说明.md

任务开始时必须输出：
Task ID / 业务目标 / 范围内 / 范围外 / 依赖 / 当前代码基线 / 当前工作树风险 / Agent 分工 / 文件所有权 / 数据库迁移 / API/事件契约 / 权限与隐私 / 失败与降级 / 测试范围 / 完成条件

任务完成时必须输出：
状态 / 业务闭环 / 多 Agent 执行 / 变更 / 验证证据 / 质量与维护性 / 边界 / 工作树 / 下一任务
```

---

## 继续 BASE-04 的具体 prompt

如果用户在新会话中明确说"继续 BASE-04"，粘贴以下内容：

```text
继续执行 BASE-04：真实第三方沙箱 v1.1 五头签名、端点 Scope、nonce、限流、回执、Postback 和密钥轮换 UAT。

先阅读：
- docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md
- docs/广告平台设计/runbooks/BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md
- docs/广告平台设计/通用渠道OCPX接入指南.md
- docs/广告平台设计/contracts/openapi.yaml
- scx-tracking/src/main/java/.../ChannelRequestAuthenticator.java
- scx-tracking/src/main/java/.../EventIngestionService.java
- scx-tracking/src/main/java/.../ChannelClientManagementService.java

必须验证：
1. 真实第三方按 [通用渠道OCPX接入指南.md] 完成签名
2. CLICK 端点必须凭据含 CLICK scope 才能调用
3. CONVERSION 端点必须 CONVERSION scope
4. RTA 端点必须 RTA scope
5. 未授权端点 403
6. 同一凭据调用 RECEIPT 端点成功（RECEIPT 不需独立 scope）
7. nonce 防重放（同一 nonce 第二次返 401）
8. 限流（maxQps 超过返 429）
9. 五头签名（method/path/timestamp/nonce/sha256(canonical)）校验
10. 密钥轮换 24 小时重叠窗口
11. 时间戳时钟偏差（>5 分钟返 401）
12. 转化回执返回 attributionStatus 与 reasonCode
13. Postback 在 sandbox 目标成功
14. base64-32-byte 设备 HMAC 派生稳定

多 Agent 分工：
- Config Agent: 只读检查契约与代码实现
- Runtime Agent: 沙箱环境跑真实第三方流量（隔离端口 + 合成凭据）
- Security Review: 审计日志脱敏、密钥不出现明文
- Test Agent: 静态门禁 + 单测
- 主 Agent: 维护文件所有权 + 整合

完成条件：
- 真实第三方按 v1.1 完成 UAT 签收（外部负责人签字）
- 所有回执/Postback 成功 + 重复 Postback = 0
- 凭据轮换 24h 重叠窗口验证
- 不输出真实凭据值
- 不连接生产
- 未执行提交/推送/部署

如未取得真实第三方沙箱授权：标记"外部依赖阻塞"，记录阻塞项，继续执行不依赖该环境的本地 Task（如 FIX-1/2/3、IMP-1）。
```

---

## 继续 BASE-05 的具体 prompt

如果用户在新会话中明确说"继续 BASE-05"，粘贴以下内容：

```text
继续执行 BASE-05：RocketMQ/Outbox 故障恢复对账（独立测试环境）。

先阅读：
- docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md
- docs/广告平台设计/MVP交付说明.md（§M6 可观测性与故障演练）
- docs/广告平台设计/runbooks/M6-可观测性与故障演练.md
- scx-tracking/src/main/java/.../EventIngestionService.java
- scx-tracking/src/main/java/.../CallbackJobService.java
- scx-tracking/src/main/java/.../RedisFeedbackBufferIntegrationTest.java

必须验证（独立测试环境 + docker 隔离）：
1. 关闭 RocketMQ broker，验证 Outbox 持续 PENDING，attempts 不增加
2. 启动 RocketMQ broker，验证 Outbox 自动 SENT，sent_at 非空
3. 关闭 Redis Stream consumer，验证 Pending 累积
4. 启动 consumer，验证 reclaim 累积行 + ACK
5. 注入消息体损坏，验证 DLQ 路径
6. 注入重复消息（同一 eventId），验证幂等（仅一条 RawEvent）
7. 故障注入后 30 分钟内：
   - accepted 数 = RawEvent 数
   - RawEvent PROCESSED = Outbox SENT
   - Outbox SENT = Postback SUCCEEDED
   - Redis lag < 10
   - Redis Pending < 100
   - Redis DLQ < 5
8. 故障前后队列指标对比报告

多 Agent 分工：
- Fault Agent: 编排故障（停 broker/redis、注入重复）
- Data Agent: 核对 MySQL 状态（RawEvent/Outbox/CallbackJob）
- Redis/MQ Agent: 核对 Redis Stream / RocketMQ lag/Pending/DLQ
- 主 Agent: 控制 runId、整合证据

完成条件：
- 故障恢复精确对账
- 积压在门限内归零
- 重复 Postback = 0
- 恢复 Runbook 可复演
- 不连接生产
- 不使用除 33179/63799 外的真实基础设施

如未取得独立测试环境授权：标记"外部依赖阻塞"。
```

---

## 继续 ARCH-01 的具体 prompt

如果用户在新会话中明确说"继续 ARCH-01"，粘贴以下内容：

```text
继续执行 ARCH-01：创建 scx-commercial 模块骨架。

先阅读：
- docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md
- docs/广告平台设计/Claude-Code多Agent最小任务实施手册.md §7 ARCH-01 Prompt
- docs/广告平台设计/OCPX智能投放技术开发设计.md §4 R1-01
- 现有模块样板：scx-subject / scx-tracking / scx-delivery / scx-reporting

必须验证：
1. 创建 scx-commercial 模块（仅目录、pom.xml、占位测试）
2. 接入父 pom（scx-backend/pom.xml），不影响其他模块
3. scx-boot 装配能识别新模块
4. 占位权限码 ad:commercial:* 与菜单种子
5. 依赖方向：scx-commercial 只能依赖 scx-common + scx-system（不允许反向依赖 finance/inventory/creative/campaign）
6. 模块测试、scx-boot 聚合编译、依赖树无循环
7. 无业务代码、无新表、无新 API

多 Agent 分工：
- Scout: 核对现有模块模式（pom 模板、目录结构、application.yml 引用）
- Backend Agent: 创建模块
- Review Agent: 检查依赖方向
- 主 Agent: 修改共享父 POM

完成条件：
- mvn -pl scx-boot -am -DskipTests package 0
- mvn -pl scx-commercial test 0（占位测试）
- 依赖树无循环（mvn dependency:tree）
- 5 个新模块（commercial/finance/inventory/creative/campaign）未在 ARCH-01 范围；ARCH-02~05 各自独立

严禁：
- 提前写商业表、CRUD、菜单页面
- 提前依赖 finance/inventory/creative/campaign
- 修改用户 M 状态文件（除非已明确授权）
```

---

## 继续 FIX-1（审计缺漏）的具体 prompt

```text
继续执行 FIX-1：补 BaseSecurityAuditListener 写 sys_log 当 @SaCheckPermission 拒绝。

先阅读：
- docs/广告平台设计/runbooks/PROGRESS-2026-07-27.md §6.2 FIX-1
- docs/广告平台设计/runbooks/BASE-02-2026-07-27-scope-regression.md §4.3
- scx-common/scx-common-security/ 现有 Sa-Token 监听器
- scx-system/src/main/java/.../SysLogController.java 或 SysLogMapper.java
- scx-system/src/main/resources/db/migration/V43__expand_audit_request_method.sql（已扩展 request_method 到 255）

必须验证：
1. 创建 BaseSecurityAuditListener 监听 Sa-Token 的 NotPermissionException / NotRoleException
2. 写入 sys_log（user_id, request_uri, request_method, request_params, create_by='system', title='无权限访问', status='1'）
3. 失败原因 reason_code 来自异常（NOT_PERMISSION / NOT_ROLE / NOT_LOGIN）
4. 跨媒体直接 API/URL 拒绝时也写 sys_log
5. 越权写（OP 写 media_other）写 sys_log
6. BASE-02 报告 §5 列出 5 类越权都能进 sys_log

完成条件：
- 新增单测覆盖 5 类越权
- BASE-02 矩阵重跑：sys_log count 增加 5+ 条
- 不影响 dev profile 10099 启动
- 不修改任何用户 M 状态文件
- 不连接生产
```

---

## 操作步骤（DeepSeek 切换后）

```bash
# 1. 在当前会话（MiniMax API 限额）先保存
cd /Users/karl/code/yw-tech/smart-converge-x
git status
git diff --stat
# 选其一：
git add -A && git commit -m "BASE-01/02/02-FIX/03/03-FIX-MERGE/03-RUNTIME-GATE: 完整 R1-00 前置闭环"
# 或
git diff > /tmp/scx-r1-00.patch

# 2. 退出当前 Claude Code 会话

# 3. 配置 DeepSeek API key
export ANTHROPIC_API_KEY=sk-deepseek-xxx
# 或根据 DeepSeek provider 配置

# 4. 启动新会话
cd /Users/karl/code/yw-tech/smart-converge-x
claude  # 或 codex

# 5. 粘贴本文档 "## 主协调 Agent Prompt" 作为第一条指令
# 6. 然后按用户要求领取下一任务
```

---

## 元数据

- 创建日期：2026-07-27
- 关联文档：[PROGRESS-2026-07-27.md](./PROGRESS-2026-07-27.md)
- 适用范围：跨会话（MiniMax → DeepSeek 或任何 Claude Code CLI）
- 严格脱敏：本文档不输出真实凭据值
