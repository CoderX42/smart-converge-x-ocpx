# SCX OCPX 管理 API V102 字段契约

本文件固定 V102 管理端字段边界。请求和响应不接受旧字段别名。

| 资源 | 写入字段 | 列表/详情附加字段 | 删除 |
|---|---|---|---|
| advertiser | `name,bidUrl,credentialConfigKey,macro` | `id,balance,createTime,updateTime` | 无 |
| promotion-link | `advertiserId,name,platform,channelId,linkUrl,deeplink,linkExt,remark` | `id,advertiserName,createTime,updateTime` | 逻辑删除 |
| media | `name,uri,credentialConfigKey,macro` | `id,createTime,updateTime` | 无 |
| monitor-task | `monitorName,mediaId,advertiserId,promotionLinkId,unitPrice,bizId,remark` | `id,mediaName,advertiserName,promotionLinkName,platform,showUrl,clkUrl,createTime,updateTime` | 逻辑删除 |
| deduction-rule | `mediaId,promotionLinkId,ratio` | `id,mediaName,promotionLinkName,createTime,updateTime` | 逻辑删除并同步 Redis |

平台必须是整数 `1..4`。浏览器仅可提交和接收 `credentialConfigKey` 引用，契约中不存在账号、密码、Secret、AES Key 或已解析凭据字段。

保留的资源操作见 [管理面实现说明](../OCPX管理面实现说明.md)。状态切换、归档、预检、连通性、独立 URL 校验、媒体导出和版本参数均不属于 V102 契约。
