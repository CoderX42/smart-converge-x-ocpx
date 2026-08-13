# SCX 对 `sgyz-ocpx` 通用数据闭环的兼容基线

> 参考仓库：`/Users/karl/code/sgyz-ocpx`。本基线只覆盖通用
> Feedback 与 Callback 数据闭环。

## 实体和 ID 映射

| `sgyz-ocpx` | SCX | Query ID | SCX 表 |
|---|---|---|---|
| `UserDsp` | `OcpxAdvertiser` | `aId` | `scx_ocpx_advertiser` |
| `DspLink` | `OcpxPromotionLink` | `lId` | `scx_ocpx_promotion_link` |
| `UserChannel` | `OcpxMedia` | `mId` | `scx_ocpx_media` |
| `ChannelMonitorTask` | `OcpxMonitorTask` | `tId` | `scx_ocpx_monitor_task` |

所有 ID 均保留为 `String`，值是对应 SCX 实体的主键。
`credentialConfigKey` 对应旧 `UserDsp.ekey`，`bidUrl` 继续作为广告主 URL 模板，
`OcpxMonitorTask.taskCode` 对应旧 `bizId`。

## 兼容闭环

```text
媒体 GET /feedback
  -> FeedbackIngressAdapter
  -> Feedback Redis 去重、队列
  -> TemplateGetUpstreamDeliveryService 替换 bidUrl 宏、生成 AES ext 和 signature
  -> 广告主 GET
  -> 广告主 GET /callback
  -> 解密 ext、Callback 去重和 lId+mId 扣量
  -> 原 callback_url 追加 event_type
  -> 媒体 GET
  -> StorageTask 写明细、日明细和临时报表
  -> Admin DataMergeTask 写最终报表
```

SCX 保留旧 Redis Key、去重算法、队列分桶、扣量数组、StorageTask 批次与写入顺序、
配置内存索引、共享刷新标记和最终报表合并方式。Feedback 重复仍转发；扣量数组长度
固定为 11；Redis 数组读改写、List 消费和多表写入仍按旧代码的非原子方式运行。

## 必要适配与排除项

仅适配 Java/Spring Boot 版本、`com.yzytech.scx.ocpx` 包名、SCX 字符串主键/表名，
并使用无真实密钥和域名的本机配置。没有迁移爱奇艺、百度、OPPO、华为、快手等
合作方 Controller、固定 ID、URL、枚举项和专用协议；不包含智能出价、预算、
账务、重试、DLQ 或管理端扣量配置页面。

> 2026-08-13 增补：上述排除范围已部分收窄——管理端扣量配置、极简充值、
> 小米/KIMI 合作方适配等已交付，详见末节「2026-08 增补：已对齐能力」；
> 未列入该节的内容仍维持原排除。

## 2026-08 增补：已对齐能力

本节登记 2026-08-13 从 SGYZ 继续平移并已在当前工作树交付的能力（迁移 V99–V101）。
「现状」以代码、测试与门禁脚本证据为准，不代表目标数据库或生产已发布。

| # | 已对齐能力 | SGYZ 来源 | SCX 落地 |
|---|---|---|---|
| 1 | 管理端扣量规则配置 | 管理端扣量配置页 + Redis 路由扣量比例 | 新表 `scx_ocpx_deduction_rule`（V99，`media_id`+`promotion_link_id`+`ratio`，软删唯一）；Admin CRUD `/ocpx/deduction-rules`（权限 `ocpx:deduction-rule:*`）；写库后同步 `ocpx:route:ratio:{lId}:{mId}`，变更/删除时清理 `ocpx:route:original-count:`/`ocpx:route:delivered-count:`，删除复位 1.0；菜单种子「扣量规则」`/ocpx/deduction`；前端 `views/ocpx/deduction/index.vue`、`api/ocpx/deduction.ts`；`check-ocpx-four-entities.sh` 放宽为五业务表 |
| 2 | 监测任务双链接与业务字段 | advertise/flow 页形态 | `scx_ocpx_monitor_task` 增加 `unit_price`/`biz_id`/`show_url`/`clk_url`/`remark`（V100）；Admin 创建/更新时持久化生成 `show_url`/`clk_url`（`{engine-base}/feedback?{媒体宏参数}&event=IMP\|CLK&mId=&tId=&aId=&lId=`），配置项 `scx.ocpx.admin.tracking.engine-base-url` 在 yml 直写（dev `http://127.0.0.1:10000/ocpx`、prod `https://ocpx.zhongsenyuedong.cn/ocpx`）；旧 `/c/{taskCode}` trackingUrl 已移除；前端监测任务页对齐（业务编号/投放单价/双链接点击复制列、级联弹窗） |
| 3 | 充值/余额极简版与监测任务删除 | 广告主充值（Redis 单值余额） | `POST /ocpx/advertisers/{id}/recharge`（权限 `ocpx:advertiser:recharge`，V101 权限种子），Redis key `ocpx:advertiser:balance:{aId}` 单值累加，列表/详情逐行回填 balance，无流水表；`DELETE /ocpx/monitor-tasks/{id}`（逻辑删，权限 `ocpx:monitor-task:delete`）；前端广告主页余额列 + 充值弹窗、监测任务删除按钮 |
| 4 | 时段报表与导出 | AChannelTime 报表形态 | `GET /ocpx/reports/summary`（`dateType` 0时/1日/2月/3年、`linkRadio` 左联扣量规则、`clickRate`、asc/desc snake_case 白名单排序、`current`/`size` 分页）、`GET /ocpx/reports/summary:export`（CSV 带 BOM，`时段报表.csv`）、`GET /ocpx/media:export`（`媒体管理报表.csv`）；前端报表页重写为 SGYZ AChannelTime 形态（`api/ocpx/report.ts`） |
| 5 | 小米 + KIMI 合作方适配 | mi/kimi Controller 与投递协议 | 引擎 `GET /mi/feedback`（小米方言入站，固定响应 `{"code":0,"failMsg":"","success":true}`）与小米回传（`trail.e.mi.com/api/callback` + MD5 sign，媒体 `adapterCode=MI` 分发）；`POST /kimi/callback`（query `ext`/`s` + body `action_type`/`action_time`，响应 `{"code":0}`）与 KIMI 投递（广告主 `adapterCode=KIMI` 分发，回调地址 `{domain}/kimi/callback?ext=&s={lId}_{mId}`）；Admin 侧 adapterCode 白名单增加 `MI`/`KIMI`；GET-only 门禁对 `KimiCallbackController` 加白名单 |
| 6 | 数据面运维回补 | 日志拆分与清理任务 | 引擎 `logback-spring.xml`（fb/cb/imp/clk/error/other 等具名 logger 分文件滚动 + 异步）；`IndexDataLoader` 经 `@TableLogic` 自动过滤逻辑删除（核实无需改代码，补锁定测试）；`scx_ocpx_log_feedback_day` 每日 00:10 清理任务（`ResetRedisKeyTask.deleteFeedbackDay`） |

仍未对齐（维持原排除）：SGYZ 其余 11 个媒体与 19 个广告主合作方适配、RTA、
渠道应用审核、自助账号体系、点击扣费/CPC、账务流水表，以及智能出价、预算、
重试与 DLQ。
