# SCX Backend

`scx-backend` 是 Smart Converge X 的 Java 通用管理后端，基于 Java 17、Spring Boot 3.x、MyBatis-Plus 和 MySQL 8.x，采用单体多模块结构。项目不包含租户隔离，保留认证授权、系统管理、代码生成、监控和通用支付能力。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `scx-auth` | 登录、验证码和令牌认证 |
| `scx-system` | 用户、角色、菜单、部门、字典和日志 |
| `scx-common` | Web、安全、数据源、Redis、RocketMQ、Excel 等公共能力 |
| `scx-pay` | 通用支付配置、交易、退款和回调能力 |
| `scx-visual/scx-generator` | 代码生成 |
| `scx-visual/scx-monitor` | Spring Boot Admin 监控 |
| `scx-boot` | 应用启动模块，启动类为 `com.yzytech.scx.ScxApplication` |

## 环境要求

- JDK 17+
- Maven 3.9+
- Docker Compose v2（用于本地 MySQL、Redis、RocketMQ）
- 可选：MySQL 客户端，用于手工初始化数据库

## 初始化数据库

数据库脚本位于 `db/smart-converge-x`，默认数据库名为 `smart_converge_x`。脚本会创建系统、权限、支付、代码生成和 XXL-JOB 所需表，并创建默认账号 `system / 123456`。

```bash
cd db/smart-converge-x
MYSQL_USERNAME=root MYSQL_PASSWORD=123456 ./init-database.sh
```

生产环境请替换密码，并通过环境变量或外部配置注入支付密钥、数据库密码等敏感信息。

## 启动依赖服务

在 `scx-backend` 目录执行：

```bash
docker compose up -d smart-converge-x-mysql smart-converge-x-redis \
  smart-converge-x-rocketmq-namesrv smart-converge-x-rocketmq-broker
```

默认端口：MySQL `3306`、Redis `6379`、RocketMQ NameServer `9876`、Broker `10911`。

## 编译与启动

```bash
mvn -pl scx-boot -am -DskipTests compile
mvn -pl scx-boot -am -DskipTests package
java -jar scx-boot/target/scx-boot.jar
```

应用默认监听 `9999`，上下文路径为 `/boot`。Swagger 地址：`http://localhost:9999/boot/swagger-ui.html`。

也可以使用 Docker 构建并启动完整后端：

```bash
docker compose up -d --build scx-boot
```

## 配置方式

默认配置在 `scx-boot/src/main/resources/application.yml`，开发和生产配置分别在 `application-dev.yml`、`application-prod.yml`。两个远程运行 profile 都只从环境变量读取 MySQL/Redis 连接；`application-local.yml` 保留为隔离本机 Compose profile。先复制模板并在 Git 外填写真实值：

```bash
cp ../.env.example ../.env.local
set -a
source ../.env.local
set +a
```

连接变量包括：

- `SCX_DATASOURCE_URL`、`SCX_DATASOURCE_USERNAME`、`SCX_DATASOURCE_PASSWORD`
- `SCX_REDIS_HOST`、`SCX_REDIS_PORT`、`SCX_REDIS_USERNAME`、`SCX_REDIS_PASSWORD`
- 可选 `SCX_REDIS_DATABASE`；未设置时 dev 使用 DB 2，prod 使用 DB 0

`make prod-secret-injection-check` 会检查 dev/prod 配置没有回退为内联地址、用户名或密码，但不会输出变量值。

不要将真实数据源地址、用户名、密码、支付证书或访问密钥提交到 Git；线上应由部署平台的密钥管理能力注入。

## 验证

```bash
mvn -pl scx-boot -am -DskipTests compile
```

接口健康检查：`GET http://localhost:9999/boot/actuator/health`。
