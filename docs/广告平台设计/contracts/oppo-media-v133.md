# OPPO 媒体接入 V133

2026-09-09。源码实现与测试状态见交付记录；未发布生产、未调用真实 OPPO/微风。

## 配置

1. 媒体管理 → RTA 配置选择 OPPO；签名凭据引用对应 Engine `app.rta-relay.credentials` 的字面量密钥，建议随机 32 字符以上。配置 `ownerMode=EMPTY`（缺失/null 拼空串）或 `VALUE`（ownerId 十进制数值），必须与合作方确认。默认同步时限 1000ms，可设 100–5000ms。
2. 广告主非 RTA 协议选择 WEIFENG，RTA 接入选择 WEIFENG_RTA，保存微风提供的 aid/did；请求地址由适配器固定。
3. 监测任务 RTA 绑定上述接入，点击模板 aid/did 必须一致。保存后生成稳定 Integer 策略 ID。一条任务是一条下游策略，不直接把 SCX 雪花 ID 传给 OPPO。通用上游的产品 ID/任务 ID 仍是独立映射；微风用 aid/did 标识计划，不虚构上游任务 ID。
4. 媒体或任务配置可复制 RTA 地址，任务配置可复制策略 ID、点击监测链接。对外密钥通过约定渠道另行交付，管理 API 不返回明文。
5. 转化启用前填写任务的真实应用包名；媒体的 uploadCredentialRef 对应 Engine profile 的 `ocpx.partner-credentials.oppo-upload`，字段 salt/aes-key/owner-id/api-id/api-key。Salt 和 AES Key 由 OPPO 提供，三项 Omni 参数齐全时动态生成 ownerToken；未填 Omni 参数时按 v3.23 的可选规则省略。不支持半套 Omni 配置。

## RTA

`POST /ocpx/rta/oppo/{媒体凭据引用}`，UTF-8 JSON 最大 64KiB。只接受 OPPO 媒体配置。签名 MD5(request_id + owner_id约定值 + channel_name + request_time + secret)，比较时兼容十六进制大小写；request_time 为毫秒，允许正负5分钟。保持原通用异步 HMAC 接口不变。

最多100个不重复正整数策略。当前首接微风，每个策略单独查询，有界并发共享总截止时间。OAID 原文可缺省，但无设备时无法查询微风，返回不投放。收到的性别/年龄/兴趣标签等可选信息仅校验结构，不发送给微风、不持久化。不要把 RTA 请求当成点击或计费事件。

返回 ret/msg/data.id/data.agree_type_list。全部正常但均拒绝时 ret=0，列表为空；基础设施/上游错误且无允许策略时 ret=1，列表为空；部分成功只返回已确认的允许策略，msg 标记 PARTIAL_OR_UNAVAILABLE。策略不可用不回退到其他任务。相同媒体请求 ID 的策略集合/设备/上下文变化返回409，重复相同请求不重复上游询量。内部派发请求 ID 与媒体 ID 分开。

OPPO 终态不入异步通知队列。SYNC_READY 是决策已完成，SYNC_RETURNED 是已选入同步响应路径，不等于 OPPO 确认收到。Redis 状态和日志/统计沿用24小时及XXL-JOB落库。成功抽样、失败全量阶段日志不记录签名或设备原文。

## 点击与回传

`GET /ocpx/oppo/click/{媒体凭据引用}/{策略ID}`。必需 request_id/oaid/ad_id/ts/model，可选 rta_request_id/ip/ua/osv。附件正文及新建监测链接截图确认宏 __REQ__/__OAID__/__ADID__/__TS__；生成的 __MODEL__/__IP__/__UA__/__OSV__ 必须在 OPPO 联调中确认支持，未替换的必需宏明确拒绝。

默认 request_id 同时用来查询该策略的 RTA 决策。如果点击 ID 与 RTA ID 不同，必须显式传入 rta_request_id；不做设备猜测关联。检查媒体、策略、设备哈希、已返回的 ALLOW、有效期和配置版本后，进入原 Feedback 事实/计费/上游派发流程。微风点击 requestId 使用供应商 RTA ID，OPPO 原点击 ID/创意 ID 则保留于加密 Callback 上下文。

RTA 原有通用队列仍只持有脱敏快照。OPPO OAID 经 AES-GCM 加密作为进程内短暂字段交给微风点击适配器，在实际组装请求时解密；不会进入 Callback ext。Callback ext 只携带设备 MD5、媒体协议、凭据引用、包名、媒体请求 ID 和创意 ID。

微风 Callback 走原事实去重与扣量。放行后按回调上下文中的 OPPO 媒体协议调用固定 HTTPS uploadActiveData。使用 userIdType=1、type=2、AES-128-ECB/PKCS5Padding 加密 OAID MD5，包含毫秒事件时间、真实包名、数字 adId、channel=1、ascribeType=1、appType=1、dataCategory=0。激活→1，注册→2，应用首次付费→7。暂不回传金额/付费流水，避免臆测微风金额单位。微风未提供发生时间时，沿用其 Callback 适配器记录的接收时间。

签名用实际发送的 JSON 字符串，只有 HTTP 2xx 且整数 ret=0 才记成功。上传结果不记录可能回显凭据的原始 body。沿用现有异步投递，暂不新增持久重试/补偿队列；进程崩溃或上传失败需根据日志人工核对，不承诺恰好一次成功。

## 迁移与发布

V132 → V133 显式执行新增 SQL，同步 schema、校验脚本。无新账户/媒体实体。新增列兼容旧通用配置，旧 V131/V132 SQL 不修改、不重放。配置变更必须重新打包。生产执行前核对目标、备份、SQL SHA-256 和人工授权；本文不授权执行生产变更。

Engine 出站允许域名须包含 rta.adapi.cn 和 api.ads.heytapmobi.com；微风点击沿用后台 WEIFENG 域名白名单。Nginx 模板新增 OPPO RTA POST 与点击 GET 路由，正式启用前登记合作方出口 CIDR。关闭 OPPO 配置可停止接入；回滚前处置在途请求和回传，保留扩展列。

旧 link_ext.downstreamProtocol=OPPO 的未验收上传实现被媒体协议上下文替代；新链路需要重新生成 OPPO 点击链接，不把旧加密回调自动解释成新协议。真实联调必须确认 owner_id、时间预算、点击/RTA ID 对应、全部宏、包名、授权凭据和目标事件。

## 本地交付验证（2026-09-09）

后端完整测试通过：`mvn -pl scx-boot,scx-ocpx-engine -am test`，管理面另外指定隔离实例 `rta.mysql.url` 执行真实数据库测试。新增媒体协议配置、策略生成/更新稳定性、权限隔离、设备保护和上传签名回归已通过。最后增补同步 HTTP 返回结构、64KiB 请求限制及过期结果测试后，定向运行 `OppoProtocolTest,RelayIntegrationTest` 共17项通过。后端管理面与 Engine JAR 打包通过。

前端 `pnpm check:type`、`pnpm lint`、`pnpm test:unit` 和 `pnpm build:ele` 通过；单元测试47文件337项，RTA 浏览器 Mock 回归7项。Lint 无错误，保留3条既有警告。

`make schema-ledger-check ocpx-four-entity-check business-sql-check contract-check` 与 `git diff --check` 通过。无新增数据表，因此 required-tables 清单不变；当前态 schema、列校验、迁移契约和 V133 前向 SQL 一并更新。

迁移在临时本地 MySQL 9.6.0 空实例中验证：由旧结构插入已有媒体/任务绑定数据，执行 V133，确认数据保留、策略赋值，并逐列比对升级结果与当前态结构一致。该证据不替代目标 MySQL 8.x 验收，也没有执行完整生产数据库校验。

V133 SQL SHA-256：`53b3eb643fe0261a81cfd6105dc54343c1c8db56f90ef83db2989bd27951103d`。本次只修改工作树并本地验证，没有提交、发布、重启生产服务或调用真实合作方接口。
