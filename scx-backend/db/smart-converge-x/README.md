# Smart Converge X Database

纯净通用模板数据库初始化目录，包含后端当前保留模块运行所需的完整表结构与最小安全种子数据。

## 文件与执行顺序

1. `00schema.sql`：创建并切换 `smart_converge_x` 数据库。
2. `10smart_converge_x_core.sql`：系统、认证、权限、支付、代码生成及当前保留业务表。
3. `20xxl_job_schema.sql`：XXL-JOB 调度表结构与默认调度管理员。
4. `smart_converge_x_baseline.sql`：聚合入口脚本，按以上顺序 `SOURCE` 执行。
5. `init-database.sh`：直连 MySQL 初始化基线，并按 `scx_schema_migration` 只应用尚未执行的业务迁移。
6. `smart_converge_x_mvp_v42_init.sql`：空库一次性初始化入口，包含基线、V1–V42 和已对账的 sandbox/demo 种子。
7. `smart_converge_x_mvp_test_data.sql`：已完成结构初始化后，可重复补齐 V30 基础测试数据并执行 V42 引用对账；禁止用于生产数据环境。
8. `verify-database.sh`：只读校验基础表、主体表、默认管理员和主体权限。

## 推荐导入方式

```bash
cd scx-backend/db/smart-converge-x
MYSQL_USERNAME=root MYSQL_PASSWORD=你的密码 ./init-database.sh
```

可选环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`。脚本导入完成后会校验必需表以及默认管理员角色关系。

对全新空库，也可以直接使用 MySQL 客户端执行当前 V42 聚合入口：

```bash
mysql --commands -h数据库地址 -P3306 -u用户名 -p \
  < smart_converge_x_mvp_v42_init.sql
```

聚合入口使用 MySQL 客户端的 `SOURCE` 命令，因此必须从本目录执行。已有数据库不要执行该文件，应使用 `init-database.sh` 读取并校验迁移账本后做前向升级。

仅在 sandbox/dev 数据库补齐可识别测试数据：

```bash
mysql --commands -h数据库地址 -P3306 -u用户名 -p smart_converge_x \
  < smart_converge_x_mvp_test_data.sql
```

已有开发库或线上库请使用统一的前向迁移入口；目标连接由 `MYSQL_*` 环境变量提供：

```bash
make mvp-db-migrate
```

启动开发 profile：

```bash
cd scx-backend && mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=dev
```

仅校验已有数据库而不写入数据：

```bash
MYSQL_HOST=数据库地址 MYSQL_USERNAME=用户名 MYSQL_PASSWORD=密码 ./verify-database.sh
```

仅初始化通用系统基线时，也可以直接使用 MySQL 客户端：

```bash
mysql --commands -uroot -p < smart_converge_x_baseline.sql
```

如果客户端不支持 `SOURCE`，请按上面的文件顺序依次执行。

项目不再提供 Docker/Compose 启动入口；MySQL、Redis、RocketMQ 使用外部服务，数据库由 `init-database.sh` 前向升级。

## 默认数据

- 默认管理员：`system`
- 默认密码：`123456`；首次登录后请立即修改。
- 默认角色：`ROLE_ADMIN`，已关联模板保留模块的菜单和全部后端权限码。
- sandbox/demo 数据：`media_sandbox` 下的合作方、客户、文档、渠道、产品、链接、任务、RTA、事件、归因、回传、Outbox 和日报样例。

## 说明

- 脚本不再携带原线上数据源、支付证书、真实小程序配置等敏感种子数据。
- V25/V30 的 sandbox/demo 数据不包含真实媒体凭据或真实回调目标，但仍不得导入生产数据环境。
- 脚本不包含已剔除的垂直业务表。
- `shop_id` 是当前实体和查询使用的通用业务主体字段，可按新项目领域模型继续重命名。
- 支付配置表只初始化结构，不写入密钥或证书；启动后请在管理端按环境配置。
