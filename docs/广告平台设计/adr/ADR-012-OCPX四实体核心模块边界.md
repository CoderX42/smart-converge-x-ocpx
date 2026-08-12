# ADR-012：OCPX 四实体抽取为独立 Core 模块

- 状态：Accepted
- 日期：2026-08-12
- 范围：`scx-ocpx-core`、`scx-ocpx-admin`、`scx-ocpx-engine` 与 `scx-boot`
- 补充：ADR-006 的四实体唯一模型决策
- 取代：ADR-007、ADR-009、ADR-011 中依靠 Boot 组件扫描排除 Engine 的边界

## 背景

四个共享实体原本物理位于 `scx-ocpx-engine`。`scx-ocpx-admin` 为复用实体直接依赖
Engine，进而使 `scx-boot` 的运行 classpath 包含 Engine 启动类、Controller、定时任务、
Redis 组件和数据面服务。Boot 只能使用包名正则排除 Engine 运行组件，模块依赖方向与
运行时边界不一致，且正则会随 Engine 分包变化而持续维护。

项目负责人决定移除 Boot 正则，并将共享实体抽到独立核心模块。

## 决策

- 新增普通 Jar 模块 `scx-ocpx-core`，承载 `OcpxAdvertiser`、`OcpxPromotionLink`、
  `OcpxMedia`、`OcpxMonitorTask` 四个 MyBatis-Plus 实体，以及管理面通知 Engine 刷新配置
  所需的最小常量契约。
- 四实体继续使用 `com.yzytech.scx.ocpx.entity` 包名，数据库映射、Java FQCN 和业务接口
  不变。
- `scx-ocpx-admin` 与 `scx-ocpx-engine` 分别依赖 `scx-ocpx-core`；Admin 不再依赖
  Engine。
- `scx-boot` 只依赖 Admin，并恢复标准 `@SpringBootApplication` 扫描；不添加 OCPX
  正则或自定义组件扫描排除规则。
- Core 不包含 Controller、Service、Mapper、Spring Boot 启动类、配置文件或运行时
  基础设施，只依赖实体注解所需的 `mybatis-plus-core`。
- 质量门同时验证实体唯一归属、Admin/Engine 到 Core 的依赖方向，以及 Boot 不含自定义
  组件扫描排除。

## 后果

Boot 的 classpath 不再包含 `ScxOcpxEngineApplication` 和 Engine 运行组件，管理面与
数据面的隔离由 Maven 依赖图保证，不再依赖包名正则。Engine 的普通 Jar 仍是独立模块
产物，但不再作为 Admin 的共享模型依赖。四实体没有数据库或序列化兼容性变化。
