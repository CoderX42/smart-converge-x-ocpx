# P0：本地可信边界收口

> 验证日期：2026-07-27
>
> 状态：**已实现并验证（代码、单元、契约与本地构建范围）**
>
> 边界：目标环境四角色、真实第三方、独立故障恢复与容量门禁仍未验收。

## 1. 安全拒绝审计

- 保留全局异常处理器直接发布脱敏安全事件的唯一链路，删除 `scx-auth` 中重复的 AOP 监听器。
- 关闭 Bean 定义覆盖；最小上下文测试确保发布器与日志监听器各只有一个。
- `method` 保存 HTTP 方法，`request_method` 保存 `SECURITY_DENIAL:<reason>`，`status=0`。
- `user_name` 保存实际用户名或 `anonymous`，`create_by=system`。
- 请求参数只保存排序后的参数名；禁止记录参数值、Token、密钥和媒体凭据。
- 单元测试覆盖拒绝事件、日志映射和 MyBatis 显式 `create_by` 保留。
- 隔离 MySQL 的真实未登录 HTTP 请求返回 401，`sys_log` 精确新增 1 行；参数只保存排序后的名称，合成参数值未落库。

## 2. 生产必填配置

- 新增 prod 专用早期配置校验器，开发环境不启用。
- 校验数据源 URL/用户名/密码、Redis 主机/密码、RocketMQ NameServer、XXL-JOB 地址/Token、登录加密密钥、Tracking 主密钥和设备 HMAC 密钥。
- 错误只列出缺失的环境变量名，不输出配置值。
- 删除 XXL-JOB Java 默认 Token，删除 prod 的 localhost 管理地址回退。
- 静态密钥注入检查同步覆盖上述配置；CI 已存在 `gitleaks/gitleaks-action@v2`，未重复新增。

## 3. 点击敏感详情

- 响应不再提供原始 `clickId`、`campaignId`、`creativeId`。
- 改为 `clickIdMasked`、`campaignIdMasked`、`creativeIdMasked`，仅保留前六位和 `***`。
- OAID、OAID-MD5、IP 仅提供存在标记。
- DTO、SQL、OpenAPI、前端类型及页面说明已同步；序列化测试阻止原字段回归。

## 4. 迁移和生成物边界

- 当前迁移头保持 V47；本次未新增迁移，后续只允许从 V48 增加前向迁移。
- `scx-commercial`、`scx-finance`、`scx-inventory`、`scx-creative`、`scx-campaign`
  目录只有历史 `target/**` 生成物，没有源码、POM 或父工程装配，不能作为模块已实现证据。
- 前端 `.turbo`、`dist`、`dist.zip` 及日志同样只视为生成物。
- 本次未删除、归档或改写上述既有生成物。

## 5. 验证证据

| 范围 | 结果 |
|---|---|
| `mvn -pl scx-boot -am test` | 30 个 Maven reactor 模块通过；Redis 集成测试因未提供测试开关跳过 5 个 |
| `mvn -pl scx-boot -am -DskipTests package` | 30 个 Maven reactor 模块打包通过 |
| 本次 Java 文件定向 `spring-javaformat:validate` | 8 个受影响模块全部通过 |
| 隔离 MySQL + 本地 HTTP 拒绝审计 | HTTP 401，`sys_log` 精确新增 1 行；字段和脱敏规则符合任务合同 |
| prod 配置校验器测试 | 3 个场景组通过，含 11 项逐项缺失/空白检查 |
| `scx-reporting` 测试 | 通过；敏感字段序列化测试通过 |
| OpenAPI / JSON Schema 契约校验 | 通过 |
| 前端 TypeScript / 单测 / 生产构建 | 通过；32 个测试文件、267 个用例、11 个构建任务 |
| 全仓前端 Lint | 本次两个文件定向 Prettier/ESLint 通过；全仓被既有 `QueueOverview.vue` 格式问题阻塞 |
| 生产密钥注入静态检查 | 通过 |
| V1–V47 迁移打包检查 | 通过；版本连续且唯一 |
| Shell 语法 / Python 编译 / `git diff --check` | 通过 |

全仓 Java 格式校验仍被 `scx-common-core` 中 25 个既有格式问题阻塞；本次未修改这些无关文件。

## 6. 下一门禁

严格按 `FIX-4 → BASE-04 → BASE-05` 执行：

1. 在受控目标环境应用 V43–V47，核对账本、菜单、角色权限和历史数据，并执行四角色浏览器/API/审计矩阵。
2. 完成历史凭据轮换签收及真实第三方 v1.1 UAT。
3. 在独立 MySQL/Redis/RocketMQ 环境执行故障恢复、事实对账和 20k/40k 容量门禁。

在这些外部门禁签收前，不创建 ARCH-01～06，不把 R1 描述为已具备生产接入条件。
