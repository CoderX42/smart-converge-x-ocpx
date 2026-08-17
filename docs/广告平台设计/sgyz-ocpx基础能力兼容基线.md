# SCX 对 `sgyz-ocpx` 通用数据闭环的兼容基线

> 参考仓库：`/Users/karl/code/sgyz-ocpx`。本基线只覆盖通用
> Feedback 与 Callback 数据闭环。
>
> 2026-08-15 更新：V108 已将事件去重、账户、CPC 流水和小时统计切换为 MySQL
> 事务真相。下文中 Redis 去重、旧 List/报表合并仅是 SGYZ 历史兼容链路，不再决定首页、报表或计费结果。

## 实体和 ID 映射

| `sgyz-ocpx` | SCX | Query ID | SCX 表 |
|---|---|---|---|
| `UserDsp` | `OcpxAdvertiser` | `aId` | `scx_ocpx_advertiser` |
| `DspLink` | `OcpxPromotionLink` | `lId` | `scx_ocpx_promotion_link` |
| `UserChannel` | `OcpxMedia` | `mId` | `scx_ocpx_media` |
| `ChannelMonitorTask` | `OcpxMonitorTask` | `tId` | `scx_ocpx_monitor_task` |

所有 ID 均保留为 `String`，值是对应 SCX 实体的主键。
`credentialConfigKey` 是旧 `UserDsp.ekey` 的安全替代引用，原始值不迁入浏览器或业务表；
`bidUrl` 继续作为广告主 URL 模板，`OcpxMonitorTask.bizId` 对应旧 `bizId`。

## 兼容闭环

```text
媒体 GET /feedback
  -> FeedbackIngressAdapter
  -> MySQL 唯一事实 + 账户锁 + CPC 流水 + 小时指标
  -> 提交后写旧 Redis 队列（仅兼容日志）
  -> TemplateGetUpstreamDeliveryService 替换 bidUrl 宏、生成 AES ext 和 signature
  -> 广告主 GET
  -> 广告主 GET /callback
  -> 解密 ext、MySQL Callback 唯一事实、目标转化冻结和 lId+mId 扣量
  -> 原 callback_url 追加 event_type
  -> 媒体 GET
  -> 旧 StorageTask/DataMergeTask 继续保留兼容日志，不作为经营数据真相
```

SCX 保留旧 Redis Key、队列分桶、扣量数组、StorageTask 和 DataMergeTask 用于兼容观察。
V108 起重复 Feedback/Callback 不再重复计数或投递；MySQL 事实、流水和小时指标在同一事务中写入。
扣量路由 Redis 状态仍按现有兼容实现运行，但不影响账户余额和有效事件口径。

## 必要适配与排除项

仅适配 Java/Spring Boot 版本、`com.yzytech.scx.ocpx` 包名、SCX 字符串主键/表名，
并使用无真实密钥和域名的本机配置。没有迁移爱奇艺、百度、OPPO、华为、快手等
合作方 Controller、固定 ID、URL、枚举项和专用协议；不包含智能出价、预算、
账务、重试、DLQ 或管理端扣量配置页面。

> 2026-08-13 增补：上述排除范围曾部分收窄——管理端扣量配置、极简充值和
> 小米/KIMI 合作方适配曾完成源码平移；其中两套合作方专用协议已于 2026-08-17 移除；
> 未列入该节的内容仍维持原排除。

## 2026-08 增补：已对齐能力

本节登记 2026-08-13 至 2026-08-14 从 SGYZ 继续平移并已在当前工作树交付的能力（迁移 V99–V103）。
「现状」以代码、测试与门禁脚本证据为准，不代表目标数据库或生产已发布。

| # | 已对齐能力 | SGYZ 来源 | SCX 落地 |
|---|---|---|---|
| 1 | 管理端扣量规则配置 | 管理端扣量配置页 + Redis 路由扣量比例 | 新表 `scx_ocpx_deduction_rule`（V99，`media_id`+`promotion_link_id`+`ratio`，软删唯一）；Admin CRUD `/ocpx/deduction-rules`（权限 `ocpx:deduction-rule:*`）；写库后同步 `ocpx:route:ratio:{lId}:{mId}`，变更/删除时清理 `ocpx:route:original-count:`/`ocpx:route:delivered-count:`，删除复位 1.0；菜单种子「扣量规则」`/ocpx/deduction`；前端 `views/ocpx/deduction/index.vue`、`api/ocpx/deduction.ts`；`check-ocpx-four-entities.sh` 放宽为五业务表 |
| 2 | 监测任务双链接与业务字段 | advertise/flow 页形态 | `scx_ocpx_monitor_task` 保存 `unit_price`/`biz_id`/`show_url`/`clk_url`/`remark`，V102 将名称字段收敛为 `monitor_name` 并使用 `is_delete`；Admin 创建/更新时持久化生成 `show_url`/`clk_url`（`{engine-base}/feedback?{媒体宏参数}&event=IMP\|CLK&mId=&tId=&aId=&lId=`）；旧 `/c/{taskCode}` 已移除；前端展示业务编号、单价、双链接复制和级联弹窗 |
| 3 | 充值/余额、CPC 与监测任务删除 | 广告主充值与首次有效点击 | V108 以 `scx_ocpx_advertiser_account` 和不可变 `scx_ocpx_billing_entry` 替代 Redis 余额；充值增加 `requestId`、账前/账后余额和真实 `ROLE_ADMIN` 校验；首次有效点击按任务 CNY 单价扣费，余额不足记拒绝流水且不投递；有账务或事实后禁止广告主物理删除 |
| 4 | 时段报表与导出 | AChannelTime 报表形态 | `GET /ocpx/reports/summary`（`dateType` 0时/1日/2月/3年、四实体级联、扣量、CTR、消耗、转化列、排序和分页）、`GET /ocpx/reports/summary:export`（CSV 带 BOM）；V108 与 Dashboard 统一读取 `scx_ocpx_hourly_metric` |
| 5 | 小米 + KIMI 合作方适配（已移除） | mi/kimi Controller 与投递协议 | 2026-08-17 删除两套专用入口、适配器、调度分支和专用凭据配置；当前下游统一走通用 Callback URL，上游只保留 `TEMPLATE_GET` 与 AERIM |
| 6 | 数据面运维回补 | 日志拆分与清理任务 | 引擎 `logback-spring.xml`（fb/cb/imp/clk/error/other 等具名 logger 分文件滚动 + 异步）；`IndexDataLoader` 经 `@TableLogic` 自动过滤逻辑删除（核实无需改代码，补锁定测试）；`scx_ocpx_log_feedback_day` 每日 00:10 清理任务（`ResetRedisKeyTask.deleteFeedbackDay`） |
| 7 | vientiane 管理端硬替换对齐 | `vientiane-frontend/src/views` | V102 精简五实体字段、数值平台、删列改名、函数唯一索引与 RBAC；删除状态/归档/预检/连通性/媒体导出等旧接口；七个列表页统一列控制、复制、级联和分页；详见 [V102 基线](OCPX管理端vientiane对齐V102.md) |
| 8 | 广告主开户与只读账号 | DSP 新增时同步创建账号 | V103 使用 SCX `sys_user`/`sys_user_role` 原子开户，BCrypt 保存密码，固定 `ROLE_ADVERTISER`；type=2 账号以 `shop_id` 强制限制为自身广告主，只读六页与报表 CSV，既有广告主不自动开户；详见 [V103 基线](OCPX管理端开户与数据范围V103.md) |

仍未对齐（维持原排除）：SGYZ 其余 11 个媒体与 19 个广告主合作方适配、RTA、
渠道应用审核、广告主自助增删改运营、账务流水页面，以及智能出价、预算、重试与 DLQ。
