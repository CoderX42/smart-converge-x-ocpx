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

广告域数据库另保留一张 `scx_ocpx_flow_log`。管理端可以查询和聚合已有日志，但当前源码没有生成 Feedback/Callback 流程日志的数据面实现。

当前 `scx-ocpx-engine` 只保留四个实体和最小 Spring Boot 启动骨架，默认监听 `10000/ocpx`。它不包含机器接口、同步转发、Adapter 或鉴权；Feedback/Callback、监测跳转及数据面重建均属于后续工作，不能把进程健康或历史契约视为业务能力已实现。

用户、角色、菜单、部门、字典、操作日志和登录日志仍由原系统管理框架承载。本次只调整广告业务菜单与权限。

## 推荐阅读顺序

1. 仓库根目录 `AGENTS.md`。
2. [四实体实现与新人上手](OCPX管理面实现说明.md)。
3. [OCPX Engine 重建状态说明](OCPX双向转发引擎实现说明.md)。
4. [sgyz-ocpx 核心流程对照](sgyz-ocpx基础能力兼容基线.md)。
5. [下游媒体 API 接入文档](OCPX下游渠道API接入文档.md)。
6. [上游广告主 Adapter 指南](OCPX上游广告主适配器开发指南.md)。
7. `contracts/` 中的 OpenAPI、JSON Schema 和 Postman 集合。

其余 MVP、竞品、ADR/RFC、旧模块规格和 V79–V95 文档是历史或规划资料，不代表当前运行能力。

## 数据库与发布边界

- 空库结构以 `scx_current_schema.sql` 为准。
- `V96__reset_ocpx_ad_business_to_four_entities.sql` 是手工、破坏性的广告域重建 SQL；本次源码实现不执行它。
- V96 不修改用户、角色、部门、字典、操作日志、登录日志或 `menu_ocpx_system`。
- 当前 Engine 只有通用 `application.yml` 和独立进程骨架，没有 profile 凭据配置；后续重建前必须另行确定协议、安全和生产部署方案。
- V79–V95 保持不变；不读写退役的 `scx_schema_migration`。
