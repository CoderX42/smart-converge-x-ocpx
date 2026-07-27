# FIX-2：渠道凭据角色权限闭环

> Task ID：FIX-2
> 验证日期：2026-07-27
> 状态：**已实现并验证（隔离 MySQL）**

## 任务合同

- 目标：使 `ROLE_OPERATOR` 可通过写入与轮换权限门禁管理其已授权媒体的渠道凭据，并使 `ROLE_ANALYST` 可在授权媒体范围内只读。
- 范围内：角色—权限前向迁移与既有 Scope 实现核查。
- 范围外：菜单可见性、分析员权限、跨媒体 Scope 规则、目标环境升级和部署。

## 根因与修复

V41 创建渠道凭据菜单和三项权限后，仅给运营关联了只读权限，导致运营用户在写入及轮换控制器门禁前被拒绝；分析员则完全无法读取渠道客户端，不符合四角色验收矩阵。

新增前向、幂等迁移 `V47__grant_operator_channel_client_write.sql`，按角色编码补齐：

- `ROLE_OPERATOR`：`perm_ad_channel_client_write`、`perm_ad_channel_client_rotate`；
- `ROLE_ANALYST`：`menu_ad_channel_clients`、`perm_ad_channel_client_read`。

迁移不依赖固定 role ID，不改写 V41–V46。V47 尚未在生产执行，因此在首次目标环境执行前补齐其既定四角色权限合同。

服务层 `ChannelClientManagementService.requireWrite` 及更新/轮换/吊销路径仍调用 `MediaScopeService.canWrite(mediaId)`；因此新增权限码不能授予跨媒体写入能力。

## 验证证据

| 命令 / 操作 | 结果 |
| --- | --- |
| 一次性 MySQL 8 容器：`init-database.sh` 从空库应用 V1–V47 | 通过 |
| 同一隔离库再次执行升级器 | 通过，迁移幂等 |
| 角色—菜单连接查询 | `ROLE_OPERATOR → write/rotate` 各 1 条；`ROLE_ANALYST → menu/read` 各 1 条 |
| `make migration-packaging-check` | 通过，V1–V47 连续且无重复 |
| `make prod-secret-injection-check`、脚本语法与 `py_compile`、`git diff --check` | 全部通过 |

## 边界与下一任务

- 未连接生产或目标环境，未执行真实凭据创建/轮换，也未改变用户会话；目标环境应用 V47 后，四角色用户均需重新登录以刷新权限缓存。
- 未提交、未推送、未部署。
- 下一优先级：FIX-3（核查 `/boot/ad/upstream-customers` 的 HTTP 200 + 业务 code 404 语义）。
