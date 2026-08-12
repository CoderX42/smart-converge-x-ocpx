# SCX OCPX 最小双向转发引擎实现说明

## 模块边界

| 模块 | 当前职责 |
|---|---|
| `scx-ocpx-admin` | 四实体 CRUD、流程日志查询、Dashboard 和报表 |
| `scx-ocpx-contract` | `FeedbackReqV2`、`CallbackReqV2`、合作方上下文和 Adapter SPI |
| `scx-ocpx-runtime` | 鉴权、四实体关系解析、同步双向协议转换、流程日志 |
| `scx-ocpx-engine` | 独立 WebFlux 数据面启动应用和合作方凭据配置 |

## Feedback：下游媒体到上游广告主

```text
POST /open-api/ocpx/v2/media/{mediaCode}/feedback
  -> 解析 FeedbackReqV2
  -> 用 mediaCode + monitor_task_id 联查四张业务表
  -> 校验任务和四实体均为 ACTIVE
  -> 按媒体配置执行 NONE/TOKEN/SIGNATURE 鉴权
  -> 生成带短期签名 ext 的广告主 callback URL
  -> UpstreamAdapter 构造请求并同步调用广告主
  -> 写 scx_ocpx_flow_log(FEEDBACK, SUCCESS/FAILED)
```

请求不能通过重复提交业务 ID 改变关系；广告主、推广链接和媒体全部由监测任务派生。上游接受后 SCX 返回 202，上游网络错误或业务拒绝返回 502。

## Callback：上游广告主到下游媒体

```text
GET /open-api/ocpx/v2/advertisers/{advertiserCode}/callback?ext=...
  -> UpstreamAdapter 提取并解析 ext
  -> 校验短期签名和过期时间
  -> 用 advertiserCode + monitorTaskId 联查四张业务表
  -> UpstreamAdapter 归一化广告主事件
  -> DownstreamAdapter 构造 CallbackReqV2
  -> 同步调用媒体 callbackUrl
  -> 写 scx_ocpx_flow_log(CALLBACK, SUCCESS/FAILED)
```

`ext` 只携带监测任务 ID、源事件 ID、点击 ID 和过期时间，并使用协议密钥签名；不需要 callback 引用表或路由快照表。

## 监测地址

`GET /c/{taskCode}` 直接查询 ACTIVE 监测任务及所属推广链接，把请求参数填入 URL 模板后返回 302。任务暂停、归档，或任一关联实体停用时返回 410。

## 日志与报表

唯一广告技术表 `scx_ocpx_flow_log` 不保存请求/响应正文、账号或 Secret。它保存四实体 ID、流程方向、事件标识、结果、合作方 HTTP 状态、耗时和截断后的说明。流程日志页面查询明细；Dashboard 与报表按时间、广告主、推广链接、媒体、监测任务聚合成功/失败数量。

## 明确不提供的能力

当前最小版本没有 Redis Stream、事件事实、路由版本、投递任务、自动重试、幂等冲突检测、DLQ、重放、扣量或账务。因此一次 502 是否重试由调用方决定，合作方必须评估重复投递风险。
