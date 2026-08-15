# SCX OCPX 管理 API V107 增量契约

V107 继承 V102 的五对象字段、V103 的开户/行级范围和 V104 的完整 CRUD，只增加系统托管共享广告主角色的权限管理。

## 接口

两个接口均要求 `ocpx:advertiser:role:manage`，该权限只授予 `ROLE_ADMIN`。即使广告主账号的角色数据被误配置，服务端也禁止 `sys_user.type=2` 调用。

- `GET /ocpx/advertisers/account-role/permissions`：返回固定角色信息、当前选中菜单、全量范围状态、受影响账号数和可授予 OCPX 权限树。
- `PUT /ocpx/advertisers/account-role/permissions`：请求 `{ "menuIds": ["..."], "allDataScope": true }`，全量替换现有授权并返回保存后的同结构结果。

响应 `data`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `roleId/roleName/roleCode` | string | 固定 `ROLE_ADVERTISER` 角色信息 |
| `selectedMenuIds` | string[] | 已选可授予菜单，不包含范围节点 |
| `allDataScope` | boolean | 是否拥有 `ocpx:advertiser:scope:all` |
| `affectedAccountCount` | integer | 当前 `sys_user.type=2` 账号数 |
| `menuTree` | node[] | 可授予 OCPX 菜单树 |

树节点包含 `id/parentId/name/type/permission/requiresAllDataScope/children`。`ocpx:advertiser:role:manage`、`ocpx:advertiser:scope:all`、系统权限、非 OCPX 权限、停用和删除节点均不进入树。

## 保存约束

- 空数组表示清空业务权限；服务端自动补齐所有仍在白名单内的父级菜单。
- 广告主新增/删除、媒体全部操作和扣量规则全部操作标记 `requiresAllDataScope=true`；提交这些节点而 `allDataScope=false` 返回业务拒绝。
- 全量范围只扩展数据可见性，不授予任何动作权限；动作接口仍需各自权限码。
- 成功保存后，角色菜单替换、角色更新时间和事务内校验共同提交；菜单缓存被清空，全部广告主会话被注销。
