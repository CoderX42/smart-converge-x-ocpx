# SCX Fronted

`scx-fronted` 是 Smart Converge X 的后台管理 Web，基于 Vue 3、TypeScript、Vite、pnpm workspace 和 Turborepo。项目仅保留管理后台，不包含门户、移动端或小程序工程。

## 应用与包

- `apps/web-ele`：Element Plus 管理后台，默认构建目标
- `apps/web-antd`：Ant Design Vue 管理后台变体
- `apps/web-naive`：Naive UI 管理后台变体
- `packages/@core`：基础布局、权限、菜单和 UI 组件
- `packages/effects`：请求、布局、插件和业务交互能力
- `packages/utils`、`packages/types`、`packages/constants`：通用工具、类型和常量

## 环境要求

- Node.js 20+
- pnpm 10.12.4（仓库通过 `packageManager` 固定版本）
- 后端默认地址：`http://localhost:9999/boot`

## 安装依赖

```bash
cd scx-fronted
corepack enable
pnpm install --frozen-lockfile
```

## 本地开发

先启动 `scx-backend`，再选择一个管理后台：

```bash
pnpm dev:ele       # Element Plus，默认推荐
pnpm dev:antd      # Ant Design Vue
pnpm dev:naive     # Naive UI
```

开发服务器启动后，按终端输出地址访问。接口代理配置位于对应应用目录的 Vite 配置文件中。

## 构建与预览

```bash
pnpm build:ele
pnpm build:antd
pnpm build:naive
pnpm preview
```

Docker 静态资源构建：

```bash
pnpm build:docker
```

输出目录为 `scripts/deploy/dist`，部署配置位于 `scripts/deploy`，服务名和容器名统一为 `scx-fronted`。

## 代码质量与测试

```bash
pnpm check:type
pnpm lint
pnpm test:unit
pnpm check
```

清理缓存和构建产物：

```bash
pnpm clean
```

## 登录与联调

默认数据库账号为 `system`，密码为 `123456`。首次登录后应立即修改密码。后台接口上下文路径为 `/boot`；若后端地址或端口发生变化，请同步调整应用的 API 代理配置。

## 目录命名约定

工程目录和后端模块使用 `scx-*` 前缀。前端内部包名仍使用现有 `@boot/*`、`@boot-core/*` workspace 命名，以避免破坏已有组件引用；这不影响对外工程目录的 `scx-*` 统一命名。
