# ADR-001：OCPX 双向转发采用独立数据面

- 状态：Accepted
- 日期：2026-08-03
- 范围：V70 / Coco 首接

> 后续说明：V72 在不改变独立数据面决策的前提下，以幂等账务流水和余额行锁替代了本 ADR 的 Billing `OFF/AUDIT_ONLY` 临时约束；RTA `OFF` 约束仍有效。详见 `../OCPX点击计费与充值账务说明.md`。

## 背景

现有 `scx-boot/scx-adx` 是管理面单体，主要处理后台配置、查询、权限与报表，不适合直接承担 20,000 accepted feedback QPS 的机器流量。历史 OCPX 模块和契约已退出当前构建，不能视为可复用运行能力。

## 决策

新增三个模块：

- `scx-ocpx-contract`：版本化 DTO 与上下游适配器、RTA、Billing SPI。
- `scx-ocpx-runtime`：标准化、签名、Redis Streams、批量落库、投递任务、重试、扣量和 Coco/标准下游适配器。
- `scx-rtb`：独立 WebFlux/Netty 进程，端口 `10000`，context path `/rtb`。

管理面与数据面共享 MySQL/Redis。MySQL 保存最终事实、版本配置、投递任务与审计；Redis 保存接入队列、短期幂等、防重放、扣量计数和配置失效通知。本地路由索引以不可变快照替换，只有 `PUBLISHED` 配置可接入流量。

## 约束

- feedback 方向固定为 `下游 → SCX → 上游`；callback 固定为 `上游 → SCX → 下游`。
- 202 只表示事件已进入满足副本确认策略的 Redis Stream。
- callback 必须先写 MySQL 事实与唯一扣量决策，再向上游返回成功。
- 扣量不修改原始事实，只控制是否创建下游投递任务。
- RTA 只能 `OFF`；Billing 只能 `OFF/AUDIT_ONLY`，不得修改广告主余额。
- 应用配置直写 `application-*.yml`，不新增运行时环境变量依赖。

## 后果

收益是机器流量可独立扩容、管理面故障域与数据面隔离、适配器可扩展、事实与投递任务可审计。代价是新增独立 Java 进程、Redis Stream 运维、V70 数据表、配置发布与两套健康检查。生产启用必须单独完成数据库迁移、Redis 持久化/副本、真实协议 UAT 和容量验收。
