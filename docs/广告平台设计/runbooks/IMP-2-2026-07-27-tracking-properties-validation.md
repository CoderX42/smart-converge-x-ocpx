# IMP-2：TrackingProperties 必填密钥启动校验

> Task ID：IMP-2
> 验证日期：2026-07-27
> 状态：**已实现并验证（Spring 配置绑定范围）**

## 任务合同

- 目标：当 `scx.tracking.channel-master-key` 或
  `scx.tracking.device-hmac-key` 缺失或为空白时，阻止 Spring 配置绑定完成。
- 范围内：`scx-tracking` 的配置类和最小绑定测试。
- 范围外：生产配置、迁移、公开 API、真实密钥、目标环境启动和部署。
- 权限与隐私：不新增日志字段；测试仅使用合成值。
- 失败与降级：两项密钥没有安全默认值，任一无效即启动失败；不降级到明文、空密钥或随机密钥。

## 变更

- `TrackingProperties` 增加 `@Validated`。
- `channelMasterKey` 与 `deviceHmacKey` 增加 `@NotBlank`，并提供不包含密钥值的配置错误说明。
- 新增 `TrackingPropertiesValidationTest`：
  - 两项配置存在时，配置上下文可启动；
  - 缺少 channel master key 时，配置上下文启动失败；
  - device HMAC key 为空白时，配置上下文启动失败。

## 验证证据

| 命令 / 操作 | 结果 |
| --- | --- |
| `mvn -pl scx-tracking test` | 54 tests passed，5 个既有 Redis 集成测试因环境条件跳过 |
| `mvn -pl scx-boot -am -DskipTests package` | 30 个 Maven reactor 模块构建成功 |
| `make prod-secret-injection-check` | 通过 |
| `bash -n scripts/deploy/*.sh scripts/check-*.sh` | 通过 |
| `python3 -m py_compile scripts/deploy/scx-nginx-panel.py` | 通过 |
| `git diff --check` | 通过 |

## 边界

- 本任务证明的是 Spring `@ConfigurationProperties` 的实际绑定行为，不是目标环境或第三方 UAT。
- 尝试在本机用自动配置排除项执行完整 `scx-boot` 失败场景时，应用先因测试隔离造成的 DataSource/Captcha 依赖缺失退出；该结果不计入历史 7 场景的严格运行时统计，也未写入仓库日志。
- 后续如需重新宣称“严格判据 4/7”，应在不遮蔽 `TrackingProperties` 绑定、且具备完整本地依赖的独立运行时中重跑 BASE-03-RUNTIME-GATE。

## 工作树与下一任务

- 仅增加本任务的配置类、单测和本 runbook；未修改用户已有配置、迁移或日志。
- 未提交、未推送、未部署。
- 下一优先级：FIX-1（`BaseSecurityAuditListener` 补写 `sys_log`）。
