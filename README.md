# Smart Converge X

Smart Converge X 是面向新项目的单组织通用管理系统开发模板，采用 Java 17、Spring Boot、MyBatis-Plus、Vue 3，所有业务数据直接归属于当前系统。

## 工程组成

- `boot-backend`：单体后端，含认证、权限、用户、角色、菜单、部门、字典、日志、存储、在线用户和代码生成；
- `boot-fronted`：后台管理 Web；
- `.boot-admin`：新项目初始化器及配置模板；
- 通用支付模块：随后端保留，可按项目需要接入支付渠道。

## 创建一个新项目

先修改 `.boot-admin/project.template.yml`，再预览：

```bash
node .boot-admin/init-project.mjs --config ../my-project.yml --target ../my-project
```

确认后执行实际生成：

```bash
node .boot-admin/init-project.mjs --config ../my-project.yml --target ../my-project --apply
```

详细步骤见 [研发文档中心](docs/广告平台设计/README.md) 和 [从零到一开发实施手册](docs/广告平台设计/20-从零到一开发实施手册.md)。

## 后端验证

```bash
cd boot-backend
mvn -pl smart-converge-x-boot -am -DskipTests compile
```

后台管理 Web 构建入口位于 `boot-fronted`，数据库初始化脚本位于 `boot-backend/db/smart-converge-x`。
