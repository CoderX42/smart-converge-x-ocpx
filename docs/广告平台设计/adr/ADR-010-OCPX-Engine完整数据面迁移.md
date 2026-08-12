# ADR-010：OCPX Engine 一比一迁移通用数据闭环

- 状态：Accepted
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` 通用 Feedback/Callback、Redis、落库和 Admin 汇总
- 取代：ADR-007 至 ADR-009 中与当前数据面实现冲突的决定

## 背景

项目负责人要求不保留 Engine 现有或历史实现，按
`/Users/karl/code/sgyz-ocpx/vientiane-rtb` 一比一迁移选定的通用数据层，不优化
原算法，不新增能力；同时继续复用 SCX 四实体和独立启动能力。

## 决策

- Engine 切换为 Spring MVC，公开接口只有 `GET /general/feedback` 和
  `GET /union/callback`；上游和下游出站都只执行 GET。
- `chid/mid/dspid/linkid` 分别直接使用媒体、监测任务、广告主、推广链接的 SCX
  字符串主键。四个字段不改为实体对象。
- 迁移旧 Feedback/Callback 对象、通用参数适配、四索引、Redis Key、去重、分桶、
  扣量、两个 500 固定线程 Manager、StorageTask 和 DataMergeTask。
- Feedback 重复仍入队并转发；扣量数组固定 11；非原子 Redis 数组更新、无事务
  List 消费、多表顺序写入和高 Action 越界均有意保留。
- 新增五张 SCX 命名数据表，四个关系 ID 统一 `VARCHAR(32)`；
  `scx_ocpx_flow_log` 不再由 Engine 写入，但不生成删除 SQL。
- 不迁移合作方专用 Controller、枚举项、固定 ID、URL 和协议；不增加 RTA、预算、
  账务、可靠队列、重试、DLQ 或扣量管理页面。
- 旧固定 AES Key、真实域名和广告主签名密钥不得复制；仓库只保留本机联调字面量。

## 后果

该实现继承旧数据层的并发、锁、队列和数组边界缺陷。源码验收只能证明本地测试、
打包、契约和 schema 门禁，不包含目标库执行、上下游 UAT 或生产发布。已有数据库
须在备份、预览和明确审批后人工执行 `V97__add_ocpx_legacy_data_layer.sql`。
