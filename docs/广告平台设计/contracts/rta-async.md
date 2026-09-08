# SCX 通用异步 RTA 契约与切换说明

对应 ADR-016 与 ADR-017。V132 起支持通用异步及微风同步决策，上游接入配置归广告主管理、媒体配置归媒体管理、绑定归监测任务，RTA 目录仅保留请求记录、流程日志和统计报表。详见 [V132 使用与交付说明](../RTA配置归属与多协议接入V132.md)。

本文下述上游 202、HMAC、独立回调及批量候选约定适用于 `GENERIC_ASYNC`，不适用于 `WEIFENG_RTA`；媒体侧仍使用统一异步协议。媒体就是现有 OcpxMedia；凭据解析 mediaId，报文不得声明另一媒体。此协议是首版通用协议，真实合作方需另行验收。

## HTTP 与签名

数据面 Engine 前缀 `/ocpx`：`POST /ocpx/rta/requests`、`POST /ocpx/rta/callbacks/{accessCode}`。管理面 Boot 前缀 `/boot`，前台经 `/api` 代理访问管理 API `/ocpx/rta/{upstreams,media,bindings,records,logs,reports}`，前三项是对象配置接口，不再是独立菜单页面。所有 RTA 路径不带版本号，旧 `/rta` 和 Profile 入口停止受理。

UTF-8 JSON，最大 64 KiB。未知字段、重复 JSON 键、字段类型错误均拒绝。请求、受理、决策与通知结构见 `rta-async-*.schema.json`。设备与上下文的业务值保持不变；SCX 将候选任务映射为上游产品/任务组合。

四个认证头：`X-Rta-Key`（服务端凭据引用）、`X-Rta-Timestamp`（13 位毫秒时间戳）、`X-Rta-Nonce`（16–128 位字母、数字、下划线、短横线）、`X-Rta-Sign`（64 位小写十六进制）。签名是服务端密钥执行 HMAC-SHA256，以下六项用换行符连接，无末尾换行：

1. `POST`
2. 完整外部路径，例如 `/ocpx/rta/requests`，不得重写路径后签名
3. 凭据引用
4. 时间戳
5. nonce
6. 原始 UTF-8 请求体的 SHA-256 小写十六进制

时间偏差最多正负 5 分钟；nonce 在同一凭据下保留 10 分钟防重放。业务重试必须重新签名并使用新 nonce。合作方密钥至少 32 字符。响应不含密钥或设备报文。出站仅允许服务端允许列表内的 HTTPS 地址，校验并固定 DNS 解析结果，禁止私网、重定向和代理。

## 异步顺序

媒体提交 `requestId`、1–100 个去重 `taskIds`、非空设备和 context。同批任务必须归属认证媒体、同一广告主及上游接入。提交时保存路由/绑定版本快照。

SCX 原子持久化 Redis 请求和待发送任务，返回 202：`requestId`、`relayRequestId`、`deadline`（毫秒）。此时状态为 PENDING，不允许投放。同一 `mediaId + requestId` 24 小时内内容相同返回原受理信息，内容不同返回 409。

上游请求包含 `requestId`、`relayRequestId`、`candidates: [{productId,taskId}]`、`device`、`context`、`callbackUrl`。上游必须按 `relayRequestId` 幂等受理并返回 202；SCX 在截止前重试提交，确认受理后删除加密暂存设备。上游通过 callbackUrl 独立发起签名 HTTP 请求：`relayRequestId`、`status: ALLOW|DENY|ERROR`、`matched`。ALLOW 必须命中至少一个本次候选组合；DENY/ERROR 必须为空。未知组合记为 ERROR，不能自动放行其他任务。

绑定快照用于回调映射，回调可早于上游受理响应。终态原子写入统计及通知队列。相同终态回调幂等确认，不同结果 409；超时后回调不能恢复 ALLOW。等待默认 5 秒，超时通知不投放。

媒体通知包含原 `requestId`、`relayRequestId`、`status`、`matchedTaskIds`、`reasonCode`、`expiresAt`。仅 ALLOW 的命中任务可投放，默认有效 300 秒。媒体以 `relayRequestId` 幂等签收，返回任意 2xx；首次立即投递，失败后间隔 1/3/10 秒，最多四次、总窗口 30 秒。其余终态不投放，expiresAt 不作为放行依据。

曝光/点击 Feedback 必须另传 **`rtaRequestId=relayRequestId`**，事件自身仍使用原 `request_id` 字段。核验媒体、命中任务、有效期、绑定与接入版本；不回退到事件请求 ID。非 RTA 任务走现有 OCPX 流程。RTA 不产生点击、转化或计费。

关联与通知状态保留 24 小时；设备 JSON 使用 AES-256-GCM 暂存 Redis，relayRequestId 为 AAD，在上游受理或终态后删除。实时工作由 Engine 执行，重启从 Redis 队列恢复，已过截止请求不重发。Redis 不可恢复丢失时无法保证恢复请求和未落库数据。

## 配置与数据范围

配置直接写入各 profile `app.rta-relay`，重新打包才生效：`enabled`、`callback-base-url`、`public-base-url`、`allowed-hosts`（允许域名，可逗号分隔）、`credentials`（引用到密钥的映射）、`payload-key`（随机 32 字节密钥的 Base64）。Boot 维护 public-base-url/allowed-hosts；Engine 使用全部实时配置。默认停用，示例域名不可用于生产。

一个 mediaId 仅一份 RTA 配置，credential_ref 唯一。媒体和广告主名称从原主数据读取；不新增渠道主表。广告主接入、媒体配置编辑仅系统管理员可用。任务绑定与查询沿用 AdvertiserScope，普通角色不因切换自动获得权限；由管理员在既有角色管理中显式授予所需页面权限及 `ocpx:rta:bindings:edit`。六个页面权限为 `ocpx:rta:{resource}:view`；前三个配置的编辑权限为 `ocpx:rta:{resource}:edit`。新绑定默认关闭。

查询均返回独立分页，记录含实时通知状态；日志展示落库时间。报表以接收小时聚合，支持 DAY/HOUR，候选任务分别累计，受理幂等不重复；允许数/受理数是 RTA 命中率，不是转化率。未落库统计快照没有 TTL，成功提交后才设置宽于请求窗口的 TTL。

## XXL-JOB 注册

复用 Boot 的执行器，不新增 RTA 专属执行器。管理控制台注册 Bean 任务，路由策略 FIRST，阻塞处理策略 SERIAL_EXECUTION；不要使用应用内 @Scheduled 执行落库。

- `ocpxRtaLogFlushJob`：Cron `*/5 * * * * ?`。每批最多 500、每次最多 4 批。固定事件 ID，事务批量落库后 ACK；数据库失败保留 Pending，30 秒后接管恢复，ACK 失败可重放，不重复入库。坏消息隔离到 `scx:{rta-async}:logs:dlq`，仅保留源消息 ID 和原因。
- `ocpxRtaMetricFlushJob`：Cron `*/5 * * * * ?`。读取累计版本快照，MySQL 仅接受更高版本，写成功且 Redis 版本未变才清除 dirty 标记。
- `ocpxRtaLogCleanupJob`：Cron `0 */10 * * * ?`。每批最多 1000，最多 10 批，删除超过 24 小时摘要。

成功请求按 relayRequestId 稳定采样 10%，失败阶段全部记录。摘要不含设备、签名和密钥。生产端入队失败只增加运行告警指标，不回退同步 SQL、不改业务决策。任务暂停只延迟日志和报表。

关注 Redis send/deadline/notify 队列、日志 Stream 与 Pending、死信、metric-dirty、XXL-JOB 失败与调度延迟，以及 Engine 的 `rta_*` 失败计数和 `rta_queue_depth`、`rta_log_backlog`、`rta_log_pending`、`rta_log_dead_letters`、`rta_metric_dirty` 仪表。日志是尽力记录，Redis 不可恢复丢失时不能承诺补齐。

## 发布顺序与回退

1. 暂停旧入口受理，通知合作方停止旧同步协议。记录旧 Profile、绑定、未处理 Stream/Pending 数和相关指标，隔离保留旧 Redis/表，不把旧状态导入新命名空间。
2. 在隔离预演环境核对 V131 SQL。生产须另行获得明确授权：记录目标身份、备份、Git 提交、SQL SHA-256、审批执行记录和 information_schema 后置条件。脚本不使用退役迁移账本，不物理删除旧表，不重放旧迁移。
3. 应用 `V131__rebuild_async_rta.sql`，它创建五个新结构、菜单及管理员权限，并停用旧任务 RTA 开关。新空库 schema 只含新结构；旧表依然留在已有库。迁移只解除旧 RTA 表的外键，使遗留历史不再约束现有业务对象生命周期；不删除旧表或历史行。
4. 发布 Boot、Engine、前端及 Nginx。替换占位域名，录入服务端密钥与 AES 密钥，登记出口 CIDR，注册上述三个 XXL-JOB。新配置仍停用。
5. 显式配置媒体、广告主接入、任务绑定，先在隔离环境完成模拟闭环，再按合作方验收结果逐个启用。密钥轮换时保留旧 AES 密钥对应的暂存请求直到处置完成；当前首版单 AES 密钥不支持并行解密轮换。
6. 回退优先关闭新入口并暂停新绑定，保留 Engine 处理既有请求到终态/通知耗尽，保留 XXL-JOB 消费日志统计；核对新命名空间积压后再停止新进程。不得直接使用旧代码继续解释新请求或新放行结果。恢复旧版本需恢复匹配的配置、任务开关和旧入口，并与上下游重新协商协议；新表保留，不回滚删除数据。

非 RTA 的 OPPO 转化上传仍保留，移除对旧 Profile 的依赖；推广链接 `link_ext.downstreamProtocol="OPPO"` 为明确选择。常规点击、转化、计费、扣量按原流程运行。

## 隔离验收

`RelayIntegrationTest` 自启临时本地 Redis 与 HTTP 上下游，覆盖 202、独立 HTTP 回调（早于受理响应）、媒体签收、A/C 映射与 B 门禁、幂等冲突、超时/迟到、重启读队列、通知耗尽、签名篡改/重放和 AES AAD。测试需本机可执行 `redis-server`，可用 `-Drta.redis.executable=/path/to/redis-server` 指定。

管理端/落库测试和前端 E2E 使用本地隔离或 Mock 数据；真实合作方协议、容量、生产放量另行验收。不要用 dev/prod 的共享数据库执行集成测试。
