# ADR-013：OCPX 路由主键短命名

- 状态：Accepted
- 日期：2026-08-12
- 范围：Feedback Query、Callback ext、Engine/Admin 模型与五张数据表

## 背景

旧路由字段 `chid/mid/dspid/linkid` 来自历史系统，无法直接表达 SCX 四实体；完整实体名
又会显著拉长媒体监测链接。负责人决定使用带注释的短字段，并接受一次性破坏性切换。

## 决策

- Java 与 Query 统一为 `mId/tId/aId/lId`，分别对应 `OcpxMedia.id`、
  `OcpxMonitorTask.id`、`OcpxAdvertiser.id`、`OcpxPromotionLink.id`。
- 数据库统一为 `m_id/t_id/a_id/l_id`。`aId/a_id` 是广告主主键，既有
  `advId/adv_id` 继续表示广告主侧广告 ID。
- 不提供旧 Query、旧 Callback ext 或旧数据库列别名。缺少短主键或混入旧 Query
  时 Feedback 返回 `400`；旧 ext 按 Callback 解析异常返回 `500`。
- Redis 扣量 Key 的值和命名空间不变，仍按推广链接 ID 与媒体 ID 组成；旧 JSON 报表
  队列必须在切换前排空。
- V97 保持不变，已有数据库通过前向 V98 原位改列；生产执行必须另行取得授权。

## 后果

旧监测链接和发布前生成的 Callback ext 在切换后失效。Boot、Engine 与 V98 必须在同一
维护窗口切换；源码门禁不代表目标数据库、真实上下游或生产验收。
