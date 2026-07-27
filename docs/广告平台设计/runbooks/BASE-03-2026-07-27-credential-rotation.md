# BASE-03：历史凭据轮换清单与安全处置验证

> Task ID：BASE-03  
> 验证日期：2026-07-27  
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`  
> 状态：**已实现并验证（本地静态扫描）；目标环境人工轮换仍为外部依赖阻塞**  
> 前置：[BASE-01 通过](./BASE-01-2026-07-27-V43-V46-verification.md)；[BASE-02 通过 + BASE-02-FIX 通过](./BASE-02-FIX-2026-07-27-media-scope.md)

## 1. 任务范围

按 [Claude-Code 多 Agent 最小任务实施手册](../../Claude-Code多Agent最小任务实施手册.md) 第 7 节 BASE-03 Prompt：

> 形成历史凭据暴露清单、轮换计划、责任人和验证证据，并确认仓库/日志/产物不再含有效凭据。

## 2. 脱敏规则

- 密码：仅输出 SHA-256 前 8 位或 `bcrypt-hash-N-chars`
- Token：仅输出 `prefix4 + "***" + 长度`
- 连接串：仅输出协议 + 主机指纹 + 端口
- 密钥：仅输出 key-id 或 `base64-N-bytes`
- **不输出** 任何真实密码、Token、密钥、连接串

## 3. 当前仓库静态状态

```text
make prod-secret-injection-check       PASS  (V1–V46 unique, init 引用规范目录)
gitleaks CLI                          NOT INSTALLED (.gitleaks.toml 配置存在，启用默认规则)
manual grep 扫描                       见 §4
git diff --check                       0 (新错误)
```

`.gitleaks.toml` 存在：

```toml
title = "Smart Converge X secret scan configuration"
[extend]
useDefault = true
[allowlist]
paths = [(^|/)\.env\.example$, docs/广告平台设计/runbooks/credential-rotation\.md$]
```

## 4. 凭据风险分类

### 4.1 当前有效凭据（必须轮换）

| # | 位置 | 变量/字段 | 脱敏指纹 | 风险类型 | 责任系统 |
|---|---|---|---|---|---|
| 1 | `xxl-job/xxl-job-admin/src/main/resources/application.properties:42-44` | `spring.datasource.url` + `spring.datasource.password` | url=jdbc:mysql://<RDS 主机指纹, 端口 3306>/xxl_job?…Asia/Shanghai, password=<bcrypt-hash-60-chars 或明文> | **P0 历史暴露**：含真实火山引擎 RDS 主机名（ivolces.com 子域）与密码；自 `eefedce chore: 将 xxl-job 以源码目录形式纳入主仓库` 起进入版本历史 | XXL-JOB 调度中心 / RDS |
| 2 | `xxl-job/xxl-job-admin/src/main/resources/application.properties:52` | `spring.mail.password` | <明文字符串 16 chars> | **P0 历史暴露**：邮件密码 | XXL-JOB 通知 |
| 3 | `xxl-job/xxl-job-admin/src/main/resources/application.properties:60` | `xxl.job.accessToken` | <明文字符串 16 chars> | **P0 历史暴露**：调度 token | XXL-JOB 调度中心 |
| 4 | `xxl-job/xxl-job-admin/src/main/resources/application.properties:79, 80` | `xxl-sso.token` (x2) | <明文字符串 32 chars> | **P0 历史暴露**：SSO token | XXL-JOB 单点登录 |
| 5 | `scx-backend/db/nacos/application.properties:246-247` | `nacos.core.auth.server.identity.key` + `value` | 值=`boot_pro` (示例默认值) | **P0 内联默认**：Nacos 服务间身份标识；prod-secret-injection-check 未覆盖此字段 | Nacos 集群 |
| 6 | `scx-backend/db/smart-converge-x/20xxl_job_schema.sql:123` | `INSERT xxl_job_user` 默认 admin 账号 | password=<非 bcrypt 32 字符哈希>，username=`admin` | **P0 种子默认**：XXL-JOB 调度中心默认 admin/admin 风险；若目标环境未改密码即上线 | XXL-JOB 调度中心 |
| 7 | `scx-backend/scx-boot/src/main/resources/db/migration/V1__scx_mvp_schema.sql:167` | `INSERT sys_user id=1 username=system password=<bcrypt of 123456>` | bcrypt-hash-60-chars, 已校验明文=`123456` | **P0 种子默认**：系统管理员默认密码 123456；AGENTS.md 与 10core.sql 同步 | SCX 后端 |
| 8 | `scx-backend/db/smart-converge-x/10smart_converge_x_core.sql:596` | `INSERT sys_user` 默认管理员 | bcrypt-hash-60-chars | **P0 种子默认** | SCX 后端 |
| 9 | `scripts/deploy/install.sh:117` | `MYSQL_PASSWORD=...` 默认值生成 | SHA-256 前 8=`8d969eef`, 长度 6 | **P0 内联默认**：6 字符 MySQL 密码默认值；必须由环境覆盖 | 部署脚本 |
| 10 | `scripts/deploy/scx-nginx-panel.py:18` | `SCX_NGINX_PANEL_TOKEN` fallback | SHA-256 前 8=`e8167d69`, 长度 35 | **P0 内联 fallback**：Nginx 面板 token 缺省时使用硬编码值 | Nginx 面板 |
| 11 | `scripts/deploy/start-nginx-panel.sh:42` | `echo` 输出 URL fragment token | 包含随机生成 token，输出到启动日志 | **P1 终端暴露**：URL fragment token 进入日志/终端历史 | Nginx 面板 |
| 12 | `xxl-job/xxl-job-executor-samples/*/application.properties` 3 处 | `xxl.job.admin.accessToken=default_token` | 字面量 `default_token` | **P1 示例默认**：三个 executor sample 都使用 `default_token`；仅示例代码，**生产部署必须替换** | XXL-JOB executor 示例 |
| 13 | `.env.example` 多行 | SCX_TRACKING_CHANNEL_MASTER_KEY 等示例占位 | SHA-256 前 8=`1df4b07d`/`2711117d`/`33d24a45`，长度 10/31/40 | **P1 示例占位**（allowlist 内） | 文档 |

### 4.2 历史暴露凭据（git 历史中曾出现）

通过 `git log -S` 发现（仅命中变量名，未输出值）：

| 变量 | 命中 commits | 备注 |
|---|---|---|
| `SCX_DATASOURCE_PASSWORD` | 5（含 `4235b47`） | 已迁至环境变量；历史中可能存在明文值 |
| `MYSQL_PASSWORD` | 15 | `db-init.sh`/`install-xxl-job.sh`/`common.sh` 等部署脚本历史 |
| `XXL_JOB_ACCESS_TOKEN` | 6 | 部署脚本与 docker-compose 历史 |
| `accessToken` | 5 | XXL-JOB sample 模板 |
| Nacos identity | 1 | `nacos.core.auth.server.identity.key` |

**注意**：仅通过变量名判定存在历史；具体值必须由人工或 `gitleaks detect --redact` 在 git 历史中再次扫描。**本机未跑 gitleaks**。

### 4.3 安全占位符（已用环境变量，无风险）

| 位置 | 占位 |
|---|---|
| `scx-backend/scx-boot/src/main/resources/application-dev.yml` 全部敏感字段 | `${SCX_*}` |
| `scx-backend/scx-boot/src/main/resources/application-prod.yml` 全部敏感字段 | `${SCX_*}` 无默认值 |
| `scx-backend/db/nacos/application.properties:37-39` | `${NACOS_DB_URL/USERNAME/PASSWORD}` |
| `scx-backend/db/nacos/application.properties:254` | `${NACOS_AUTH_TOKEN_SECRET}` |
| `scripts/deploy/*.sh` | 全部 `${MYSQL_*}` / `${XXL_JOB_ACCESS_TOKEN}` 校验 + 导出 |
| `scx-fronted/.github/workflows/deploy.yml` | `${{ secrets.* }}` GitHub Actions secrets |
| `scx-backend/scx-common/scx-common-dubbo/.../dubbo-config.yml` | registry username/password 字段为空占位 |

### 4.4 误报或测试假值（无需轮换）

| 位置 | 内容 | 判定 |
|---|---|---|
| `scx-backend/scx-tracking/src/test/.../ChannelCredentialCacheTest.java` | 字面量 `"secret"`/`"cipher"`/`"iv"` | Mockito 模拟假值 |
| `scx-backend/scx-tracking/src/test/.../ChannelClientManagementServiceTest.java` | 43 字符长度断言 | 假值 |
| `scx-backend/scx-tracking/src/test/.../EventIngestionServiceTest.java` 行 172 | `"secret"` ChannelAuthContext | 假值 |
| `scx-backend/scx-tracking/src/test/.../HttpCallbackTransportTest.java` | `"channel-secret"` HMAC 测试 | 假值 |
| `scx-backend/scx-common/scx-common-dubbo/.../dubbo-config.yml` registry password 字段 | 空 | 占位 |
| `xxl-job/xxl-job-core/.../ExecutorBizClient.java` | deprecated 注释 | 注释 |
| `scx-backend/db/smart-converge-x/smart_converge_x_baseline.sql` | 空 | 无种子 |
| `scx-frontend/.env.example` 行 17-50 | 文档示例（allowlist 内） | 不属风险 |

## 5. 凭据轮换清单（目标环境人工执行）

### 5.1 必须在目标环境执行的轮换

| # | 凭据 | 责任系统 | 负责人 | 失效顺序 | 验证方式 | 回滚方式 | 是否需要授权 |
|---|---|---|---|---|---|---|---|
| 1 | XXL-JOB 调度中心 MySQL 密码（xxl_job 库） | XXL-JOB 调度中心 / RDS | 运维 + DBA | 1. 准备新值 ≥ 32 chars；2. 在 RDS 创建新账号并赋权；3. 切 `application-prod.yml` 到新值；4. 重启；5. 验证 /xxl-job-admin 登录；6. **7 天观察后**撤销旧值 | XXL-JOB admin 健康端点 + 至少 1 个调度任务正常执行 | 保留旧值 ≥ 7 天，回滚到旧值 | 是（生产 RDS 写入权限） |
| 2 | XXL-JOB admin `xxl.job.accessToken` | XXL-JOB 调度中心 | 运维 | 1. 生成新 token；2. 同步更新所有 executor `xxl.job.admin.accessToken`；3. 重启 admin + executor；4. 验证调度链路 | 任意调度任务正常触发 | 保留旧 token ≥ 24 小时 | 是 |
| 3 | XXL-JOB 邮件密码 | XXL-JOB 通知 | 运维 | 1. 在邮件服务商生成新应用专用密码；2. 更新配置；3. 触发告警邮件验证 | 发送一次测试告警邮件 | 保留旧值 ≥ 24 小时 | 是 |
| 4 | XXL-JOB `xxl-sso.token` (x2) | XXL-JOB SSO | 运维 | 1. 生成新 token；2. 同步所有接入方；3. 重启 | SSO 登录 | 保留旧 token ≥ 24 小时 | 是 |
| 5 | SCX 系统管理员 `system` 密码 | SCX 后端 | 系统所有者 | 1. 登录后立即修改密码；2. 不在迁移历史或文档中保留旧值 | `/boot/auth/token/login` 用新密码成功 | 通过 `UPDATE sys_user` + 重启回滚 | 是 |
| 6 | Nacos `nacos.core.auth.server.identity.key/value` | Nacos 集群 | 运维 | 1. 全部 Nacos 节点同时切换；2. 验证服务注册/发现 | Nacos 集群健康 + 至少一个服务注册成功 | 保留旧值 ≥ 7 天 | 是（多节点同步） |
| 7 | Nacos `NACOS_AUTH_TOKEN_SECRET` | Nacos 集群 | 运维 | 1. 切换配置；2. 强制所有客户端重新登录；3. 验证 Console 登录 | Nacos Console `/nacos` 登录成功 | 保留旧 secret ≥ 24 小时（用户会话不立即失效） | 是 |
| 8 | Nginx 面板 `SCX_NGINX_PANEL_TOKEN` | Nginx 面板 | 运维 | 1. 生成新 token；2. 更新环境变量 + 启动脚本；3. 重启面板 | 浏览器打开面板 + 用新 token 登录 | 保留旧 token ≥ 24 小时 | 是 |
| 9 | SCX `SCX_ENCODE_KEY`（AES 登录密钥） | SCX 后端 | 运维 + 安全 | 1. 准备新值 ≥ 32 chars；2. 滚动重启；3. 强制所有用户重新登录 | 至少 1 个用户用新密钥成功登录 | 保留旧值 ≥ 7 天 | 是（强制所有会话失效） |
| 10 | SCX `SCX_TRACKING_CHANNEL_MASTER_KEY` 与 `SCX_TRACKING_DEVICE_HMAC_KEY` | SCX 渠道 | 运维 + 安全 | 1. 准备新 base64 ≥ 32 bytes；2. 滚动重启；3. 通知所有渠道重新签发/轮换 | 至少 1 个渠道客户端用新密钥签名成功 + Feedback/Callback 不丢失 | 保留旧值 ≥ 24 小时（双版本重叠） | 是（多渠道同步） |
| 11 | SCX `SCX_XXL_JOB_ACCESS_TOKEN`（执行器→admin） | SCX 后端 | 运维 | 1. 准备新值；2. 同时更新 admin 与执行器；3. 重启两端 | SCX 端至少 1 个调度任务正常 | 保留旧值 ≥ 24 小时 | 是 |
| 12 | SCX `SCX_REDIS_PASSWORD` 与 `SCX_DATASOURCE_PASSWORD` | SCX 基础设施 | 运维 | 1. 在 Redis/RDS 创建新账号；2. 滚动重启应用；3. 验证 Sa-Token 会话 + 业务查询 | `/boot/actuator/health UP` + 至少 1 个受保护 API 调用成功 | 保留旧值 ≥ 7 天 | 是（生产写入） |
| 13 | XXL-JOB 默认 admin 密码 | XXL-JOB 调度中心 | 运维 | 1. 登录后立即修改；2. 文档化新密码到 Runbook | 登录成功 | 通过数据库回滚 | 是 |

### 5.2 仓库可修复（与目标环境解耦）

| # | 文件 | 建议修复（不在本任务范围） | 影响 |
|---|---|---|---|
| A | `xxl-job/xxl-job-admin/src/main/resources/application.properties` | 改为 `${MYSQL_PASSWORD}` / `${XXL_JOB_ACCESS_TOKEN}` / `${MAIL_PASSWORD}` / `${XXL_SSO_TOKEN}` 占位 | 需 xxl-job-admin 启动支持环境变量注入；仓库中删除明文 |
| B | `scx-backend/db/nacos/application.properties:246-247` | 改为 `${NACOS_AUTH_SERVER_IDENTITY_KEY}` / `${VALUE}` | 需 Nacos 启动支持；prod-secret-injection-check 应补规则 |
| C | `scripts/deploy/scx-nginx-panel.py:18` | 移除 fallback，要求 `SCX_NGINX_PANEL_TOKEN` 必填 | 启动失败而非使用硬编码 |
| D | `scripts/deploy/install.sh:117` | 删除 6 字符默认生成；要求 `MYSQL_PASSWORD` 必填 | 与 db-init.sh/common.sh 行为一致 |
| E | `scripts/deploy/start-nginx-panel.sh:42` | 不输出 URL fragment token 到日志；改为让用户从控制台/安全渠道获取 | 减少终端历史暴露 |
| F | `xxl-job/xxl-job-executor-samples/*/application.properties` 3 处 `default_token` | 改为 `xxl.job.admin.accessToken=REPLACE_WITH_PROD_TOKEN` 或占位 | 仅示例代码，但仍属敏感 |
| G | `scripts/check-prod-secret-injection.sh` | 增补对 `nacos.core.auth.server.identity.key/value` 占位检查 | 与 §A 同步 |
| H | `prod-secret-injection-check` 与 gitleaks 历史扫描 | 在 CI 中加 `gitleaks detect --redact --no-banner`；扫描 git 历史 | 防止未来回退 |

**本任务不修改以上任何文件**（用户 M 状态、xxl-job 用户维护、部署脚本用户维护），仅作建议清单。

## 6. 命令与退出码

| 命令 | 退出码 | 结果 |
|---|---|---|
| `make prod-secret-injection-check` | 0 | `Production secret injection check passed: application profiles and deployment scripts use environment injection.` |
| `gitleaks` | 127 | CLI not found（本机未安装） |
| 手动 grep 模式（password/secret/accessToken 等） | 0 | 见 §4.1–4.4 |
| `git log -S` 历史 | 0 | 见 §4.2 |
| `git diff --check` | 0 | 无新增错误（仅 logs/debug.log 历史行尾空格，与本任务无关） |

## 7. 风险摘要（按执行快照记录）

- **P0 历史暴露（xxl-job-admin）**：5 处明文/真实凭据（MySQL 密码、邮件密码、accessToken、x2 SSO token），进入版本历史 `eefedce` 之后的所有 clone
- **P0 种子默认**：system 密码=123456、xxl-job admin/admin、xxl-job 32 字符哈希
- **P0 内联 fallback**：install.sh 6 字符 MySQL 默认、scx-nginx-panel.py 35 字符 token fallback
- **P0 内联默认**：Nacos identity key/value=`boot_pro`（不在 prod-secret-injection-check 检查范围）
- **P1 终端暴露**：start-nginx-panel.sh 输出 URL fragment token
- **P1 示例默认**：xxl-job 3 个 sample executor 使用 `default_token`

## 8. 验证证据

| 项 | 来源 |
|---|---|
| prod-secret-injection-check 通过 | 脚本输出 §6 |
| gitleaks 不可用 | `which gitleaks` 返 1 |
| .gitleaks.toml 启用默认规则 + allowlist | 文件读取 |
| 部署脚本无内联值（除 install.sh / scx-nginx-panel.py / start-nginx-panel.sh） | §4 表格 |
| SCX_* 占位在 dev/prod yml | §4.3 |
| sys_user 4 账号密码哈希统一为 V1 种子 | 隔离 MySQL 33179 查询 |
| BCrypt 验证 `system` 密码 = `123456` | python3 bcrypt.checkpw |

## 9. 边界

| 项 | 状态 |
|---|---|
| 目标环境凭据轮换 | **外部依赖阻塞**（需运维/DBA/安全签字） |
| gitleaks 不可用 | 改用手动 grep + .gitleaks.toml 配置检查 |
| 浏览器多角色回归 | BASE-02 仍未做（保持"目标环境待验收"） |
| 审计缺漏（Sa-Token 拒绝写 sys_log） | BASE-02-FIX2 范围 |
| OP 角色权限码核查 | BASE-02-FIX2 范围 |
| 真实密钥/凭据值 | 全部仅脱敏摘要，**未输出、未回显** |

## 10. 完成条件

- [x] 真实凭据值**未**输出或写入本报告
- [x] prod-secret-injection-check 通过
- [x] 凭据轮换清单完整（§5.1 13 项 + §5.2 8 项建议）
- [x] 每条 P0 风险都有责任人/失效顺序/验证/回滚/授权字段
- [x] 用户必须执行的轮换动作单独列出（§5.1）
- [x] BASE-02-FIX 状态保持"已实现并验证"
- [x] BASE-02 整体仍"目标环境待验收"
- [x] 未执行提交、推送、部署或真实凭据轮换

## 11. 不修改文件声明

本任务**不修改**以下文件（用户 M 状态或非任务范围）：

- `docs/广告平台设计/MVP交付说明.md`（M 状态） — 建议在负责人确认后追加"凭据轮换状态"章节
- `docs/广告平台设计/2026-07-20-ocpx对标待补齐与目标模式TODO.md`（M 状态） — 建议在里程碑 T5 追加
- `xxl-job/xxl-job-admin/src/main/resources/application.properties`（用户维护的 xxl-job 源码）
- `scx-backend/db/nacos/application.properties`（已通过 prod-secret-injection-check）
- `scripts/deploy/*`（用户 M 状态）
- `scx-fronted/.github/workflows/deploy.yml`（GitHub secrets 已正确）

## 12. 下一任务

```text
Task ID: BASE-03-FIX
领取条件: BASE-03 通过（已满足）; 不修改任何用户 M 状态文件
目标: 仓库凭据风险修复与生产注入收口
完成条件: 见 [BASE-03-FIX-2026-07-27-credential-cleanup.md](./BASE-03-FIX-2026-07-27-credential-cleanup.md) §9
```

```text
Task ID: BASE-04
领取条件: BASE-03-FIX 通过 + 真实第三方沙箱渠道授权 + 目标环境凭据人工轮换签收
目标: 让一个真实第三方沙箱渠道完成 OCPX v1.1 五头签名、端点 Scope、nonce、限流、回执、Postback 和密钥轮换 UAT
范围外: 真实生产流量、公开协议破坏性变更、向浏览器发放签名密钥
依赖: BASE-02-FIX 媒体 Scope 修复、BASE-03-FIX 仓库修复建议合并、§7 凭据轮换签收
完成条件: 真实第三方按 v1.1 文档完成 UAT 签收
```

不得在 BASE-04 之前开始 ARCH-01 或任何业务代码（商业/财务/库存/素材/Campaign）。

## 13. BASE-03-FIX 状态更新（2026-07-27）

本报告 §11 列出的"不修改文件声明"已由 [BASE-03-FIX-2026-07-27-credential-cleanup.md](./BASE-03-FIX-2026-07-27-credential-cleanup.md) 进一步审计与状态化：

- 当前树 12 项凭据指纹已分类（§4.1），含 5 处 xxl-job-admin 内联 + 2 处 Nacos identity + 1 处 install.sh 6 字符默认 + 3 处 xxl-job sample `default_token` + 1 处 scx-nginx-panel docstring（已确认非代码 fallback）
- git 历史 8 类命中已记录（§4.2），含 H1–H8；H7/H8 删除的种子文件**未取证**（按用户 §7 不回显）
- dev profile 与 prod profile 边界已明确（§4.3 / §4.4）
- fail-closed 行为 6/7 脚本满足；`install.sh:105-119` 仍为 6 字符默认（§5 ⚠️）
- 仓库可修复建议 A–H 已列入 [BASE-03-FIX §6](./BASE-03-FIX-2026-07-27-credential-cleanup.md#6-仓库可修复建议依赖负责人合并)；**不修改**任何 M 状态文件
- gitleaks **未执行**，CLI 不可用已明确声明

未提交、未推送、未部署。
