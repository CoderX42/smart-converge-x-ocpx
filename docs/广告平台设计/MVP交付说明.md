# Smart Converge X MVP-A 交付说明

> 更新时间：2026-07-27
>
> 交付判断：MVP 沙箱能力已实现；完整 OCPX 改造处于 R1-00，本地门禁通过，目标环境与生产验收未完成

## 已交付范围

- 后端保持 `scx-boot` 单体部署，新增 `scx-delivery`、`scx-tracking`、`scx-reporting` 三个业务模块，并复用 `scx-subject` 媒体对象授权。
- 主数据包含合作方、产品、媒体渠道和接入文档元数据；所有读写均绑定媒体范围。
- 追踪链接与监测任务支持创建、查询、状态迁移、归档和乐观锁；已启用配置不可原地覆盖。
- 沙箱事件接口支持 CLICK、ACTIVATE、REGISTER、PAY，具备 HMAC、时间窗、nonce 防重放、事件投递幂等、设备窗口去重、转化业务键去重和最后点击归因。
- Outbox、沙箱回传任务、重试/死信/人工重放已落地，MVP 后端只接受 `sandbox` 回传目标。
- 效果日报按冻结输入截止时间生成快照，事件明细展示接入、去重、归因状态及脱敏设备哈希。
- Element Plus 管理端已提供主数据、追踪链接、监测任务、效果日报、事件明细和回传任务页面，采用统一投放控制台视觉体系。
- 契约位于 `contracts/`：`openapi.yaml`、`asyncapi.yaml`、`event-envelope.schema.json`。
- R1-00 补齐媒体对象范围、渠道端点权限、数据库凭据时钟、归因回执和敏感配置环境注入。
- 部署脚本不再提供目标数据库或调度 token 默认值；Nacos 数据源/token 改为环境注入；Nginx
  管理面板 token 只通过请求头传输，拒绝缺 token 启动且不在脚本输出中展示。
- 商业、虚拟资金、库存、素材、Campaign 和媒体同步模块尚未创建，不属于“已交付范围”。

## 数据库升级

已有数据库可执行幂等初始化入口，它会按版本顺序应用当前 V1–V46；已登记迁移会比对脚本名和 SHA-256，发现历史与工作包不一致即停止：

```bash
cd scx-backend/db/smart-converge-x
MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD=本地密码 ./init-database.sh
MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD=本地密码 ./verify-database.sh
```

开发库、线上库和全新空库均使用 `init-database.sh`，当前工作树不提供版本聚合 SQL 或独立
测试数据聚合入口。项目不再提供 Docker/Compose 启动入口。发布前执行
`make migration-packaging-check`、`make prod-secret-injection-check` 和 `make db-verify`，实际
发布、备份、回滚和数据核验按
[MVP-A 发布、回滚与数据核验手册](runbooks/MVP-A-发布与回滚.md) 执行。

## 已通过的验证

```bash
cd scx-backend && mvn -pl scx-boot -am test
cd scx-fronted && pnpm --filter @boot/web-ele typecheck
cd scx-fronted && pnpm exec eslint apps/web-ele/src/api/ad/index.ts apps/web-ele/src/views/delivery apps/web-ele/src/views/reporting apps/web-ele/src/views/operations apps/web-ele/src/router/access.ts
cd scx-fronted && pnpm test:unit
cd scx-fronted && pnpm build:ele
bash ./scripts/check-contracts.sh
git diff --check
```

后端全量 Reactor 测试通过；前端 32 个测试文件、267 个测试通过；生产构建 11 个 Turbo 任务全部成功。前端资源加载测试在 Node 24 + Happy DOM 下仍会输出对模拟失败地址的连接错误日志，但最终测试结果为通过。

2026-07-27 的 R1-00 验证还包括：MySQL 8 隔离空库 V1–V46、保留数据的 V39→V46
升级、V40/V41/V43–V46 直接重复与迁移器重复、迁移校验和、菜单融合、凭据时钟修复和三类
双 Worker `SKIP LOCKED` 竞争。前端全量 `pnpm lint` 当前被未修改的
`QueueOverview.vue` 既有 Prettier 基线阻断；本任务文件的类型、ESLint、Prettier、267 个单测
和 11 项生产构建均通过。`spring-javaformat:validate` 也在进入任务模块前被未修改的
`scx-common-core` 既有格式问题阻断；后端编译、单测和打包通过。两项基线阻断均不写成全量
格式门禁通过。

## 尚未声称完成的验收

- 未对用户当前指向的 MySQL 执行写入验证，避免误操作未知环境。
- 未启动后端连接当前 `application-dev.yml` 的外部依赖，因此尚无登录后真实 HTTP 纵向冒烟证据。
- 运行态验收应覆盖：登录、媒体范围、主数据、链接/任务启用、四类事件、重复投递、归因结果、日报刷新、沙箱派发与人工重放。
- R1-00 还需覆盖：目标环境 V43–V46、多角色越权回归、历史凭据轮换、真实渠道 UAT、
  RocketMQ/Outbox 恢复和专用多实例 20k/40k 容量门禁。

完成上述运行态步骤后，才可把 MVP 状态从“代码完成”提升为“已验收”。
