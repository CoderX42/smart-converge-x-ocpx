# OCPX 重构实现说明

## 1. 当前产品边界

SCX 已收敛为与参考广告服务平台一致的后台管理功能面：

1. 首页：真实效果仪表盘，提供今日点击/激活/首活/付费、昨日次留、7/30/90 天与自定义趋势、漏斗、D2–D7 留存、回传质量、配置覆盖及按权限展示的运行/账务面板。
2. 报表中心：时段报表，支持小时/日期/月/年、渠道、监测链接、广告主、应用链接、日期范围筛选与 Excel 下载，并与仪表盘共用点击质量、首活、拉活和 D2–D7 口径。
3. 渠道管理：渠道资料和监测链接管理。
4. 广告主管理：广告主、应用链接和扣量规则管理，含幂等充值、账务流水和 CPC 点击扣费。
5. 系统管理：原样复用 `scx-system` 的 Sa-Token + RBAC，恢复用户、角色、菜单、部门、字典、存储配置、操作日志和登录日志管理。

汽车线索上报已退出当前产品边界；前后端接口、菜单、权限和业务表由 V72 前向移除。

原商业、财务/支付、媒体主体/资源、创意、投放、监测归因、旧报表和运营中心不再是运行模块。

## 2. 工程结构

- 后端：`scx-backend/scx-ocpx-admin`，包根 `com.yzytech.scx.ocpx.admin`。
- 前端 API：`scx-fronted/apps/web-ele/src/api/ocpx.ts`。
- 前端页面：`scx-fronted/apps/web-ele/src/views/ocpx/`。
- 空库当前态结构、菜单、账户、OCPX 与效果大屏事实统一由 `db/schema/scx_current_schema.sql` 初始化。
- V1-V78 历史脚本已完成基线整合，不再由初始化器扫描或重放。
- 未来已有库结构变化使用独立前向增量 SQL，并同步当前态初始化脚本。
- 旧表物理退役：`scx-backend/db/smart-converge-x/retirement/retire-superseded-ad-domains.sql`。

## 3. 接口契约

| 功能 | 接口 |
| --- | --- |
| 首页 | `GET /ocpx/dashboard/summary?startDate&endDate&channelId&advertiserId&appLinkId&monitorId` |
| 首页账务概览 | `GET /ocpx/dashboard/account-summary`（`ocpx:account:read`） |
| 渠道 | `GET/POST /ocpx/channels`，`PUT/DELETE /ocpx/channels/{id}` |
| 广告主 | `GET/POST /ocpx/advertisers`，`PUT/DELETE /ocpx/advertisers/{id}` |
| 账户 | `POST /ocpx/advertisers/{id}/recharge`，`GET /ocpx/advertisers/{id}/ledger` |
| 应用链接 | `GET/POST /ocpx/app-links`，`PUT/DELETE /ocpx/app-links/{id}` |
| 监测链接 | `GET/POST /ocpx/monitors`，`PUT/DELETE /ocpx/monitors/{id}` |
| 扣量规则 | `GET/POST /ocpx/deduction-rules`，`PUT/DELETE /ocpx/deduction-rules/{id}` |
| 时段报表 | `GET /ocpx/report-options`，`GET /ocpx/reports/hourly`，`GET /ocpx/reports/hourly/export` |

所有管理接口使用原系统 Sa-Token + RBAC 权限码。权限数据继续落在 `sys_user`、`sys_role`、`sys_menu`、`sys_role_menu`，不新增平行权限表或第二套授权模型。渠道和广告主密钥、广告主密码均只写不回显；密码使用 BCrypt 保存。新增广告主会同步创建 `sys_user` 登录账号并通过原 RBAC 关系绑定 `ROLE_ADVERTISER`，其首页、筛选选项、报表查询和导出都会强制限制到自身广告主 ID；广告主账号缺少绑定关系时按失败关闭处理。

## 4. 新数据表

- `scx_ocpx_channel`
- `scx_ocpx_advertiser`
- `scx_ocpx_app_link`
- `scx_ocpx_monitor_link`
- `scx_ocpx_deduction_rule`
- `scx_ocpx_hourly_report`
- `scx_ocpx_billing_entry`（充值、审计与 CPC 扣费共用的幂等流水）

报表事实粒度为“小时 + 渠道 + 广告主 + 应用链接 + 监测链接”。扣量规则保存 0 到 1 的保留比例。

V78 在小时事实中增加有效/重复/跳过/已分类点击、首活、拉活、D3–D7 和事实更新时间，并已并入空库当前态结构。已有库若曾在缺少 V77 去重表时部分执行 V78，使用 V79 前向校正：补齐去重基础设施及大屏字段后，再从不可变去重决策做证据回填；缺少决策的历史行不推断。`revenue_amount` 没有稳定写入链路，不再作为首页或报表正式指标。

## 4.1 效果指标口径

- 点击：唯一 `event_id` 的全部 CLICK 事实；重复设备窗点击仍保留事实，但不计费、不投递。
- 激活率、首活率、拉活率：对应事件次数 ÷ 设备窗有效点击。所选区间 `classified_click_count != clicks` 或有效点击为 0 时返回 `null`。
- 首活：`FIRST_ACTIVATION`；拉活：`REACTIVATION`；`FIRST_REACTIVATION` 仍是首次唤起并进入唤起列。
- 二留至七留：`NEXT_DAY_RETENTION`、`RETENTION_D3`…`RETENTION_D7`，按上游 `actionTime` 明确回传统计，不由 SCX 推算 cohort。
- 付费只展示 `PAYMENT` 事件次数；回调金额因币种口径未统一，不进入大屏。
- 自然日和顶部今日/昨日比较均使用 `Asia/Shanghai`；趋势缺行补 0，未知点击口径使用 `null`，二者不混用。

## 5. 迁移与退役门禁

空库只执行当前态 schema；已有库变更由已授权人员在备份和审批后显式执行经审查的前向 SQL。`V{n}__*.sql` 只是发布包标识，源码文件不等于已在目标库执行。数据库迁移账本已经退役，当前及未来 SQL 不得创建、读取、写入或依赖它；目标结果通过真实结构、约束、种子、关键数据对账和数据库校验确认。

V68/V69 保留历史事实；V72 会物理删除 `scx_ocpx_clue` 和对应菜单/权限，并扩展账务流水。部署前若旧线索仍需归档，必须先备份该表。

物理退役旧表必须单独执行：

1. 明确目标库并完成全库备份。
2. 依次运行 V68、V69，部署新后端和前端，完成新功能与系统管理 RBAC 冒烟。
3. 确认旧域不再被任何进程、报表或外部任务读取。
4. 预览 `retire-superseded-ad-domains.sql` 中的表清单并取得本次生产授权。
5. 执行退役脚本，再运行 `make db-verify`。

## 6. 账户语义与运行边界

充值要求 16–64 位 `requestId`，同一广告主和请求号重复提交只返回首次流水，不会重复入账。CPC 以首次落库的 CLICK `event_id` 为扣费唯一键，使用监测链接 `unit_price`，币种固定 CNY；余额不足写 `REJECTED` 流水、不扣余额且不创建上游投递任务。`AUDIT_ONLY` 只写 `RECORDED` 流水。充值、余额修改和流水在同一 MySQL 事务内完成。

feedback 的 HTTP 202 仅表示已可靠进入 Redis Stream；实际扣费由异步批量持久化完成。生产启用 CPC 前仍须应用 V72、完成初始余额核对、重复事件/余额不足/故障恢复测试以及真实流量对账。
