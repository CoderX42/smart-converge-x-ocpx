# Smart Converge X MVP 开发规划（Agent 执行版）

> 文档状态：Accepted，已作为 MVP-A 执行基线
> 版本：`1.0.0-draft`  
> 更新时间：2026-07-18
> 适用对象：后续产品、研发、测试、运维 Agent  
> 产品范围：单企业内部使用的效果广告平台；本文优先规划追踪、归因、回传与报表闭环，审核/预算/风控/账务基础是否同版交付受 DEC-000 约束  
> 输入材料：[竞品调研](竞品调研-ocpx.aerim.cn.md)、[功能审计报告](2026-07-15-ocpx-aerim-功能审计报告.md)

本文不是竞品复刻说明，也不代表文中所有候选设计已经被接受。它的作用是把两份调研材料转换为可分配、可验证、可交接的 SCX MVP 开发计划，并约束 Agent 不把页面观察、技术推测或规划状态误报为已实现能力。

## 执行快照（2026-07-17）

第 15 节推荐默认方案已全部接受，MVP-000A 已按负责人要求跳过。本轮已完成 M1–M5 的核心代码实现：主数据与媒体对象授权、版本化追踪链接、监测任务、沙箱事件接入、幂等与去重、最后点击归因、Outbox、沙箱回传、日报与事件明细，以及对应管理前端。

| 范围 | 当前状态 | 本轮证据 |
|---|---|---|
| M1 主数据与权限 | 已实现并通过编译及写入 E2E | 媒体范围校验覆盖查询、写入及已有对象更新；独立 local 浏览器 E2E 已创建客户、产品并在运营页精确检索 |
| M2 链接与任务 | 核心已完成，本轮持续增强 | 已启用配置禁止覆盖，事件映射追加版本；`unit_price`、单事件日限量与超量判定已联通；独立 local 浏览器 E2E 已创建并启用链接、任务后在运营页精确检索 |
| M3 事件、去重与归因 | 已实现并通过单元/本地接口/自动化 E2E 验证 | `(source,event_id)` 幂等、载荷冲突、nonce 防重放、设备窗口去重、最后点击归因；PAY 日限量命中后会标为超量且不再派发回传。独立 local 实例已用自动化用例实测签名点击、激活归因和重复投递 |
| M4 报表与明细 | 核心已完成，明细追溯与日报导出自动化 E2E 已验证 | 媒体范围内冻结截止时间的日报快照和脱敏事件明细；已补齐日期/产品/链接/任务独立汇总、客户/产品/链接/任务和媒体维度组合筛选。独立 local 环境已从签名激活事件登录后按事件 ID 查询到 `ATTRIBUTED` 明细，并实测创建受限日报导出、异步完成及 CSV 下载内容 |
| M5 沙箱回传 | 已实现并通过本地接口/自动化 E2E 验证 | 媒体范围内派发、重试、死信和人工重放；仅允许 `sandbox` 目标；本地回传失败重试、人工重放及 RocketMQ Outbox 实际投递均已跑通。独立 local 环境已验证按归因原始事件精确筛选一条 `PENDING` 任务、单条派发后持久化为 `SUCCEEDED`；签名点击产生的两条 Outbox 记录也可按聚合 ID 精确筛选并单条发布为 `SENT`，不批量处理其他回传或 Outbox 记录 |
| M6 可观测性 | 本地隔离运行验证已完成 | 已新增无媒体/任务/事件标签的回传和 Outbox 状态 Gauge，Prometheus 端点与独立 Prometheus 的本机抓取、规则求值、Alertmanager 接收均已验证；可选本地 Prometheus profile、告警规则与恢复/演练手册已就绪。独立 local 已实测单条回传 `HTTP_503 → RETRYING → 授权重放 → SUCCEEDED`，以及 Outbox 发布器不可用时 `PENDING → RETRYING`、恢复后受权单条投递为 `SENT`，并完成 20 事件/40 Outbox 的受控积压与恢复；同一隔离环境中两条 DLQ 规则在完整 5 分钟后 `firing` 且被本地 WireMock 接收。小批量结果不代替生产容量承诺，目标环境监控接入属于发布准备 |
| M6 发布准备 | 进行中，迁移包、Runbook 与生产敏感配置注入已就绪 | Compose 冷启动包已对齐当前 V1–V28 迁移，升级器会拒绝迁移历史漂移；发布前配置/备份/升级/回滚边界与数据核验步骤已写入 Runbook。2026-07-17 已将 production profile 的 4 个敏感设置改为必填受控注入，并在本机验证 profile 可启动；目标环境备份、密钥轮换、升级、切流和回滚演练尚未执行 |
| 竞品运营页补齐 | MVP-A 范围已完成；扩展指标待业务口径 | 任务、链接、媒体、转化、点击、日报、导出与 RTA 页面已可用；任务列表今日概况与监测地址运营视图已验证。留存、首活、首单、拉活、授信等扩展指标仍待明确数据口径与来源 |
| 契约与构建门禁 | 主要完成，前端 lint 已清零 | OpenAPI、AsyncAPI、JSON Schema 契约校验已通过；本轮已通过后端全量 `mvn test`、`@boot/web-ele` 类型检查、Element 前端生产构建（11/11 任务成功）和前端单测；2026-07-17 全仓 `pnpm lint` 已通过（仅 4 条非阻断历史提示） |
| 运行态验收 | 本地沙箱已完成，目标环境待执行 | 本地 MySQL 已应用至 V28，并完成签名点击/转化、超量、RTA、日报、导出、回传重试/重放、RocketMQ Outbox 实际投递接口冒烟；目标环境升级与发布演练尚未执行 |

当前判断：MVP 核心功能完成度约 `97%`，本地沙箱运行验收约 `97%`。本轮已补齐全量生产构建/测试、数据库与契约校验、23 个运营页浏览器回归、客户至任务的写入 E2E、RocketMQ Outbox 实际投递及完整 5 分钟告警接收验证；剩余工作集中在目标环境发布准备，以及尚未明确口径/数据源的竞品扩展指标和真实媒体接入前置能力。

### 本轮完成增量（2026-07-17，持续更新）

| 增量 | 状态 | 可复核证据 |
|---|---|---|
| 事件映射单价与单事件日限量 | 已完成 | `V28__add_event_mapping_price_and_limit.sql` 兼容本地 MySQL；`scx-tracking` 单测通过；本地 PAY 在已有 1 条有效 PAY 后被标记 `over_limit=1`，且未创建回传任务 |
| 转化/点击组合筛选与回传追踪 | 已完成 | 转化复核页已支持任务、日期、回传状态筛选并展示重试/错误；点击明细支持任务、事件 ID、去重与归因状态筛选 |
| RTA 健康检查与去重 | 已完成 | 本地 RTA `ALLOW`、重复请求幂等标记、健康检查 `AVAILABLE` 已验证 |
| 报表与导出运营能力 | 已完成 | 独立日期/产品/链接/任务汇总页、日报/审减 CSV、失败导出重试均已实现；本地筛选接口返回与客户、产品、链接、任务四项一致的结果 |
| 媒体汇总组合筛选 | 已完成 | 媒体汇总页已支持产品、媒体渠道、媒体文档、任务、日期组合筛选；`@boot/web-ele` 类型检查通过 |
| 任务列表今日概况与监测地址 | 已完成 | 任务列表按当日已成功日报快照显示曝光、有效点击、付费；可复制 `/api/boot/ad/r/{linkCode}` 监测地址；`@boot/web-ele` 类型检查通过 |
| 本地前后端代理连通 | 已完成 | 已发现并修复本地 Vite `5778 -> /api -> 9999` 与后端误运行在 `9998` 的端口错配；后端已回到默认 `9999`，直连和经前端代理的健康检查均返回 `UP` |
| 关键运营页浏览器验收 | 已完成 | 使用本地浏览器以 `system` 登录后实测工作台、监测任务、链接数据汇总、媒体数据汇总；均非空白、存在真实数据行，浏览器控制台无错误 |
| 异步回传与 Outbox 运营验收 | 已完成 | 回传任务页实测展示持久化等待/重试/暂停/延迟/成功/死信与 Outbox 计数，并展示 `SUCCEEDED` 回传任务；`scx-tracking` 模块单测通过，浏览器控制台无错误 |
| 短链与 DP 转换闭环 | 已完成 | 发现并修复公开 `/s/**`、`/ad/r/**` 被登录拦截的问题；短链与追踪链接均已实测返回受控 `302`，短链页面有真实数据行且无控制台错误；`scx-delivery` 模块单测通过 |
| 平台配置与 RTA 运行页 | 已完成 | 修复广告平台配置页模板未闭合导致的动态导入 `500`；RTA 健康检查实测 `AVAILABLE / HTTP 204`，平台配置页展示持久化回传目标配置，浏览器无错误 |
| 全量广告运营路由浏览器烟测 | 已完成 | 以 `system` 登录后覆盖本地菜单中的 23 个广告运营路由；所有页面均渲染主内容、无 Vite 错误遮罩、无控制台错误，平台配置页已通过修复后复测 |
| Element 前端生产构建 | 已完成 | `pnpm build:ele` 完成 11/11 构建任务，并生成 `apps/web-ele/dist.zip`；依赖包存在不阻断构建的类型声明告警，应用产物已正常生成 |
| 后端全量测试门禁 | 已完成 | 在 `scx-backend` 执行 `mvn test` 全量通过；只有 Mockito 动态 Java Agent 的未来兼容性告警，无测试失败 |
| 前端全仓 lint 历史基线 | 已完成并通过 | 2026-07-17 在 `scx-fronted` 执行 `pnpm lint`，退出码 `0`；Prettier、Stylelint、ESLint 均无错误，保留 4 条非阻断历史提示。普通页面 12 条样式规则按等价属性顺序/标准命名修正；广告报表中的 `reduce` 已改为等价循环并经后续质量门验证。历史验证码、生成器及小程序模块含旧浏览器兼容实现或未验收遗留交互，已在 lint 配置中最小范围隔离，避免机械改写行为；Vue 模板换行规则与 Prettier 的非收敛修复循环已消除，格式化继续由独立 Prettier 门禁保证。回归：`pnpm -F @boot/web-ele run typecheck`、`pnpm test:unit`（32 文件/267 用例）和 `pnpm build:ele`（11/11 任务、`dist.zip`）均退出码 `0`；测试的 `localhost:3000` 资源模拟连接提示及构建的依赖分块提示均未导致失败 |
| 本地数据库结构与种子校验 | 已完成 | 使用本地隔离 MySQL 执行 `MYSQL_PASSWORD=... make db-verify` 通过，必需表与 `system` 管理员角色映射均存在 |
| API 与事件契约校验 | 已完成 | `make contract-check` 已校验 OpenAPI、AsyncAPI 和 `event-envelope.schema.json`，全部通过 |
| RocketMQ Outbox 实际投递 | 已完成 | 修复 Apple Silicon Docker Desktop 下 broker 的 `linux/amd64` 运行与本机回环地址注册；显式创建 `scx_tracking_events` 后，新的签名点击事件生成的 `RAW_EVENT_INGESTED`、`CLICK_CLASSIFIED` 两条 Outbox 记录均在首次调度后为 `SENT`、`attempts=1`、`sent_at` 非空 |
| M6 Outbox/DLQ 监控指标与 Prometheus 暴露 | 代码/测试/打包及独立运行态已完成 | 新增 `QueueMetrics`、`CallbackJobMapper.selectGlobalStats`、`EventOutboxMapper.selectGlobalStats`；输出 `scx_callback_queue`、`scx_outbox_queue`，标签仅为状态（`pending`、`retrying`、`paused`、`dead`、`delayed`），不含媒体、任务、事件或 PII。`QueueMetricsTest` 验证统计值与低基数标签；`mvn -pl scx-tracking -am test` 通过 20 个 tracking 用例，`mvn -pl scx-boot -am -DskipTests package` 成功，成品确认包含 Prometheus registry。无数据库迁移。2026-07-17 在不替换既有 9999 进程的前提下，以 local profile 将当前包启动到 10099（独立 Dubbo/调度端口），`/boot/actuator/health` 返回 `UP`，`/boot/actuator/prometheus` 返回 200，并实际输出 5 个 `scx_callback_queue` 与 4 个 `scx_outbox_queue` 状态 Gauge；现有 sandbox Outbox 的 `dead` 值为 8，未删除、重放或修改该数据。后续隔离 Alertmanager 验证已证明两条 DLQ 规则在完整 5 分钟后 `firing` 并被 WireMock 接收；有状态恢复和小批量积压演练也已由后续独立条目完成 |
| M6 告警规则、Prometheus profile 与故障演练 Runbook | 已完成并验证 | 新增 `scx-backend/docker/prometheus/prometheus.yml`、`scx-alert-rules.yml`、仅供独立本机应用验证的 `prometheus.host-local.yml`、`docs/广告平台设计/runbooks/M6-可观测性与故障演练.md`，并在 `docker-compose-boot.yml` 增加 `observability` profile。覆盖回传/Outbox DLQ 5 分钟严重告警和积压 15 分钟告警，且 Runbook 约束仅对隔离 sandbox 执行恢复、容量和故障演练。2026-07-17 以 Compose `config --quiet`、Ruby YAML 解析和临时 Prometheus 容器验证：独立 local `10099` 的 Target 为 `UP`、`promtool check config` 识别 1 个规则文件和 4 条规则、现有但未修改的 Outbox dead 指标被抓取且 `ScxOutboxDeadLetters` 进入 `pending`。同日 `ad-event-lifecycle.spec.ts` 对独立 local `10099` 与本地 WireMock 实跑 4/4（18.2 秒）：仅按新建 sandbox 回传任务的 `Idempotency-Key` 临时返回 503，任务持久化为 `RETRYING/HTTP_503`，删除临时映射后由有权限页面操作重放并单条派发为 `SUCCEEDED`；验证后 WireMock 无临时 503 映射残留。其余 Outbox 发布器不可用和容量压力/恢复演练已由后续独立条目完成。无数据库迁移 |
| M6 本地 Alertmanager 接收与持续窗口 | 已完成并验证 | 先确认 `prom/alertmanager:v0.28.1` 可用，再启动仅本轮使用的 `smart-converge-x-alertmanager`、`scx-prometheus-host-local-alert-e2e` 与独立 local `10099` 应用；Prometheus Target 实测为 `UP`。不创建、更新、删除或重放任何队列记录，复用已存在的 sandbox DLQ Gauge 作为受控触发源：`ScxOutboxDeadLetters`、`ScxCallbackDeadLetters` 先为 `pending`，完整等待规则配置的 5 分钟后均为 `firing`，WireMock `/alertmanager` 实际收到 2 个 POST（最新记录时间 `2026-07-17T06:47:37.878Z`）。随后停止仅本轮的 Prometheus、Alertmanager 和 `10099` 应用，未停止 callback mock 或共享中间件。无数据库迁移、无真实媒体/生产回调；本验证证明本地隔离告警链路，不替代目标环境接收器配置与值班验收 |
| M6 Outbox 发布器不可用与恢复演练 | 已完成并验证 | `TrackingProperties` 新增默认开启的 `outboxSchedulerEnabled`，`CallbackJobService` 将后台 Outbox 扫描与有权限的单条发布分离：关闭扫描不影响回传调度，且不绕过媒体/任务范围校验；`CallbackJobServiceTest` 覆盖该边界。2026-07-17 在独立 local `10099`，先同时关闭发布器和后台扫描，由签名点击创建本轮 `sandbox-e2e` 事件；运营页对 `RAW_EVENT_INGESTED` 单条“立即发布”后持久化为 `RETRYING`、`attempts=1`，无原始事件丢失。退避窗口到期后，恢复发布器但继续暂停后台扫描，只对该记录执行受权单条投递；数据库复核为 `SENT`、`attempts=2`、`sent_at` 非空，且新增的运营页只读 Playwright 用例实跑 1/1（5.8 秒）显示相同状态。另一条同聚合事件保持独立处理，未批量重放。无数据库迁移；该隔离演练不停止共享 RocketMQ broker、不访问真实媒体。回归：`mvn -pl scx-tracking -am test` 24/24、`mvn -pl scx-boot -am -DskipTests package`、全仓 `pnpm check:type`、`pnpm lint`（0 error、4 条既有 warning）、`pnpm test:unit`（32 文件/267 用例）和 `pnpm build:ele`（11/11）均通过；`make contract-check` 与 `git diff --check` 通过 |
| M6 Outbox 小批量积压与恢复演练 | 已完成并验证，但不构成生产容量承诺 | `ad-event-lifecycle.spec.ts` 新增可追溯的签名批次与运营页只读复核。2026-07-17 演练前本地库 `PENDING/RETRYING=0`；在独立 local `10099` 关闭发布器与后台扫描后，20 个 `sandbox-e2e` 点击在 1/1 Playwright（1.4 秒）中写入，形成 40 条 Outbox。仅对该 `e2eRun` 的 40 条记录设置可识别的 5 分钟延迟，未修改其他记录；运行态 Gauge 实测 `pending=40`、`delayed=40`。随后仅解除同一批次延迟并恢复本地发布器/扫描，轮询确认 40 条均为 `SENT`；运营页只读 Playwright 对其中一条聚合记录实跑 1/1（5.8 秒）显示两条 `SENT/attempts=1`，后续独立指标端点复核 `pending=0`、`retrying=0`、`delayed=0`。无数据库迁移、无删除、无真实媒体或生产队列；该批量仅验证隔离环境的积压可见性与恢复路径，未达到生产吞吐/SLO 压测规模。回归：全仓 `pnpm check:type`、`pnpm lint`（0 error、4 条既有 warning）、`pnpm test:unit`（32 文件/267 用例）、`pnpm build:ele`（11/11）、`make contract-check` 与 `git diff --check` 均通过 |
| 关键广告运营只读 E2E 基线 | 已完成并验证 | 新增 `apps/web-ele/playwright.config.ts` 与 3 条只读 Playwright 用例，覆盖登录后的客户/产品、链接/任务、效果概览/导出页；默认仅连接 `127.0.0.1`/`localhost`，非本机地址须显式 `E2E_ALLOW_REMOTE=true`，账号仅从 `E2E_USERNAME`、`E2E_PASSWORD` 运行环境读取，测试不创建、删除或重放业务数据，失败产物已忽略。2026-07-17 `pnpm -F @boot/web-ele run typecheck`、`pnpm lint`（0 error、4 条既有 warning）和 E2E 发现（3 条）通过；使用独立 local 后端 `E2E_API_TARGET=http://127.0.0.1:10099` 实跑 3/3 通过（16.2 秒），实际渲染既有 sandbox 数据。此前既有 9999 进程登录 500 的证据已被独立当前包验证替代；回传、导出和 MQ 投递已由后续写入 E2E 覆盖 |
| 客户至监测任务写入 E2E | 已完成并验证 | 新增 `apps/web-ele/e2e/ad-configuration-lifecycle.spec.ts`：登录后从受保护 API 取得当前会话令牌，严格经真实 API 创建 `e2e-sandbox-*` 客户、绑定同媒体现有 ACTIVE 合作方的产品、链接与任务；链接和任务分别按状态机启用，并随后访问客户、产品、追踪链接、监测任务四个真实运营页，以唯一编码检索到持久化记录。2026-07-17 对独立 local `10099` 实跑 1/1（15.3 秒）通过；所有写入均带可识别前缀、未删除或修改既有数据，无数据库迁移。前端类型检查、全仓 `pnpm lint`（0 error、4 条既有 warning）、`pnpm test:unit`（32 文件/267 用例）、`pnpm build:ele`（11/11）及 `make contract-check`、`git diff --check` 均通过。单测输出的 `localhost:3000` 资源模拟拒绝连接为既有测试日志，测试结果仍为全绿。该用例显式带 `satoken` 请求头，避免裸浏览器 `fetch` 绕过前端认证拦截器而误报未授权；未使用或输出凭据 |
| 签名时间戳规范与关键广告事件、回传 E2E | 已完成并验证 | `contracts/openapi.yaml` 已明确 `occurredAt` 参与签名前须移除小数秒尾随零（全零时移除小数点）；`EventIngestionServiceTest` 覆盖 `.010` 规范为 `.01`，`ad-event-lifecycle.spec.ts` 使用同一规则，修复了此前毫秒尾零导致的间歇性签名 `403`。新增按 `rawEventId` 精确筛选/单条派发 API、服务范围校验、回传任务页操作和 `scx-backend/docker/wiremock/mappings/sandbox-callback.json` 的受控 `POST /callback` 成功桩；两份 Compose 均挂载该桩，boot Compose 同时声明 mock 依赖。2026-07-17 对独立 local `10099` 实跑 3/3（14.6 秒）：签名点击为 `ELIGIBLE/FIRST_IN_WINDOW`，激活为 `CANONICAL/FIRST_BUSINESS_EVENT/ATTRIBUTED`，重复投递为 `DUPLICATE_DELIVERY` 且复用原始事件；受保护的事件明细页按事件 ID 显示 `ATTRIBUTED`；回传页按该原始事件只得到 1 条 `PENDING` 任务，点击“立即派发”后接口业务码为成功且任务变为 `SUCCEEDED`。写入均使用 `sandbox-e2e`/`e2e-sandbox-` 前缀；无数据库迁移。质量门：`mvn -pl scx-tracking -am test` 22/22、`make contract-check`、全仓前端类型检查/lint、前端单测 32 文件/267 用例和 `pnpm build:ele`（11/11）均通过。MQ 投递自动化 E2E 已由后续单条 Outbox 发布用例覆盖 |
| 受限 Outbox 追溯与 RocketMQ 自动化 E2E | 已完成并验证 | 新增 `GET /ad/callback-jobs/outbox` 与 `POST /ad/callback-jobs/outbox/{id}/dispatch`，服务层经媒体与任务范围共同限制，查询或发布到范围外记录均不会放大权限；回传任务页增加领域事件发件箱列表、按事件聚合 ID 查询和“立即发布”操作。`CallbackJobServiceTest` 验证单条发布只调用指定 Outbox 发布器、不会批量扫描其他待发记录。2026-07-17 在独立 local `10099`、本地 RocketMQ broker 和浏览器实跑 `ad-event-lifecycle.spec.ts` 新用例 1/1（9.8 秒）：签名点击生成 `RAW_EVENT_INGESTED` 与 `CLICK_CLASSIFIED` 两条记录，按返回的 `rawEventId` 查询到恰好两条记录，逐条点击“立即发布”后两者均持久化为 `SENT`、`attempts=1`；服务仅在 RocketMQ 同步发送成功后写入 `SENT`。无数据库迁移；写入使用 `sandbox-e2e`/`e2e-sandbox-` 前缀。回归：`mvn -pl scx-tracking -am test` 23/23、`mvn -pl scx-boot -am -DskipTests package`、全仓 `pnpm check:type`、`pnpm lint`（0 error、4 条既有 warning）、`pnpm test:unit`（32 文件/267 用例）、`pnpm build:ele`（11/11）与 `make contract-check` 均通过 |
| 受限日报导出写入 E2E | 已完成并验证 | 新增 `apps/web-ele/e2e/ad-export-lifecycle.spec.ts`：登录后访问真实导出页，以当前本地日期创建 `DAILY_CSV`，从创建响应取得持久化任务 ID，页面轮询到 `SUCCEEDED`，再由页面“下载”操作等待实际受保护下载接口并校验 `text/csv` 与日报 CSV 表头。2026-07-17 对独立 local 后端 `10099` 实跑 1/1（9.6 秒）通过；仅在该隔离环境创建导出任务，不初始化、修改或删除原始业务事实。无数据库迁移。回归：`mvn -pl scx-reporting -am test`（reporting 7、tracking 22、delivery 8 用例均通过）、全仓 `pnpm check:type`、`pnpm lint`（0 error、4 条既有 warning）、`pnpm test:unit`（32 文件/267 用例）、`pnpm build:ele`（11/11）及 `make contract-check` 均通过 |
| M6 发布迁移包一致性与 Runbook | 迁移包/Runbook 静态验证完成，目标环境演练待执行 | 修复 `docker-compose.yml`、`docker-compose-boot.yml` 初始数据库仅挂载至 V25 的缺口，现均挂载 V1–V28；新增 `scripts/check-migration-packaging.sh` 与 `make migration-packaging-check`，并让 `init-database.sh` 比对已登记迁移的脚本名与 SHA-256，防止工作包漂移后继续升级。新增 `docs/广告平台设计/runbooks/MVP-A-发布与回滚.md`，覆盖配置保密、受影响表备份、前向升级、数据核验、应用发布和不可自动化的回滚边界；同步修正 `MVP交付说明.md` 的迁移版本描述。2026-07-17 `make migration-packaging-check`（28 个脚本）、`bash -n`（升级/校验/新增检查脚本）、两个 Compose `config --quiet` 和 `git diff --check` 均通过；生产敏感配置受控注入已由后续独立条目完成验证。无新增数据库迁移，未连接目标库、未执行备份/升级/密钥轮换/流量或回滚演练，故发布准备仍为进行中 |
| M6 生产敏感配置受控注入 | 已完成并验证，目标环境轮换与发布演练待执行 | `application-prod.yml` 的 Redis、数据源、XXL 调度令牌与 AES 登录密钥已改为必填 `SCX_*` 环境注入；新增 `scripts/check-prod-secret-injection.sh` 与 `make prod-secret-injection-check`，仅校验字段路径与占位符，不输出值。2026-07-17 静态门禁、`bash -n`、迁移包校验和契约校验均通过；`mvn -pl scx-boot -am test` 全量通过，`mvn -pl scx-boot -am -DskipTests package` 成功；再以 production profile、仅从本机安全配置读取的临时注入值启动独立 `10099`，健康端点返回 `UP`。无数据库迁移、未写入或输出凭据、未连接目标发布环境。生产环境新值准备、双值轮换、备份、升级、切流与回滚仍需发布负责人按 Runbook 在目标环境执行，不能由本机启动替代 |
| 迁移器历史校验修复与本地升级 | 已完成并验证 | `scx-backend/db/smart-converge-x/init-database.sh` 为 MySQL 查询增加 `--raw`，保留迁移历史查询中的 Tab 分隔符，修复此前误报 `V1` mismatch 的解析问题。2026-07-18 在整合工作树执行 `bash -n .../init-database.sh` 与 `make mvp-db-migrate`，成功应用 V26–V28，随后 `verify-database.sh` 返回 `Smart Converge X database verification passed`；无新增数据库迁移文件 |
| 发布前本地静态门禁复核 | 已完成并验证，目标环境发布仍待执行 | 2026-07-18 在 `codex/scx-mvp-complete` 执行 `make migration-packaging-check prod-secret-injection-check contract-check` 全部通过；使用已配置的本地隔离 MySQL 执行 `MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USERNAME=root MYSQL_PASSWORD=*** make db-verify` 通过。未连接目标环境、未执行生产备份/密钥轮换/切流 |
| 前端质量门禁回归 | 已完成并验证 | 2026-07-18 在整合工作树执行 `pnpm check:type && pnpm lint && pnpm test:unit`：类型检查通过，lint 0 error/4 条既有 warning，单测 32 个文件/267 个用例全部通过；happy-dom 个别资源测试产生预期的本地 `localhost:3000` 拒绝连接日志，但测试结果为全绿 |
| Playwright E2E 清单复核 | 已完成，运行态重跑待本地服务 | 2026-07-18 执行 `pnpm -F @boot/web-ele exec playwright test --list`，发现 4 个文件、15 个用例，覆盖登录、客户/产品、链接/任务、签名事件、归因、回传、报表明细、导出和 RocketMQ Outbox；未在无后端服务时伪造运行通过结论 |
| Playwright 运行态回归与 Outbox 恢复 | 已完成并验证，本地沙箱范围 | 2026-07-18 使用独立 local `10099`、本地 MySQL/Redis/RocketMQ/WireMock 实跑：配置写入、签名归因、Outbox 单条发布、报表明细、回传重试及导出等用例通过；停掉本地 RocketMQ broker 后，故障注入用例 1/1 通过并产生可追溯 sandbox 聚合 ID；恢复 broker 后恢复链路 2/2 通过。修复 `CallbackJobService.dispatchSandboxOutbox`：授权的单条人工恢复可绕过调度器退避时间，后台扫描仍继续遵守 `next_retry_at`。回归：`mvn -pl scx-tracking -am test` 24/24、`mvn -pl scx-boot -am -DskipTests package`、Outbox 故障/恢复 Playwright 3/3；未连接生产环境 |
| 最新整体验收门禁 | 已完成并验证，本地范围 | 2026-07-18 执行 `make backend-test` 全量后端测试通过（tracking 24、delivery 8、reporting 7 等模块均无失败）；随后 `make migration-packaging-check prod-secret-injection-check contract-check` 全部通过，并使用本地隔离 MySQL 执行 `db-verify` 通过。仅包含本地/静态门禁，不代表生产发布验收 |
| 线上 MySQL/Redis 只读盘点 | 已完成，线上升级仍未执行 | 2026-07-18 按授权连接目标 MySQL：确认库身份可达，发现 `scx_schema_migration` 不存在；现有表结构已包含 `scx_machine_nonce`、`scx_event_outbox`、`scx_callback_job` 等 MVP 表，但 `scx_link_event_mapping`、Outbox、回传、原始事件、日报、链接和任务表当前均为 0 行；系统用户/角色/菜单已有数据。Redis DB 2 `PING=PONG`、`DBSIZE=21`。未执行迁移、基线写入、备份、清理或任何业务写入；因缺少迁移账本，后续必须先由负责人确认“完整 schema + V28 基线”策略后再操作 |
| 线上 V28 基线登记核验 | 阻塞，禁止继续线上升级 | 负责人确认基线策略后，迁移器 `auto` 因缺少探测表误判为 V4，尝试执行 V5；V5 结构已存在，V6 因重复字段 `rta_enabled` 停止。随后使用显式 `SCX_MIGRATION_BASELINE_VERSION=28` 登记 V6–V28，但 `verify-database.sh` 发现实际仅 47/55 张必需表。缺失表为 `scx_upstream_customer`、`scx_short_link`、`scx_ad_platform_config`、`scx_media_channel_user`、`scx_delivery_config_revision`、`scx_delivery_status_history`、`scx_user_task_scope`、`scx_rta_decision_log`。当前线上账本已记录 V1–V28，但结构与 V28 不一致；未删除账本、未继续执行 DDL，等待负责人确认修复策略 |
| 线上 V29 结构对账迁移 | 已完成并验证 | 在负责人明确授权继续 DDL 后，新增前向 `V29__reconcile_v28_schema_gap.sql`，仅补齐 8 张缺失表和 V8/V12/V14–V16/V20/V28 缺失字段、索引；不写入 V21 沙箱回调配置或 V25 演示链路数据。2026-07-18 已在本地升级并通过数据库校验，再对线上执行 V29；迁移账本含 V28/V29，8 张缺失表均已存在，`verify-database.sh` 对目标库通过。同步更新两个 Compose 迁移挂载，并新增 `db/smart-converge-x/smart_converge_x_mvp_v29_init.sql` 作为空库一次性初始化入口；其全部 `source` 路径已验证。未执行生产应用发布、密钥轮换、真实媒体回调或 RocketMQ 线上连接 |
| MVP 本地启动与验收手册 | 已完成 | 新增 `MVP本地验收操作手册.md`，覆盖本机 Compose 数据库升级、`local` profile 后端、Element 前端、浏览器页面验收、质量门禁、Playwright E2E、sandbox 演示种子边界、常见问题和停止/清理方式。手册明确所有本地命令均不得指向线上库 |
| V30 sandbox/demo 基础测试数据 | 已完成并验证 | 新增 `V30__seed_sandbox_basic_test_data.sql`，使用固定 `sandbox`/`demo` 标识、可重复执行的 `ON DUPLICATE KEY UPDATE` 初始化合作方、客户、文档、渠道、渠道用户、产品、链接、4 条事件映射、任务、配置/状态追溯、RTA 配置、点击/激活归因、已完成回传、已发送 Outbox 与日报；不写入真实回调或媒体凭据。2026-07-18 本地升级/数据库校验、30 个迁移 Compose 挂载检查均通过；线上应用 V30 后数据库校验通过，逐项复核主数据 5 项、映射 4 条、事件 2 条、归因/回传/日报各 1 条、Outbox 2 条均存在。新增 `smart_converge_x_mvp_v30_init.sql` 作为空库 V30 一次性初始化入口 |
| 系统菜单初始化接口 404 修复 | 代码与构建验证完成，目标环境待发布复测 | 2026-07-18 根据运行日志的 `No static resource system/dict/page`、`system/dictvalue/type/status` 与 `system/dictvalue/type/sboot_storage_type` 定位到前端全局字典请求与后端路由前缀不一致。`SysDictController`、`SysDictValueController` 以及 19 个同类系统管理 Controller 现同时接受规范路径和旧控制台 `/system/**` 路径，避免已部署前端在字典修复后继续因同一前缀问题逐页失败；字典前端 API 同步改为规范路径。新增 `SysDictionaryRouteContractTest` 固化 21 个控制器路由契约。验证：`mvn -pl scx-system/scx-system-biz -am test -Dtest=SysDictionaryRouteContractTest -Dsurefire.failIfNoSpecifiedTests=false`（1/1）、`mvn -pl scx-boot -am -DskipTests package`（29/29 模块）、`pnpm -F @boot/web-ele run typecheck && pnpm lint && pnpm build:ele` 均通过；lint 仍仅有 4 条既有 warning。尚未对目标环境发布或以 `system` 用户逐菜单复测，不能写为线上已恢复；无数据库迁移。 |
| 后端报错续修：支付路由与 MVP 授权种子 | 已完成并验证；路由发布复测待执行 | 2026-07-18 对运行日志 14 个静态资源 404 去重后，除系统管理旧路径外还确认支付前端请求 `/pay/pay`、`/pay/notify`、`/pay/payconfig`、`/pay/refundorder` 与后端原始路由不一致。四个支付 Controller 已兼容旧前缀，新增 `PayRouteContractTest`；支付模块补充测试依赖。另以目标库只读核对确认 `system` 的管理员角色缺少 `ad:customer:read`（计数 0）：V28 基线登记没有补跑 V8 之后的菜单/角色数据迁移。新增幂等前向迁移 `V31__reconcile_mvp_access_control_seeds.sql`，补齐 V8–V27 缺失的 14 个运营菜单、11 个权限项和管理员/运营/分析员角色关联；两个 Compose 挂载及空库 `smart_converge_x_mvp_v31_init.sql` 已同步。验证：本地 `make mvp-db-migrate` 应用 V31、`make db-verify`、`make migration-packaging-check`（31 个脚本）均通过；路由契约测试 2/2 与全量后端打包 29/29 通过。目标库因账号无全局锁权限不能执行 `mysqldump`，已先导出受影响菜单/角色关联；随后升级器应用 V31 且数据库校验通过，账本 `V31=1`、`system` 的 `ad:customer:read=1`、14 个补齐菜单均存在。权限被登录会话持有，需退出并重新登录才会加载新权限；路由代码仍待发布新后端包后逐菜单验证。 |

---

## 0. 文档权威边界

### 0.1 证据标签

本文使用以下标签。后续 Agent 在任务、提交说明和交接中必须保留同样的区分。

| 标签 | 含义 | 能否直接据此编码 |
|---|---|---|
| `[仓库事实]` | 当前工作树中可定位到代码、配置、SQL 或测试源码 | 可以作为现状输入；仍需运行验证后才能写“已通过” |
| `[竞品已实测]` | 审计中实际打开页面并读取到字段、按钮、分页或前端校验 | 只能作为需求发现证据，不能证明竞品后端实现 |
| `[竞品静态确认]` | 从公开路由、菜单或构建产物确认 | 不能证明服务端权限、数据正确性或运行状态 |
| `[SCX 规划]` | 本文为 SCX 提出的产品或技术方案 | 项目负责人接受后才能作为实现基线 |
| `[待决策]` | 会影响领域、协议、数据、合规或架构的开放问题 | 未关闭前只允许调研、设计、契约草案和合成数据验证 |

### 0.2 冲突处理顺序

发现描述冲突时不得自行挑选最方便实现的一条，应先判断冲突属于哪一类：

- **实现状态**：数据库与运行日志、本轮测试、当前代码/配置依次用于证明“现在实际运行了什么”。静态代码不能覆盖运行证据。
- **目标设计**：用户明确决定、Accepted ADR/RFC、已评审的本规划依次用于判断“应该实现什么”。当前 `adr/` 没有可作为已接受依据的记录。
- **需求发现**：2026-07-15 审计报告优先于早期竞品调研；两者都只是参考输入。

若当前实现与已接受设计冲突，Agent 必须报告实现漂移和影响，不能让工作树反向覆盖目标决策。

审计报告用于修正早期调研：单账号菜单不能证明角色矩阵，隐藏菜单不能证明服务端授权，空任务列表不能证明任务运行闭环，页面指标名称不能证明指标口径，作者提出的微服务、Apache Druid 或工期也不是竞品事实。

### 0.3 状态词规则

- `已实现`：只能表示当前代码中存在对应实现。
- `已验证`：必须附本轮执行的命令、环境、结果和日志/报告位置。
- `已验收`：必须满足本任务全部验收条件并由验收人确认。
- `已规划`：不能写成 `已实现`、`已交付` 或 `已支持`。
- 页面能打开、测试文件存在、静态扫描通过，都不能单独等同于业务闭环已验收。

---

## 1. Agent 执行合同

### 1.1 开工阅读顺序

每个开发 Agent 在修改文件前必须依次阅读：

1. 仓库根目录 `AGENTS.md`。
2. 本文件。
3. 两份输入材料中与任务相关的章节。
4. `templates/Agent任务模板.md` 和 `templates/Agent交接模板.md`。
5. 当前任务涉及的真实代码、SQL、配置和测试。
6. `git status --short`，确认并保留用户已有改动。

README 当前仍包含部分已删除文档的旧链接。文件不存在时不得根据旧标题臆造规则，应以当前工作树和本文件为准，并在交接中记录缺失依赖。

### 1.2 一次只领取一个可验收任务

- 一个 Agent 任务建议控制在 0.5–3 个工作日，可独立测试和回滚/前向修复。
- “完成整个 MVP”“完成事件系统”“完成全部报表”不是合法的单任务。
- 后端、前端、契约、数据迁移可以是同一纵向切片的交付项，但任务过大时必须按本文任务队列继续拆分。
- 未满足 Definition of Ready 时，只能补调研、设计、测试基线或契约草案，不得替产品负责人做高风险决定。

开工前必须输出：

```markdown
## 任务合同
- Task ID / 状态：
- 目标：
- 明确不做：
- 规则与证据来源：
- 当前代码事实：
- 影响模块 / 表 / API / 消息：
- 权限、PII 与审计影响：
- 依赖、风险和停止条件：
- 验证命令：
```

### 1.3 必须暂停并请求决策的情况

- 需要决定广告主、媒体主体、上游合作方、产品或渠道的所有权边界。
- 需要决定真实媒体协议、回调格式、签名密钥、归因窗口、去重规则或事件保留期。
- 需要接收 OAID、IDFA、IMEI、IP 等真实设备或个人标识。
- 需要引入新 Maven 业务模块、微服务、分析数据库或替换认证授权模型。
- 需要实现价格、审减、授信、扣费、账单、对账、结算或真实支付。
- 需要删除/重写已执行 SQL、历史数据、公开 API 或消息字段。
- 需要使用生产地址、凭据、真实回调、真实导出或执行禁用、重放、投放等外部副作用。
- 用户已有改动与任务文件重叠，且无法证明可以安全保留。

### 1.4 禁止事项

- 不提交账号、密码、Token、证书、生产 URL、设备原始标识或浏览器取证文件。
- 不通过前端隐藏菜单、按钮权限或管理员身份代替服务端对象级授权。
- 不改写已存在的 `V*.sql` 来修复已执行环境；使用新的前向脚本。
- 不把 Redis、报表聚合表、缓存或图表结果当作原始事件事实源。
- 不把现有 `scx-pay` 通用支付模板描述成广告计费/结算能力。
- 不照搬竞品路径、组件库、字段命名、`?all=1` 全量下拉或本地 Token 方案。
- 不顺手清理模板遗留模块、做全仓重命名或拆微服务。
- 不运行 `make dev-reset`，除非任务明确授权清空本地数据卷。

### 1.5 交接要求

任务完成、暂停或阻塞时，使用 `templates/Agent交接模板.md`，至少记录：已完成/未完成、实际差异、代码/SQL/契约/配置、命令原始结果、权限与审计证据、限制、前向修复或回滚、下一任务建议。没有证据不得标记“已验收”。

---

## 2. 当前真实实现基线

以下为 2026-07-16 的静态代码核对结果，不代表本轮已经启动或测试通过。

| 范围 | 当前事实 | 状态判断 |
|---|---|---|
| 后端架构 | Maven 模块化单体；现有聚合模块为 `scx-auth`、`scx-common`、`scx-system`、`scx-pay`、`scx-subject`、`scx-visual`、`scx-boot` | `[仓库事实]` |
| 广告业务切片 | 仅存在媒体主体只读能力：`scx_media`、`scx_user_subject_scope`、媒体列表/详情 GET、`subject:media:read` | `[仓库事实]`，不是主体 CRUD |
| 对象授权 | 管理员读取全部启用媒体；普通用户经 `scx_user_subject_scope` 查询；无权限详情返回不泄漏存在性的 404 | `[仓库事实]`，运行效果待验证 |
| 前端 | `web-ele` 只有媒体主体只读列表、刷新、空态和错误态 | `[仓库事实]`，不是媒体管理页 |
| 测试 | 媒体服务管理员/普通用户分支与 Controller 不泄漏测试源码存在 | `[仓库事实]`，本轮尚未运行 |
| 广告主 | 当前工作树删除了广告主只读代码，并增加 `V3__remove_advertiser_read_model.sql` | 用户已有改动；不得擅自恢复广告主模型 |
| 业务模块 | 不存在 `scx-delivery`、`scx-tracking`、`scx-attribution`、`scx-reporting` | 均为未实现候选 |
| 数据库升级 | 启动模块未启用 Flyway；`init-database.sh` 按文件名排序重复执行 `V*.sql`，随后运行校验 | 现阶段要求 SQL 可重复执行；迁移方案仍需决策 |
| 契约 | `contracts/` 当前不存在，`make contract-check` 会因固定文件缺失失败 | 契约门禁尚未启用 |
| 开发配置 | `application-dev.yml` 当前包含远程依赖地址和敏感配置风险 | 未确认隔离本地目标前，禁止启动后端或执行任何数据库写验证 |
| 广告闭环 | 合作方、产品、渠道、文档、追踪链接、任务、事件、归因、回传、报表、导出均未实现 | 不得描述为已交付 |

### 2.1 对后续 Agent 的直接影响

1. 第一个任务只做环境、配置、源码、构建和测试基线审计；不启动后端、不启动有状态依赖、不初始化数据库。
2. 迁移号必须在任务开始时重新检查；当前 `V3` 是用户工作树中的未提交文件，Agent 不得修改、重命名或覆盖。
3. 广告主是否重新成为安全主体属于领域决策；在决策关闭前，沿用“媒体主体是当前唯一广告业务安全主体”的事实。
4. `make contract-check` 只能在契约基线任务补齐必需文件和语义校验后作为通过门禁。
5. 本地运行与数据库验证必须另开任务，先证明目标为隔离的本地 Compose 环境，并显式传入本地连接参数。
6. 本文中的接口、表和模块名称在评审前均为候选；Agent 不得仅凭候选名称批量搭空壳。

---

## 3. MVP 产品目标

### 3.1 一句话目标

在沙箱环境交付一个可重复演示、具备服务端对象授权和测试证据的效果广告追踪闭环：

```text
媒体主体与合作方/产品
  → 版本化追踪链接
  → 绑定媒体渠道的监测任务
  → 点击/激活/注册/付费事件（曝光由 DEC-002 决定）
  → 幂等、去重、按已接受规则归因（推荐默认：最后点击）
  → 沙箱回传
  → 按日/产品/任务/链接/媒体汇总
  → [P1] 受限异步导出
```

MVP 的目标不是证明可以承载竞品页面中显示的流量量级，也不是复刻全部 23 个路由。成功标准是业务对象、权限、状态、事件事实、指标口径、异步失败处理和验收证据能够闭环。

### 3.2 MVP 发布级验收目标

1. 管理员可维护最小主数据；运营只能操作被授权媒体范围内的资源。
2. 运营可创建、启用、暂停和归档追踪链接/任务，历史数据不因归档被删除。
3. 沙箱事件接口使用 DEC-002 接受的机器认证协议和版本化信封；重复投递只产生一条原始事实。
4. 归因使用 DEC-003 接受的规则，对命中、设备去重、超窗、乱序和未归因给出可解释结果。
5. 回传失败可重试、进入死信并由有权限用户人工重放；所有动作可审计。
6. 报表按明确时区和口径展示基础漏斗，并能追溯到原始事件/归因结果。
7. 直接访问隐藏路由、猜测资源 ID、扩大导出范围均不能越权。
8. 空库初始化、增量升级、后端/前端测试、契约校验和运行文档可重复验证。

### 3.3 优先级定义

| 级别 | 定义 |
|---|---|
| P0 | 缺失即无法形成可验收的追踪、归因和报表闭环 |
| P1 | MVP 演示有价值，但可在 P0 闭环后补齐 |
| OUT | 首版明确不实现，只保留扩展点或设计记录 |

### 3.4 SCX 原有范围保护

竞品偏链接、任务和渠道运营，不能据此自动裁掉 SCX 原有的审核、预算、风控与广告账务基础。本文推荐先交付“追踪与归因 MVP-A”，但这只是 `[SCX 规划][待决策]`：DEC-000 必须明确这些能力是同版 P0、后续 MVP-B，还是独立里程碑。真实支付/结算与“不可变广告账务基础事实”必须分开决策。

---

## 4. MVP 范围

### 4.1 P0 必须交付

| 领域 | 最小能力 | 竞品输入 | SCX 约束 |
|---|---|---|---|
| IAM 与对象授权 | 复用登录/RBAC；媒体主体范围；资源归属；操作审计 | 菜单按条件变化，但可直达隐藏路由 | 权限必须在 Controller/Application Service/查询层校验 |
| 合作方与产品 | 上游合作方、最小产品档案、启停、搜索分页 | `/supplier`、`/product` 页面与下拉 | 合作方不自动等同广告主；产品所有权先经 DEC-001 决策 |
| 媒体渠道 | 媒体主体下的渠道配置、状态、任务下拉 | `/distributor` 页面和任务媒体下拉 | 复用 `scx_media`，不得再造重复媒体主体 |
| 接入文档元数据 | 名称、版本、类型、受控对象引用/外链、状态 | 客户/媒体文档关联 | 不存密钥和正文配置事实；访问需要授权 |
| 追踪链接 | 创建、查询、编辑、设备类型、跳转/下载、模板校验、事件映射、版本、启停/归档 | `/task-link` 列表和抽屉 | 不提供物理删除；关联对象必须同一授权范围 |
| 监测任务 | 链接+媒体渠道、点击必选、曝光可选、去重窗口、状态/归档 | `/task` 条件表单 | MVP 不调用真实 RTA；条件规则前后端一致 |
| 事件接入 | click/activate/register/pay 的沙箱接入；impression 是否同版实现由 DEC-002 决定 | 报表和表单字段暗示事件链路 | 这是 SCX 产品决策，不是已验证竞品 API |
| 幂等与归因 | 唯一业务键、设备哈希、DEC-003 接受的归因规则、原因码 | 竞品有重复点击/转化/审减字段 | MySQL 原始事实最终判重；Redis 只加速 |
| 沙箱回传 | 归因后创建回传任务、重试、DLQ、人工重放 | 媒体回传字段与队列页 | 不接真实生产回调；重放必须授权和审计 |
| 基础报表 | 日期、产品、任务、链接、媒体维度；基础漏斗和数据时间 | 多个汇总页、明细页 | 指标先定义再实现；聚合不能反写原始事实 |

### 4.2 P1 增强

- 仪表盘环比、更多趋势图和自定义列。
- 接入文档上传、签名预览和版本历史的完整 UI。
- 受限异步普通导出：日期必选、范围快照、任务 ID、进度、过期和字段权限重校验。
- 高级导出、批量导出和更长时间范围。
- 曝光监测诊断、任务运行诊断、回传失败详情和队列运营大盘。
- 次留、3 留、7 留、首活、首单、拉活等扩展事件与 cohort 报表。
- RTA 配置元数据和模拟适配器；仍不执行生产同步决策。

### 4.3 待 DEC-000 决定的 SCX 能力

- 投放配置审核和状态发布门。
- 预算上限、消耗保护与超限处置。
- 最小风控规则、命中记录和人工复核。
- 广告计费口径与不可变账务基础流水（不等同真实支付/结算）。
- “审减/超量”规则、复核流程和受控导出。

这些能力未决定前不得编码，也不得标成 OUT。DEC-000 若要求纳入同版，必须先补领域、数据、API、权限、测试和独立任务拆解，再更新本文里程碑。

### 4.4 首版明确不做

- RTB/OpenRTB、DSP/SSP 交易、自动出价、学习期、实验平台和模型训练。
- 真实生产 RTA、真实广告下发、真实媒体回调和生产设备标识接入。
- SaaS 对外售卖、多租户计费、代理商返点、授信和代投关系。
- 真实充值/支付、发票、资金结算以及未经 DEC-000 定义的财务审减审批。
- 淘宝/京东 DP 转换、通用短链平台、素材生产和完整素材中心。
- 仅为匹配竞品而引入 OpenTiny、本地 Token、8–10 个微服务或 Apache Druid。
- 未定义口径的“自定义”“授信”“超量”“审减”指标直接进入报表。

---

## 5. 领域与权限边界

### 5.1 MVP 领域对象

| 对象 | MVP 定义 | 不允许混淆 |
|---|---|---|
| Media | 当前已存在的媒体安全主体，是对象授权范围的根 | 不等同媒体渠道、媒体账户或广告位 |
| Partner | 上游业务合作方/转化提供方候选，不默认成为登录安全主体 | 不直接命名为 Advertiser，除非 DEC-001 接受 |
| Product | 被推广的应用、内容或业务产品，归属规则待 DEC-001 确认 | 不等同追踪链接或投放任务 |
| MediaChannel | Media 下可被任务选择的具体渠道配置 | 不重复保存 Media 的主体属性 |
| IntegrationDocument | 接入说明的受控元数据和版本引用 | 不是密钥仓库，也不是运行时配置真相 |
| TrackingLink | 产品到目标地址/上游地址的版本化追踪配置 | 不等同短链服务 |
| TrackingTask | 链接与媒体渠道的运行配置及去重策略 | 不等同广告计划或自动出价任务 |
| RawEvent | 不可变的曝光、点击或转化事实 | 不得被报表、审减或回传状态覆盖 |
| AttributionResult | 对转化事件执行规则后的可解释结果 | 不直接作为金额或结算事实 |

### 5.2 MVP 角色候选（待 DEC-009）

下表是 `[SCX 规划][待决策]`，不是从单个竞品账号验证出的角色事实。

| 角色 | 数据范围 | 允许操作 |
|---|---|---|
| 平台管理员 | 全部媒体主体和平台配置 | 主数据、授权、状态、审计、重放 |
| 运营 | `scx_user_subject_scope` 授权的 Media 及下游资源 | 产品/链接/任务、报表；P1 受限导出 |
| 只读分析员 | 授权 Media 的报表和脱敏明细 | 查询；P1 受限导出；不可变更配置 |

推荐首版不建设独立广告主/媒体自助门户，但必须由 DEC-009 接受。无论最终角色如何命名，菜单与按钮只改善体验，服务端始终重新校验 `permission + subject scope + resource ownership`。未授权详情统一返回 404 或项目统一的不泄漏响应，不能让调用方判断资源是否存在。

---

## 6. 架构设计与模块调整方案

### 6.1 运行时原则

- 保持单个 `scx-boot` 部署的模块化单体，不拆微服务。
- 配置、权限、原始事件、归因结果和异步任务最终事实落 MySQL。
- Redis 用于短期幂等加速、去重窗口、限流和热点缓存；数据库唯一约束承担最终判重。
- RocketMQ 用于事件处理、回传和导出异步解耦；推荐使用本地事务/Outbox 保证可恢复投递，但具体可靠性模式受 DEC-010 约束，不能只依赖“发送成功”日志。
- 现有“Druid”是 JDBC 连接池，不是 Apache Druid 分析数据库。MVP 默认先用 MySQL 明细+日聚合验证口径；未来是否引入 OLAP 必须另做 ADR 和容量验证。
- MinIO/OSS 用于受控文档与导出产物；数据库只存对象 key、元数据、哈希和生命周期。

### 6.2 推荐模块边界（待 DEC-005 接受）

```text
scx-auth / scx-system
  └─ 登录、RBAC、菜单、用户、审计基础

scx-subject
  └─ Media、安全主体范围、Partner 边界（如被接受）

scx-delivery（候选新 Maven 模块）
  └─ Product、MediaChannel、IntegrationDocument、TrackingLink、TrackingTask

scx-tracking（候选新 Maven 模块）
  └─ 事件接入、幂等、去重、归因包、回传任务、可靠消息发布

scx-reporting（候选新 Maven 模块）
  └─ 日聚合、仪表盘、明细查询、ExportTask

scx-boot
  └─ 单体装配与统一启动
```

MVP 不单独创建 `scx-attribution` 微服务；归因先作为 `scx-tracking` 内的清晰包边界。若项目负责人不接受新增 Maven 模块，则先暂停编码并确认替代包结构，不能把所有业务塞入 `scx-system`。

### 6.3 最小数据流

```text
管理端保存版本化配置
        ↓
TrackingLink + TrackingTask 激活快照
        ↓
机器认证事件 API → 校验 → MySQL RawEvent（唯一约束）
        ↓                         ↓
 [候选 Outbox] ─RocketMQ─→ 去重/归因处理
                                  ↓
                         AttributionResult
                           ↓              ↓
                    CallbackJob      DailyAggregate
                           ↓              ↓
                    沙箱回调/DLQ      报表/异步导出
```

HTTP 请求只完成校验、原始事实写入和可追踪受理响应；归因、回传、大查询和导出不在请求线程同步完成。若 DEC-010 不接受 Outbox，必须在 ADR 中给出等价的事务边界、重复消费、失败恢复和消息清理方案。

---

## 7. 数据库设计基线

以下为逻辑设计候选。实际字段、外键和索引必须在对应任务的数据库迁移说明中确认。

| 表/聚合 | 关键字段 | 关键约束 | 所有者 |
|---|---|---|---|
| `scx_media` | 现有 id/code/name/status/timezone/settlement_currency | 复用现表；code 唯一 | subject |
| `scx_user_subject_scope` | user_id/subject_type/subject_id | 用户+类型+主体唯一；现有授权入口 | subject/system |
| `scx_partner` | id/code/name/type/status/version | code 唯一；是否关联安全主体待决策 | subject |
| `scx_product` | id/partner_id/code/name/platform/status/version | 所有权范围内 code 唯一 | delivery |
| `scx_media_channel` | id/media_id/code/name/status/document_id/version | Media 内 code 唯一 | delivery |
| `scx_integration_document` | id/owner_type/owner_id/name/type/version/storage_type/object_key/external_url/hash/status | 所有权形态受 DEC-001 约束；object_key 与 external_url 二选一；不存凭据 | delivery |
| `scx_tracking_link` | id/code/product_id/partner_id/document_id/device_type/redirect_enabled/targets/status/version | code 唯一；URL/占位符白名单；乐观锁 | delivery |
| `scx_link_event_mapping` | id/link_id/config_version/internal_event/external_event | link+version+internal_event 唯一 | delivery |
| `scx_tracking_task` | id/code/link_id/channel_id/impression_enabled/dedup_window_sec/status/version | 只能引用启用且同范围对象 | delivery |
| `scx_raw_event` | id/source/event_id/task_id/event_type/business_event_key_hash/occurred_at/received_at/device_key_hash/payload_hash/payload_ref/status | `(source,event_id)` 唯一；写入后不可改业务内容 | tracking |
| `scx_conversion_identity` | source/task_id/event_type/business_event_key_hash/canonical_event_id/created_at | 业务转化键唯一；每个业务转化只指向一个 canonical RawEvent | tracking |
| `scx_conversion_dedup_result` | conversion_event_id/status/reason_code/canonical_event_id/processed_at | 每个转化 RawEvent 一行；状态为 CANONICAL/DUPLICATE/INVALID | tracking |
| `scx_click_dedup_result` | click_event_id/rule_version/status/reason_code/canonical_click_event_id/processed_at | click_event 唯一；状态为 ELIGIBLE/DEDUPLICATED | tracking |
| `scx_event_outbox`（候选） | id/aggregate_id/event_type/payload_ref/status/attempts/next_retry_at | 受 DEC-010 约束；若采用则与业务事实同事务写入 | tracking |
| `scx_attribution_result` | id/conversion_event_id/revision/click_event_id/rule_version/status/reason_code/current_flag/supersedes_id/attributed_at | `(conversion_event_id,revision)` 唯一；修订追加且只允许一个 current | tracking |
| `scx_callback_job` | id/attribution_result_id/action_type/target_ref/status/attempts/next_retry_at/last_error_code | 目标+归因 revision+动作的幂等键唯一；敏感目标只保存引用 | tracking |
| `scx_report_run` | id/report_date/timezone/input_cutoff_received_at/input_snapshot_hash/rule_version/status/started_at/finished_at | 固定本次聚合输入快照、规则和运行状态 | reporting |
| `scx_report_daily` | report_run_id/report_date/timezone/media_id/partner_id/product_id/link_id/task_id/metric_set/revision/calculated_at | 使用可联合筛选的最细聚合粒度；run+完整维度+revision 唯一 | reporting |
| `scx_export_task`（P1） | id/requester_id/subject_scope/query_snapshot/status/object_key/expires_at/error_code | 查询与权限范围快照不可变 | reporting |

### 7.1 通用字段与数据规则

- 配置和任务表包含创建/更新人、创建/更新时间、逻辑归档时间和乐观锁版本。
- 状态变更记录操作者、原因、生效时间、请求 ID 和前后状态摘要；优先复用现有操作日志，缺口再通过 ADR 决定是否新增专用历史表。
- 原始事件不做物理更新；纠错通过新事件、修订状态或补偿记录表达。
- 事件时间 `occurred_at` 与接收时间 `received_at` 必须同时保存。
- `(source,event_id)` 只解决传输幂等；转化事件还必须提供/规范化 DEC-003 定义的业务转化键。相同业务键的不同 RawEvent 全部保留，由 `scx_conversion_identity` 选定 canonical 事件，`scx_conversion_dedup_result` 逐条分类，非 CANONICAL 记录不得进入归因或回传。
- 设备键默认只接收合成值或不可逆哈希；真实标识接入受 DEC-004 阻塞。
- 归因重算不覆盖旧结果，追加 revision 并通过 `supersedes_id/current_flag` 建立修订链；报表运行按输入快照选择当时有效的 revision。
- 点击设备去重属于 `scx_click_dedup_result`，不属于 AttributionResult 状态；只有 ELIGIBLE 点击能进入归因候选集合。
- 已成功外部回传的归因发生新 revision 时，不得静默重复回传。DEC-002/003 必须决定忽略、差异告警、CORRECTION 或 REVERSAL；协议不支持更正时默认记录差异并转人工处理。
- `scx_report_daily` 每行使用同一原子 grain，按查询条件再次汇总；不能用互相独立的单维汇总拼接联合筛选结果。
- 报表不能用单一“归因 revision 水位”代表所有转化；每次聚合通过 `scx_report_run` 固定接收时间截止点、输入快照哈希、规则版本和运行 ID，聚合行引用该 run。
- 报表聚合与导出结果不是计费/结算事实源。

### 7.2 SQL 迁移规则

1. 当前没有 Flyway schema history；`init-database.sh` 会按名称重复执行全部 `V*.sql`。
2. 新脚本编号在领取任务时通过 `rg --files .../db/migration` 重新确认，不能假设本文中的下一个编号仍可用。
3. 当前普通 `sort` 会把 `V10` 排在 `V2` 前；出现多位版本号前必须修正为数值/版本排序，或完成迁移工具决策。
4. 在迁移方案变更前，SQL 必须可重复执行，DDL 使用适当的存在性保护，DML 使用唯一条件保护。
5. 不修改 V1/V2 或用户的 V3；修复一律使用新的前向脚本。
6. 每个新增业务表同步扩展 `verify-database.sh` 的结构、权限或关键种子校验。
7. MVP-002A 必须覆盖并发执行、V9/V10 排序、部分失败后恢复和重复执行。
8. 任何引入 Flyway/Liquibase 或迁移账本的方案必须先完成 DEC-006。

---

## 8. 状态机、事件与指标口径

### 8.1 候选状态机

| 对象 | 状态 | 允许迁移 |
|---|---|---|
| Partner/Product/Channel | `ACTIVE`、`DISABLED` | ACTIVE ↔ DISABLED；禁用后不可创建新下游资源 |
| IntegrationDocument | `DRAFT`、`ACTIVE`、`RETIRED` | DRAFT → ACTIVE → RETIRED；历史版本只读 |
| TrackingLink | `DRAFT`、`ACTIVE`、`DISABLED`、`ARCHIVED` | DRAFT → ACTIVE ↔ DISABLED → ARCHIVED |
| TrackingTask | `DRAFT`、`ACTIVE`、`PAUSED`、`ARCHIVED` | DRAFT → ACTIVE ↔ PAUSED → ARCHIVED |
| ClickDedupResult | `ELIGIBLE`、`DEDUPLICATED`、`INVALID` | 每个 CLICK 一条处理结果；只有 ELIGIBLE 可参与归因 |
| ConversionDedupResult | `CANONICAL`、`DUPLICATE`、`INVALID` | 每个转化 RawEvent 一条分类；相同业务键只允许一个 canonical 事件参与归因/回传 |
| Attribution | `ATTRIBUTED`、`UNATTRIBUTED`、`OUT_OF_WINDOW`、`INVALID` | 以 canonical 转化为输入；处理完成后只追加 revision，不原地改旧结果 |
| CallbackJob | `PENDING`、`RUNNING`、`SUCCEEDED`、`RETRYING`、`DEAD`、`CANCELLED` | 失败按退避重试；DEAD 仅授权重放 |
| ExportTask | `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`EXPIRED` | 过期后删除产物但保留任务审计 |

状态枚举是 `[SCX 规划]`。实际编码前应由产品负责人确认名称、迁移权限以及禁用/归档对新事件和历史查询的影响。

### 8.2 P0 标准事件

| 标准事件 | 生产方 | P0 处理 |
|---|---|---|
| `IMPRESSION`（条件性） | 媒体沙箱 | 仅在 DEC-002 要求同版交付时实现；可在任务级关闭；不作为点击分母 |
| `CLICK` | 媒体沙箱 | 幂等、设备去重、进入归因候选集合 |
| `ACTIVATE` | 上游/业务沙箱 | 按 DEC-003 接受的规则归因 |
| `REGISTER` | 上游/业务沙箱 | 按 DEC-003 接受的规则归因 |
| `PAY` | 上游/业务沙箱 | 按 DEC-003 接受的规则归因；仅表示转化事件，不产生广告账务或支付事实 |

事件信封至少包含：`schema_version`、`event_id`、`source`、`task_code`、`event_type`、`occurred_at`、`device_key_hash`、`attributes`；转化事件还必须提供可规范化的 `business_event_key`。机器认证方式、业务键规则、时间戳容差、防重放、允许属性和最大 payload 在 DEC-002/003 与契约任务中固定。

### 8.3 P0 报表口径候选

为避免竞品中的“重复点击、激活率、注册率、付费率”语义不清，SCX 先分离接入重试和业务去重。比率只有在 DEC-003 关闭 cohort、唯一业务键和归集日期后才能出现在 UI：

| 指标 | 候选定义 |
|---|---|
| duplicate_delivery_attempt_count | 相同 `(source,event_id)` 的重复投递次数；来自接入可观测指标，不作为业务点击量 |
| unique_click_event_count | 通过格式/任务校验并成功落成 RawEvent 的唯一 CLICK 事实数 |
| deduplicated_click_count | `scx_click_dedup_result.status=DEDUPLICATED` 的 CLICK 数 |
| eligible_click_count | `scx_click_dedup_result.status=ELIGIBLE` 的 CLICK 数 |
| attributed_activate_count | 按 DEC-003 唯一转化键和 cohort 计数的 ATTRIBUTED ACTIVATE 数 |
| attributed_register_count | 按同一 cohort/唯一键计数的 ATTRIBUTED REGISTER 数 |
| attributed_pay_count | 按同一 cohort/唯一键计数的 ATTRIBUTED PAY 数；不是金额 |
| click_to_activate_rate | attributed_activate_count / eligible_click_count |
| activate_to_register_rate | 同一 cohort 的 attributed_register_count / attributed_activate_count |
| register_to_pay_rate | 同一 cohort 的 attributed_pay_count / attributed_register_count |

所有比率在分母为 0 时返回 `null`，不伪造 0%。在 DEC-003 接受前只允许展示不歧义的事件计数，不展示漏斗比率。DEC-003 必须明确按点击日还是转化发生日归集、同一旅程/cohort 的定义、每类转化唯一业务键、自然日边界、迟到修订窗口和最终日结时间。报表必须显示时区、数据更新时间、归集口径和是否为预估/修订数据。

---

## 9. API、消息与权限契约

### 9.1 管理 API 候选

| 能力 | 候选路径 | 说明 |
|---|---|---|
| 媒体主体只读 | `GET /boot/subjects/media`、`/{id}` | 已有路径，保持兼容 |
| 合作方/产品/渠道/文档 | `/boot/ad/partners`、`products`、`media-channels`、`integration-documents` | 列表必须分页/远程搜索，不提供无限 `all=1` |
| 追踪链接 | `/boot/ad/tracking-links` | 状态变更使用独立 action，并要求 reason/version |
| 监测任务 | `/boot/ad/tracking-tasks` | 条件配置由服务端二次校验 |
| 报表 | `/boot/ad/reports/daily`、`/details` | 查询范围按主体授权裁剪 |
| 导出 | `/boot/ad/export-tasks` | POST 返回 task ID；GET 查询进度/下载权限 |

路径属于候选，必须先进入 OpenAPI 后再实现。Controller 继续返回项目统一 `Result<T>`；分页、错误码、幂等键、乐观锁冲突和不泄漏响应需在契约中统一。

### 9.2 机器事件 API 候选

- `POST /boot/open-api/events/v1/impressions`
- `POST /boot/open-api/events/v1/clicks`
- `POST /boot/open-api/events/v1/conversions`

机器接口不使用浏览器会话，也不把机器认证凭据下发给前端。`api.sign.enabled=false` 的现状不满足生产机器接口要求；是否复用现有签名拦截器、如何限定路径、密钥轮换和沙箱凭据均由 DEC-002 决定。

### 9.3 RocketMQ 主题候选

- `scx-event-ingested`
- `scx-attribution-requested`
- `scx-attribution-completed`
- `scx-callback-requested`
- `scx-export-requested`

Topic、Tag、Key、消息版本、顺序要求、重试、DLQ 和重放规则必须写入 AsyncAPI/消息规范，不能只存在于 Java 常量。

### 9.4 外部 URL 与出网安全

追踪跳转、下载地址、接入文档外链和回调目标都属于不可信输入，具体策略受 DEC-011 约束，P0 至少要求：

- 只允许明确协议和域名 allowlist，拒绝用户信息段、非常规端口和混淆地址。
- 解析并拒绝环回、私网、链路本地、保留地址和云 metadata 地址；连接前复检解析结果，防止 DNS rebinding。
- 限制重定向次数，每一跳重新校验；设置连接/读取超时、响应大小和出网策略。
- 外链预览默认只展示链接元数据，不让后端任意抓取用户提供的 URL。
- 回调凭据只保存安全引用，日志脱敏；沙箱 callback-mock 与真实出网配置隔离。
- 跳转链接防开放重定向滥用；未知模板占位符、脚本协议和控制字符直接拒绝。

### 9.5 权限最小集

| 资源 | 权限候选 |
|---|---|
| Media | `subject:media:read`（已有） |
| 主数据 | `ad:master:read`、`ad:master:write`、`ad:master:status` |
| 链接 | `ad:link:read`、`ad:link:write`、`ad:link:status` |
| 任务 | `ad:task:read`、`ad:task:write`、`ad:task:status` |
| 报表/明细 | `ad:report:read`、`ad:event-detail:read` |
| 导出/重放 | `ad:export:create`、`ad:callback:replay` |

权限码只是第一层。每个资源 ID 还必须反查到 Media 范围；导出和重放在执行时重新校验，而不是只相信创建任务时的前端参数。

---

## 10. 前端信息架构与交互

所有页面落在现有 `scx-fronted/apps/web-ele`、动态菜单和 Element Plus 体系中，不创建孤立的“竞品复刻”应用。

| 菜单 | 页面 | 候选视图目录 | P0 交互 |
|---|---|---|---|
| 主体管理 | 媒体主体 | `views/subject/media` | 保留现有只读列表和范围提示 |
| 投放配置 | 合作方/产品/媒体渠道/接入文档 | `views/delivery/*` | 搜索、分页、抽屉、状态原因、远程下拉 |
| 投放配置 | 追踪链接 | `views/delivery/tracking-link` | 级联选择、URL/占位符校验、事件映射、归档 |
| 投放配置 | 监测任务 | `views/delivery/tracking-task` | 条件开关、去重窗口、启停/暂停/归档 |
| 数据中心 | 每日汇总 | `views/reporting/daily` | 日期、产品、任务、链接、媒体筛选；汇总行 |
| 数据中心 | 事件/归因明细 | `views/reporting/event-detail` | 默认脱敏、权限受限、原因码 |
| 任务中心 | 回传与导出 | `views/operations/jobs` | task ID、进度、失败原因、过期、受控重放 |
| 工作台 | 效果概览 | 现有 dashboard | 报表闭环后再接入真实聚合，不使用假数据 |

### 10.1 通用交互验收

- 列表：关键词、级联筛选、状态、日期范围、分页、加载、空态、错误态和汇总行。
- 表单：必填、数值下限、条件字段展开、枚举、只读回填、并发版本冲突、取消不保存。
- 状态：禁用/归档等高风险动作必须二次确认并填写原因。
- 导出：未选日期拒绝；显示任务进度、失败原因、过期时间和权限范围。
- 安全：隐藏菜单直达、猜测 ID、修改请求中的 Media ID、下载他人产物均被服务端拒绝。
- 可访问性：键盘可操作、明确 label、错误信息与控件关联，不只用颜色表达状态。

---

## 11. 里程碑与阶段验收门

本规划不直接采用早期调研的 14–21 周估算。排期必须在 M0 基线验证、领域决策和容量输入后由负责人生成；Agent 只按依赖和验收门推进。

| 阶段 | 目标 | 出口证据 |
|---|---|---|
| M0 基线与决策 | 验证工程；关闭领域、协议、指标、PII、模块和迁移决策 | 测试报告、ADR/RFC、契约草案、明确阻塞 |
| M1 主数据 | 合作方、产品、媒体渠道、文档元数据 | SQL/API/UI、对象授权、状态审计、测试 |
| M2 追踪链接 | 版本化链接、模板校验、事件映射 | 状态机、越权、版本冲突和 UI E2E |
| M3 监测任务 | 链接+渠道、去重、曝光开关、生命周期 | 条件校验、授权、并发和状态测试 |
| M4 事件归因回传 | 沙箱事件、幂等、已接受归因规则、回传/DLQ | 重复投递/设备去重/乱序/超窗/未归因/重试黄金样例 |
| M5 报表 | 原子粒度日聚合、明细、仪表盘 | cohort/口径对账、权限、归因与聚合修订 E2E |
| M5-P1 导出 | 受限异步普通导出 | 查询快照、字段权限、失败与产物生命周期 E2E |
| M-B 条件性范围 | DEC-000 决定的审核/预算/风控/账务基础 | 独立领域规划、任务队列和验收证据 |
| M6 上线准备 | 安全、容量、监控、告警、轮换、Runbook | 演练报告、发布/回滚清单、负责人验收 |

任何阶段都不能仅因为页面完成而过门。前一阶段的数据所有权、权限和契约未稳定时，不提前并行构建依赖它的报表或真实事件链路。

---

## 12. 可领取 Agent 任务队列

状态定义：`READY` 可立即领取；`PROPOSAL` 只产出决策提案，用户接受后才能解锁实现；`AFTER` 等待前置；`P1` 不阻塞 P0 发布。

决策任务的固定流转是：Agent 输出选项、推荐、影响和验证方式 → 用户接受/修改/拒绝 → 更新第 15 节的状态/决定/日期 → 后续任务解锁。Agent 自己写完提案不等于决策已关闭。

| Task ID | 单一目标 | 前置 | 主要验收证据 | 状态 |
|---|---|---|---|---|
| MVP-000A | 审计 Git、工具链、远程配置风险，并运行不启动服务的后端测试/构建与前端质量门 | 无 | 命令结果、失败归因、外部地址风险清单；不改源码/配置/数据库，不启动后端或依赖 | READY |
| MVP-000B | 仅修复 MVP-000A 证实的 P0 基线问题 | MVP-000A + 用户确认修复范围 | 最小差异和对应回归 | AFTER |
| MVP-000C | 在明确隔离的本地 Compose 目标验证初始化、重复迁移、db-verify、媒体授权 API；禁止未知/远程库 | MVP-000A；必要时 MVP-000B；用户确认本地目标 | host/schema/容器隔离证据、两次初始化结果、API/日志 | AFTER |
| MVP-010A | 提交 DEC-000 MVP 范围提案 | 两份调研与 SCX 定位 | MVP-A/MVP-B/同版选项、影响、用户决定 | PROPOSAL |
| MVP-010B | 提交 DEC-001 领域所有权提案 | MVP-000A | Media/Partner/Product/Channel/Document 关系、迁移与权限矩阵 | PROPOSAL |
| MVP-010C | 提交 DEC-009 角色与门户提案 | MVP-010B | 角色、主体范围、自助门户、现有 RBAC 映射 | PROPOSAL |
| MVP-010D | 提交 DEC-002 事件/回传协议与机器认证提案 | MVP-000A | 事件集、信封、认证、防重放、沙箱目标 | PROPOSAL |
| MVP-010E | 提交 DEC-003 归因与指标口径提案 | MVP-010D | 归因规则、窗口、cohort、业务转化键、日期/修订及已回传后的修订策略 | PROPOSAL |
| MVP-010F | 提交 DEC-004 PII/设备键提案 | MVP-010D | 用途、哈希、保留、权限、脱敏与删除 | PROPOSAL |
| MVP-010G | 提交 DEC-005 Maven 模块边界提案 | MVP-000A/010B | 依赖方向、单体装配、替代包结构 | PROPOSAL |
| MVP-010H | 提交 DEC-006 数据库迁移提案 | MVP-000A | runner/Flyway 选项、V9/V10、并发、失败恢复 | PROPOSAL |
| MVP-010I | 提交 DEC-007 报表存储/容量提案 | MVP-010E | 原子 grain、数据量/SLO、MySQL 基线、演进条件 | PROPOSAL |
| MVP-010J | 提交 DEC-008 P1 导出边界提案 | MVP-010I | 格式、范围、最大量、过期、对象存储 | PROPOSAL |
| MVP-010K | 提交 DEC-010 消息可靠性提案 | MVP-010D/010G | Outbox/替代方案、事务、顺序、重试、清理 | PROPOSAL |
| MVP-010L | 提交 DEC-011 外部 URL/出网安全提案 | MVP-000A | allowlist、SSRF/重绑定、重定向、凭据与出网 | PROPOSAL |
| MVP-001B | 输出事件/回传 OpenAPI、JSON Schema、消息规范与错误码 | DEC-002/003/004/010/011 已接受 | 正反例、兼容策略、权限与安全用例 | AFTER |
| MVP-001C | 建立必需契约文件并升级 `make contract-check` 为语义门禁 | MVP-001B | `management.openapi.yaml`、`event-ingestion.openapi.yaml`、`asyncapi.yaml`、`event-envelope.schema.json` 的语义校验及失败样例 | AFTER |
| MVP-002A | 实现并验证 DEC-006 接受的迁移方案 | DEC-006 已接受、MVP-000C | 空库、重复、V9/V10、并发、升级、部分失败恢复 | AFTER |
| MVP-100A | 合作方后端创建/查询/编辑、状态、权限、审计 | DEC-001/005/006/009 已接受、MVP-001C/002A | SQL、API、服务/越权测试 | AFTER |
| MVP-100B | 产品后端创建/查询/编辑、归属、状态 | MVP-100A | SQL、API、禁用引用和越权测试 | AFTER |
| MVP-101A | 媒体渠道后端与 Media 归属 | DEC-001/005/009 已接受、MVP-001C/002A | SQL、API、范围测试 | AFTER |
| MVP-101B | 接入文档元数据后端 | DEC-001/005/009/011 已接受、MVP-001C/002A | 所有权、受控引用、URL 安全；无秘密 | AFTER |
| MVP-102A | 合作方/产品前端列表与抽屉 | 对应后端任务 | 类型检查、单测、取消不保存、E2E | AFTER |
| MVP-102B | 媒体渠道/文档前端 | 对应后端任务 | 远程下拉、权限、错误态 E2E | AFTER |
| MVP-200A | 追踪链接表、API、模板/URL 校验和状态机 | M1、DEC-011、契约门禁 | SQL/OpenAPI、SSRF/开放重定向、越权、乐观锁测试 | AFTER |
| MVP-200B | 事件映射版本化后端 | MVP-200A、事件字典 | 历史版本不被覆盖、重复配置测试 | AFTER |
| MVP-201A | 追踪链接前端列表、抽屉与事件映射 | MVP-200A/B | 条件校验、只读字段、状态 E2E | AFTER |
| MVP-300A | 监测任务表/API、条件规则和生命周期 | M2 | SQL/OpenAPI、关联归属、状态测试 | AFTER |
| MVP-300B | 去重窗口服务端规则和并发测试 | MVP-300A、DEC-002/003 已接受 | 0 边界、窗口边界、投递重复与设备去重分离证据 | AFTER |
| MVP-301A | 监测任务前端条件表单 | MVP-300A/B | 开关展开、必填/数字边界、E2E | AFTER |
| MVP-400A | 事件接入、传输幂等、业务转化判重、机器认证和可靠发布 | M3、DEC-002/003/004/005/006/010/011 已接受、MVP-001C/002A | 相同 event_id、不同 event_id/相同业务键、防重放、非法 payload、事务恢复测试 | AFTER |
| MVP-400B | 点击设备去重及 DEC-003 接受的归因规则、原因码与 revision | MVP-400A、DEC-003/004 已接受 | ELIGIBLE/DEDUPLICATED、命中、乱序、超窗、未归因、修订黄金样例 | AFTER |
| MVP-401A | 沙箱回传、归因修订处置、退避重试、DLQ 和人工重放 | MVP-400B、DEC-002/003/010/011 已接受 | 原始/更正/撤销或人工差异策略、幂等、SSRF、故障注入、审计 | AFTER |
| MVP-500A | `report_run` 输入快照、原子 grain 日聚合和指标口径测试 | M4、DEC-003/007 已接受 | RawEvent→canonical 转化→归因 revision→report run→聚合对账 | AFTER |
| MVP-500B | 报表/明细 API 与字段脱敏 | MVP-500A、DEC-004/009 已接受 | 联合筛选、范围裁剪、敏感字段、性能基线 | AFTER |
| MVP-501A | 每日汇总/明细/仪表盘前端 | MVP-500B | 筛选、空态、口径提示、E2E | AFTER |
| MVP-502A | 受限异步普通导出和对象存储生命周期 | MVP-500B、DEC-008/011 已接受 | 日期/范围、进度、失败、过期、越权测试 | P1 |
| MVP-550A | 若 DEC-000 纳入审核/预算/风控/账务基础，补充独立领域规划与任务队列 | DEC-000 已接受为同版/下一版 | 数据/API/权限/状态/验收完整拆解；此任务不写业务代码 | AFTER |
| MVP-600A | 安全、容量、可观测性和故障演练 | M1–M5 | 指标/告警、密钥轮换、积压/DLQ/恢复报告 | AFTER |
| MVP-600B | 发布、回滚、数据核验和运行手册 | MVP-600A | 可执行 Runbook 与演练证据 | AFTER |

### 12.1 第一任务推荐指令

```text
阅读 AGENTS.md、docs/广告平台设计/MVP开发规划.md 和两份 ocpx 调研材料，
领取 MVP-000A。先输出任务合同；检查 application-dev.yml 的外部连接风险；
不启动后端、MySQL、Redis、RocketMQ 或其他有状态服务，不修改源码、配置或数据库；
只运行不依赖外部服务的构建/测试/静态检查，并逐条记录环境、结果和失败根因。
```

---

## 13. MVP 端到端验收场景

| ID | Given | When | Then | 必需证据 |
|---|---|---|---|---|
| E2E-01 | 两个 Media 与不同授权用户 | 用户查询媒体/主数据 | 只能看到授权范围，猜测 ID 不泄漏存在性 | 服务端越权测试 + API 记录 |
| E2E-02 | 启用的合作方、产品、渠道、文档 | 运营创建追踪链接 | 关联范围正确，模板合法，生成版本，记录审计 | SQL/API/UI E2E |
| E2E-03 | ACTIVE 链接和渠道 | 运营创建并启用任务 | 条件字段、去重窗口和状态合法 | 状态机/边界/E2E |
| E2E-04 | 相同 event_id 重复投递，以及不同 event_id 命中设备窗口 | 并发发送 CLICK | 前者只有一条 RawEvent 且计入接入重试；后者保留事实并写入 ClickDedupResult | 并发测试 + 唯一约束 + 指标证据 |
| E2E-05 | 满足 DEC-003 归因条件的 ELIGIBLE CLICK 与 ACTIVATE | 发送转化 | 按已接受规则归因并生成回传任务 | 黄金数据集 + 归因记录 |
| E2E-06 | 两个不同 event_id 表达同一 business_event_key | 并发发送转化 | RawEvent 均保留，每条有分类结果，但只有 identity 指向的 canonical 转化进入归因和回传 | 业务键并发测试 + identity/classification 记录 |
| E2E-07 | 无点击、超窗、乱序和设备去重点击 | 分别发送事件 | 每种情况得到稳定原因码且不重复回传 | 参数化测试 |
| E2E-08 | 沙箱回调连续失败 | 执行回传 | 按策略重试、进入 DEAD；授权重放可成功且有审计 | 故障注入 + DLQ/审计 |
| E2E-09 | 旧归因 revision 已回传成功后产生新 revision | 执行修订处理 | 严格执行 DEC-002/003 的忽略/差异/CORRECTION/REVERSAL 策略，不静默重复 | 协议 mock + 幂等 + 审计 |
| E2E-10 | 已处理事件、归因 revision 和迟到修订 | 生成原子 grain 日聚合 | report run 固定输入快照；聚合可追溯并与当时有效 revision 对账 | run/snapshot/SQL 服务对账 |
| E2E-11 | 有权和无权用户使用同一筛选 | 查询报表/明细；P1 查询导出 | 结果、字段和 P1 产物均按范围隔离 | API + E2E；P1 下载测试 |
| E2E-12 | 归档链接或任务 | 查询历史并发送新事件 | 历史仍可查；新事件按接受规则拒绝/隔离且原因明确 | 状态测试 + 报表验证 |
| E2E-13（P1） | 未选择日期或超过限制 | 创建导出 | 服务端拒绝；合法范围返回 task ID 并最终过期 | 契约 + 异步 E2E |
| E2E-14 | 已明确确认的隔离本地 Compose 库；未知/远程库禁止使用 | 在可备份/可丢弃的本地库执行初始化、升级和重复运行 | V9/V10 顺序、并发/部分失败恢复、无重复种子和校验通过 | host/schema/容器证据 + 数据库报告 |
| E2E-15 | 私网/metadata/重绑定/多跳地址和合法 allowlist 地址 | 保存链接或执行沙箱回调 | 非法目标在解析与每次跳转前拒绝；合法目标受超时/大小/出网限制 | URL 安全单测 + 集成测试 |

---

## 14. 质量门与 Definition of Done

### 14.1 不启动服务的工程验证

```bash
# 后端构建不运行测试；适合 MVP-000A
make backend-build

# 前端：类型、lint、单测；以及生产构建
make frontend-check
make frontend-build
```

`make backend-test` 只有在 MVP-000A 先确认测试不会读取当前远程 `application-dev.yml` 或连接外部服务后才能运行；发现 Spring 上下文/集成测试可能外连时立即停止，改用明确的单元测试范围。

### 14.2 仅限 MVP-000C 的本地有状态验证

以下命令会启动容器并写入本地数据库。执行前必须确认 Docker 目标、本机 `127.0.0.1:3306`、库名 `smart_converge_x` 和本地卷影响，且确认没有端口转发到外部环境：

```bash
make dev-up

cd scx-backend/db/smart-converge-x
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USERNAME=root \
  MYSQL_PASSWORD=scx_local_only ./init-database.sh

cd ../../..
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USERNAME=root \
  MYSQL_PASSWORD=scx_local_only make db-verify
```

`make dev-up` 只由 Compose 初始挂载 00/10/20，媒体相关 V1–V3 需要显式执行 `init-database.sh`。`make contract-check` 当前只会因契约缺失而失败，并且现有脚本仅做 YAML/JSON 语法解析；只有 MVP-001C 补齐四类必需契约和语义验证后才能成为门禁。`make dev-reset` 会删除本地卷，不属于常规验证命令。

### 14.3 每个业务任务的 DoD

- 需求、非目标、领域归属、权限和验收条件可判定。
- 数据变更使用新的前向 SQL，并完成空库、升级、重复执行和校验脚本验证。
- API/消息先有契约，错误码、示例、版本和兼容策略完整。
- OpenAPI、AsyncAPI 和 JSON Schema 使用对应规范的语义校验器，并至少有一个能证明门禁会失败的负样例。
- 服务端权限码、主体范围、资源归属和不泄漏负向测试通过。
- 创建/查询/更新/状态、非法输入、幂等、并发和失败补偿有测试。
- 高风险操作有原因、操作者、request_id 和审计记录。
- 外部 URL/回调通过协议和域名 allowlist、私网/metadata 拒绝、DNS 复检、跳转逐跳校验、超时/大小/出网限制测试。
- 前端包含加载、空态、错误、表单校验、权限、冲突和取消不保存。
- 相关后端测试/构建、前端质量门/构建和数据库校验实际运行并记录结果。
- 没有提交秘密、真实 PII、生产地址或取证数据。
- 交接文档记录已完成、未完成、差异、风险和下一任务；验收人明确结论。

---

## 15. 开工前必须关闭的决策

| 决策 ID | 问题 | 推荐默认 | 未确认时允许 | 阻塞内容 | 状态/决定/日期 |
|---|---|---|---|---|---|
| DEC-000 | 当前发布只做追踪归因 MVP-A，还是同版包含审核/预算/风控/广告账务基础 | 先交付 MVP-A；同时为 MVP-B 单独规划，不把真实支付/结算与账务基础混为一体 | 竞品转译、范围选项和影响分析 | M-B 范围与整版发布定义 | ACCEPTED：先交付 MVP-A；2026-07-16 |
| DEC-001 | Partner、Product、Document 与 Media 的所有权；是否需要 Advertiser 安全主体 | Partner 为上游业务对象，Product 归 Partner；Media 是当前安全主体；不恢复 Advertiser；具体共享/分配关系在提案中确定 | RFC、合成模型、媒体只读验证 | 主数据 DDL、对象授权、链接关联 | ACCEPTED：Partner 与下游对象归属 Media；2026-07-16 |
| DEC-002 | 首批事件/回传协议、机器认证、沙箱目标及更正/撤销能力 | click/activate/register/pay；impression 条件性；优先评估现有签名能力，不直接全局启用 | OpenAPI/Schema 草案 | 机器 API、回传修订、真实适配器、密钥管理 | ACCEPTED：P0 click/activate/register/pay，沙箱签名；2026-07-16 |
| DEC-003 | 归因规则/窗口、cohort、业务转化键、归集日期、时区、迟到修订、已回传后的处置和日结 | 推荐可配置最后点击，但必须比较候选；不硬编码窗口 | 黄金数据与口径选项 | 转化判重、归因/回传修订、漏斗比率、日报验收 | ACCEPTED：可配最后点击，默认 7 天，Asia/Shanghai；2026-07-16 |
| DEC-004 | 设备标识用途、哈希方式、保留期和可见角色 | MVP 仅使用合成或不可逆哈希设备键 | 接口占位、脱敏 UI | 真实设备数据、明细导出 | ACCEPTED：仅合成/不可逆哈希键；2026-07-16 |
| DEC-005 | 是否新增 `scx-delivery/tracking/reporting` Maven 模块 | 接受三个模块，保持单体部署；归因不独立拆服务 | 包结构草案 | 新模块创建和跨模块依赖 | ACCEPTED：三个 Maven 模块，单体部署；2026-07-16 |
| DEC-006 | 继续幂等 SQL runner，还是引入 Flyway/迁移账本 | 先验证当前 runner，再评审迁移账本；必须解决 V9/V10、并发和部分失败 | 静态审计、幂等 SQL 草案 | 非幂等 DDL、生产升级承诺 | ACCEPTED：MVP 继续幂等 runner 并使用版本排序；2026-07-16 |
| DEC-007 | MVP 报表 grain、存储与容量目标 | MySQL 原始事实 + 原子 grain 日聚合；不引入 Apache Druid | 小规模口径测试 | 聚合 DDL、OLAP 选型、容量/成本承诺 | ACCEPTED：MySQL 事实+原子日聚合；2026-07-16 |
| DEC-008 | P1 普通导出的格式、最大跨度、行数与过期时间 | XLSX/CSV 异步任务，范围配置化，MinIO 沙箱 | UI/契约草案 | 导出实现和性能验收 | ACCEPTED：CSV 异步、31 天/10 万行/24 小时；2026-07-16 |
| DEC-009 | MVP 角色、现有 RBAC 映射和是否建设广告主/媒体自助门户 | 管理员、运营、只读分析员；首版不建独立门户 | 权限矩阵草案 | 菜单、权限种子、对象操作边界 | ACCEPTED：管理员/运营/只读分析员，无独立门户；2026-07-16 |
| DEC-010 | RocketMQ 可靠发布、消费幂等、顺序、重试和清理方案 | 本地事务 + Outbox；必须给出与替代方案比较 | 时序图、故障模型和测试草案 | 事件异步实现、回传和导出可靠性 | ACCEPTED：本地事务 + Outbox；2026-07-16 |
| DEC-011 | 跳转/下载/文档/回调 URL 的 allowlist 与出网安全 | 默认拒绝；仅沙箱 allowlist；逐跳/DNS 复检并禁止私网/metadata | 安全用例和 mock | 外链保存/预览、沙箱回调、生产出网 | ACCEPTED：默认拒绝，仅沙箱 allowlist；2026-07-16 |

项目负责人可以逐项接受、修改或拒绝。关闭时必须在最后一列记录 `ACCEPTED / REJECTED / SUPERSEDED`、决定摘要和日期；Agent 不得把“推荐默认”或提案完成自动视为已决策。

---

## 16. Agent 最终交付格式

```markdown
## 交付结果
- Task ID / 最终状态：
- 已完成：
- 未完成及原因：
- 与规划/契约的差异：
- 代码 / SQL / API / 消息 / 配置 / 文档：
- 权限、PII 与审计：
- 测试命令、环境、结果、证据路径：
- 已知限制与放行风险：
- 回滚或前向修复：
- 下一可领取任务：
- 验收人 / 结论 / 日期：
```

只有项目负责人明确接受决策、任务满足 DoR、实现通过对应质量门且交接被验收后，任务才能从“已规划”变更为“已验收”。
