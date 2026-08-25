# Smart Converge X 广告平台文档中心

> 当前源码基线：2026-08-25 最小 OCPX + vientiane 管理端、广告主开户、共享广告主角色权限、创意与推广链接多对多，以及 MySQL 真实事件、可配置点击/转化计费、人工余额扣减、监测任务点击测试、下游媒体联调、上游 Callback 转化记录、V119 六阶段脱敏链路日志、V120 引力上游协议、Callback 密钥环兼容、V122 退役 V121 全量原始请求审计及 V123 流程日志检索优化（迁移号至 V123）。源码变更不代表目标数据库、UAT 或生产发布已验收。

## 当前广告业务

SCX 当前只保留连接上游广告主/DSP 与下游媒体/渠道所需的最小业务：

| 业务对象 | Java 实体 | 数据库表 |
|---|---|---|
| 上游广告主 | `OcpxAdvertiser` | `scx_ocpx_advertiser` |
| 推广链接 | `OcpxPromotionLink` | `scx_ocpx_promotion_link` |
| 下游媒体 | `OcpxMedia` | `scx_ocpx_media` |
| 监测任务 | `OcpxMonitorTask` | `scx_ocpx_monitor_task` |
| 扣量规则 | `OcpxDeductionRule` | `scx_ocpx_deduction_rule` |

管理端页面字段和交互以 `/Users/karl/code/sgyz-ocpx/vientiane-frontend/src/views` 为参考；`/manage/channel/channel` 映射为 SCX“媒体管理”。管理 API 始终使用 `advertiserId/mediaId/promotionLinkId/monitorTaskId`，不提供参考系统旧字段别名。

`scx-ocpx-engine` 默认监听 `10000/ocpx`，实现通用 `/feedback`、`/callback` GET 闭环，以及 AERIM `/aerim/callback`、微风互动 `/weifeng/callback`、引力 `/gravity/callback` GET。`mId/tId/aId/lId` 分别是媒体、任务、广告主和链接的 SCX 主键。广告主在管理端选择 `TEMPLATE_GET`、`AERIM`、`WEIFENG` 或 `GRAVITY`，协议允许域名由后台维护并热刷新；协议为空或白名单为空时拒绝上游转发。引力按单条 Android+IOS 推广链接和 Feedback 的 `os` 动态选择设备参数，由广告主协议选择和 GRAVITY 精确域名白名单共同控制投递；下游媒体统一使用 Feedback 携带的 Callback URL 走通用回传。KIMI 与小米 MI 协议已于 2026-08-17 移除。

六个共享实体位于 `scx-ocpx-core`。V108 由 MySQL 唯一事件事实、不可变账务流水、广告主账户和小时汇总统一驱动 Dashboard 与报表；V110 增加仅真实系统管理员可用的幂等人工余额扣减；V111 增加广告主自有图片创意、多链接绑定、关闭/点击/目标转化三种计费模式和全量账务流水查询。V111 不引入 `cId`、创意级监测地址、创意事实或创意报表维度，外部 `creative_id` 语义不变。Redis 余额和旧报表队列不再是数据真相。Dashboard 默认展示今天，支持今天、昨天、过去 7 天、过去 30 天和自定义日期；过去 7/30 天采用不含今天的完整历史日口径。广告主只能查看自己的可用余额和今日消费；业务指标可按授权数据范围查询。浏览器只传输服务端凭据引用；广告主开户时一次性提交登录用户名和原始密码，密码只用于服务端 BCrypt，不进入业务表、响应或日志。Secret、AES Key 和解析后的合作方凭据始终不进入浏览器。

V112 为监测任务增加仅真实系统管理员可用的完整 CLK 测试：参数可选、随机生成和编辑，后台只向固定 Engine 地址发送，并自动携带指向 Engine 受控测试接收端的不可编辑 `callback_url`。结果复用事实、账务与流程日志，覆盖点击入站、上游点击、广告主 Callback、转化计费、媒体扣量和最后一跳。上游 HTTP 200 不等于合作方入库，测试接收端成功也不等于真实媒体入库。

V113 将广告主计费和媒体扣量彻底解耦：监测任务的扣费开关、扣费形式、投放单价和目标转化继续决定广告主计费；媒体扣量规则统一解释为 `0%～100%` 回传比例，API/数据库保存 `0～1`。Callback 先写唯一事实、广告主账务和原始数量，再由 Redis Lua 原子判定并将 `PENDING` 条件更新为 `PASSED/DEDUCTED`；扣量结果只影响媒体回传提交，不改广告主余额或消费。报表从 V113 实际判定时间开始展示原始 Callback、放行、拦截和待判定数量，不估算历史结果；`PASSED/FORWARDED` 只表示进入或提交异步媒体投递，不等于媒体已接收。

V114 在监测任务页增加管理员“联调”入口：查询 Engine 异步写入的下游 Feedback 规范化快照，并可选择其中一条携带 `callback_url` 的请求发送真实媒体 Callback。联调回传不制造广告主转化事实、不扣费、不执行正式媒体扣量；结果写入脱敏流程日志。快照不是网络层原始报文，媒体 HTTP 2xx 也不代表业务入库。

V115 删除 Engine yml 中广告主主键映射和 `allowed-hosts`：广告主协议直接存入 MySQL，AERIM/WEIFENG 精确域名通过管理员接口维护。Engine 刷新索引后生效，并在出站前拒绝非白名单、DNS 指向非公网、链接归属不符和残留未知宏。上游真实凭据仍只保留在 Engine 服务端配置。

V116 在流程日志每行增加管理员“详情”入口：按源 request ID 与 `mId/aId/tId/lId` 精确关联下游 Feedback 规范化快照，补充留存设备型号、系统版本、IPv6、CAID2 和当前已解析的设备标识。详情不保存原始 URL、签名或凭据；历史记录不回填新增字段，异步快照缺失也不等于下游未发起请求。

V117 将详情调整为结果优先：流程结果、HTTP 状态、稳定结论、耗时与时间直接来自流程日志；新请求可查看经常见凭据脱敏且最多 4096 字符的上游响应预览。历史记录不回填响应正文，异步请求快照缺失时仍返回结果摘要。

V118 新增独立“上游转化记录”页面：直接分页读取 `scx_ocpx_event_fact` 的 Callback 唯一事实，展示上游接收时间、事件、目标转化、广告主计费、SCX 扣量判定、放行率快照和订单金额，并可按 request ID 跳转完整流程。重复 Callback 只保留首条唯一事实；`PASSED` 仍只表示 SCX 已提交下游投递，不等于媒体签收。

V119 用确定性 trace 聚合媒体入站、广告主出站/响应、广告主 Callback、计费、媒体扣量和媒体出站/响应。设备原值详情只对拥有专用权限的真实系统管理员开放 7 天，脱敏阶段元数据保留 90 天；签名、Token、密钥、测试令牌和加密 ext 永不保存。媒体快捷宏由后端统一目录输出，正式 CLK 不再接受缺失或原样占位的 request_id。

V120 新增独立 `GRAVITY` 上游：仅投递 CLK，按 iOS/Android 使用引力规定字段并生成认证加密 `ctx` Callback；`{idfa}` 为正确字段。引力 HTTP 2xx 只表示点击接口接受，Callback 固定映射为 V120 契约确认的 ACTIVATE。GRAVITY 不使用静态启用开关；广告主未选择该协议或白名单为空时拒绝投递。

V123 将流程日志精确总数限制为最多扫描 10,001 条：结果超过 10,000 条时返回 `total=10000,totalCapped=true`，前端提示缩小日期或增加筛选。列表按 `event_time,id` 倒序并使用同序索引，避免默认七日范围为页数展示扫描和排序数百万条日志；目标库索引迁移、部署和线上复测仍需独立授权。

## 推荐阅读顺序

1. 仓库根目录 `AGENTS.md`。
2. [V108 首页真实数据与广告主计费闭环](OCPX首页真实数据与计费闭环V108.md)。
3. [ADR-015 广告主余额人工扣减边界](adr/ADR-015-广告主余额人工扣减边界.md)。
4. [V107 广告主共享角色权限管理](OCPX广告主共享角色权限V107.md)。
5. [V104 超级管理员五对象 CRUD](OCPX超级管理员CRUDV104.md)。
6. [V103 广告主开户与数据范围](OCPX管理端开户与数据范围V103.md)。
7. [V102 vientiane 管理端对齐基线](OCPX管理端vientiane对齐V102.md)。
8. [OCPX 管理面实现说明](OCPX管理面实现说明.md)。
9. [OCPX Engine 实现说明](OCPX双向转发引擎实现说明.md)。
10. [sgyz-ocpx 核心流程对照](sgyz-ocpx基础能力兼容基线.md)。
11. [下游媒体 API 接入文档](OCPX下游渠道API接入文档.md)。
12. [上游广告主接入指南](OCPX上游广告主适配器开发指南.md)。
13. [微风互动上游接入说明](微风互动上游接入说明v1.3.md)。
14. `contracts/` 中的 OpenAPI、JSON Schema、Postman 和管理端字段契约。

其余 MVP、竞品、ADR/RFC、旧模块规格、V72 点击计费/充值账务设计和 V79–V95 文档是历史或规划资料，不代表当前运行能力。

## 待评审实现方案

- [V111 创意管理与可配置计费实现方案（Accepted）](OCPX创意管理与可配置计费实现方案V111.md)：上游客户自有图片创意、创意与推广链接多对多、运营点击/转化计费和全量账务流水的实现基线；目标库迁移、部署和真实合作方 UAT 仍需独立证据。
- [V112 监测任务完整点击测试契约](contracts/management-v112.md)：管理员生成并执行真实 CLK 测试，查询点击、广告主 Callback、转化计费、媒体扣量和受控最后一跳；目标库迁移、部署和真实合作方 UAT 仍需独立证据。
- [V113 广告主计费与媒体回传扣量契约](contracts/management-v113.md)：保留监测任务广告主计费，将扣量限定为媒体回传数量控制，并从首条新口径事实开始提供可审计报表；目标库迁移、Redis 切换、部署和合作方 UAT 仍需独立证据。
- [V114 下游媒体联调契约](contracts/management-v114.md)：管理员查询下游 Feedback 落库快照并发送不计费、不入正式报表的真实媒体 Callback；目标库迁移、部署和媒体 UAT 仍需独立证据。
- [V115 上游协议与允许域名契约](contracts/management-v115.md)：广告主协议和 AERIM/WEIFENG 精确域名由管理后台维护；AERIM 按推广链接支持一广告主多产品。
- [V116 流程日志请求参数详情契约](contracts/management-v116.md)：管理员按流程日志读取下游 Feedback 规范化快照，不留存原始报文、签名或凭据。
- [V117 流程日志结果与响应预览契约](contracts/management-v117.md)：管理员查看流程结果、脱敏限长响应预览与可选的下游请求快照。
- [V118 上游 Callback 转化记录契约](contracts/management-v118.md)：按授权数据范围查询 Callback 唯一事实、计费和扣量判定，并跳转流程日志诊断重试。
- [V119 完整链路请求日志与媒体宏契约](contracts/management-v119.md)：六阶段 trace、管理员受限原值、7/90 天保留策略、统一宏目录和正式 CLK request_id 规则。
- [V120 引力上游协议契约](contracts/management-v120.md)：Android/iOS 点击参数、认证 Callback、精确 JSON、白名单与已确认的 ACTIVATE 语义边界。
- [V121 全量原始请求审计契约（已退役）](contracts/management-v121.md)：仅保留历史发布证据，不再代表当前能力。
- [V122 原始请求审计退役契约](contracts/management-v122.md)：删除 V121 表、权限、接口、页面和 WAL，保留 Callback `kid` 兼容。
- [V123 流程日志检索性能契约](contracts/management-v123.md)：限制日志总数扫描窗口、阻止深翻页，并使用事件时间有序索引读取最新记录。

## 数据库与发布边界

- 空库结构以 `scx_current_schema.sql` 为准；已执行 V121 的数据库必须在部署不含审计/WAL的新版本后前向执行 V122，禁止修改或重放 V121。V122 删除 V121 两张审计表及专用权限，不影响 V119 流程日志和业务事实表。V123 只增加流程日志分页索引；在数百万行目标表执行前必须备份、审核在线 DDL 并取得明确授权。此前各迁移的数据预检和不回填边界保持不变；发布前还必须人工盘点 Redis 非零余额，任一存在都不得直接迁移。
- V102 预检会拒绝无法映射的平台值和已软删的广告主/媒体，不静默删除或复活数据。
- V96–V102 保持不变；所有当前和未来 SQL 都不读写已退役的 `scx_schema_migration`。
- Engine profile 中只保存服务端凭据映射；生产值不得出现在浏览器、日志、文档或对外差异中。
- 发布前必须盘点目标库广告主并在管理端全部分类；目标库迁移、凭据填充、宝塔部署和重启均需独立授权。
