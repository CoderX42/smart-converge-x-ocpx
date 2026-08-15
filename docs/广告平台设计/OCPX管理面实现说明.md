# SCX OCPX 管理面实现说明

## 业务关系

```text
广告主 1 ── N 推广链接
媒体 1 ── N 监测任务 N ── 1 推广链接 ── 1 广告主
媒体 + 推广链接 ── 0..1 生效扣量规则
```

管理 API 使用 `advertiserId/mediaId/promotionLinkId/monitorTaskId`。外部数据面使用同一组主键的短名 `aId/mId/lId/tId`。任务保存时校验所属链接确实属于所选广告主。

## 当前字段

| 对象 | 业务字段 | 删除语义 |
|---|---|---|
| 广告主 | `name/bidUrl/credentialConfigKey/macro`；新增时另带 `username/password` 开户 | 仅零余额、无流水且无事件事实时可物理删除；同时清理关联业务配置、旧兼容日志/报表和 Redis 路由状态，并注销托管账号；共享媒体保留 |
| 推广链接 | `advertiserId/name/platform/channelId/linkUrl/deeplink/linkExt/remark` | `is_delete` 逻辑删除 |
| 媒体 | `name/uri/credentialConfigKey/macro` | 无任何业务或历史引用时物理删除 |
| 监测任务 | `monitorName/mediaId/advertiserId/promotionLinkId/unitPrice/conversionEvent/bizId/showUrl/clkUrl/remark` | `is_delete` 逻辑删除 |
| 扣量规则 | `mediaId/promotionLinkId/ratio` | `is_delete` 逻辑删除；函数唯一索引保证同一路由只有一条生效规则 |

所有对象另有主键和创建/修改时间。平台为数值：`1 Android`、`2 iOS`、`3 Android+iOS`、`4 快应用`。监测 URL 由后端生成，前端不提交。

广告主 `bidUrl` 和推广链接 URL 按 V103 产品决策仅做必填、长度和首尾空白处理，允许任意 scheme、本机和内网地址。广告主 `bidUrl` 会被 Engine 用作服务端出站目的地址，因此该决策保留明确的 SSRF 风险；它不应被描述为安全校验。媒体 `uri` 是接入标识，不作为服务端请求目的地址；Engine 其他回调和协议安全边界不因本决策放宽。

## 管理 API

| 前缀 | 保留操作 |
|---|---|
| `/ocpx/advertisers` | 列表、详情、新增、编辑、删除、`POST /{id}/recharge`、共享角色权限 GET/PUT |
| `/ocpx/promotion-links` | 列表、选项、详情、新增、编辑、逻辑删除 |
| `/ocpx/media` | 列表、详情、新增、编辑、删除 |
| `/ocpx/monitor-tasks` | 列表、详情、新增、编辑、逻辑删除 |
| `/ocpx/deduction-rules` | 列表、详情、新增、编辑、逻辑删除并同步 Redis 比例 |
| `/ocpx/reports` | 选项、小时明细、时段汇总、CSV 导出 |
| `/ocpx/flow-logs` | 脱敏流程日志查询 |

状态切换、归档、连通性测试、预检、独立 URL 校验、媒体导出和乐观锁版本参数均已删除，不提供兼容路径或字段别名。软删除前会校验依赖关系。

## 管理前端

七个列表页统一使用 `RightToolbar`：查询区显隐、刷新、列选择、当前行高亮、内部滚动、分页 `[10,30,60]` 且默认 30 条。列选择默认全选、不持久化，操作列不参与隐藏。长 URL、宏和扩展信息省略显示，悬浮看全文，点击复制并反馈结果；级联父项改变时清空无效子项；删除必须二次确认。

Dashboard 只保留账户卡和九项真实指标卡，支持今天、昨天、过去 7 天、过去 30 天和自定义日期；不展示数据排行、趋势图或列表页工具栏。流程日志没有参考页面，因此保留现有业务字段，仅统一列表交互。

按钮同时受权限控制，并提供加载禁用、键盘焦点和可访问名称。`ROLE_ADMIN` 对五个管理对象拥有完整 CRUD，并通过广告主管理页的“账号角色权限”统一维护共享 `ROLE_ADVERTISER`。浏览器只读写 `credentialConfigKey` 引用；广告主登录密码仅在新增弹窗提交一次，编辑、列表和详情均不返回或提交密码。

广告主开户同时创建 `sys_user.type=2` 账号、绑定系统托管的共享 `ROLE_ADVERTISER`，并以 `shop_id` 作为默认自身数据范围。V107 允许管理员统一勾选 OCPX 动作权限，并用独立的 `ocpx:advertiser:scope:all` 开关放开全部 OCPX 数据；未开启时仍强制限制自身广告主、链接、任务、报表和日志。媒体、扣量及广告主新增/删除依赖全量范围。数据范围不能绕过动作权限，系统管理和角色权限入口不能下放；保存后全部广告主会话失效并需重新登录。

## 报表和日志

时段报表与 Dashboard 统一读取 `scx_ocpx_hourly_metric`，支持小时/日/月/年、媒体到任务、广告主到链接级联筛选、合计行和 CSV。流程日志保存时间、流程、事件、四实体主键、结果、HTTP、耗时和脱敏说明；未登记广告主拒绝转发时会写入失败日志。系统操作日志和登录日志不受影响。
