# ADR-011：OCPX Engine 采用业务功能式分包

- 状态：Partially Superseded by ADR-012；Engine 功能式分包仍有效
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` Java 包结构
- 延续：ADR-010 的通用数据闭环和行为兼容约束

## 背景

ADR-010 按 `sgyz-ocpx` 迁入通用数据层后，源码仍沿用原项目的半分层包结构：
Controller、Manager、Service、DTO 和 Mapper 的物理位置不能准确表达 Feedback、
Callback、报表和配置索引的职责边界。项目负责人要求在不改变行为的前提下合理分包。

## 决策

- Engine 运行代码按 `feedback`、`callback`、`report`、`configuration`、`protocol`
  和 `shared` 六个功能边界聚合，各功能内部再按 `web/model/service/delivery/mapper/task`
  划分必要职责。
- `ScxOcpxEngineApplication` 保留根包；四个共享实体继续使用
  `com.yzytech.scx.ocpx.entity` FQCN。实体的 Maven 模块归属后由 ADR-012 调整为
  `scx-ocpx-core`。
- `TemplateGetProtocolAdapter` 同时承担正向回调地址构造和反向回调解密，放入
  `protocol.template`，不为分包拆类或复制逻辑。
- Mapper 分别放入 `configuration.mapper` 和 `report.mapper`，启动类精确扫描这两个包；
  MyBatis XML namespace 同步修改。
- 旧包不保留兼容壳。仓库内引用、测试、质量脚本和文档一次性切换到新 FQCN。
- Controller、Query DTO、协议适配器和上下游派发类按业务职责命名，移除从旧工程继承的
  `General` / `Union` 模糊命名；不修改 HTTP 路径、AES、签名、Redis、线程池、队列、
  扣量、落库或任务行为。
- `FeedbackIngressController` 与 `UpstreamCallbackController` 分别依赖
  `FeedbackRequestAdapter`、`CallbackRequestAdapter` 抽象；上下游 Dispatcher 依赖投递服务
  抽象，具体模板 GET 和 Callback URL 实现不反向渗透到入口。

## 后果

Engine 内部类型的 FQCN 发生变化，仓库外若直接引用这些内部类需要同步调整；公开 HTTP
契约和四个共享实体不受影响。ADR-012 后，Boot 不再依赖 Engine，也不再使用 OCPX
正则排除；管理面和数据面隔离改由 Maven 模块依赖图与边界测试保证。
