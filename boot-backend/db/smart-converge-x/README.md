# Smart Converge X Database

纯净通用模板数据库初始化目录，包含后端当前保留模块运行所需的完整表结构与最小安全种子数据。

## 文件与执行顺序

1. `00schema.sql`：创建并切换 `smart_converge_x` 数据库。
2. `10smart_converge_x_core.sql`：系统、认证、权限、支付、代码生成及当前保留业务表。
3. `20xxl_job_schema.sql`：XXL-JOB 调度表结构与默认调度管理员。
4. `smart_converge_x_baseline.sql`：聚合入口脚本，按以上顺序 `SOURCE` 执行。

## 推荐导入方式

```bash
cd boot-backend/db/smart-converge-x
MYSQL_USERNAME=root MYSQL_PASSWORD=你的密码 ./init-database.sh
```

可选环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`。脚本导入完成后会校验必需表以及默认管理员角色关系。

也可以直接使用 MySQL 客户端：

```bash
mysql --commands -uroot -p < smart_converge_x_baseline.sql
```

如果客户端不支持 `SOURCE`，请按上面的文件顺序依次执行。

使用 `docker-compose.yml` 启动全套环境时，MySQL 容器会按 `00`、`10`、`20` 顺序自动导入三个脚本；聚合入口和校验脚本不会被容器重复执行。

## 默认数据

- 默认管理员：`system`
- 默认密码：`123456`；首次登录后请立即修改。
- 默认角色：`ROLE_ADMIN`，已关联模板保留模块的菜单和全部后端权限码。

## 说明

- 脚本不再携带原线上数据源、支付证书、真实小程序配置等敏感种子数据。
- 脚本不包含已剔除的垂直业务表。
- `shop_id` 是当前实体和查询使用的通用业务主体字段，可按新项目领域模型继续重命名。
- 支付配置表只初始化结构，不写入密钥或证书；启动后请在管理端按环境配置。
