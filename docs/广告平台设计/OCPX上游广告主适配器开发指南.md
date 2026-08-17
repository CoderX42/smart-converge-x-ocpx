# SCX OCPX 上游广告主接入指南

## 选择协议

SCX 不再从广告主表读取 Adapter 字段。上游协议由 `AdvertiserProtocolRegistry` 按 SCX 广告主主键固定登记：

| 协议 | 用途 |
|---|---|
| `TEMPLATE_GET` | 通用 GET 模板转发 |
| `AERIM` | AERIM CLICK 投递与专用 GET Callback |

未登记广告主必须拒绝转发，并写一条不含请求载荷、凭据或密钥的失败流程日志。不能把“未登记”自动降级为通用协议。

## 接入步骤

1. 在管理端创建广告主，保存名称、请求地址、凭据引用和宏，并设置一次性的登录用户名与密码；系统原子创建只读广告主账号，编辑广告主时不再提交账号密码。
2. 获取管理端生成的广告主主键。
3. 在 Engine 对应 profile 的 `ocpx.protocols.advertisers` 中把该主键显式映射到 `TEMPLATE_GET` 或 `AERIM`。
4. `TEMPLATE_GET` 需在 `ocpx.partner-credentials.advertisers.<credentialConfigKey>` 填写服务端字面量凭据；AERIM 需配置允许的监测域名。
5. 运行注册表、四主键链路、签名、脱敏和未登记拒绝测试。
6. 在隔离环境完成真实协议联调；生产主键和凭据需在发布提交中单独复核。

注册表不允许保存账号、密码、Secret、AES Key、签名材料或完整 URL。管理 API 和浏览器也只能看到 `credentialConfigKey`，不能返回已解析凭据。

## 通用模板协议

`TEMPLATE_GET` 使用广告主 `bidUrl` 和宏配置构造请求，以任务 `bizId` 替换 `__BIZ_ID__`，把 Feedback 加密为 `ext`，并使用解析后的服务端凭据计算签名。`bidUrl` 保存时必须通过统一 URL 安全校验。

新增宏时应同时补充：请求 DTO、模板替换、契约样例、URL 编码测试和敏感字段日志测试。禁止把原始 Feedback、设备标识、账号或 Secret 写入异常消息。

## AERIM 协议

只有配置为 `AERIM` 的广告主才进入 AERIM 投递，当前只处理 CLICK。上游监测地址必须通过 `ocpx.aerim.allowed-hosts` 白名单，回调入口为 `GET /ocpx/aerim/callback`。

## 发布门槛

发布前必须从目标库只读盘点全部广告主主键，并逐个分类为 `TEMPLATE_GET` 或 `AERIM`。存在任何未分类广告主、通用模板凭据无法解析、AERIM 域名未进入白名单或把敏感值带入代码/日志时均为 No-Go。主键盘点、配置填充、目标库操作、部署和重启需要独立授权。
