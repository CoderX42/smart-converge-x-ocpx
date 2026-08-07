# scx-ocpx-engine

`scx-ocpx-engine` 是 SCX OCPX 独立引擎端，也是 `scx-ocpx-runtime` 的唯一生产启动模块。它采用 Spring WebFlux + Reactor Netty，与管理端 `scx-boot` 分进程部署和扩容。

## 运行边界

| 项目 | 值 |
| --- | --- |
| 应用名 | `scx-ocpx-engine` |
| 默认端口 | `10000` |
| 上下文路径 | `/ocpx` |
| Feedback | `POST /ocpx/open-api/ocpx/v1/channels/{channelCode}/feedback` |
| Callback | `GET /ocpx/open-api/ocpx/v1/upstreams/{upstreamCode}/callback` |
| 健康检查 | `GET /ocpx/actuator/health` |
| Prometheus | `GET /ocpx/actuator/prometheus`，仅内部监控网络使用 |

公网只应通过 Nginx 暴露 `/ocpx/open-api/`，不得暴露 `/ocpx/actuator/`。请求原始路径参与 HMAC 签名，Nginx 必须保留完整 `/ocpx` 路径。

## 配置

- 通用配置：`src/main/resources/application.yml`。
- 开发配置：`src/main/resources/application-dev.yml`。
- 生产配置：`src/main/resources/application-prod.yml`。
- 应用运行配置采用配置文件字面量，禁止新增 `${SCX_*}` 等运行时占位符或 `/etc/scx/scx.env` 依赖。
- 修改配置后必须重新打包并重启；只修改服务器源码不会改变现有 JAR。
- 上下游真实外发默认关闭。开启 `upstream-delivery-enabled` 或 `downstream-delivery-enabled` 需要单独的 UAT 和上线授权。

## 本地构建与启动

```bash
make ocpx-engine-test
make ocpx-engine-build
make ocpx-engine-run-dev
```

也可以在 `scx-backend` 中直接执行：

```bash
mvn -pl scx-ocpx-engine -am test
mvn -pl scx-ocpx-engine -am spring-boot:run -Dspring-boot.run.profiles=dev
```

## 宝塔部署

生产构建产物为：

```text
/opt/smart-converge-x/build/backend/scx-ocpx-engine.jar
```

在生产服务器构建：

```bash
cd /opt/smart-converge-x
bash scripts/bt-deploy/01-deploy.sh --no-pull engine
```

构建脚本不会管理 Java 进程。构建完成后在宝塔“Java 项目管理器”中重启 `SCX-OCPX引擎端`，启动参数必须包含：

```text
--spring.profiles.active=prod
```

重启后在生产服务器执行：

```bash
bash scripts/bt-deploy/02-manage.sh engine-health
```

源码、JAR、健康检查或本地测试通过都不等于生产放行。目标数据库迁移、Redis 持久化与副本、真实上下游 UAT、故障恢复和容量验收仍是独立门禁。
