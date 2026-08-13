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
| 广告主 | `name/bidUrl/credentialConfigKey/macro` | 不提供删除 |
| 推广链接 | `advertiserId/name/platform/channelId/linkUrl/deeplink/linkExt/remark` | `is_delete` 逻辑删除 |
| 媒体 | `name/uri/credentialConfigKey/macro` | 不提供删除 |
| 监测任务 | `monitorName/mediaId/advertiserId/promotionLinkId/unitPrice/bizId/showUrl/clkUrl/remark` | `is_delete` 逻辑删除 |
| 扣量规则 | `mediaId/promotionLinkId/ratio` | `is_delete` 逻辑删除；函数唯一索引保证同一路由只有一条生效规则 |

所有对象另有主键和创建/修改时间。平台为数值：`1 Android`、`2 iOS`、`3 Android+iOS`、`4 快应用`。监测 URL 由后端生成，前端不提交。

广告主 `bidUrl`、推广链接 URL 统一使用 `OcpxUrlPolicy` 校验，只允许安全的 HTTP/HTTPS 地址并拒绝本机、内网、链路本地地址、危险协议和非法模板。媒体 `uri` 是接入标识，不作为服务端请求目的地址。

## 管理 API

| 前缀 | 保留操作 |
|---|---|
| `/ocpx/advertisers` | 列表、详情、新增、编辑、`POST /{id}/recharge` |
| `/ocpx/promotion-links` | 列表、选项、详情、新增、编辑、逻辑删除 |
| `/ocpx/media` | 列表、详情、新增、编辑 |
| `/ocpx/monitor-tasks` | 列表、详情、新增、编辑、逻辑删除 |
| `/ocpx/deduction-rules` | 列表、详情、新增、编辑、逻辑删除并同步 Redis 比例 |
| `/ocpx/reports` | 选项、小时明细、时段汇总、CSV 导出 |
| `/ocpx/flow-logs` | 脱敏流程日志查询 |

状态切换、归档、连通性测试、预检、独立 URL 校验、媒体导出和乐观锁版本参数均已删除，不提供兼容路径或字段别名。软删除前会校验依赖关系。

## 管理前端

七个列表页统一使用 `RightToolbar`：查询区显隐、刷新、列选择、当前行高亮、内部滚动、分页 `[10,30,60]` 且默认 30 条。列选择默认全选、不持久化，操作列不参与隐藏。长 URL、宏和扩展信息省略显示，悬浮看全文，点击复制并反馈结果；级联父项改变时清空无效子项；删除必须二次确认。

Dashboard 保留 SCX 指标卡和四维排行，不使用列表页工具栏。流程日志没有参考页面，因此保留现有业务字段，仅统一列表交互。

按钮同时受权限控制，并提供加载禁用、键盘焦点和可访问名称。浏览器只读写 `credentialConfigKey` 引用，永远不读写真实账号或密钥。

## 报表和日志

时段报表读取最终报表表，支持小时/日/月/年、媒体到任务、广告主到链接级联筛选、合计行和 CSV。流程日志保存时间、流程、事件、四实体主键、结果、HTTP、耗时和脱敏说明；未登记广告主拒绝转发时会写入失败日志。系统操作日志和登录日志不受影响。
