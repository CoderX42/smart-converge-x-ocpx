# FIX-1：Sa-Token 拒绝审计

> Task ID：FIX-1
> 验证日期：2026-07-27
> 状态：**已实现并验证（单元、构建与隔离 MySQL 范围）**

## 任务合同

- 目标：`@SaCheckPermission`、`@SaCheckRole` 与未登录拒绝均生成可落库的 `sys_log` 审计记录。
- 范围内：安全异常处理、脱敏审计事件、既有日志服务适配与最小单测。
- 范围外：数据库迁移、目标环境、浏览器回归、生产部署和权限规则本身。
- 降级：审计发布或持久化异常只写服务端告警，保留原本 401/403 响应，不转换为 5xx。

## 变更

- `BootGlobalExceptionHandlerResolver` 在 `NOT_PERMISSION`、`NOT_ROLE`、`NOT_LOGIN` 三类 Sa-Token 异常时触发审计。
- 安全模块保留一条全局异常处理器直接发布 `BaseSecurityAuditEvent` 的路径，并删除 `scx-auth` 中重复的 AOP 监听器；快照 HTTP 方法、URI、来源 IP、当前用户名和**排序后的参数名列表**，不记录 query/body 参数值、Token、密钥、媒体凭据或设备标识。
- 日志模块监听该事件并通过既有 `RemoteSysLogService` 写入 `sys_log`：
  - `title=无权限访问`；
  - `status=0`；
  - HTTP 方法写入既有 `method` 字段；
  - 稳定原因写入 `request_method=SECURITY_DENIAL:<reason>` 与 `ex_msg`；
  - `user_name` 写实际用户名，取不到安全上下文时写 `anonymous`；
  - `create_by=system`，并由 MyBatis 自动填充器保留显式值。

`spring.main.allow-bean-definition-overriding` 已关闭，最小 Spring 上下文测试确保安全审计发布器和日志监听器各只有一个。现有
`sys_log` 没有独立 `user_id` 字段；本变更沿用现有字段，不伪造新列或修改已执行迁移。

## 验证证据

| 命令 / 操作 | 结果 |
| --- | --- |
| `mvn -pl scx-common/scx-common-mybatis,scx-common/scx-common-security,scx-common/scx-common-log,scx-auth -am test` | 13 个 Maven reactor 模块构建成功；安全事件、单监听器上下文、日志映射及 `create_by` 保留测试通过 |
| 隔离 MySQL + 本地 HTTP 未登录请求 | HTTP 401；`sys_log` 精确新增 1 行，字段为 `GET`、`SECURITY_DENIAL:NOT_LOGIN`、`status=0`、`anonymous`、`system` |
| 隔离 MySQL 参数脱敏核对 | 仅落库 `params=[alpha,token,zeta]`，三个合成参数值均未出现 |

## 边界与下一任务

- 已在保留的隔离 MySQL 执行真实 HTTP 与 `sys_log` 落库核对；未在目标环境或浏览器执行四角色回归，也没有连接生产。
- 后续 FIX-4 应在授权的独立环境重跑 BASE-02 矩阵，并核对每类拒绝均新增一条 `sys_log`。
- 未提交、未推送、未部署。
- 本地下一优先级的 FIX-2、FIX-3、生产配置校验与敏感详情收口均已完成；下一执行点是 P1 目标环境验收。
