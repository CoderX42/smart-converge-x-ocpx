# SCX 对 `sgyz-ocpx` 核心流程的对照基线

> 参考仓库：`/Users/karl/code/sgyz-ocpx`。SCX 参考其业务关系和双向回传思路，不复制硬编码合作方枚举、账号或私有 URL。

## 实体映射

| `sgyz-ocpx` | SCX | 表 |
|---|---|---|
| `UserDsp` | `OcpxAdvertiser` | `scx_ocpx_advertiser` |
| `DspLink` | `OcpxPromotionLink` | `scx_ocpx_promotion_link` |
| `UserChannel` | `OcpxMedia` | `scx_ocpx_media` |
| `ChannelMonitorTask` | `OcpxMonitorTask` | `scx_ocpx_monitor_task` |

## 核心流程对照

`sgyz-ocpx` 的核心是媒体事件 → 广告主，以及广告主转化 → 媒体的双向协议转换。SCX 当前对应流程为：

```text
媒体 FeedbackReqV2
  -> mediaCode 鉴权
  -> monitor_task_id 解析四实体关系
  -> UpstreamAdapter 同步转发广告主
  -> 广告主 callback 携带签名 ext
  -> UpstreamAdapter 解析转化
  -> DownstreamAdapter 同步回传媒体
  -> scx_ocpx_flow_log 记录结果并供报表聚合
```

SCX 用 Adapter 注册表代替在业务服务中按 DSP/渠道 ID 写大量分支；监测任务和数据库外键表达合作关系，账号和 Secret 只存在于 Engine 配置。

## 当前覆盖与差异

| 能力 | 当前 SCX |
|---|---|
| 四方关系 | 已用四实体 CRUD、组合外键和运行时联查实现 |
| 媒体 Feedback | V2 JSON、NONE/TOKEN/SIGNATURE 鉴权、同步上游转发 |
| 广告主 Callback | 统一入口、签名 ext、同步下游回传 |
| 合作方协议差异 | `UpstreamAdapter` / `DownstreamAdapter` |
| 日志与报表 | 单一脱敏流程日志，五维直接聚合 |
| 扣量、账务、队列、重试、DLQ | 当前最小版本不提供 |
| RTA | 不属于当前产品边界 |

因此，SCX 已保留 `sgyz-ocpx` 的四实体关系和 Feedback/Callback 主闭环，但没有保留旧项目或此前 SCX 的所有扩展能力。每个真实 DSP/媒体私有协议仍需单独 Adapter 和合作方 UAT。
