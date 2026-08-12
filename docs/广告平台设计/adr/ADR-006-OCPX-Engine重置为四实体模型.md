# ADR-006：OCPX Engine 重置为四实体模型

- 状态：Partially Superseded by ADR-007；四实体清理决策仍有效
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` 当前源码与部署边界
- 替代：ADR-001 的独立数据面实现决策

## 背景

合并后的 Engine 同时容纳协议、Adapter、Controller、Service、配置和运行支持类，当前结构不适合作为后续重建基线。项目负责人决定清空该实现，只保留当前广告业务的四个实体。

## 决策

- `scx-ocpx-engine` 只保留 `OcpxAdvertiser`、`OcpxPromotionLink`、`OcpxMedia`、`OcpxMonitorTask`。
- 四个实体统一位于 `com.yzytech.scx.ocpx`，不建立内部子包。
- 实体从 `scx-ocpx-admin` 迁入 Engine，Admin 通过 Maven 依赖复用，仓库中不保留重复模型。
- 删除 Engine 的启动类、Controller、Service、Mapper、协议 DTO、Adapter、配置资源和测试。
- 本决策最初移除了 Spring Boot 启动骨架；后续 ADR-007 恢复最小启动类、可执行 JAR 和 `10000/ocpx`，但不恢复任何数据面业务实现。
- 公网 `/ocpx/` 路由在当前部署模板中返回 404。

## 后果

当前仍可通过 `scx-boot/scx-ocpx-admin` 管理四实体，但没有 Feedback、Callback、监测跳转或合作方外发能力。数据库中的流程日志表可以保留，现有管理查询也可以保留，但当前源码不会产生新的数据面流程日志。

后续数据面必须作为新的实现任务重新设计和验收；不得直接把 ADR-001、历史契约或已删除源码恢复为生产能力。
