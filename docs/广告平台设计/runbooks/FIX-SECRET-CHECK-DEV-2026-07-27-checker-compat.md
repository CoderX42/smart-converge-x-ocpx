# FIX-SECRET-CHECK-DEV：check-prod-secret-injection.sh dev/prod 兼容性修复

> Task ID：FIX-SECRET-CHECK-DEV
> 验证日期：2026-07-27
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`
> 状态：**已实现并验证（make 退出 0；4 个负向测试全通过；prod 检查严格度不降低）**
> 前置：[IMP-1-2026-07-27-fail-on-unresolved.md §6](./IMP-1-2026-07-27-fail-on-unresolved.md) 发现 check 脚本对 dev yml 误报

## 1. 任务范围

按用户指令：
- 修复 `make prod-secret-injection-check` 因 dev yml 可选占位符导致的误报
- 不降低生产环境密钥检查强度
- 区分 dev/prod 匹配逻辑
- 增加 4 个负向测试
- 不修改 application-dev.yml / application-prod.yml / TrackingProperties

## 2. 根因

`check-prod-secret-injection.sh` 原 required_placeholders 9 个占位符对 dev/prod 都用同一 `${VAR}`（闭合）pattern：

| 字段 | dev yml 实际 | 原 pattern | 结果 |
|---|---|---|---|
| `name-server` | `${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}` | `${ROCKETMQ_NAME_SERVER}` | dev 有 `${VAR:default}` 不含 `${VAR}` 闭合 → MISS ❌ |
| `channel-master-key` | `${SCX_TRACKING_CHANNEL_MASTER_KEY:}` | `${SCX_TRACKING_CHANNEL_MASTER_KEY`（开放） | 巧合匹配（dev 写法末尾 `:` 之前含子串 `${VAR`） |
| `device-hmac-key` | `${SCX_TRACKING_DEVICE_HMAC_KEY:}` | `${SCX_TRACKING_DEVICE_HMAC_KEY`（开放） | 巧合匹配 |

行 22-24（`channel-master-key`、`device-hmac-key`）pattern 末尾**无** `}`，是开放 `${VAR`；行 16-22 是严格 `${VAR}` —— **check 脚本内部不一致**。

## 3. 修复方案

把 required_placeholders 拆为 `dev_required`（开放 `${VAR`）和 `prod_required`（严格 `${VAR}`）：

```bash
dev_required=(
  'host: ${SCX_REDIS_HOST'         # dev 接受 ${VAR:default} 或 ${VAR}
  'password: ${SCX_REDIS_PASSWORD'
  'url: ${SCX_DATASOURCE_URL'
  'username: ${SCX_DATASOURCE_USERNAME'
  'password: ${SCX_DATASOURCE_PASSWORD'
  'name-server: ${ROCKETMQ_NAME_SERVER'
  'key: ${SCX_ENCODE_KEY'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY'
)

prod_required=(
  'host: ${SCX_REDIS_HOST}'        # prod 严格 ${VAR} 无 default
  'password: ${SCX_REDIS_PASSWORD}'
  'url: ${SCX_DATASOURCE_URL}'
  'username: ${SCX_DATASOURCE_USERNAME}'
  'password: ${SCX_DATASOURCE_PASSWORD}'
  'name-server: ${ROCKETMQ_NAME_SERVER}'
  'key: ${SCX_ENCODE_KEY}'
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY}'
  'device-hmac-key: ${SCX_TRACKING_DEVICE_HMAC_KEY}'
)
```

新增 3 段检查：
1. **dev_required**：dev 9 个占位符开放检查（dev 用 `${VAR:default}` 形式可接受）
2. **prod_required**：prod 9 个占位符严格检查（无 default）
3. **prod default check**：prod 中禁止 `${VAR:default}` 形式（用 `grep -F "${placeholder_var}:"` 检测冒号后默认；行 49-57）
4. **inline check**：dev + prod 各自禁止明文（保留 BASE-03-FIX-MERGE 行为）

## 4. 命令与退出码

| 命令 | 退出码 | 结果 |
|---|---|---|
| `bash -n scripts/check-prod-secret-injection.sh` | 0 | OK |
| `make prod-secret-injection-check` | 0 | PASSED + BASE-03-FIX-MERGE 4-class |
| `git diff --check` | 0 | 0 错误（logs/ 历史行尾空格已记录） |
| `git diff --stat scripts/check-prod-secret-injection.sh` | - | +75/-10 |

## 5. 4 个负向测试结果

| # | 测试 | 预期 | 实际 | 判读 |
|---|---|---|---|---|
| 1 | dev 原始 yml（含 `${VAR:default}` 批准空默认） | PASS | exit 0 | ✅ |
| 2 | dev 把 `sandbox-signing-key` 替换为明文 `syn-plaintext-fake` | FAIL inline check | `inline sensitive configuration exists in application-dev.yml` | ✅ |
| 3 | prod 缺 `host: ${SCX_REDIS_HOST}` 占位 | FAIL missing placeholder | `prod profile missing required environment placeholder 'host: ${SCX_REDIS_HOST}'` | ✅ |
| 4 | prod 把 `password: ${SCX_REDIS_PASSWORD}` 改为 `${SCX_REDIS_PASSWORD:default-fake}` | FAIL prod_required | `prod profile missing required environment placeholder 'password: ${SCX_REDIS_PASSWORD}'` | ✅ |

负向测试 1 通过 + 3 个负向测试按预期失败 = 检查器对 dev/prod 区分正确、对 prod 严格度不降低。

## 6. 完成条件

- ✅ `make prod-secret-injection-check` 退出码 0
- ✅ 生产配置检查严格度不降低（9 个占位符全部严格要求 `${VAR}` 无 default）
- ✅ dev 可选占位符不再误报（dev_required 接受 `${VAR:default}` 形式）
- ✅ 负向测试 4 个全部通过
- ✅ 不修改 application-dev.yml / application-prod.yml / TrackingProperties
- ✅ 不删除/放宽已有检查规则
- ✅ IMP-1 Runbook §6 兼容性问题修复（见 [§8](#8-imp-1-runbook-更新)）
- ✅ 未提交、未推送、未部署

## 7. 边界

| 项 | 状态 |
|---|---|
| prod profile 行 49-57 prod default check 冗余 | 死代码（步骤 2 prod_required 已先捕获 `${VAR:default}`），保留作冗余防御 |
| TrackingProperties `@Validated + @NotBlank` | 未做（IMP-2 范围） |
| 真实凭据轮换 | 外部依赖 |
| gitleaks 扫描 | CLI 不可用（明确"未执行，工具不可用"） |

## 8. IMP-1 Runbook 更新

[IMP-1-2026-07-27-fail-on-unresolved.md §6 兼容性发现](./IMP-1-2026-07-27-fail-on-unresolved.md) 标记为**已修复**。

原描述：
> check 脚本 pattern 无冒号，dev yml 实际有冒号默认值；`grep -Fq` 严格匹配字面字符串，dev yml 的 `:` 后缀导致 `required_placeholders` 检查不通过

修复后：dev_required 用 `${VAR` 开放 pattern，dev yml 的 `${SCX_TRACKING_CHANNEL_MASTER_KEY:}` / `${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}` 等可被 `channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY` / `name-server: ${ROCKETMQ_NAME_SERVER` 子串匹配。

## 9. 下一任务

```text
Task ID: IMP-2
目标: 在 TrackingProperties 加 @Validated + @NotBlank；使 SCX_TRACKING_CHANNEL_MASTER_KEY / SCX_TRACKING_DEVICE_HMAC_KEY 在缺失时启动失败
范围: scx-tracking/src/main/java/.../config/TrackingProperties.java
依赖: FIX-SECRET-CHECK-DEV 完成（已满足）；BASE-03-RUNTIME-GATE 通过
完成条件: 严格判据提升至 4/7
```

未提交、未推送、未部署。
