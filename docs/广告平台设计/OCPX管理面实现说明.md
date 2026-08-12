# SCX 广告业务四实体实现与新人上手

## 先理解上下游

```text
下游媒体上报 Feedback
  -> SCX 根据监测任务找到广告主和推广链接
  -> 协议转换并同步转发上游广告主

上游广告主回调 Callback
  -> SCX 根据签名 ext 找回监测任务
  -> 协议转换并同步回传下游媒体
```

SCX 当前是效果回传与协议转换平台，不是完整 RTB/RTA 竞价平台。

## 四个业务实体

| 含义 | 类 | 表 | 管理 API |
|---|---|---|---|
| 上游广告主 | `OcpxAdvertiser` | `scx_ocpx_advertiser` | `/ocpx/advertisers` |
| 推广链接 | `OcpxPromotionLink` | `scx_ocpx_promotion_link` | `/ocpx/promotion-links` |
| 下游媒体 | `OcpxMedia` | `scx_ocpx_media` | `/ocpx/media` |
| 监测任务 | `OcpxMonitorTask` | `scx_ocpx_monitor_task` | `/ocpx/monitor-tasks` |

一个广告主拥有多条推广链接；一条推广链接只属于一个广告主；一条监测任务连接一个广告主、该广告主的一条推广链接和一个媒体。数据库组合外键与服务校验都会阻止任务选择其他广告主的链接。

- 广告主保存上游 Adapter、URL、宏、主机白名单和凭据配置键/版本。
- 推广链接保存平台、链接类型、URL 模板、Deeplink、下载地址和宏。
- 媒体保存下游 Adapter、鉴权类型、接入 URI、Callback URL、主机白名单和凭据配置键/版本。
- 监测任务只保存四方关系、运营人员、状态和配置版本。创建后直接为 ACTIVE，可暂停、恢复或归档。

账号和 Secret 不进入业务表或流程日志，只在 Engine profile yml 中按配置键读取。

## 保留的报表与日志

`scx_ocpx_flow_log` 是广告域唯一额外技术表。Feedback 和 Callback 外发成功或失败时各写一条脱敏记录。管理后台保留：

- 经营总览；
- 报表中心，支持 `TIME/ADVERTISER/PROMOTION_LINK/MEDIA/MONITOR_TASK`；
- 流程日志，可按时间、流程、结果和事件 ID 查询。

报表直接聚合流程日志，不保留小时事实或报表导出任务表。系统原有操作日志、登录日志不受影响。

## 新人阅读和操作顺序

1. 看四个 Entity、DTO、Controller，理解字段和管理 API。
2. 看 `OcpxMonitorTaskService` 的关系校验与状态变化。
3. 看 `PartnerContextRepository`，理解运行时如何只联查四表。
4. 沿 Feedback 阅读 `FeedbackIngressController` → `FeedbackIngressService` → `UpstreamAdapter`。
5. 沿 Callback 阅读 `UpstreamCallbackController` → `CallbackIngressService` → `DownstreamAdapter`。
6. 看 `OcpxFlowLogRepository` 和 `OcpxUnifiedReportService`，理解日志与报表共用一个数据源。
7. 管理后台按广告主 → 推广链接 → 媒体 → 监测任务的顺序建数据。

## 当前删除的广告业务

事件配置、路由发布/快照/ACK、对象范围、广告主账号授权、充值账务、扣量、点击去重、队列、投递重试、DLQ、运行中心、API 调试和报表导出均不属于当前最小版本。

系统管理基础框架和 `sys_*` 数据保持不变；V96 只清理明确列出的旧广告菜单、角色菜单和广告表。
