# IMP-1：application-prod.yml fail-on-unresolved-placeholders 验证

> Task ID：IMP-1
> 验证日期：2026-07-27
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`
> 状态：**部分完成（属性已存在；对 2/7 场景生效；5/7 仍命中框架内部 placeholder 解析）**
> 前置：[BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md](./BASE-03-RUNTIME-GATE-2026-07-27-fail-closed.md)
> 用户授权 2026-07-27 21:01 修改 application-prod.yml（M 状态）

## 1. 任务范围

按 [PROGRESS-2026-07-27.md §6.2 IMP-1](./PROGRESS-2026-07-27.md)：
> `application-prod.yml` 加 `spring.config.fail-on-unresolved-placeholders=true` 或对所有 password/token/key 加非空 Bean Validation（5/7 当前依赖 MySQL 通信失败作为 fail-closed 兜底）

## 2. 关键发现：属性已存在

```text
scx-backend/scx-boot/src/main/resources/application-prod.yml:2-3
spring:
  config:
    fail-on-unresolved-placeholders: true
```

提交来源：`f9cc73b feat: 完成R1-00 OCPX全量改造，新增渠道端点权限与数据库时钟修复`（2026-07-27）。

`git diff application-prod.yml` 无输出：与 HEAD 一致；无需新增修改。

## 3. 重新跑 7 场景严格判据

| # | 场景 | 严格判据（AFS/PLACEHOLDER） | 实际原因 |
|---|---|---|---|
| 1 | MISS-DATASOURCE-PASSWORD | ? | `CommunicationsException: Communications link failure` |
| 2 | MISS-REDIS-PASSWORD | ? | `CommunicationsException` |
| 3 | MISS-ROCKETMQ-NAME-SERVER | ✓ | `PlaceholderResolutionException: Could not resolve placeholder 'ROCKETMQ_NAME_SERVER'` |
| 4 | MISS-ENCODE-KEY | ✓ | `PlaceholderResolutionException: Could not resolve placeholder 'SCX_ENCODE_KEY'` |
| 5 | MISS-CHANNEL-MASTER-KEY | ? | `CommunicationsException` |
| 6 | MISS-XXL-JOB-ACCESS-TOKEN | ? | `CommunicationsException` |
| 7 | MISS-DEVICE-HMAC-KEY | ? | `CommunicationsException` |

**严格判据：2/7**（与 BASE-03-RUNTIME-GATE 相同；fail-on-unresolved-placeholders=true 实际**只**对 2 个变量生效）

**fail-closed 总判据：7/7**（所有 7 场景进程仍 exit=1 + Application run failed）

## 4. 为什么 fail-on-unresolved-placeholders 对 5/7 无效

Spring 的 `spring.config.fail-on-unresolved-placeholders=true` 只对**标准 `PropertySourcesPlaceholderConfigurer`** 处理的占位符生效：

| 变量 | 实际解析器 | fail-on-unresolved 是否生效 |
|---|---|---|
| `SCX_DATASOURCE_PASSWORD` | mybatis-plus `dynamic-datasource` + druid | ❌ 框架内自定义解析 |
| `SCX_REDIS_PASSWORD` | Spring Boot `RedisAutoConfiguration` | ❌ 实测未生效（Spring 3.5.9 行为） |
| `ROCKETMQ_NAME_SERVER` | `RocketMQAutoConfiguration` | ✅ |
| `SCX_ENCODE_KEY` | `@Value` 标准注入 | ✅ |
| `SCX_TRACKING_CHANNEL_MASTER_KEY` | `@ConfigurationProperties` 绑定 | ❌ `TrackingProperties` 用 setter 而非 @NotBlank |
| `SCX_XXL_JOB_ACCESS_TOKEN` | xxl-job executor 内部读 | ❌ 框架内部占位符 |
| `SCX_TRACKING_DEVICE_HMAC_KEY` | `@ConfigurationProperties` 绑定 | ❌ 同上 |

## 5. 真正解决路径（不在 IMP-1 范围）

要实现 7/7 严格占位符判据通过，需要更深层修改：

1. **`TrackingProperties` 加 `@Validated + @NotBlank`**（scx-tracking/src/main/java/.../config/TrackingProperties.java）
2. **新增 `ProductionPlaceholderValidator` 启动期 bean**（`ApplicationContextInitializer` 阶段显式校验所有 SCX_* 必需变量非空）
3. **改用 Bean Validation `@PostConstruct` 启动期校验**（每 bean 注入后检查）
4. **修改 xxl-job-executor 与 RedisAuto 内部逻辑**（不可行，框架外部）

按"用户指令只增加最小 fail-closed 覆盖，不得删除已有检查"原则，IMP-1 不应回退或绕开现有检查。

## 6. 静态门禁兼容性发现

```text
$ make prod-secret-injection-check
Secret injection check failed: required environment placeholder is missing in application-dev.yml.
make: *** [prod-secret-injection-check] Error 1
```

**根因（不是 IMP-1 引入）**：

```text
check-prod-secret-injection.sh:23
  'channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY'        # 无冒号

application-dev.yml:64
    channel-master-key: ${SCX_TRACKING_CHANNEL_MASTER_KEY:}      # 有冒号（dev 允许空默认）
```

`grep -Fq` 严格匹配字面字符串，dev yml 的 `:` 后缀导致 `required_placeholders` 检查不通过。

**这个 bug 早于 IMP-1**（check 脚本是 f9cc73b 提交时已存在）。BASE-03-FIX-MERGE 报告说 0 退出，可能当时 dev yml 行 64-65 写法不同。

按硬规则"不得为通过检查而删除扫描规则"，**不修改 check 脚本**。该 bug 列入已知未消除项。

## 7. 单测回归

| 模块 | 测试 | 结果 |
|---|---|---|
| scx-subject MediaScopeServiceTest | 7/7 | OK |
| scx-tracking ChannelClient* + ChannelRequest* + EventIngestion* + ChannelCredential* | 15/15 | OK |
| scx-boot 29/29 模块构建 | OK |
| 后端 jar 重新打包 | OK (BUILD SUCCESS) |

## 8. 完成状态

- ✅ fail-on-unresolved-placeholders 属性已存在（无需新增）
- ✅ 7/7 fail-closed 仍成立（exit=1 + Application run failed）
- ⚠️ 严格判据仍 2/7（与 BASE-03-RUNTIME-GATE 一致）
- ❌ IMP-1 目标"严格判据 5/7 → 7/7"未达成（druid / dynamic-datasource / xxl-job 框架限制）
- ❌ prod-secret-injection-check 失败（dev yml 兼容性问题，非 IMP-1 引入）

按用户 §"完成状态"三档：
- 严格判据 5/7 → 7/7：**未达成**（框架限制）
- 7/7 fail-closed：**仍成立**
- prod-secret-injection-check：**外部阻塞**（check 脚本兼容性问题，非 IMP-1 引入）

## 9. 边界

| 项 | 状态 |
|---|---|
| 7/7 严格占位符判据 | **未达成**（需要 IMP-2：在 `TrackingProperties` 加 `@Validated + @NotBlank`，或新建 `ProductionPlaceholderValidator`） |
| prod-secret-injection-check 失败 | 外部阻塞（check 脚本 dev yml 兼容性问题） |
| application-prod.yml 实际修改 | 无（属性已存在） |
| 真实凭据轮换 | 外部依赖 |
| gitleaks 扫描 | CLI 不可用（明确"未执行，工具不可用"） |

## 10. 下一任务

```text
Task ID: IMP-2
目标: 在 TrackingProperties 加 @Validated + @NotBlank；使 SCX_TRACKING_CHANNEL_MASTER_KEY / SCX_TRACKING_DEVICE_HMAC_KEY 在缺失时启动失败
范围: scx-tracking/src/main/java/.../config/TrackingProperties.java
依赖: IMP-1
完成条件: 严格判据提升至 4/7
```

```text
Task ID: FIX-1
目标: 补 BaseSecurityAuditListener 写 sys_log
依赖: BASE-02-FIX 完成（已满足）
完成条件: BASE-02 5 类越权都进 sys_log
```

未提交、未推送、未部署、未真实凭据轮换。
