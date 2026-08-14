# SCX OCPX 超级管理员 CRUD V104

## 决策

`ROLE_ADMIN` 对广告主、推广链接、媒体、监测任务和扣量规则拥有完整的增删改查能力。现有接口与权限码保持兼容，只新增此前缺失的两项删除能力：

- `DELETE /ocpx/advertisers/{id}`，权限 `ocpx:advertiser:delete`。
- `DELETE /ocpx/media/{id}`，权限 `ocpx:media:delete`。

推广链接、监测任务和扣量规则继续沿用既有删除接口。`ROLE_ADVERTISER` 保持只读，不获得任何新增、编辑或删除权限。

## 删除边界

推广链接、监测任务和扣量规则的独立删除继续使用逻辑删除。广告主删除是超级管理员明确确认后的不可逆级联操作：

- 按外键安全顺序物理删除其报表数据、Feedback/Callback 明细、流程日志、扣量规则、监测任务和推广链接，再物理删除广告主。
- 删除该广告主所有扣量路由的 Redis 比例与计数 key，以及广告主余额 key。
- 同一数据库事务内逻辑删除托管 `sys_user`、移除角色关系并注销其 Sa-Token 会话。
- 媒体属于共享主数据，不随广告主级联删除。
- 媒体存在监测任务、扣量规则、流程日志、Feedback/Callback 明细或报表数据时拒绝删除。

完整增量接口见 [V104 管理契约](contracts/management-v104.md)。

## 数据库与发布

`V104__grant_admin_full_ocpx_crud.sql` 只新增广告主/媒体删除权限并授予 `ROLE_ADMIN`，不修改业务数据，也不授予 `ROLE_ADVERTISER`。该文件不自动执行；目标库必须先完成 V102、V103，再经备份、SQL 审核和明确授权后执行 V104，并重新登录管理员账号刷新会话权限。

源码测试通过不代表目标数据库、宝塔部署、服务重启或浏览器 UAT 已完成。
