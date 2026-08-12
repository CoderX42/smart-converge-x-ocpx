# SCX 对 `sgyz-ocpx` 通用数据闭环的兼容基线

> 参考仓库：`/Users/karl/code/sgyz-ocpx`。本基线只覆盖通用
> Feedback 与 Callback 数据闭环。

## 实体和 ID 映射

| `sgyz-ocpx` | SCX | Query ID | SCX 表 |
|---|---|---|---|
| `UserDsp` | `OcpxAdvertiser` | `aId` | `scx_ocpx_advertiser` |
| `DspLink` | `OcpxPromotionLink` | `lId` | `scx_ocpx_promotion_link` |
| `UserChannel` | `OcpxMedia` | `mId` | `scx_ocpx_media` |
| `ChannelMonitorTask` | `OcpxMonitorTask` | `tId` | `scx_ocpx_monitor_task` |

所有 ID 均保留为 `String`，值是对应 SCX 实体的主键。
`credentialConfigKey` 对应旧 `UserDsp.ekey`，`bidUrl` 继续作为广告主 URL 模板，
`OcpxMonitorTask.taskCode` 对应旧 `bizId`。

## 兼容闭环

```text
媒体 GET /feedback
  -> FeedbackIngressAdapter
  -> Feedback Redis 去重、队列
  -> TemplateGetUpstreamDeliveryService 替换 bidUrl 宏、生成 AES ext 和 signature
  -> 广告主 GET
  -> 广告主 GET /callback
  -> 解密 ext、Callback 去重和 lId+mId 扣量
  -> 原 callback_url 追加 event_type
  -> 媒体 GET
  -> StorageTask 写明细、日明细和临时报表
  -> Admin DataMergeTask 写最终报表
```

SCX 保留旧 Redis Key、去重算法、队列分桶、扣量数组、StorageTask 批次与写入顺序、
配置内存索引、共享刷新标记和最终报表合并方式。Feedback 重复仍转发；扣量数组长度
固定为 11；Redis 数组读改写、List 消费和多表写入仍按旧代码的非原子方式运行。

## 必要适配与排除项

仅适配 Java/Spring Boot 版本、`com.yzytech.scx.ocpx` 包名、SCX 字符串主键/表名，
并使用无真实密钥和域名的本机配置。没有迁移爱奇艺、百度、OPPO、华为、快手等
合作方 Controller、固定 ID、URL、枚举项和专用协议；不包含智能出价、预算、
账务、重试、DLQ 或管理端扣量配置页面。
