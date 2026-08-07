# SCX Backend

`scx-backend` 是 Smart Converge X 的 Java 广告服务管理后端，基于 Java 17、Spring Boot 3.x、MyBatis-Plus 和 MySQL 8.x，采用单体多模块结构。运行产品面收敛为认证授权、账号/角色管理与精简 ADX 业务闭环。

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `scx-auth` | 登录、验证码和令牌认证 |
| `scx-system` | 用户、角色、菜单、部门、字典和日志 |
| `scx-common` | Web、安全、数据源、Redis、RocketMQ、Excel 等公共能力 |
| `scx-adx` | 渠道、广告主及其登录账号、应用链接、监测链接、扣量、充值/点击账务和时段报表 |
| `scx-ocpx-contract` / `scx-ocpx-runtime` | 双向协议、适配器 SPI、可靠投递和 CPC 点击计费 |
| `scx-ocpx-engine` | 独立 OCPX 引擎端，端口 10000、上下文 `/ocpx` |
| `scx-visual/scx-generator` | 代码生成 |
| `scx-visual/scx-monitor` | Spring Boot Admin 监控 |
| `scx-boot` | 应用启动模块，启动类为 `com.yzytech.scx.ScxApplication` |

## 环境要求

- JDK 17+
- Maven 3.9+
- 可访问的 MySQL 8.x、Redis 7.x、RocketMQ 5.x 服务
- MySQL 客户端，用于初始化/升级数据库

## 初始化数据库

数据库脚本位于 `db/smart-converge-x`，默认数据库名为 `smart_converge_x`。V68 会创建初始 ADX 表和菜单；V72 会移除已退出的汽车线索表/权限并启用充值与 CPC 账务。历史迁移保持不可变。应用 V72 前如需留存旧线索数据必须先备份；旧广告域和支付表仍须在预览和单独授权后执行 `retirement/retire-superseded-ad-domains.sql` 退役。

```bash
cd db/smart-converge-x
MYSQL_HOST=<mysql-host> MYSQL_PORT=3306 \
MYSQL_USERNAME=<mysql-user> MYSQL_PASSWORD=<mysql-password> \
./init-database.sh
```

生产环境请替换默认管理员密码。

## 编译与启动

```bash
mvn -pl scx-boot -am -DskipTests compile
mvn -pl scx-boot -am -DskipTests package
java -jar scx-boot/target/scx-boot.jar
```

应用默认监听 `9999`，上下文路径为 `/boot`。Swagger 地址：`http://localhost:9999/boot/swagger-ui.html`。

## 配置方式

默认配置在 `scx-boot/src/main/resources/application.yml`，开发和生产配置分别在 `application-dev.yml`、`application-prod.yml`。运行配置按项目规范直接写入对应 profile 文件，不使用 `${SCX_*}`、`${ROCKETMQ_*}` 等环境变量占位，也不依赖 `/etc/scx/scx.env`。配置变更后必须重新打包并重启；不要在代码评审、日志或文档中暴露真实密钥。

## 验证

```bash
mvn -pl scx-boot -am -DskipTests compile
```

接口健康检查：`GET http://localhost:9999/boot/actuator/health`。
