# SCX OCPX Engine 通用数据层实现说明

## 运行边界

`ScxOcpxEngineApplication` 是 Spring MVC + Undertow 独立进程，默认监听
`10000/ocpx`。`scx-boot` 只扫描四个共享实体和 `ocpx.admin`，不会装配 Engine 的
Controller、Task、Mapper、Redis、索引或线程池。

| 请求字段 | SCX 主键 |
|---|---|
| `chid` | `OcpxMedia.id` |
| `mid` | `OcpxMonitorTask.id` |
| `dspid` | `OcpxAdvertiser.id` |
| `linkid` | `OcpxPromotionLink.id` |

## 正向 Feedback

`GET /ocpx/general/feedback` 绑定旧 Query 名称。宏占位值会清空；缺少
`request_id` 时按完整请求对象 JSON 计算 MD5；`callback_url` 严格 URL 编码。
服务先写 Redis 去重计数，再把日志写入 `r:da:report:{bucket}`，最后交给
`RequestServiceManager` 异步构造广告主 GET。重复请求仍入队并转发。

`UnionParamAdapter` 从四实体内存索引读取广告主、链接、媒体和任务：
`bidUrl` 替换设备/请求/回调宏，`taskCode` 替换 `__BIZ_ID__`，Feedback JSON 使用
本机配置 AES Key 加密为 `ext`，最后按 `requestUrl + credentialConfigKey` 计算
MD5 `signature`。

## 反向 Callback

`GET /ocpx/union/callback` 使用 `ext/s/event_type/event_time`。AES 解密恢复原
Feedback，按请求 ID 与 Action 去重，日志写入 `r:da:report:cb:{bucket}`。扣量维度
是 `linkid + chid`，比例缺失按 `1.0`；允许回传时解码 Feedback 保存的
`callback_url`、追加 `event_type` 并执行 GET。成功为空 `200`，解析异常为 `500`。

## 落库与汇总

`StorageTask` 每分钟尝试十分钟 Redis 锁，读取 List 的 `0..1000`，过滤转义异常
数据，依次写 Feedback/Callback 明细、Feedback 日明细、临时报表，全部成功后才
裁剪 List。Admin 的 `OcpxDataMergeTask` 再把临时报表汇总到
`scx_ocpx_report_mat`；统一报表服务从该表输出曝光、去重曝光、点击、去重点击、
11 类转化和按 `base_price / 10000000` 计算的金额。

## 刻意保留的旧约束

- 双向 Manager 都是 500 固定线程、队列容量 100、`CallerRunsPolicy`。
- Redis 扣量数组长度 11 且非原子；第 12 类 Action 仍触发数组越界。
- List 消费没有 processing 队列、事务、重试、DLQ 或锁所有者校验。
- `scx_ocpx_flow_log` 仍可保留历史数据，但 Engine 不再写入。
- 仅迁移通用 `general/union`，不迁移任何合作方专用协议。

前向结构文件为 `V97__add_ocpx_legacy_data_layer.sql`。应用源码、测试和文档均不
执行目标数据库变更，也不代表真实上下游 UAT 或生产发布。
