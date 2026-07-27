# FIX-3：上游客户空列表的 HTTP 200 / 业务码语义

> Task ID：FIX-3
> 验证日期：2026-07-27
> 状态：**已实现并验证（控制器回归测试范围）**

## 任务合同

- 目标：确保具备可读媒体范围的 `GET /boot/ad/upstream-customers` 在没有匹配记录时返回 HTTP 200、业务 `code=0` 与空 `records`；业务 `code=404` 只表示缺少或无权访问媒体范围。
- 范围内：控制器返回语义核查与回归测试。
- 范围外：修改媒体 Scope 策略、改变 HTTP 状态码映射、连接目标环境或生产环境。

## 根因与结论

`MasterDataController.page` 仅在 `mediaId` 为空或 `MasterDataService.readable(mediaId)` 为 `false` 时返回 `Result.fail(404, "资源不存在")`。对已授权媒体，它始终调用 MyBatis 分页查询并返回 `Result.success(page)`；因此空结果页本来就是业务 `code=0`。

基线请求中的 `mediaId` 未填入可读媒体标识，观察到的 `data=null`、业务 `code=404` 是访问范围前置校验的结果，不是空分页被错误映射为 404。

新增 `MasterDataControllerTest`，覆盖：

- 已授权 `mediaId` 与空查询结果：`code=0`、`records=[]`，且执行 Mapper 分页查询；
- 不可读 `mediaId`：`code=404`、`data=null`，且不访问 Mapper。

## 验证证据

| 命令 / 操作 | 结果 |
| --- | --- |
| `cd scx-backend && mvn -pl scx-delivery -am test` | 通过；新增 `MasterDataControllerTest` 的 2 个场景通过，delivery 模块共 12 个测试通过 |
| `cd scx-backend && mvn -pl scx-boot -am -DskipTests package` | 通过；30 个 reactor 模块构建成功 |
| `make migration-packaging-check prod-secret-injection-check` | 通过；V1–V47 连续，生产配置仅通过环境注入密钥 |
| 脚本语法、`py_compile`、`git diff --check` | 全部通过 |

## 边界与下一任务

- 未调用真实上游客户接口，也未连接目标环境；上述证据证明控制器语义和单元回归，不构成浏览器、目标环境或生产验收。
- BASE-02 复测时应带入该运营用户拥有读取 Scope 的真实 `mediaId`，并确认响应 HTTP 200、业务 `code=0`、`data.records=[]`。
- 未提交、未推送、未部署。
- 下一项需要授权的高优先级验证是 FIX-4：以多角色浏览器会话复测被拒绝请求的 `sys_log` 审计落库。
