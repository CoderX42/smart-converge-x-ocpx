# Smart Converge X 广告平台文档中心

> 当前源码基线：2026-08-13 最小 OCPX + vientiane 管理端对齐（迁移号至 V102）。源码变更不代表目标数据库、UAT 或生产发布已验收。

## 当前广告业务

SCX 当前只保留连接上游广告主/DSP 与下游媒体/渠道所需的最小业务：

| 业务对象 | Java 实体 | 数据库表 |
|---|---|---|
| 上游广告主 | `OcpxAdvertiser` | `scx_ocpx_advertiser` |
| 推广链接 | `OcpxPromotionLink` | `scx_ocpx_promotion_link` |
| 下游媒体 | `OcpxMedia` | `scx_ocpx_media` |
| 监测任务 | `OcpxMonitorTask` | `scx_ocpx_monitor_task` |
| 扣量规则 | `OcpxDeductionRule` | `scx_ocpx_deduction_rule` |

管理端页面字段和交互以 `/Users/karl/code/sgyz-ocpx/vientiane-frontend/src/views` 为参考；`/manage/channel/channel` 映射为 SCX“媒体管理”。管理 API 始终使用 `advertiserId/mediaId/promotionLinkId/monitorTaskId`，不提供参考系统旧字段别名。

`scx-ocpx-engine` 默认监听 `10000/ocpx`，实现通用 `/feedback`、`/callback` GET 闭环、小米 `/mi/feedback` GET 和 KIMI `/kimi/callback` POST。`mId/tId/aId/lId` 分别是媒体、任务、广告主和链接的 SCX 主键。协议实现由 Java 主键注册表选择：未登记广告主拒绝上游转发并记录脱敏失败日志，未登记媒体使用 Feedback 携带的回调 URL 走通用回传；注册表不保存密钥。

五个共享实体位于 `scx-ocpx-core`。Admin 提供五实体管理、扣量规则、广告主 Redis 单值充值/余额、流程日志、Dashboard、时段报表和 CSV 导出。浏览器只传输服务端凭据引用，不返回或提交账号、密码、Secret、AES Key。

## 推荐阅读顺序

1. 仓库根目录 `AGENTS.md`。
2. [V102 vientiane 管理端对齐基线](OCPX管理端vientiane对齐V102.md)。
3. [OCPX 管理面实现说明](OCPX管理面实现说明.md)。
4. [OCPX Engine 实现说明](OCPX双向转发引擎实现说明.md)。
5. [sgyz-ocpx 核心流程对照](sgyz-ocpx基础能力兼容基线.md)。
6. [下游媒体 API 接入文档](OCPX下游渠道API接入文档.md)。
7. [上游广告主接入指南](OCPX上游广告主适配器开发指南.md)。
8. `contracts/` 中的 OpenAPI、JSON Schema、Postman 和管理端字段契约。

其余 MVP、竞品、ADR/RFC、旧模块规格、V72 点击计费/充值账务设计和 V79–V95 文档是历史或规划资料，不代表当前运行能力。

## 数据库与发布边界

- 空库结构以 `scx_current_schema.sql` 为准；已有 V101 库只允许人工审核后前向执行 `V102__align_ocpx_management_with_vientiane.sql`。
- V102 预检会拒绝无法映射的平台值和已软删的广告主/媒体，不静默删除或复活数据。
- V96–V101 保持不变；所有当前和未来 SQL 都不读写已退役的 `scx_schema_migration`。
- Engine profile 中只保存服务端凭据映射；生产值不得出现在浏览器、日志、文档或对外差异中。
- 发布前必须盘点目标库广告主主键并在注册表中全部分类；目标库迁移、凭据填充、宝塔部署和重启均需独立授权。
