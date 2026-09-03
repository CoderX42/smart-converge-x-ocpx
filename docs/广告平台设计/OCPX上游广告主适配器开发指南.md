# SCX OCPX 上游广告主接入指南

## 选择协议

SCX 不再从广告主表读取旧 Adapter 字段。上游协议由广告主 `upstreamProtocol` 显式选择，`AdvertiserProtocolRegistry` 从 Engine 内存索引读取：

| 协议 | 用途 |
|---|---|
| `TEMPLATE_GET` | 通用 GET 模板转发 |
| `AERIM` | AERIM CLICK 投递与专用 GET Callback |
| `WEIFENG` | 微风互动 v1.3 CLICK 投递与专用 GET Callback |
| `GRAVITY` | 引力 Android/iOS CLICK 投递与认证上下文 GET Callback |
| `RENREN_VIDEO` | 人人视频分平台 CLICK 投递与认证上下文 GET Callback |
| `PHOENIX` | 凤凰新闻 IMP/CLICK 投递与 AES-GCM GET Callback |

未选择协议的存量广告主必须拒绝转发，并写一条不含请求载荷、凭据或密钥的失败流程日志。不能自动降级为通用协议。

## 接入步骤

1. 在管理端创建广告主，选择 `TEMPLATE_GET`、`AERIM`、`WEIFENG`、`GRAVITY`、`RENREN_VIDEO` 或 `PHOENIX`，并设置一次性的登录用户名与密码；系统原子创建广告主账号，编辑时不再提交账号密码。
2. `TEMPLATE_GET` 填广告主请求地址、凭据引用和宏，并在 `ocpx.partner-credentials.advertisers.<credentialConfigKey>` 填写服务端字面量凭据。
3. AERIM 或微风互动在“协议与域名”中维护合作方精确域名，并为每个产品创建一条推广链接，保存该产品完整点击模板；微风模板须保留固定 `aid/did`。
   GRAVITY 只创建一条 Android+IOS 基础链接，不保存动态参数或宏；无需账号、Token、凭据引用和广告主级请求地址。
   RENREN_VIDEO 按产品分别创建 Android、IOS 完整 `/ad-action/click` 模板，固定 `userId` 写入模板；不保存 Bearer Token、凭据引用或广告主级请求地址。
   PHOENIX 按实际投放创建 Android、IOS 或 Android+IOS 推广链接；完整 `/d/cli` 模板保留凤凰商务提供的静态 `cid/gid/aid/mid/channel`，不填写广告主凭据或 `bidUrl`。
4. 运行协议索引、四主键归属、域名/DNS、签名、宏残留、脱敏和未选择协议拒绝测试。
5. 在隔离环境完成真实协议联调；目标库迁移、生产凭据和发布需单独复核。

协议字段和允许域名表不允许保存账号、密码、Secret、AES Key 或签名材料。管理 API 和浏览器也只能看到 `credentialConfigKey`，不能返回已解析凭据。

## 通用模板协议

`TEMPLATE_GET` 使用广告主 `bidUrl` 和宏配置构造请求，以任务 `bizId` 替换 `__BIZ_ID__`，把 Feedback 加密为 `ext`，并使用解析后的服务端凭据计算签名。`bidUrl` 保存时必须通过统一 URL 安全校验。

新增宏时应同时补充：请求 DTO、模板替换、契约样例、URL 编码测试和敏感字段日志测试。禁止把原始 Feedback、设备标识、账号或 Secret 写入异常消息。

## AERIM 协议

只有配置为 `AERIM` 的广告主才进入 AERIM 投递，当前只处理 CLICK。Engine 按 `lId` 读取所属推广链接的完整点击模板；同一广告主可用多条链接对应多个产品。上游监测地址必须通过后台维护的 AERIM 精确域名白名单和 DNS 公网校验，回调入口为 `GET /ocpx/aerim/callback`。

## 微风互动协议

只有配置为 `WEIFENG` 的广告主才进入微风互动投递，当前只处理 CLICK。关联推广链接的 `linkUrl` 保存商务提供的完整 `/click` 模板，其中 `aid/did` 是上游固定参数；Engine 只替换设备、时间戳和 `__CALLBACK__`，缺失固定 `aid/did` 或出现重复/未知宏时拒绝投递。上游地址必须通过后台维护的 WEIFENG 精确域名白名单和 DNS 公网校验，回调入口为 `GET /ocpx/weifeng/callback`。字段和事件映射详见《微风互动上游接入说明v1.3》。

## 引力协议

只有配置为 `GRAVITY` 的广告主才进入引力投递，且仅处理 CLICK。推广链接平台必须是 Android+IOS，基础地址不得预置 `idfa/oaid/android_id/imei/ip/ua/callback` 或 `{...}`；Engine 根据 Feedback `os` 生成参数，其中正确字段为 `{idfa}`。Callback 通过认证加密 `ctx` 恢复点击路由，并返回引力规定的 JSON。GRAVITY 不使用静态启用开关；空白名单拒绝投递，清空白名单可热停止。

## 人人视频协议

只有配置为 `RENREN_VIDEO` 的广告主才进入人人视频投递，且仅处理 CLICK。推广链接平台必须分别选择 Android 或 IOS；完整模板路径固定为 `/ad-action/click`，参数名不可重复，必须包含固定 `userId`、公共必填宏和正确的分平台设备宏。MD5 缺失但有原值时由 Engine 生成小写 32 位 MD5，`__PASS_THROUGH__` 只取推广链接 `linkExt`。HTTP/HTTPS 均需通过精确空白名单、公网 DNS 与 SSRF 检查；清空白名单可热停止。Callback 使用 `/ocpx/renren-video/callback?kid&ext&event`，不新增事实或报表口径。

## 凤凰新闻协议

只有配置为 `PHOENIX` 的广告主才进入凤凰新闻投递。Engine 按事件使用 `/d/imp` 曝光或 `/d/cli` 点击；推广链接的五个固定参数必须使用凤凰商务实际值，不能改写为 SCX `aId/mId`。Android 输出 `OS=0` 并要求 OAID/OAIDMD5，iOS 输出 `OS=1` 并要求 CAID（IDFA 可选），`REQID` 使用 `request_id`，`TS` 为 13 位毫秒时间戳，所有宏只编码一次。HTTP 200 且 JSON `code=0` 才算成功，10001–10007 记录稳定拒绝结论和脱敏响应摘要。Callback 使用 `/ocpx/phoenix/callback?kid&ext&event_type`，AES-GCM 密钥环不接受明文回退；事件映射和 `DOWNLOAD(20)`、`ACTIVITY(21)`、`ADDICTION(22)` 详见 `contracts/management-v127.md`。

## 发布门槛

发布前必须从目标库只读盘点全部广告主并逐个分类。存在任何未分类广告主、凭据无法解析、域名未进入白名单、链接宏或固定参数不合规时均为 No-Go。GRAVITY 的事件语义以 V120 契约为准；RENREN_VIDEO 必须在正式模板、固定 `userId` 和域名确认后再配置；PHOENIX 必须在 Android 模板、OAID、精确白名单和 Callback 密钥环确认后再配置。目标库操作、配置填充、部署、重启和第三方 UAT 仍需要独立授权与证据。
