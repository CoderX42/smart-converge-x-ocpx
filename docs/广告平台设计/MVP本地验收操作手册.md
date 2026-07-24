# SCX 开发环境启动与验收操作手册

## 适用范围

本手册用于开发环境与线上环境共用外部 MySQL、Redis、RocketMQ 的模式。项目不依赖 Docker/Compose 启动，也不提供本机数据库卷。

## 1. 前置条件

- JDK 17 或更高版本
- Maven 3.9+
- Node.js 20 或更高版本、pnpm 10
- 已分配且可访问的 MySQL 8.x、Redis 7.x、RocketMQ 5.x
- 已在 Git 外部准备连接变量

## 2. 准备连接变量

在仓库根目录创建 Git 忽略文件：

```bash
cp .env.example .env.local
set -a
source .env.local
set +a
```

开发和线上应使用同一组 `SCX_DATASOURCE_*`、`SCX_REDIS_*` 连接变量；若共用 Redis 逻辑库，显式设置相同的 `SCX_REDIS_DATABASE`。

## 3. 数据库初始化或升级

已有数据库只执行前向迁移，不要导入空库聚合文件：

```bash
export MYSQL_HOST=<mysql-host>
export MYSQL_PORT=3306
export MYSQL_USERNAME=<mysql-user>
export MYSQL_PASSWORD=<mysql-password>

make db-migrate
make db-verify
```

执行前必须确认目标库、备份和迁移历史。sandbox 测试数据只能写入明确的开发库，不能写入生产业务库。

## 4. 启动后端

```bash
cd scx-backend
mvn -pl scx-boot -am spring-boot:run -Dspring-boot.run.profiles=dev
```

默认地址：`http://127.0.0.1:9999/boot`。

```bash
curl http://127.0.0.1:9999/boot/actuator/health
```

## 5. 启动前端

```bash
cd scx-fronted
corepack enable
pnpm install --frozen-lockfile
pnpm dev:ele
```

根据终端输出访问前端地址。开发服务器的 `/api` 代理应指向后端 `9999` 端口。

## 6. 质量门禁

```bash
make backend-test
make contract-check
make prod-secret-injection-check
cd scx-fronted
pnpm check:type
pnpm lint
pnpm test:unit
pnpm build:ele
```

## 7. 停止服务

后端和前端分别在终端按 `Ctrl+C` 停止。MySQL、Redis、RocketMQ 由其外部服务平台或运维系统管理，禁止在本项目中执行卷清理、数据库重置或线上服务删除。
