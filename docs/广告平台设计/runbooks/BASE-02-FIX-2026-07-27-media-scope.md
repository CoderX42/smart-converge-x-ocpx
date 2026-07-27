# BASE-02-FIX：MediaController MediaScope 漂移修复

> Task ID：BASE-02-FIX  
> 修复日期：2026-07-27  
> 工作区：`/Users/karl/code/yw-tech/smart-converge-x`  
> 状态：**已实现并验证**  
> 前置：[BASE-02-2026-07-27-scope-regression.md §4.1 / §4.2 / §4.3](./BASE-02-2026-07-27-scope-regression.md)

## 1. 修复范围

按 BASE-02 报告 §4.1 / §4.2 暴露的 P0 越权：

| 文件 | 变更类型 | 是否在用户 M 状态 |
|---|---|---|
| `scx-backend/scx-subject/src/main/java/com/yzytech/scx/subject/controller/MediaController.java` | 修改 | **否**（独立修改） |
| `scx-backend/scx-subject/src/test/java/com/yzytech/scx/subject/service/MediaScopeServiceTest.java` | 新增 2 用例 | **否**（目录从未跟踪） |
| `scx-backend/scx-subject/src/main/java/com/yzytech/scx/subject/service/MediaScopeService.java` | **未改**（M 状态） | 用户保留 |
| `scx-backend/scx-boot/src/main/resources/db/migration/V*.sql` | **未改** | 任何 V 文件不动 |
| 用户 32 个 M 文件 | **未动** | 全部保留 |

## 2. 修改内容

### 2.1 MediaController 关键变化

```java
// 之前：page() 不过滤 scope
var query = Wrappers.<Media>lambdaQuery()
  .eq(Media::getStatus, "ACTIVE")
  .and(...keyword...);

// 之后：非管理员用 readableMediaIds() 过滤；空集返空 Page（不泄漏）
if (mediaScopeService.isAdmin()) {
  query.and(StringUtils.hasText(keyword), q -> q.like(...).or().like(...));
} else {
  List<String> allowedIds = mediaScopeService.readableMediaIds();
  if (allowedIds.isEmpty()) {
    return Result.success(page.setRecords(Collections.emptyList()).setTotal(0));
  }
  query.in(Media::getId, allowedIds);
  if (StringUtils.hasText(keyword)) { query.and(q -> q.like(...).or().like(...)); }
}
```

```java
// 之前：detail() 只看是否存在
Media media = mediaMapper.selectById(id);
return media == null || !"ACTIVE".equals(media.getStatus()) ? Result.fail(404, "资源不存在") : Result.success(media);

// 之后：不可区分 — 不存在与无权限都返 404
Media media = mediaMapper.selectById(id);
if (media == null || !"ACTIVE".equals(media.getStatus())) {
  return Result.fail(404, "资源不存在");
}
if (!mediaScopeService.canRead(media.getId())) {
  return Result.fail(404, "资源不存在");
}
return Result.success(media);
```

### 2.2 MediaScopeServiceTest 新增 2 用例

- `analystWithScopeCannotEnumerateUnauthorizedMedia` — 验证 AN 列表仅 1 个授权 media，越权 media_other 不可读不可写
- `readableMediaIdsExcludesUnauthorisedAndUnauthenticated` — 验证空 scope 与未登录都返 `[]`

## 3. 修复后验证（隔离 MySQL 33179 / 后端 10099）

| 角色 | 路径 | 修复前 | 修复后 | 期望 |
|---|---|---|---|---|
| ADMIN | GET `/boot/subjects/media` | 200 total=2 | 200 total=2 ids=[media_other, media_sandbox] | ✅ |
| OP | GET `/boot/subjects/media` | **200 total=2 [越权]** | 200 total=1 ids=[media_sandbox] | ✅ |
| AN | GET `/boot/subjects/media` | **200 total=2 [越权]** | 200 total=1 ids=[media_sandbox] | ✅ |
| NS | GET `/boot/subjects/media` | 403 | 403 | ✅ |
| ADMIN | GET `/media_other` | 200 | 200 | ✅ |
| OP | GET `/media_other` | **200 [越权]** | 200 code=404 msg=资源不存在 | ✅ 不可区分 |
| AN | GET `/media_other` | **200 [越权]** | 200 code=404 msg=资源不存在 | ✅ 不可区分 |
| OP | GET `/NONEXISTENT` | 200 code=404 | 200 code=404 | ✅ |
| AN | GET `/NONEXISTENT` | 200 code=404 | 200 code=404 | ✅ |

**关键不可区分证据**：OP/AN 访问 `media_other` 与 `NONEXISTENT` 行为完全一致（http=200, code=404, msg=资源不存在）。

## 4. 命令与退出码

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn -pl scx-subject -Dtest=MediaScopeServiceTest test` | 0 | Tests run: 7, Failures: 0（5 既有 + 2 新增） |
| `mvn -pl scx-boot -am -DskipTests package` | 0 | scx-boot 29/29 模块编译/打包成功 |
| 重启 10099 后端 dev profile | 0 | health UP |
| 28 条 HTTP 请求覆盖 4 角色 × 5 路径 | 0 | 全部符合期望 |
| 后端进程 pkill | 0 | 已停 |

## 5. 已知未消除项

按 BASE-02 报告遗留项：

1. **审计缺漏（§4.3）**：越权尝试仍未进入 `sys_log`。本任务**未引入** `BaseSecurityAuditListener`，需要新任务（建议 `BASE-02-FIX2`）补 Sa-Token 拒绝钩子。
2. **OP 越权写 media_other 渠道 → 403（无权限码）**：这是 `@SaCheckPermission` 在 controller 之前的拦截，**与 scope 拒绝路径不可区分**。AN/NS 行为类似。需要 scope 拒绝路径单独返回 403/404（与权限码拒绝 403 区分）或合并为同一 403。当前未修。
3. **`/boot/ad/upstream-customers` 等返 404 的真伪**：BASE-02 报告 §4.6 标记 API 设计意图可能为"data 为空"而非"资源不存在"，本任务未排查该路径的真实 controller 实现。
4. **浏览器多角色回归**：无 Playwright 授权，BASE-02 仍退化为纯 API 矩阵。
5. **OP 越权写渠道失败原因**：OP 写 `media_other` 渠道返 403（"无 ad:channel-client:write"），但实际 OP 应该有此权限码（否则无法写自己的媒体）。需要核查 OP 角色绑定是否缺该权限码 — 这是新发现的实现漂移，不在本次任务范围。

## 6. 下一任务

```text
Task ID: BASE-02-FIX2 (可选)
目标:
  1. 补 BaseSecurityAuditListener 写 sys_log 当 @SaCheckPermission 拒绝
  2. 补 SaToken error 拦截器统一 scope 拒绝与权限码拒绝的响应
  3. 核查 /boot/ad/upstream-customers 等"data=null 但 code=404"的真实 controller 语义
  4. 核查 OP 角色是否绑定 ad:channel-client:write 权限码
依赖: BASE-02-FIX
完成条件: 上述 4 项各 1 个修复 + 单元/集成测试通过
```

```text
Task ID: BASE-03
领取条件: BASE-02-FIX 通过（已满足）; 用户选择继续
目标: 历史凭据轮换清单与安全处置验证
```

未提交、未推送、未部署。
