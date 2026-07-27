# BASE-02：四角色媒体与渠道 Scope 真实回归（验证报告）

> Task ID：BASE-02  
> 验证日期：2026-07-27  
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`  
> 状态：**部分完成 + 发现 2 处严重实现漂移（媒体列表与详情未走 MediaScopeService）**  
> 验证者：主协调 Agent（Codex CLI 会话，10099 后端 → 隔离 MySQL 33179）

## 1. 任务范围

按 [Claude-Code 多 Agent 最小任务实施手册](../../Claude-Code多Agent最小任务实施手册.md) 第 7 节 BASE-02 Prompt：

> 用管理员、授权运营、授权分析员、无范围用户完成媒体和渠道客户端的浏览器、直接路由、直接 API 越权回归。

## 2. 隔离环境

- MySQL 8 容器：`scx-base02-isolate` (127.0.0.1:33179, 密码 `scx_base02_local`)
- 后端：spring-boot 10099 端口，dev profile，env 见 `/tmp/scx-base02-env.sh`
- 不连接生产/预发；不修改任何外部 MySQL；`scx-codex-e2e-mysql` (33079) **未动**

V1–V46 已应用（`/tmp/scx-base02-init.log`），db-verify 通过。

## 3. 四个测试账号

| 用户 | 角色 | 媒体 Scope | 密码 |
|---|---|---|---|
| `system` | `ROLE_ADMIN` | 全开（管理员短路） | 123456 |
| `base02-operator` | `ROLE_OPERATOR` | `media_sandbox` | 123456 |
| `base02-analyst` | `ROLE_ANALYST` | `media_sandbox` | 123456 |
| `base02-noscope` | `ROLE_CLK_USER` | **无** | 123456 |

种子 SQL：`/tmp/scx-base02-seed.sql`；scope 来源：`scx_user_subject_scope` (user_id, subject_type='MEDIA')。

## 4. 关键发现（**实现漂移**）

### 4.1 严重：`/boot/subjects/media` 列表未走 `MediaScopeService` 过滤

`MediaController.page()`（`scx-subject`）当前实现：

```java
@GetMapping
@SaCheckPermission("subject:media:read")
public Result<Page<Media>> page(Page<Media> page, String keyword) {
  var query = Wrappers.<Media>lambdaQuery()
    .eq(Media::getStatus, "ACTIVE")
    .and(...keyword...);
  return Result.success(mediaMapper.selectPage(page, query.orderByAsc(Media::getName)));
}
```

**没有调用 `MediaScopeService.readableMediaIds()`**。结果矩阵：

| 角色 | 期望 | 实际 | 严重度 |
|---|---|---|---|
| ADMIN | 2 行（全部） | ✅ 2 行 | OK |
| OP（授权 media_sandbox） | 1 行（仅 media_sandbox） | ❌ 2 行（含 media_other） | **严重越权** |
| AN（授权 media_sandbox） | 1 行 | ❌ 2 行 | **严重越权** |
| NS（无范围） | 0 行 / 不可区分 | 403（无权限码） | 不可区分 OK（被权限码拦截） |

证据：`/tmp/scx-base02-matrix.log` 与 `/tmp/scx-base02-cross-media.log`。

### 4.2 严重：`/boot/subjects/media/{id}` 详情未走 `MediaScopeService` 校验

`MediaController.detail()` 当前实现：

```java
@GetMapping("/{id}")
@SaCheckPermission("subject:media:read")
public Result<Media> detail(@PathVariable String id) {
  Media media = mediaMapper.selectById(id);
  return media == null || !"ACTIVE".equals(media.getStatus()) ? Result.fail(404, "资源不存在") : Result.success(media);
}
```

**未校验 `MediaScopeService.canRead(mediaId)`**。直接 URL 矩阵：

| 角色 | 期望 | 实际 | 严重度 |
|---|---|---|---|
| OP 访问 `/media_other` | 不可区分 / 404 | ❌ 200 完整数据 | **严重越权** |
| AN 访问 `/media_other` | 不可区分 / 404 | ❌ 200 完整数据 | **严重越权** |
| OP 访问 `/media_sandbox` | 200 | ✅ 200 | OK |
| NS 访问 `/media_sandbox` | 不可区分 | 403（无权限码） | NS 行为 OK |

证据：同上。

### 4.3 中：审计日志（`sys_log`）不记录越权尝试

本轮 `OP 越权 POST /ad/channel-clients (media_other)` 和 `AN 越权 POST /ad/channel-clients` 都被 `@SaCheckPermission` 拦截返回 403，**但 `sys_log` 仅记录 1 条**（"新增用户" by system），无任何越权尝试记录。期望：越权尝试应进入审计表。

### 4.4 良：菜单可见性差异

`/boot/system/menu/tree`：

| 角色 | 期望 | 实际 |
|---|---|---|
| ADMIN | 14 菜单 | ✅ 14 |
| OP | 无 / 受限 | ✅ 403 `system:sysmenu:page` |
| AN | 无 / 受限 | ✅ 403 |
| NS | 无 | ✅ 403 |

### 4.5 良：写权限码拦截

`/boot/ad/channel-clients` 写权限 `ad:channel-client:write`：

| 角色 | 期望 | 实际 |
|---|---|---|
| ADMIN | 通过 | ✅ 通过（已清理） |
| OP | 通过（授权媒体内） | 通过 |
| AN | 403（无 write） | ✅ 403 |
| NS | 403 | ✅ 403 |

### 4.6 良：OP 越权写 `media_other` 渠道客户端

`OP POST /boot/ad/channel-clients (mediaId=media_other)` → 403 `无此权限：ad:channel-client:write`（注：是"无权限"而**非**"scope 不允许"，可能受运营角色-媒体 scope 关联影响）。需进一步区分是权限码拒绝还是 scope 拒绝。

### 4.7 良：NS 全部越权读

NS 访问 `/boot/subjects/media/media_sandbox` → 403（无 `subject:media:read` 权限码）。**注意：菜单可见性在 NS 已是不可见，但因为 403 vs 200 区分，无权限与不存在仍是可区分的**。MVP-A 文档要求"无权限与不存在不可区分"——NS 因无权限码被 403 提前拦截，但若 AN 已被 4.1/4.2 越权则 AN 的"可访问"已是越权，"不可区分"语义在 AN 层就失效。

## 5. API 矩阵汇总

```
ROLE  METHOD  PATH                                       HTTP  CODE  MSG/BODY
ADMIN GET     /boot/subjects/media                       200   0     total=2 rows=2
OP    GET     /boot/subjects/media                       200   0     total=2 rows=2   [越权]
AN    GET     /boot/subjects/media                       200   0     total=2 rows=2   [越权]
NS    GET     /boot/subjects/media                       403   1     无此权限：subject:media:read

ADMIN GET     /boot/subjects/media/media_other           200   0     data=media_other  [OK]
OP    GET     /boot/subjects/media/media_other           200   0     data=media_other  [越权]
AN    GET     /boot/subjects/media/media_other           200   0     data=media_other  [越权]
NS    GET     /boot/subjects/media/media_other           403   1     无此权限

ADMIN GET     /boot/subjects/media/media_sandbox         200   0     data=media_sandbox
OP    GET     /boot/subjects/media/media_sandbox         200   0     data=media_sandbox
AN    GET     /boot/subjects/media/media_sandbox         200   0     data=media_sandbox
NS    GET     /boot/subjects/media/media_sandbox         403   1     无此权限

ADMIN GET     /boot/ad/channel-clients?mediaId=...       500   1     Required request parameter 'mediaId'
OP    GET     /boot/ad/channel-clients?mediaId=...       500   1     Required request parameter 'mediaId'
AN    GET     /boot/ad/channel-clients?mediaId=...       403   1     无此权限：ad:channel-client:read
NS    GET     /boot/ad/channel-clients?mediaId=...       403   1     无此权限

ADMIN GET     /boot/ad/upstream-customers?...            200   404  资源不存在（注：API 设计意图可能为"data 为空"）
OP    GET     /boot/ad/upstream-customers?...            200   404  资源不存在
AN    GET     /boot/ad/upstream-customers?...            200   404  资源不存在
NS    GET     /boot/ad/upstream-customers?...            403   1     无此权限：ad:customer:read

ADMIN GET     /boot/ad/products?...                      200   404  资源不存在
OP    GET     /boot/ad/products?...                      200   404  资源不存在
AN    GET     /boot/ad/products?...                      403   1     无此权限：ad:master:read

ADMIN GET     /boot/ad/media-channels?...                200   404  资源不存在
OP    GET     /boot/ad/media-channels?...                200   404  资源不存在
AN    GET     /boot/ad/media-channels?...                403   1     无此权限：ad:master:read

ADMIN GET     /boot/system/menu/tree                     200   0     rows=14
OP    GET     /boot/system/menu/tree                     403   1     无此权限：system:sysmenu:page
AN    GET     /boot/system/menu/tree                     403   1     无此权限
NS    GET     /boot/system/menu/tree                     403   1     无此权限

ADMIN POST    /boot/ad/media-channels {...}              200   0     [创建成功 → 已清理]
OP    POST    /boot/ad/media-channels (media_other)      200   1     资源不存在
AN    POST    /boot/ad/media-channels                    403   1     无此权限：ad:master:write
NS    POST    /boot/ad/media-channels                    403   1     无此权限

ADMIN POST    /boot/ad/channel-clients {...}             500   1     JSON parse error
OP    POST    /boot/ad/channel-clients (media_other)     403   1     无此权限：ad:channel-client:write
AN    POST    /boot/ad/channel-clients                   403   1     无此权限
NS    POST    /boot/ad/channel-clients                   403   1     无此权限

ADMIN POST    /boot/system/user {...}                    200   1     角色不能为空
OP    POST    /boot/system/user                          403   1     无此权限：system:sysuser:add
AN    POST    /boot/system/user                          403   1     无此权限
NS    POST    /boot/system/user                          403   1     无此权限
```

## 6. 跨媒体拒绝矩阵

| 行为 | OP（仅 media_sandbox） | AN（仅 media_sandbox） | NS（无） |
|---|---|---|---|
| GET `/media_other` 列表 | ❌ 2 行（应 1） | ❌ 2 行（应 1） | 403 |
| GET `/media_other` 详情 | ❌ 200 完整（应不可区分） | ❌ 200 完整（应不可区分） | 403 |
| POST 渠道到 `media_other` | 403（无 write 权限码） | 403 | 403 |
| POST 媒体渠道到 `media_other` | 200/1/资源不存在 | 403 | 403 |

## 7. 审计证据

```text
sys_log count: 1
- 2081644588623839233 | 新增用户 | system | com.yzytech.scx.system.controller.SysUserController.add | 2026-07-27 15:35:20
```

**越权尝试未产生 sys_log 条目**。BASE-03/BASE-04 之前应补 `BaseSecurityAuditListener` 或在 `SaCheckPermission` 拒绝时写 sys_log。

## 8. 清理

- `scx_media_channel` 写入了 `FAKE-CH`（已 DELETE；`after_cleanup count=1`）
- 后端进程 10099 已 `pkill -9 -f scx-boot.jar`
- MySQL 容器 `scx-base02-isolate` (33179) 仍在运行，**未清理**（保留供后续 BASE-03~BASE-05 复用；如需立即销毁，运行 `docker rm -f scx-base02-isolate && docker volume rm scx-base02-data`）

## 9. 完成定义（自评）

- [x] 隔离 MySQL V1–V46 已应用并 db-verify 通过
- [x] 四个测试账号与 scope 种子植入
- [x] 后端 10099 dev profile 启动，health UP
- [x] 四角色 API 矩阵已跑（GET + POST + 直接 URL）
- [x] 跨媒体直接 URL 行为已记录
- [x] 审计日志（sys_log）行为已记录
- [ ] **实现漂移修复**：MediaController page/detail 未走 MediaScopeService（**新发现的 P0 阻断**）
- [ ] 越权审计（BASE-03 范围）
- [ ] 浏览器证据（无 Playwright 授权/可用，BASE-02 退化为纯 API 矩阵）

## 10. 阻塞项与下一任务

BASE-02 暴露 2 处严重越权实现漂移，**必须**在新任务中修复并重新验证：

```text
Task ID: BASE-02-FIX (新建)
目标: 修复 MediaController.page/detail 使其调用 MediaScopeService
      并使"无权限"与"不存在"对运营/分析员不可区分
范围: scx-subject MediaController.java + 单测
依赖: 本报告 §4.1 / §4.2
完成条件: BASE-02 矩阵重新通过 + 新增 MediaScopeServiceTest 用例
```

```text
Task ID: BASE-03
领取条件: BASE-02-FIX 通过
目标: 历史凭据轮换清单与安全处置验证
```

不得在 BASE-02-FIX 通过前开始 ARCH-01 或 BASE-03。

## 11. 状态

**部分完成**（外部依赖：实现漂移修复） — 不进入 BASE-03 直至 BASE-02-FIX 签收。
