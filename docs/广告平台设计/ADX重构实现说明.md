# ADX 重构实现说明

## 1. 当前产品边界

SCX 已收敛为与参考广告服务平台一致的后台管理功能面：

1. 首页：昨日、近 7 日、近 30 日、累计收入和 30 日趋势。
2. 报表中心：时段报表，支持小时/日期/月/年、渠道、监测链接、广告主、应用链接、日期范围筛选与 Excel 下载。
3. 渠道管理：渠道资料和监测链接管理。
4. 广告主管理：广告主、应用链接和扣量规则管理，含广告主充值。
5. 权限管理：原样复用 `scx-system` 的 RBAC 账号、角色、菜单和按钮授权。
6. 线索上报：Excel 导入、手工维护、按勾选或筛选条件批量重置、任务状态和提交入口。

原商业、财务/支付、媒体主体/资源、创意、投放、监测归因、旧报表和运营中心不再是运行模块。

## 2. 工程结构

- 后端：`scx-backend/scx-adx`，包根 `com.yzytech.scx.adx`。
- 前端 API：`scx-fronted/apps/web-ele/src/api/adx.ts`。
- 前端页面：`scx-fronted/apps/web-ele/src/views/adx/`。
- 建表与菜单迁移：`V68__create_compact_adx_platform.sql`。
- 旧表物理退役：`scx-backend/db/smart-converge-x/retirement/retire-superseded-ad-domains.sql`。

## 3. 接口契约

| 功能 | 接口 |
| --- | --- |
| 首页 | `GET /adx/dashboard/summary` |
| 渠道 | `GET/POST /adx/channels`，`PUT/DELETE /adx/channels/{id}` |
| 广告主 | `GET/POST /adx/advertisers`，`PUT/DELETE /adx/advertisers/{id}`，`POST /{id}/recharge` |
| 应用链接 | `GET/POST /adx/app-links`，`PUT/DELETE /adx/app-links/{id}` |
| 监测链接 | `GET/POST /adx/monitors`，`PUT/DELETE /adx/monitors/{id}` |
| 扣量规则 | `GET/POST /adx/deduction-rules`，`PUT/DELETE /adx/deduction-rules/{id}` |
| 时段报表 | `GET /adx/report-options`，`GET /adx/reports/hourly`，`GET /adx/reports/hourly/export` |
| 线索 | `GET/POST /adx/clues`，`PUT /adx/clues/{id}`，`POST /upload`，`PUT /batch-status`，`POST /submit`，`GET /status` |

所有管理接口使用原系统 Sa-Token + RBAC 权限码。权限数据继续落在 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu`，不新增平行权限表或第二套授权模型。渠道和广告主密钥、广告主密码均只写不回显；密码使用 BCrypt 保存。新增广告主会同步创建 `sys_user` 登录账号并通过原 RBAC 关系绑定 `ROLE_ADVERTISER`，其首页、筛选选项、报表查询和导出都会强制限制到自身广告主 ID；广告主账号缺少绑定关系时按失败关闭处理。

## 4. 新数据表

- `scx_adx_channel`
- `scx_adx_advertiser`
- `scx_adx_app_link`
- `scx_adx_monitor_link`
- `scx_adx_deduction_rule`
- `scx_adx_hourly_report`
- `scx_adx_clue`

报表事实粒度为“小时 + 渠道 + 广告主 + 应用链接 + 监测链接”。扣量规则保存 0 到 1 的保留比例。

## 5. 迁移与退役门禁

常规数据库升级只执行 V68：创建新表、隐藏旧菜单、撤销旧权限并启用新菜单。它不会自动删除历史业务数据。

物理退役旧表必须单独执行：

1. 明确目标库并完成全库备份。
2. 运行 V68、部署新后端和前端，完成新功能冒烟。
3. 确认旧域不再被任何进程、报表或外部任务读取。
4. 预览 `retire-superseded-ad-domains.sql` 中的表清单并取得本次生产授权。
5. 执行退役脚本，再运行 `make db-verify`。

## 6. 已知外部边界

参考项目的汽车之家线索提交依赖外部任务凭据和子任务信息，仓库中没有可安全复用的凭据。当前实现会完整保存待上报线索并返回 `BLOCKED` 原因，不会伪造成功。取得目标环境的正式任务契约和凭据后，再补真实发送适配器、重试与回执测试。
