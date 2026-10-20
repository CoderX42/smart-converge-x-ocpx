# ADR-020 RTA 上下游任务多对多

- 状态：Proposed
- 日期：2026-10-20
- 关联需求/RFC：广告主多产品 RTA 映射需求；[ADR-016 RTA 异步重建](ADR-016-RTA异步重建.md)、[ADR-017 RTA 多协议与配置归属](ADR-017-RTA多协议与配置归属.md)、[ADR-018 OPPO 媒体协议](ADR-018-OPPO媒体协议.md)、[RFC-RTA 链路优化方案 v1](../RFC-RTA链路优化方案v1.md)
- 决策者：待评审

## 背景与约束

### 业务需求

1. 广告主的 RTA 服务下有多个产品（A/B/C/D），各自有上游的产品/任务 ID，由我方维护映射。
2. 上游 RTA 服务对应多个上游 RTA 任务。
3. 下游 RTA 服务（我方对媒体提供的 RTA 接口）对应多个下游 RTA 任务，下游任务 ID 由我方定义并交给媒体。
4. 上游任务与下游任务是**多对多**绑定。

### 当前实现（V131–V133）的限制

| 位置 | 现状 | 与需求的冲突 |
| --- | --- | --- |
| `scx_ocpx_rta_binding` | 主键 `monitor_task_id`，一行一个 `upstream_id + product_id + external_task_id` | 上游任务不是独立对象；一个下游任务只能对应一个上游任务 |
| 下游任务 ID | 媒体请求 `taskIds` 直接使用 SCX 监测任务 ID；OPPO 用自增 `strategy_id` | 下游任务 ID 不可自定义 |
| 绑定保存 | 同一媒体 + 上游 + 产品 + 任务只允许一条启用绑定 | 上游任务在同一媒体下只能绑一个下游任务 |
| `RelayService.accept` | 候选必须同一上游、同一广告主，否则 `RTA_SPLIT_REQUIRED` | 一个下游任务不能扇出到多个上游服务 |
| `RtaState` | 一条状态只存一个 `upstream` 和本地 `matchedTaskIds` | 没有父子请求和"命中后选哪条路由"的表达 |
| 点击准入 | Feedback 按监测任务固定 URL，`RtaGate` 校验监测任务在命中列表中 | 多对多下点击落点是询量后才确定的 |

### 约束

- 点击、扣费、上游点击投递、扣量和报表都以**监测任务**（广告主 + 推广链接 + 媒体）为单位，本 ADR 不改变这一点。
- 继续使用 Redis 状态机、Lua 原子终态、XXL-JOB 刷库；不在询量关键路径同步写 SQL 日志/统计表。
- 协议能力仍由 `RtaProtocols` 在代码中定义：通用异步每次请求最多 100 个候选，微风 RTA 每次 1 个候选、不自动重发，OPPO 媒体只接微风 RTA。
- 数据库遵循 ADR-004：新增一份前向增量 SQL，同步当前态 schema 和校验脚本，不修改已执行迁移。
- 配置遵循 AGENTS.md §6.4：新增的引擎上限直接写入 profile yml。

## 决策

### 1. 新增三个配置对象，替换单表绑定

**上游 RTA 任务** `scx_ocpx_rta_upstream_task`：归属一个上游 RTA 服务，承载产品 A/B/C/D 的映射。

```sql
CREATE TABLE scx_ocpx_rta_upstream_task (
  id               VARCHAR(32)  NOT NULL PRIMARY KEY,
  upstream_id      VARCHAR(32)  NOT NULL,
  advertiser_id    VARCHAR(32)  NOT NULL,
  name             VARCHAR(128) NOT NULL,
  product_id       VARCHAR(128) COLLATE utf8mb4_bin NULL,
  external_task_id VARCHAR(128) COLLATE utf8mb4_bin NULL,
  route_config     JSON NULL,
  match_key        VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  enabled          TINYINT NOT NULL DEFAULT 0,
  version          BIGINT  NOT NULL DEFAULT 1,
  update_time      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rta_up_task(upstream_id, match_key),
  KEY idx_rta_up_task_adv(advertiser_id),
  CONSTRAINT fk_rta_up_task_upstream FOREIGN KEY(upstream_id) REFERENCES scx_ocpx_rta_upstream(id),
  CONSTRAINT fk_rta_up_task_adv FOREIGN KEY(advertiser_id) REFERENCES scx_ocpx_advertiser(id),
  CHECK(enabled IN (0,1)), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

- `advertiser_id` 冗余等于上游服务的广告主，用于数据范围过滤，保存时强校验一致。
- `match_key` 由协议定义规范化生成：通用异步为 `productId:externalTaskId`，微风为 `aid:did`。不用可空列做唯一键，避免 MySQL 唯一索引放过 NULL。
- 微风的 `aid/did` 从上游服务的 `protocol_config` 下沉到上游任务的 `route_config`，一个微风服务可以挂多个计划。迁移期间上游服务上的旧值作为默认值保留。

**下游 RTA 任务** `scx_ocpx_rta_downstream_task`：归属一个媒体 RTA 接入，`task_code` 是媒体请求中使用的 ID。

```sql
CREATE TABLE scx_ocpx_rta_downstream_task (
  id          VARCHAR(32)  NOT NULL PRIMARY KEY,
  media_id    VARCHAR(32)  NOT NULL,
  task_code   VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name        VARCHAR(128) NOT NULL,
  enabled     TINYINT NOT NULL DEFAULT 0,
  version     BIGINT  NOT NULL DEFAULT 1,
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rta_down_task(media_id, task_code),
  CONSTRAINT fk_rta_down_task_media FOREIGN KEY(media_id) REFERENCES scx_ocpx_rta_media_access(media_id),
  CHECK(enabled IN (0,1)), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

- 通用媒体的 `task_code` 沿用现有 `[A-Za-z0-9_-]{1,32}` 规则。
- OPPO 媒体的 `task_code` 即 `ad_type_list` 中的策略 ID，必须是正整数，可由管理员指定，取代 `strategy_id` 自增。

**绑定边** `scx_ocpx_rta_task_link`：上下游任务多对多，每条边指定命中后的点击落点。

```sql
CREATE TABLE scx_ocpx_rta_task_link (
  id                 VARCHAR(32) NOT NULL PRIMARY KEY,
  downstream_task_id VARCHAR(32) NOT NULL,
  upstream_task_id   VARCHAR(32) NOT NULL,
  monitor_task_id    VARCHAR(32) NOT NULL,
  priority           INT NOT NULL DEFAULT 100,
  oppo_pkg           VARCHAR(128) NOT NULL DEFAULT '',
  enabled            TINYINT NOT NULL DEFAULT 0,
  version            BIGINT  NOT NULL DEFAULT 1,
  update_time        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rta_link(downstream_task_id, upstream_task_id),
  KEY idx_rta_link_up(upstream_task_id),
  KEY idx_rta_link_monitor(monitor_task_id),
  CONSTRAINT fk_rta_link_down FOREIGN KEY(downstream_task_id) REFERENCES scx_ocpx_rta_downstream_task(id),
  CONSTRAINT fk_rta_link_up FOREIGN KEY(upstream_task_id) REFERENCES scx_ocpx_rta_upstream_task(id),
  CONSTRAINT fk_rta_link_monitor FOREIGN KEY(monitor_task_id) REFERENCES scx_ocpx_monitor_task(id),
  CHECK(enabled IN (0,1)), CHECK(version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

保存边时在服务层同一事务内校验：

1. 监测任务未删除，其 `media_id` 等于下游任务的 `media_id`。
2. 监测任务的 `advertiser_id` 等于上游任务的 `advertiser_id`。
3. 上游协议声明了路由身份字段（微风 `aid/did`）时，监测任务推广链接中的字面量必须与上游任务的 `route_config` 一致。
4. 媒体为 OPPO 时上游协议必须是 `WEIFENG_RTA`（补齐当前保存时未拦截的组合）。
5. 监测任务存在任一启用边时，`monitor_task.rta_enabled=1`；最后一条启用边停用或删除时置 0。

### 2. 合并规则：任一命中即放行，按优先级选落点

- 下游任务 D 的结果由其所有启用边上的上游任务结果合并：
  - 至少一条边的上游任务 ALLOW → D 为 ALLOW。
  - 全部为 DENY → D 为 DENY。
  - 没有 ALLOW 且存在 ERROR/TIMEOUT → D 为 ERROR（父请求截止时为 TIMEOUT）。
- 多条边同时 ALLOW 时，取 `priority` 最小的边为**选中边**；同优先级按边 `id` 字典序，保证重复计算结果一致。
- 选中边上的监测任务就是本次点击、扣费和上游点击投递的落点；每条边可以属于不同广告主。
- 给媒体的结果只包含命中的下游 `task_code`，不暴露上游任务 ID、监测任务 ID 或广告主。

### 3. 受理：父请求展开、按上游拆子请求

1. 媒体请求 `taskIds` 解析为下游任务，查出全部启用边。
2. 按上游任务去重：同一上游任务被多个下游任务引用时只询一次，结果共用。
3. 按上游服务分组生成子请求：通用异步每组一个子请求（不超过 100 个候选），微风每个上游任务一个子请求。
4. 父请求在 Redis 中保存：下游任务列表、展开的边快照、子请求 ID 列表、待完成计数、父截止时间。子请求的截止时间不超过父截止和各自上游 `timeout_ms`。
5. 展开上限写入 Engine profile，首版默认：每个父请求最多 20 个子请求、100 个不同上游任务；超限返回 400 `RTA_FANOUT_LIMIT`，不做部分受理。
6. 媒体仍只拿到一个 `relayRequestId`（父请求）；子请求 ID 只在服务端使用。

子请求复用现有上游适配器、回调、Worker 发送和重试逻辑。回调反查只在子请求的候选范围内进行，因此取消"同一媒体下产品/任务映射不能重复"的保存限制。

### 4. 汇总：子请求终态触发父请求合并

- 新增 Lua 操作 `CHILD_FINISH`：子请求首次终态时原子地写入子结果、减少父请求待完成计数；计数归零时在同一操作内按第 2 条规则计算父结果、写入选中边、置父终态，并按媒体协议进入通知队列或 `SYNC_READY`。
- 父截止到期由现有 deadline 队列处理：未完成的子请求按 TIMEOUT 计入，然后合并已有结果。
- 父请求的 `decisionHash` 由状态和排序后的 `下游 task_code → 选中边 id` 计算。
- 父请求为每个命中的下游任务保存：

```text
winners[taskCode] = { linkId, upstreamTaskId, monitorTaskId, childRelayRequestId, providerRequestId }
```

- OPPO 等待线程改为读取父请求终态，不再逐条轮询内部请求自行汇总。[RFC-RTA 链路优化方案 v1](../RFC-RTA链路优化方案v1.md) 第 A 节"多策略结果汇总一致性"由本条吸收，该 RFC 其余部分不受影响。

### 5. 点击：按下游任务的统一点击入口

- 新增 `GET /ocpx/rta/click/{mediaCode}/{taskCode}`，参数为 `rtaRequestId`（父 `relayRequestId`）、`request_id`（事件幂等 ID）及现有媒体宏。
- 处理：读父请求状态，取 `winners[taskCode]`，用选中边的监测任务（广告主、推广链接、媒体）构造 Feedback，再经过 `RtaGate`，进入现有事实、计费和上游点击投递。
- `RtaGate` 改为：
  - 校验对象从 `matchedTaskIds` 改为 `winners`；Feedback 的监测任务必须等于选中边的监测任务。
  - 配置快照比较覆盖下游任务、选中边、上游任务、上游服务、媒体接入；任一版本变化即拒绝。
  - `providerRtaRequestId` 取**子请求**的 `providerRequestId`，保证微风点击投递透传的是实际询量 ID。
- OPPO 专用入口 `/ocpx/oppo/click/{code}/{strategy}` 改为调用同一逻辑，`strategy` 即 `task_code`；OAID 哈希和 `SYNC_RETURNED` 校验保留。
- 旧的按监测任务直接上报的 Feedback 入口：监测任务只被一条启用边引用时继续兼容；被多条边引用时拒绝，返回 `RTA_CLICK_ENDPOINT_REQUIRED`，要求媒体改用新入口。

### 6. 日志与统计

- `scx_ocpx_rta_hour_stat` 增加 `downstream_task_id`、`upstream_task_id` 维度，请求数按去重后的上游任务计数；下游任务另计受理数和命中数。
- `scx_ocpx_rta_log` 增加父/子请求关联字段，便于从媒体请求追到每次上游询量。
- 实时记录页面展示父请求，并可展开子请求。

## 候选方案

| 方案 | 优点 | 缺点 | 风险/成本 |
| --- | --- | --- | --- |
| A. 本方案：上游任务、下游任务、边三张表，边携带监测任务落点，任一命中按优先级选落点 | 完整表达多对多；产品可复用；一个流量位可让多个广告主产品竞争；兼容现有计费模型 | 引擎需要父子请求和汇总；新增点击入口 | 改动面大，需要完整回归和灰度 |
| B. 在现有 binding 表上放开主键，允许一个监测任务多行 | 改动最小 | 上游任务仍不是独立对象；下游 ID 仍是监测任务 ID；一个监测任务只属于一个广告主，无法跨广告主 | 不满足需求 3 |
| C. 下游任务固定一个监测任务，上游任务只参与决策 | 点击入口可以不变 | 所有上游任务必须属于同一广告主，否则扣费错配 | 限制了多产品竞争 |
| D. 全部命中才放行 | 适合多上游叠加风控 | 点击落点需要另外指定；与"多产品竞争"场景不符 | 本次不采用，可作为边上的后续扩展 |

## 影响

- **业务**：广告主可在一个上游服务下维护多个产品任务；一个下游任务可同时询多个产品，命中后按优先级分配给对应广告主。
- **数据**：新增三张配置表；`scx_ocpx_rta_binding` 进入只读退役期；日志和小时统计表增加维度列。
- **API**：
  - 管理端新增上游任务、下游任务、绑定边的增删改查和选项接口；广告主、媒体、监测任务三个 RTA 抽屉调整。
  - 媒体请求 `taskIds` 语义从监测任务 ID 改为下游 `task_code`；迁移后取值与原值相同，现有媒体无需修改。
  - 新增统一点击入口；多边下游任务必须使用。
- **安全**：媒体只能看到自己的下游 `task_code`；上游任务、监测任务、广告主不出现在给媒体的报文中。凭据仍只在服务端配置。
- **性能**：一个媒体请求可能扇出多个上游 HTTP 调用；扇出上限、子请求截止与 Worker 容量需要压测。所有父子状态仍在 `scx:{rta-async}:` 同一 hash slot，单槽瓶颈不变。
- **运维**：新增扇出上限配置项，写入 Engine profile yml 后需重新打包重启。
- **成本**：上游询量次数按去重后的上游任务计，可能高于现在；需要和合作方确认 QPS 限额。

## 迁移、回滚与验证

### 迁移

1. 新增前向增量 SQL `V134__rta_many_to_many_tasks.sql`：
   - 建三张新表，扩展日志与统计表维度列。
   - 从 `scx_ocpx_rta_binding` 回填：每个 `(upstream_id, product_id, external_task_id)` 生成一个上游任务（微风从上游服务 `protocol_config` 复制 `aid/did`）；每条绑定生成一个下游任务（通用媒体 `task_code = monitor_task_id`，OPPO 媒体 `task_code = strategy_id`）；生成一条边指向原监测任务，`priority=100`，复制 `oppo_pkg` 和 `enabled`。
   - 预检：存在无法映射的绑定（上游停用且无有效配置、监测任务已删除、OPPO 媒体配通用上游）时中止并输出清单，不静默丢弃。
2. 同一变更同步 `scx_current_schema.sql`、`schema-required-tables.sh`、`verify-database.sh` 和 schema 契约测试。
3. 回填后的数据保证：每个下游任务正好一条边，现有媒体请求、OPPO 策略和点击 URL 行为不变。
4. `scx_ocpx_rta_binding` 保留一个发布周期只读；删除另行备份、预览并取得生产授权。

### 回滚

- 新版本上线后、出现多边配置前：回退应用版本即可，旧表数据未改。
- 一旦有下游任务配置多条边，旧版本无法表达，回滚前必须先把这些下游任务停用到只剩一条边；回滚步骤写入发布清单。
- Redis 中新格式的父请求状态旧版本无法读取；回滚需先关闭 `app.rta-relay.enabled`，等待在途请求过期（最长 24 小时，或按准入有效期评估）。

### 验证

- 单元测试：合并规则（全 DENY、部分 ERROR + ALLOW、多 ALLOW 选优先级、同优先级排序）、扇出上限、`match_key` 规范化、边保存的五条校验。
- 引擎集成测试：一个下游任务对应两个上游服务（通用 + 微风）、上游任务被两个下游任务共用只询一次、子请求超时后父请求合并、重复回调幂等、配置版本变更后点击被拒、`providerRtaRequestId` 取子请求值。
- OPPO 回归：迁移后的策略 ID、同步返回、专用点击入口、OAID 校验保持原行为。
- 迁移测试：在含通用和 OPPO 绑定的库上执行 V134，校验回填行数、唯一键、迁移前后同一媒体请求的结果一致。
- 发布前门禁：`make backend-test`、前端 `pnpm check:type && pnpm lint && pnpm test:unit`、`make schema-ledger-check`、`make db-verify`。
- 合作方 UAT 与灰度放量另行验收；本 ADR 通过不代表已交付。

## 被替代关系

- 替代 ADR-017 中"任务绑定归监测任务"的配置归属：绑定改归上下游任务之间的边，监测任务只作为点击落点。
- 修改 ADR-018 中 OPPO 策略 ID 的来源：从绑定表自增改为下游任务 `task_code`。
- 吸收 RFC-RTA 链路优化方案 v1 第 A 节。
- ADR-016 的 Redis 状态机、异步通知和 XXL-JOB 刷库保持有效，在其上增加父子请求。
