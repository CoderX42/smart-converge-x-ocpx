# Smart Converge X 广告平台文档中心

> 当前源码基线：2026-08-12 最小 OCPX。源码变更不代表构建、测试、目标数据库、UAT 或生产发布已验收。

## 当前广告业务

SCX 当前只保留连接上游广告主/DSP 与下游媒体/渠道所需的最小业务：

| 业务对象 | Java 实体 | 数据库表 |
|---|---|---|
| 上游广告主 | `OcpxAdvertiser` | `scx_ocpx_advertiser` |
| 推广链接 | `OcpxPromotionLink` | `scx_ocpx_promotion_link` |
| 下游媒体 | `OcpxMedia` | `scx_ocpx_media` |
| 监测任务 | `OcpxMonitorTask` | `scx_ocpx_monitor_task` |

广告域另保留一张 `scx_ocpx_flow_log`，记录脱敏后的 Feedback/Callback 转发结果。Dashboard 和报表直接聚合该日志，不创建报表事实表或导出任务表。

当前链路是同步的：媒体 Feedback 成功转发上游后返回 202；上游 Callback 成功回传媒体后返回广告主 Adapter 定义的成功响应。当前版本不包含事件配置、路由发布/快照/ACK、异步队列、自动重试、DLQ、扣量、账务、充值、对象范围或 API 调试。

用户、角色、菜单、部门、字典、操作日志和登录日志仍由原系统管理框架承载。本次只调整广告业务菜单与权限。

## 推荐阅读顺序

1. 仓库根目录 `AGENTS.md`。
2. [四实体实现与新人上手](OCPX管理面实现说明.md)。
3. [双向转发引擎实现说明](OCPX双向转发引擎实现说明.md)。
4. [sgyz-ocpx 核心流程对照](sgyz-ocpx基础能力兼容基线.md)。
5. [下游媒体 API 接入文档](OCPX下游渠道API接入文档.md)。
6. [上游广告主 Adapter 指南](OCPX上游广告主适配器开发指南.md)。
7. `contracts/` 中的 OpenAPI、JSON Schema 和 Postman 集合。

其余 MVP、竞品、ADR/RFC、旧模块规格和 V79–V95 文档是历史或规划资料，不代表当前运行能力。

## 数据库与发布边界

- 空库结构以 `scx_current_schema.sql` 为准。
- `V96__reset_ocpx_ad_business_to_four_entities.sql` 是手工、破坏性的广告域重建 SQL；本次源码实现不执行它。
- V96 不修改用户、角色、部门、字典、操作日志、登录日志或 `menu_ocpx_system`。
- 合作方账号和 Secret 只写在 Engine profile yml 的 `partner-credentials`，修改后重新打包并由宝塔重启。
- V79–V95 保持不变；不读写退役的 `scx_schema_migration`。
