# Coco v1.1 正式协议与 Apifox 联调

> 适用范围：SCX V74 源码契约。本文不包含真实 Coco 域名、账号、密钥或生产开关；生产启用必须完成双方 UAT。

## 1. 数据方向和唯一入口

- 下游渠道向 SCX 上报点击：`POST /ocpx/open-api/ocpx/v1/channels/{channelCode}/feedback`。
- SCX 异步向 Coco 发出 HTTPS GET 点击请求。
- Coco 向 SCX 回调转化：`GET /ocpx/open-api/ocpx/v1/upstreams/coco/callback`。该地址是通用 `/{upstreamCode}/callback` 路由的 Coco 实例，不存在旧 `/coco/callback` 兼容入口，也不能用 `campaign_id` 代替 `monitor_id/task_id` 路由。
- SCX 根据监测链接配置异步回传下游。callback 成功固定返回 HTTP 200 和 `code=0`；响应格式由媒体协议定义，不提供后台可配响应模板。

## 2. Coco 点击 URL

广告主维护默认 Coco URL，监测链接可维护“上游点击地址覆盖”。发布优先级：

```text
监测链接 upstreamFeedbackUrl > 广告主 bidUrl
```

两者都必须为 HTTPS、使用精确 host 白名单且不能解析到私网地址。发布时把最终 URL 固化到路由快照；修改默认值或覆盖值会使相关路由回到 `DRAFT`，重新发布前不接收该路由流量。V74 上线时，已有 `PUBLISHED` 路由也必须逐条重新发布，生成新的 URL 快照后才能继续接流量。

支持 Coco v1.1 宏：

```text
__IDFA__ __IDFA_MD5__ __IMEI__ __IMEI_MD5__ __OAID__ __OAID_MD5__
__CAID__ __CAID_LIST__ __IP__ __ANDROIDID__ __ANDROID_ID__
__ANDROIDID_MD5__ __ANDROID_ID_MD5__ __AAID__ __UA__ __OS__ __MODEL__
__CALLBACK__ __CLICKID__ __CAMPAIGNID__ __ADGROUPID__ __CREATIVEID__
__ADVERTISERID__
```

所有值仅做一次 query 编码，空的可选宏替换为空。Coco 只接受 CLICK；Android 请求必须至少提供 IMEI、IMEI_MD5、OAID、OAID_MD5 之一。后台声明为必填的宏缺值时，feedback 直接返回 HTTP 400，不创建必然失败的投递任务。CAID JSON 不按文档示例冲突的 100 字符截断，最终长度在 Coco UAT 中确认。

## 3. Coco 成功语义

| 响应 | SCX 判定 |
|---|---|
| 非 2xx | 失败，进入重试/死亡任务 |
| 2xx 空 Body | 成功 |
| 2xx JSON 无 `code` | 成功 |
| 2xx JSON `code=0` 或 `code=200` | 成功 |
| HTTP 200 JSON `code=402` 或其他 code | 业务失败，记录 code/message 并重试 |
| 超时（当前 3 秒） | 失败并重试 |

## 4. callback、金额和幂等

`action_type=firstorder/first_order/first_purchase` 映射 `FIRST_ORDER`，单独累计 `firstOrderCount`，不并入普通下单或付费。`purchase` 的 `currency_amount` 只保存为购买金额事实，不参与 CPC 点击扣费；CPC 账务只由下游 CLICK 入站事件触发。

callback 事件 ID 优先采用 Coco 的 `conversion_id`、`event_id`、`order_id`。都没有时，使用签名 callback 引用和排序后的业务参数生成稳定 ID。相同 callback 重试会返回 `code=0` 且不会重复落事实或回传。

## 5. Apifox 操作

导入 [scx-ocpx-v1.postman_collection.json](contracts/scx-ocpx-v1.postman_collection.json)，配置集合变量 `baseUrl`、`channelCode`、`keyVersion`、`channelSecret`、`monitorId`。集合的前置脚本会按原始请求体生成 HMAC。

1. 执行“下游 FeedbackReqV1”，新事件应返回 202；同一事件和载荷重复发送返回 200，事件 ID 相同但载荷不同返回 409。
2. 在 Apifox Mock 中使用“Coco 上游响应 Mock”的空 Body 200、`code=0`、`code=402`、HTTP 500 示例；超时场景把响应延迟设置为 3000ms 以上。
3. 从 Mock 捕获的 Coco 点击 URL 中复制已编码 callback 的 `ext`，填入 `cocoExt`。依次运行 activate、firstorder、purchase 和重复 callback。
4. 管理后台“转发运行中心”查看事件流水；打开投递详情查看时间线与 attempts。`code=402` 的 attempt 应显示 HTTP 200 但任务为 RETRY/DEAD，错误原因包含业务 code。
5. “入口异常 / DLQ”查看重复、冲突、验签失败、限流和协议拒绝。该表和 attempts 默认保留 30 天；事件事实、账务和聚合报表不会被此清理策略删除。

管理后台不会展示原始签名、密钥、完整设备标识、完整 callback token 或原始请求体。请求 query 和响应 body 仅保存哈希、字节数等脱敏摘要。

## 6. 生产放行

V74 必须先备份并在沙箱执行。上下游外发开关保持关闭，先只开启上游完成 Coco UAT，再开启下游回传。真实 Coco 域名、宏长度、限流/成功响应、callback 重试以及专用容量证据未签字前，生产结论为 No-Go。
