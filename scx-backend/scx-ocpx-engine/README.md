# scx-ocpx-engine

`scx-ocpx-engine` 当前是 OCPX 四实体的最小可启动应用，包含一个 Spring Boot 启动类和以下 Java 实体：

- `OcpxAdvertiser`：上游广告主。
- `OcpxPromotionLink`：推广链接。
- `OcpxMedia`：下游媒体。
- `OcpxMonitorTask`：监测任务。

四个实体和 `ScxOcpxEngineApplication` 全部直接位于 `com.yzytech.scx.ocpx`，实体由 `scx-ocpx-admin` 复用。应用默认监听 `10000`，WebFlux context path 为 `/ocpx`，健康检查为 `GET /ocpx/actuator/health`。

本模块目前不包含业务 Controller、Service、Mapper、Adapter 或协议 DTO。启动成功只证明最小 Engine 进程可用，不代表 Feedback/Callback 等数据面能力已经实现。

打包后生成两个产物：`scx-ocpx-engine.jar` 是供 Admin 依赖的普通模型 JAR，`scx-ocpx-engine-exec.jar` 是独立可执行应用。

构建验证：

```bash
cd scx-backend
mvn -pl scx-ocpx-admin -am test
mvn -pl scx-ocpx-engine -am spring-boot:run
java -jar scx-ocpx-engine/target/scx-ocpx-engine-exec.jar
```
