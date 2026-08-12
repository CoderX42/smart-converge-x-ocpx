# ADR-008：OCPX Engine 采用传统分层并明确上下游边界

- 状态：Superseded by ADR-010
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` Java 包组织
- 补充：ADR-006 的四实体清理与 ADR-007 的最小启动骨架

## 背景

Engine 被重新定义为广告效果事件中转站，只负责点击/曝光接入、上下游协议转发、Redis 缓冲和 MySQL 明细落库。它不是完整的智能出价 OCPX 系统。项目负责人选择与现有工程一致的传统 Controller / Service / Mapper 分层，同时要求从包名上明确区分上游广告主/DSP 与下游媒体/渠道。

## 决策

- 启动类继续位于 `com.yzytech.scx.ocpx` 根包。
- 四个共享配置实体迁入 `com.yzytech.scx.ocpx.entity`，Admin 同步更新引用。
- HTTP 接入、服务编排、DTO 和协议适配层分别建立 `upstream` 与 `downstream` 子包。
- `controller.upstream` 接收广告主转化回调，`controller.downstream` 接收媒体曝光和点击。
- `adapter.upstream` 负责广告主协议与点击通知，`adapter.downstream` 负责媒体协议与转化回传。
- MySQL 访问统一位于 `mapper`，Redis 缓冲、幂等、消费和恢复统一位于 `redis`，Spring 装配位于 `config`。
- Controller 只调用 Service；Service 可以组织 Mapper、Adapter 和 Redis；禁止 Controller 直连 Mapper、Adapter 访问 MySQL或基础设施反向调用 Service。
- 不默认创建 `service.impl`；只有出现同一服务接口的多个实现时才增加实现子包。

## 当前实施边界

本次只迁移实体并以 `package-info.java` 固化包职责，不新增业务 Controller、Service、Mapper、DTO、Adapter、Redis Key、数据库表或公网接口。因此当前 Engine 仍只有健康检查能力，包目录不能作为数据面业务已经交付的证据。

> 本文仅保留 ADR-008 作出时的实施快照。当前包结构、线程池、Redis 和落库边界以 ADR-010 为准。

## 后果

后续点击正向链路按 `controller.downstream → service.downstream → adapter.upstream` 实现；转化反向链路按 `controller.upstream → service.upstream → adapter.downstream` 实现。双方共享的配置查询、明细落库和 Redis 能力分别进入 `mapper` 与 `redis`，避免复制两套处理基础设施。
