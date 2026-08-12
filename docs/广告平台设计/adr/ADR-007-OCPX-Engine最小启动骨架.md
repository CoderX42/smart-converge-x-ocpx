# ADR-007：OCPX Engine 增加最小启动骨架

- 状态：Accepted
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` 启动与端口边界
- 补充：ADR-006 的四实体清理决策

## 背景

Engine 已重置为四实体模型，但项目负责人要求该模块具备独立启动能力并监听 `10000` 端口。当前任务不恢复已删除的数据面业务逻辑。

## 决策

- 增加根包启动类 `com.yzytech.scx.ocpx.ScxOcpxEngineApplication`。
- 使用 Spring WebFlux + Reactor Netty，默认监听 `10000`，context path 为 `/ocpx`。
- 普通 `scx-ocpx-engine.jar` 供 Admin 依赖；带 `exec` classifier 的 JAR 作为独立可执行应用，避免 Fat JAR 破坏四实体依赖。
- 暴露本机健康检查 `GET /ocpx/actuator/health`。
- 保持四实体不变，不增加业务 Controller、Service、Mapper、Adapter 或协议 DTO。
- `scx-boot` classpath 可以复用四实体，但组件扫描必须排除 Engine 启动类，避免管理端误装配独立应用。
- 当前宝塔部署脚本和公网 Nginx 模板不自动启用 Engine；生产部署与公网路由需要后续明确授权。

## 后果

Engine 可以打包为独立可执行 JAR 并启动，但只有健康检查，没有 Feedback、Callback 或监测跳转接口。端口可用和健康状态不能作为数据面业务验收证据。
