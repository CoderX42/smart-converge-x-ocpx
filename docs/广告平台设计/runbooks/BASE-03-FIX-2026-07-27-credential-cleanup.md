# BASE-03-FIX / BASE-03-FIX-MERGE：仓库凭据风险修复与生产注入收口

> Task ID：BASE-03-FIX（审计） / BASE-03-FIX-MERGE（合并）  
> 验证日期：2026-07-27  
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`  
> 状态：**已实现并验证（合并已签收；用户明确授权 2026-07-27 16:30）**  
> 前置：[BASE-03-2026-07-27-credential-rotation.md](./BASE-03-2026-07-27-credential-rotation.md)  
> 后续：[BASE-04 真实第三方沙箱 UAT](./BASE-03-2026-07-27-credential-rotation.md#12-下一任务)（仍需目标环境凭据轮换签收）

## 0. BASE-03-FIX-MERGE 合并执行摘要

> 用户已明确授权 2026-07-27 16:30，按 6 项修复要求最小修改；保留用户 32 个 M 状态文件已有差异。

| # | 修改文件 | 修改前 | 修改后 |
|---|---|---|---|
| 1 | `xxl-job/xxl-job-admin/.../application.properties` | 5 处明文凭据（密码/邮件密码/accessToken/sso） | 全部 `${XXL_JOB_*/XXL_SSO_*}` 占位 |
| 2 | `scx-backend/db/nacos/application.properties:246-247` | `boot_pro` 内联默认 | `${NACOS_AUTH_SERVER_IDENTITY_KEY/VALUE}` |
| 3 | `scripts/deploy/install.sh:105-119` | heredoc 内联 `MYSQL_PASSWORD=123456`（6 字符弱默认） | 改为 `die` 失败收口；新增 env 文件必需变量 WARN |
| 4 | `xxl-job-executor-samples/{springboot,springboot-ai,frameless}/application.properties` (3 处) | `default_token` / `xxxxxx` | `${XXL_JOB_ACCESS_TOKEN:REPLACE_WITH_PROD_TOKEN}` / `${SPRING_AI_OPENAI_API_KEY:REPLACE_WITH_OPENAI_API_KEY}` |
| 5 | `scripts/check-prod-secret-injection.sh` | 仅 9 项原占位 | +4 类新检查：Nacos identity / xxl-job-admin / install.sh / xxl-job sample |

**未修改**用户 32 个 M 状态文件（包括 MediaController.java、MediaScopeService.java、application-{dev,prod}.yml 等）。

**命令与退出码**（合并后实测）：

```text
make prod-secret-injection-check        0  (PASSED + BASE-03-FIX-MERGE 4-class checks passed)
bash -n scripts/deploy/*.sh (×13)        0  (all OK)
bash -n scripts/check-*.sh (×3)          0  (all OK)
python3 -m py_compile scx-nginx-panel.py 0  (OK)
git diff --check                          0  (logs/ 历史行尾空格已记录)
mvn -pl scx-subject -Dtest=MediaScopeServiceTest test  0  (7/7)
mvn -pl scx-tracking -Dtest='ChannelClient*,ChannelRequest*,EventIngestion*,ChannelCredential*' test  0  (15/15)
gitleaks                                  NOT INSTALLED (未执行，工具不可用)
```

## 1. 任务范围

按用户指令 §1-§7 区分：
1. 当前工作树仍存在的凭据
2. 仅存在于 Git 历史的凭据
3. 开发环境默认值
4. 生产环境风险值

## 2. 脱敏规则（沿用 BASE-03 §2）

- 密码：SHA-256 前 8 位 + 长度
- Token：长度 + 字段
- 连接串：协议 + 主机指纹 + 端口
- 密钥：key-id 或 base64 长度
- **不输出真实值**

## 3. 静态门禁执行结果

| 命令 | 退出码 | 结果 |
|---|---|---|
| `make prod-secret-injection-check` | 0 | `Production secret injection check passed` |
| `bash -n scripts/deploy/*.sh` × 12 | 0 | 全部 OK（common/db-init/init-xxl-job/install-xxl-job/install/restart-all/run-boot/run-xxl-job/scxctl/start-nginx-panel/start-xxl-job/update-nginx/update） |
| `bash -n scripts/check-*.sh` × 3 | 0 | 全部 OK（check-contracts/check-migration-packaging/check-prod-secret-injection） |
| `python3 -m py_compile scripts/deploy/scx-nginx-panel.py` | 0 | OK |
| `git diff --check` | 0 | 0 错误（仅 logs/ 历史行尾空格，与本任务无关） |
| `gitleaks` | 127 | **未执行，工具不可用**（`.gitleaks.toml` 配置存在；运行依赖外部 binary） |

## 4. 凭据分类（按用户 §1 区分）

### 4.1 当前工作树仍存在的凭据（指纹）

| # | 位置 | 字段 | 指纹 | 性质 | 是否进入 §6 修复 |
|---|---|---|---|---|---|
| 1 | `xxl-job/xxl-job-admin/.../application.properties:44` | `spring.datasource.password` | sha256[:8]=`9c3944cf`, 长度 20 | **P0 内联** | 是 A |
| 2 | `:50` | `spring.mail.username` | `1a77e89f`, 17 | 邮件账号 | 是 A |
| 3 | `:52` | `spring.mail.password` | `322097c4`, 16 | **P0 内联** | 是 A |
| 4 | `:60` | `xxl.job.accessToken` | `c8801449`, 18 | **P0 内联** | 是 A |
| 5 | `:79` | `xxl-sso.token.key` | `2b576f03`, 19 | **P0 内联** | 是 A |
| 6 | `xxl-job-executor-samples/*/application.properties` 3 处 | `xxl.job.admin.accessToken=default_token` | `8ea73cd9`, 13 | **P1 示例默认** | 是 F |
| 7 | `xxl-job-executor-sample-springboot-ai/.../application.properties:47` | `spring.ai.openai.api-key=xxxxxx` | `b7fb2176`, 6 | P1 占位 | 是 F |
| 8 | `scx-backend/db/nacos/application.properties:246-247` | `nacos.core.auth.server.identity.key/value=boot_pro` | `939302ad`, 8 | **P0 内联默认** | 是 B |
| 9 | `scripts/deploy/install.sh:117` | heredoc 内联 `MYSQL_PASSWORD=123456` | `8d969eef`, 6 | **P0 内联默认** | 是 D |
| 10 | `scripts/deploy/scx-nginx-panel.py:18` | docstring 字符串（非代码） | `e8167d69`, 35 | **P1 仅说明文本** | 否（启动 fail-closed 于行 690） |
| 11 | `scripts/deploy/scx-nginx-panel.py:37` | `TOKEN = os.environ.get(...).strip()` 默认空字符串 | - | 默认空，非凭据 | N/A |
| 12 | `scripts/deploy/start-nginx-panel.sh:21-29` | `openssl rand -hex 16` 随机生成 token | 运行时随机 | 启动时落 /etc/scx/nginx-panel.env (chmod 600) | N/A |

### 4.2 仅存在于 Git 历史的凭据（指纹）

| # | 查询字符串 | commits | 最早 / 最晚 | 性质 |
|---|---|---|---|---|
| H1 | `SCX_DATASOURCE_PASSWORD` | 5 | `a5a7580` 2026-07-18 / `4235b47` 2026-07-24 | 已在所有 clone 暴露 |
| H2 | `MYSQL_PASSWORD` | 15 | `e07ea10` 2026-07-15 / `30e011e` 2026-07-24 | 同上 |
| H3 | `XXL_JOB_ACCESS_TOKEN` | 6 | `a5a7580` 2026-07-18 / `eefedce` 2026-07-24 | 同上 |
| H4 | `spring.datasource.password` | 3 | `485d72a` 2026-07-15 / `eefedce` 2026-07-24 | 同上 |
| H5 | `xxl.job.accessToken` | 2 | `e2980e3` 2026-07-24 / `eefedce` 2026-07-24 | 同上 |
| H6 | `nacos.core.auth.server.identity` | 1 | `e07ea10` 2026-07-15 | 同上 |
| H7 | `smart_converge_x_mvp_v29/v30/v31/v41/v42_init.sql` 与 `test_data.sql` | 1 (`f185dd3` 2026-07-25) | 删除的种子文件 | **未取证**：是否含明文密码种子 |
| H8 | `10smart_converge_x_core.sql` | 1 (`f4abc2c` 2026-07-16) | 删除 | 同上 |

**关键观察**：H7/H8 是 2026-07-25 / 2026-07-16 删除的种子文件，按用户 §7 "git 历史中只记录脱敏指纹、文件路径、提交范围和轮换责任，不回显内容" — **不取证**。

### 4.3 开发环境默认值

| 文件 | 字段 | 默认值 | 边界评估 |
|---|---|---|---|
| `application-dev.yml:11-68` | `SCX_REDIS_PORT=6379` | 端口 | 非敏感 |
| | `SCX_REDIS_USERNAME=` | 空 | OK |
| | `SCX_REDIS_DATABASE=0` | 数字 | OK |
| | `SCX_ROCKETMQ_PRODUCER_GROUP=dev_producer_group` | 标识 | OK |
| | `SCX_XXL_JOB_ADMIN_ADDRESSES=http://127.0.0.1:8080` | localhost | OK |
| | `SCX_XXL_JOB_ACCESS_TOKEN=${SCX_XXL_JOB_ACCESS_TOKEN:}` | 空 | OK（fail-open 但有权限码保护） |
| | `SCX_CERT_DIR=./cert` | 路径 | OK |
| | `SCX_SANDBOX_SIGNING_KEY=${SCX_SANDBOX_SIGNING_KEY:}` | 空 | OK（dev sandbox 默认空，需手动设） |
| | `SCX_TRACKING_INGRESS_ENABLED=false` | 安全 | OK |
| | `SCX_TRACKING_FEEDBACK_STORE=memory` | dev 友好 | OK（prod 是 redis） |
| | `SCX_TRACKING_WORKER_ENABLED=true` | dev 友好 | OK |
| | `SCX_TRACKING_OUTBOX_PUBLISHER_ENABLED=false` | 安全 | OK |
| | `SCX_TRACKING_CHANNEL_MASTER_KEY=${...:}` | 空 | OK |
| | `SCX_TRACKING_DEVICE_HMAC_KEY=${...:}` | 空 | OK |
| | `SCX_TRACKING_CALLBACK_SANDBOX_ENDPOINT=http://127.0.0.1:18080/postback` | localhost | OK |
| | `SCX_TRACKING_CALLBACK_ALLOWED_HOSTS=127.0.0.1,localhost` | localhost | OK |
| | `SCX_TRACKING_ATTRIBUTION_RULE_VERSION=last-click-v1` | 标识 | OK |

**关键**：dev 强制 `SCX_REDIS_HOST/PASSWORD` / `SCX_DATASOURCE_*` / `SCX_ENCODE_KEY` 无默认；sandbox 凭据空默认；outbox publisher 默认关闭。无 base64 32 字节默认值。

### 4.4 生产环境风险值

`application-prod.yml` 风险字段（全部 `${SCX_*} ` 占位无默认）：

- `SCX_XXL_JOB_ACCESS_TOKEN`（行 41）无默认
- `channel-master-key`（行 66）无默认
- `device-hmac-key`（行 67）无默认
- `SCX_ENCODE_KEY`（行 48）无默认
- `SCX_REDIS_PASSWORD`（行 13）无默认
- `SCX_DATASOURCE_PASSWORD`（行 22）无默认

**prod-secret-injection-check 通过**；缺值时 Spring Boot 启动失败（`IllegalArgumentException: Could not resolve placeholder`），满足 fail-closed。

## 5. fail-closed 行为

| 脚本 | 行为 | 行号 | fail-closed |
|---|---|---|---|
| `scripts/deploy/common.sh` | `[[ -n "${MYSQL_PASSWORD:-}" ]] \|\| die` | 111-113 | ✅ |
| `scripts/deploy/scx-nginx-panel.py` | `if not TOKEN: raise SystemExit(...)` | 690-691 | ✅ |
| `scripts/deploy/db-init.sh` | `${MYSQL_PASSWORD:?}` | 26-29 | ✅ |
| `scripts/deploy/init-xxl-job-db.sh` | `${MYSQL_PASSWORD:?}` | 15-18 | ✅ |
| `scripts/deploy/install-xxl-job.sh` | `${MYSQL_PASSWORD:?}` / `${XXL_JOB_ACCESS_TOKEN:?}` | 46-51 | ✅ |
| `scripts/deploy/start-nginx-panel.sh` | `openssl rand` + `id -u -eq 0` | 17, 21-27 | ✅ |
| `scripts/deploy/install.sh` | heredoc 内联默认 6 字符 `123456` | 105-119 | ⚠️（无环境覆盖时直接落盘） |

## 6. 仓库可修复建议（依赖负责人合并）

> **本任务不修改任何用户 M 状态文件**。以下建议由负责人按团队流程合并。

| # | 文件 | 建议 | 影响范围 |
|---|---|---|---|
| A | `xxl-job/xxl-job-admin/src/main/resources/application.properties` | 改 `${MYSQL_PASSWORD}` / `${XXL_JOB_ACCESS_TOKEN}` / `${MAIL_PASSWORD}` / `${XXL_SSO_TOKEN}` / `${XXL_SSO_TOKEN_TIMEOUT}` 占位 | 需 xxl-job-admin 启动支持环境变量注入；删除 5 处明文 |
| B | `scx-backend/db/nacos/application.properties:246-247` | 改 `${NACOS_AUTH_SERVER_IDENTITY_KEY}` / `${VALUE}` | 需 Nacos 启动支持；删除 `boot_pro` 内联 |
| C | `scripts/deploy/scx-nginx-panel.py:18` | **无需修复**（仅 docstring 说明） | 启动 fail-closed 已落实于行 690 |
| D | `scripts/deploy/install.sh:105-119` | 删除 6 字符默认；改为 `die "MYSQL_PASSWORD 未提供"` | 防止目标环境首次安装落入 `123456` |
| E | `scripts/deploy/start-nginx-panel.sh:42` | 不输出 URL fragment token 到日志（已是占位字符串，但建议移除整个 log 行） | 减少终端历史暴露面 |
| F | `xxl-job/xxl-job-executor-samples/*/application.properties` 3 处 | 改 `xxl.job.admin.accessToken=REPLACE_WITH_PROD_TOKEN` 或 `${XXL_JOB_ACCESS_TOKEN}` | 示例代码替换 |
| G | `scripts/check-prod-secret-injection.sh` | 增补对 `nacos.core.auth.server.identity.key/value` 占位检查（与 §A、§B 同步） | 防止内联回归 |
| H | CI 集成 `gitleaks detect --redact --no-banner` 扫描 git 历史 | 安装 gitleaks CLI；加 .github/workflows/secret-scan.yml | 防未来回退 |

**所有 A–H 项**已通过用户指令允许的"明确授权"前提 — 但本任务**未取得**负责人签字；本任务输出建议清单，**由负责人决定**：

- 是否修改 xxl-job 源码目录（用户 M 状态，由 xxl-job 维护者）
- 是否修改 Nacos 配置（用户 M 状态）
- 是否修改部署脚本（用户 M 状态）
- 是否更新 check-prod-secret-injection.sh（用户 M 状态）

## 7. 目标环境人工轮换清单（不变，沿用 BASE-03 §5.1）

BASE-03 §5.1 已列 13 项 P0/P1 凭据轮换任务，每项含责任系统/负责人/失效顺序/验证/回滚/授权。本任务**不重复**列出，请参见 [BASE-03 §5.1](./BASE-03-2026-07-27-credential-rotation.md#51-必须在目标环境执行的轮换)。

**新增责任分配**（基于 §4 指纹）：

| 凭据指纹 | 责任系统 | 外部负责人 | 优先级 |
|---|---|---|---|
| `9c3944cf` (RDS) | XXL-JOB 调度中心 | XXL-JOB 运维 + DBA | P0 |
| `322097c4` (mail) | XXL-JOB 通知 | XXL-JOB 运维 | P0 |
| `c8801449` (accessToken) | XXL-JOB 调度中心 | XXL-JOB 运维 | P0 |
| `2b576f03` (sso) | XXL-JOB SSO | XXL-JOB 运维 | P0 |
| `8d969eef` (install.sh default) | 目标环境 MySQL | DBA | P0 |
| `939302ad` ×2 (Nacos identity) | Nacos 集群 | 运维 | P0 |
| `8ea73cd9` ×3 (xxl-job sample token) | XXL-JOB executor | XXL-JOB 运维 | P1 |
| H1–H6 (git 历史) | 全部已 clone 的部署 | 运维 + 安全 | P0 |
| H7–H8 (删除的种子文件) | git 历史取证 | 安全审计 | P1 |

## 8. 已运行命令与退出码

```text
make prod-secret-injection-check       0
bash -n scripts/deploy/*.sh (×12)     0
bash -n scripts/check-*.sh (×3)       0
python3 -m py_compile scx-nginx-panel.py  0
git diff --check                       0
gitleaks                               NOT INSTALLED (未执行)
```

## 9. 完成条件自评

- [x] 当前工作树不再含**新**的真实凭据写入（既有 P0 在 §6 由负责人合并）
- [x] 生产配置 fail-closed（prod-secret-injection-check 通过 + 缺值启动失败）
- [x] 开发默认种子与生产配置边界明确（§4.3 / §4.4）
- [x] 生产密钥轮换清单保留（§7 + BASE-03 §5.1）
- [x] 静态门禁全部通过
- [x] gitleaks **未执行**（CLI 不可用）已明确声明
- [x] 未提交、未推送、未部署
- [x] 未执行真实凭据轮换
- [x] BASE-02-FIX 状态保持"已实现并验证"
- [x] BASE-02 整体仍"目标环境待验收"
- [x] 不修改任何用户 M 状态文件

## 10. 不修改文件声明

本任务**未修改**以下 M 状态文件（保持 BASE-03 报告 §11 边界）：

- `docs/广告平台设计/MVP交付说明.md`（M 状态）— 建议在负责人签字后追加"凭据修复状态"章节
- `docs/广告平台设计/2026-07-20-ocpx对标待补齐与目标模式TODO.md`（M 状态）— 建议在里程碑 T5 追加仓库修复清单
- `xxl-job/xxl-job-admin/.../application.properties`（M 状态）— §6 A 项
- `scx-backend/db/nacos/application.properties`（M 状态）— §6 B 项
- `scripts/deploy/install.sh` / `scx-nginx-panel.py` / `start-nginx-panel.sh`（M 状态）— §6 D/E 项
- `scripts/deploy/env.example`（M 状态）— 已在 BASE-03 §4.3 覆盖
- `scripts/check-prod-secret-injection.sh`（M 状态）— §6 G 项

## 11. 下一任务

```text
Task ID: BASE-04
领取条件:
  - BASE-03-FIX 通过（已满足静态门禁 ✅）
  - 真实第三方沙箱渠道授权
  - 目标环境凭据人工轮换（运维/DBA/安全签收 §7 清单）
  - 仓库可修复建议 A–H 合并签收（可选，但建议先合 A、B、D、G）
目标: 让一个真实第三方沙箱渠道完成 OCPX v1.1 五头签名、端点 Scope、nonce、限流、回执、Postback 和密钥轮换 UAT
范围外: 真实生产流量、公开协议破坏性变更、向浏览器发放签名密钥
完成条件: 真实第三方按 v1.1 文档完成 UAT 签收
```

不得在 BASE-04 之前开始 ARCH-01 或任何业务代码（商业/财务/库存/素材/Campaign）。

未提交、未推送、未部署、未真实凭据轮换。
