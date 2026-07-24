# Smart Converge X MVP-A 交付说明

> 更新时间：2026-07-24
> 交付判断：代码完成；发布 Runbook 与迁移包一致性检查已就绪，运行态验收待受控目标环境执行

## 已交付范围

- 后端保持 `scx-boot` 单体部署，新增 `scx-delivery`、`scx-tracking`、`scx-reporting` 三个业务模块，并复用 `scx-subject` 媒体对象授权。
- 主数据包含合作方、产品、媒体渠道和接入文档元数据；所有读写均绑定媒体范围。
- 追踪链接与监测任务支持创建、查询、状态迁移、归档和乐观锁；已启用配置不可原地覆盖。
- 沙箱事件接口支持 CLICK、ACTIVATE、REGISTER、PAY，具备 HMAC、时间窗、nonce 防重放、事件投递幂等、设备窗口去重、转化业务键去重和最后点击归因。
- Outbox、沙箱回传任务、重试/死信/人工重放已落地，MVP 后端只接受 `sandbox` 回传目标。
- 效果日报按冻结输入截止时间生成快照，事件明细展示接入、去重、归因状态及脱敏设备哈希。
- Element Plus 管理端已提供主数据、追踪链接、监测任务、效果日报、事件明细和回传任务页面，采用统一投放控制台视觉体系。
- 契约位于 `contracts/`：`openapi.yaml`、`asyncapi.yaml`、`event-envelope.schema.json`。

## 数据库升级

已有数据库可执行幂等初始化入口，它会按版本顺序应用当前 V1–V42；已登记迁移会比对脚本名和 SHA-256，发现历史与工作包不一致即停止：

```bash
cd scx-backend/db/smart-converge-x
MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD=本地密码 ./init-database.sh
MYSQL_HOST=127.0.0.1 MYSQL_USERNAME=root MYSQL_PASSWORD=本地密码 ./verify-database.sh
```

全新 Docker 数据卷执行 `make dev-up` 或 `docker-compose-boot.yml` 时，Compose 已挂载 V1–V42。全新非 Compose 空库可从数据库目录执行 `smart_converge_x_mvp_v42_init.sql`；V30 基础测试数据另有 `smart_converge_x_mvp_test_data.sql` 可重复入口，并由 V42 对账重叠种子的业务引用，且只能用于 sandbox/dev。已存在的数据卷不会自动重放初始化目录，应使用上面的升级入口。发布前可用 `make migration-packaging-check` 确认两个 Compose 启动包与迁移目录一致；实际发布、备份、回滚和数据核验按 [MVP-A 发布、回滚与数据核验手册](runbooks/MVP-A-发布与回滚.md) 执行。

## 已通过的验证

```bash
cd scx-backend && mvn -pl scx-boot -am test
cd scx-fronted && pnpm --filter @boot/web-ele typecheck
cd scx-fronted && pnpm exec eslint apps/web-ele/src/api/ad/index.ts apps/web-ele/src/views/delivery apps/web-ele/src/views/reporting apps/web-ele/src/views/operations apps/web-ele/src/router/access.ts
cd scx-fronted && pnpm test:unit
cd scx-fronted && pnpm build:ele
bash ./scripts/check-contracts.sh
docker-compose -f scx-backend/docker-compose.yml config --quiet
git diff --check
```

后端全量 Reactor 测试通过；前端 32 个测试文件、267 个测试通过；生产构建 11 个 Turbo 任务全部成功。前端资源加载测试在 Node 24 + Happy DOM 下仍会输出对模拟失败地址的连接错误日志，但最终测试结果为通过。

## 尚未声称完成的验收

- 未对用户当前指向的 MySQL 执行写入验证，避免误操作未知环境。
- 未启动后端连接当前 `application-dev.yml` 的外部依赖，因此尚无登录后真实 HTTP 纵向冒烟证据。
- 运行态验收应覆盖：登录、媒体范围、主数据、链接/任务启用、四类事件、重复投递、归因结果、日报刷新、沙箱派发与人工重放。

完成上述运行态步骤后，才可把 MVP 状态从“代码完成”提升为“已验收”。
