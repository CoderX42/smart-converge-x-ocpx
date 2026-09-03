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

`AdvertiserProtocolRegistry` 从广告主 MySQL 索引的 `upstreamProtocol` 选择 `TEMPLATE_GET`、`AERIM`、`WEIFENG`、`GRAVITY`、`RENREN_VIDEO` 或 `PHOENIX`；未选择协议的广告主不向上游发请求，并通过 `OcpxFlowLogWriter` 写脱敏失败日志。下游媒体不再使用专用协议注册表，统一走通用回调。

协议由管理端创建或编辑广告主时显式选择。AERIM、WEIFENG、GRAVITY、RENREN_VIDEO 与 PHOENIX 的精确域名白名单由管理后台写入 MySQL，并随 `r:da:refresh` 热刷新；空白名单默认拒绝。GRAVITY、RENREN_VIDEO 与 PHOENIX 不使用广告主凭据或静态启用开关，由广告主协议选择和精确域名白名单共同控制投递。

## 正向 Feedback

唯一媒体入口为 `GET /ocpx/feedback`。链路先在 MySQL 事务中校验四实体关系、完成事件去重、计费和小时汇总，再写兼容日志队列；只有首次有效且允许投递的事件才由 `UpstreamDeliveryDispatcher` 异步选择上游实现。

`TEMPLATE_GET` 使用广告主 `bidUrl` 替换设备、请求、回调宏，以任务 `bizId` 替换 `__BIZ_ID__`，把 Feedback JSON 用 Engine AES Key 加密为 `ext`，再使用服务端解析的凭据生成签名。AERIM 与微风互动都仅处理 CLICK，并按 Feedback 的 `lId` 读取所属推广链接的完整上游模板；因此同一广告主可用多条推广链接承载多个产品。AERIM 支持多媒体宏别名并拒绝残留未知宏；微风保留模板固定 `aid/did`。两者均执行数据库精确域名白名单和 DNS 公网地址校验。

GRAVITY 也仅处理 CLICK，但使用单条 Android+IOS 基础链接：iOS 发送 `idfa/ip/ua/callback`，Android 发送非空的 `oaid/android_id/imei` 及 `ip/ua/callback`；所有值只编码一次。出站仅允许精确白名单 HTTPS 公网地址，不自动重试。

RENREN_VIDEO 仅处理 CLICK。每个产品分别保存 Android 或 IOS `/ad-action/click` 完整模板和商务固定 `userId`；Engine 校验公共宏、Android 严格设备字段或 IOS 至少一种设备标识，按需派生小写 MD5、秒/毫秒时间戳和稳定点击 ID。出站兼容 HTTP/HTTPS，但必须精确命中数据库白名单且 DNS 全部为公网地址；动态值只编码一次且不自动重试。只有 HTTP 200 与 JSON `code=200` 同时成立才算成功。

PHOENIX 按 PDF 协议处理 Android/iOS 的 IMP 与 CLICK。推广链接保存 HTTPS 精确 `/d/cli` 点击模板，固定 `cid/gid/aid/mid/channel` 必须来自实际商务投放且保持静态，不映射为 SCX 路由主键；曝光投递将路径切换为 `/d/imp` 并省略 callback。Engine 输出 `REQID`、平台对应的 `OS(0/1)`、13 位毫秒 `TS`，Android 校验 OAID/OAIDMD5，iOS 校验 CAID（IDFA 可选），动态宏只编码一次，出站地址执行精确主机白名单、DNS 公网与 SSRF 校验。HTTP 200 且 JSON `code=0` 才成功，10001–10007 映射稳定结论并保留脱敏响应摘要。

## 反向 Callback

通用入口为 `GET /ocpx/callback`，AERIM、微风互动、引力、人人视频和凤凰新闻专用入口分别为 `/ocpx/aerim/callback`、`/ocpx/weifeng/callback`、`/ocpx/gravity/callback`、`/ocpx/renren-video/callback` 和 `/ocpx/phoenix/callback`。人人视频与 Phoenix 使用 AES-GCM `kid/ext` 恢复 Feedback 路由；Phoenix 将缺省/`active`、`recall`、`register`、`pay`、`pay_amount`、`next_day`、`first_order`、`download`、`activity`、`addiction` 映射到 `1/2/4/5/5/7/19/20/21/22`，并保存原事件字符串。所有入口继续复用唯一事实、计费、媒体扣量和下游回传链路。

Feedback 携带的通用回调地址属于服务端外发目标，必须限制为安全的 HTTP/HTTPS 公网地址，拒绝本机、内网、链路本地地址、用户信息和危险协议。

## 落库与汇总

`StorageTask` 把 Redis List 中的 Feedback/Callback 写入明细、Feedback 日明细和临时报表；Admin 的 `OcpxDataMergeTask` 汇总到最终报表。时段报表输出扣量、展示、点击、CTR、消耗及全部转化事件列。

当前 List 消费、固定线程池和扣量数组仍沿用 SGYZ 数据层实现；它们不等同于可靠消息、重试或 DLQ。V127 在源码和当前态 Schema 中增加 Phoenix 实时闭环及三个标准动作指标，不执行目标数据库迁移、模板/白名单录入、部署或真实第三方 UAT。
