# SCX OCPX Engine 双向转发实现说明

## 运行边界

`ScxOcpxEngineApplication` 是独立 Spring MVC + Undertow 进程，默认监听 `10000/ocpx`。`scx-boot` 不装配 Engine 的 Controller、任务、Mapper、Redis 索引或线程池。

| 请求字段 | SCX 主键 |
|---|---|
| `mId` | `OcpxMedia.id` |
| `tId` | `OcpxMonitorTask.id` |
| `aId` | `OcpxAdvertiser.id` |
| `lId` | `OcpxPromotionLink.id` |

四个主键都是必填。`aId` 是 SCX 广告主主键；`adv_id`/`advId` 是广告主侧广告 ID，不能混用。

## 协议注册与凭据

`AdvertiserProtocolRegistry` 从广告主 MySQL 索引的 `upstreamProtocol` 选择 `TEMPLATE_GET`、`AERIM`、`WEIFENG` 或 `GRAVITY`；未选择协议的广告主不向上游发请求，并通过 `OcpxFlowLogWriter` 写脱敏失败日志。下游媒体不再使用专用协议注册表，统一走通用回调。

协议由管理端创建或编辑广告主时显式选择。AERIM、WEIFENG 与 GRAVITY 的精确域名白名单由管理后台写入 MySQL，并随 `r:da:refresh` 热刷新；空白名单默认拒绝。GRAVITY 不使用凭据或静态启用开关，由广告主协议选择和精确域名白名单共同控制投递。

## 正向 Feedback

唯一媒体入口为 `GET /ocpx/feedback`。链路先在 MySQL 事务中校验四实体关系、完成事件去重、计费和小时汇总，再写兼容日志队列；只有首次有效且允许投递的事件才由 `UpstreamDeliveryDispatcher` 异步选择上游实现。

`TEMPLATE_GET` 使用广告主 `bidUrl` 替换设备、请求、回调宏，以任务 `bizId` 替换 `__BIZ_ID__`，把 Feedback JSON 用 Engine AES Key 加密为 `ext`，再使用服务端解析的凭据生成签名。AERIM 与微风互动都仅处理 CLICK，并按 Feedback 的 `lId` 读取所属推广链接的完整上游模板；因此同一广告主可用多条推广链接承载多个产品。AERIM 支持多媒体宏别名并拒绝残留未知宏；微风保留模板固定 `aid/did`。两者均执行数据库精确域名白名单和 DNS 公网地址校验。

GRAVITY 也仅处理 CLICK，但使用单条 Android+IOS 基础链接：iOS 发送 `idfa/ip/ua/callback`，Android 发送非空的 `oaid/android_id/imei` 及 `ip/ua/callback`；所有值只编码一次。出站仅允许精确白名单 HTTPS 公网地址，不自动重试。

## 反向 Callback

通用入口为 `GET /ocpx/callback`，AERIM、微风互动和引力专用入口分别为 `/ocpx/aerim/callback`、`/ocpx/weifeng/callback` 和 `/ocpx/gravity/callback`。引力使用 AES-GCM 认证加密 `ctx`，不依赖合作方追加参数，并在书面确认语义后固定映射为 ACTIVATE。所有入口继续复用唯一事实、计费、媒体扣量和下游回传链路。

Feedback 携带的通用回调地址属于服务端外发目标，必须限制为安全的 HTTP/HTTPS 公网地址，拒绝本机、内网、链路本地地址、用户信息和危险协议。

## 落库与汇总

`StorageTask` 把 Redis List 中的 Feedback/Callback 写入明细、Feedback 日明细和临时报表；Admin 的 `OcpxDataMergeTask` 汇总到最终报表。时段报表输出扣量、展示、点击、CTR、消耗及全部转化事件列。

当前 List 消费、固定线程池和扣量数组仍沿用 SGYZ 数据层实现；它们不等同于可靠消息、重试或 DLQ。V115 改变协议选择、域名白名单和 AERIM 链接来源，不执行目标数据库迁移，也不代表真实第三方 UAT 或生产激活。
