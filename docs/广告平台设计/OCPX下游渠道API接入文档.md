# SCX OCPX 下游媒体接入文档

## 通用 Feedback

```http
GET /ocpx/feedback?mId={mediaId}&tId={monitorTaskId}&aId={advertiserId}&lId={promotionLinkId}&event=CLK&callback_url={url}
```

四个 ID 都是 SCX 管理端生成的字符串主键。`mId/tId/aId/lId` 分别表示媒体、监测任务、广告主和推广链接；旧 `chid/mid/dspid/linkid` 不接受。实际监测 URL 由管理端创建或编辑任务后生成，不应由浏览器自行拼接。

Feedback 可携带设备、广告、请求和时间等通用 Query 参数，详见 `contracts/feedback-req-v2.schema.json` 与 Postman 集合。宏占位值会按既有兼容规则清空。

HTTP 接收成功只表示请求进入 SCX 数据层和异步上游队列，不表示广告主已经接受。上游转发失败通过脱敏流程日志观测。

## 通用 Callback 回传

上游通过 `GET /ocpx/callback` 回调 SCX。SCX 解密 `ext` 恢复原 Feedback，在去重和扣量通过后读取原请求的 `callback_url`，追加转化事件参数，以 GET 回传媒体。

`callback_url` 必须是安全的 HTTP/HTTPS 公网地址。SCX 拒绝本机、内网、链路本地地址、用户信息、危险协议和非法 URL；媒体不得依赖管理表中的固定 Callback URL。

通用媒体无需在 Java 注册表登记。只有需要专用签名或方言协议的媒体才登记到 `MediaProtocolRegistry`。

## 小米例外

小米入站使用：

```http
GET /ocpx/mi/feedback
```

响应固定为：

```json
{"code":0,"failMsg":"","success":true}
```

只有明确登记为 `MI` 的媒体才使用小米签名回传。签名材料通过媒体 `credentialConfigKey` 在 Engine profile 的 `ocpx.partner-credentials.media` 中解析，不进入媒体表、浏览器、注册表或日志。未登记媒体不会使用 MI 协议，而是回退到通用 `callback_url`。

## 凭据和日志

合作方账号、密码、Secret、AES Key 与签名材料不进入业务数据库、API 响应、浏览器、流程日志或 Postman 示例。管理端只保存凭据引用。日志只能记录四实体主键、事件 ID、协议类型、结果码、耗时和脱敏说明。

## 联调检查

- 四主键存在且任务关系一致；
- 监测任务未被逻辑删除；
- 广告主已在上游注册表分类；
- 通用 `callback_url` 通过出站 URL 安全策略；
- MI 媒体已登记并能解析服务端凭据；
- 重复、扣量和全部转化事件均按契约验证；
- 流程日志和错误响应不泄露凭据或完整敏感载荷。

生产注册表、凭据、真实第三方 UAT、目标库迁移、部署和重启不在源码实现授权范围内。
