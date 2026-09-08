# 微风 RTA 生产接入契约 V130

V130 在 V128 的 RTA 框架上增加独立的 `WEIFENG_RTA` 决策适配器。V128 的
`management-v128.md` 和 `rta-mock-example.md` 保留为 Mock 历史契约；本文件只描述
微风适配器及其生产边界。V130 不实现微风刹车接口，也不在 IMP 到达时自动发起 RTA。

## 请求与时序

调用方仍按以下顺序使用同一个、不可替换的 `requestId`：

```text
POST /rta → 微风 sendRta → ALLOW → IMP(requestId) → CLICK(requestId)
```

`/ocpx/rta` 是 Engine 的内部入口；正式 OCPX 站点的 `/rta` 只是 Nginx 别名。启用
`rta_enabled=1` 的任务会同时在 IMP 和 CLICK 检查 ALLOW admission。缺少 requestId、
仍为宏占位符、过期、错任务、错路由或跨任务伪造均拒绝，并且不会创建曝光/点击事实、
计费或下游投递。`rta_enabled=0` 的旧任务保持兼容行为。

## Profile 管理

`adapterCode` 只能是 `MOCK` 或 `WEIFENG_RTA`。`configJson` 保持字符串接口，以兼容
已有管理 API；微风 Profile 只允许下列键，不能保存 endpoint、Token、签名密钥或其他
供应商字段：

```json
{"aid":"5103","did":"zsyd","timeoutMs":3000}
```

- `aid`：1～32 位 ASCII 数字；`did`：1～64 位 URL-safe ASCII 字符（字母、数字、
  `.`, `_`, `-`）。两者由管理员维护，不从 SCX 路由 ID 推导。
- `timeoutMs` 可选，范围 100～5000，默认 3000；请求不重试。
- Endpoint 固定为 `https://rta.adapi.cn/rtaCommon/sendRta`，前端不可编辑。
- 任务启用前必须绑定 ACTIVE Profile 且至少有一个有效 `externalTaskId`；Profile 或
  适配器开关关闭时失败关闭。

每个 Redis admission 还保存签发时的 Profile `version`、配置 SHA-256 和启用绑定集合
指纹（仅为非敏感元数据摘要）。IMP/CLICK 门禁会逐项比对当前值；Profile、aid/did、适配器
开关或任一绑定发生变化后，旧 ALLOW 立即失效。V130 以前没有这些摘要的 Redis ALLOW
一律按 `RTA_ADMISSION_STALE` 失败关闭。Redis admission 过期后从历史事实恢复幂等结果时，
服务以事实 `received_at` 与当前 Profile/绑定 `update_time` 比对；无法证明未发生变更时
同样失败关闭，因此无需向事实表追加版本列。

## 微风请求映射

适配器只在单次 HTTP 请求生命周期中构造供应商 JSON；RTA 决定、admission 以及
RTA-protected 的 Feedback 兼容队列/流程日志均不得保存原始设备号。非 RTA 旧任务仍
遵循既有历史日志契约，不因 V130 回填或改写历史数据。

| SCX 字段 | 微风字段 | 规则 |
|---|---|---|
| `requestId` | `id` | 原值，响应必须完全回显 |
| `context.os` | `device.os` | 必填，统一为 `ios` 或 `android` |
| `idfa` / `idfaMd5` | `idfa` / `idfa_md5sum` | 缺 MD5 时按原值 UTF-8 计算 |
| `imei` / `imeiMd5` | `imei` / `imei_md5sum` | 原值和小写 MD5 均可按存在性发送 |
| `androidId` / `androidIdMd5` | `androidId` / `android_id_md5sum` | 原值和小写 MD5 均可按存在性发送 |
| `oaid` / `oaidMd5` | `oaid` / `oaid_md5sum` | 缺 MD5 时按原值 UTF-8 计算 |
| `macMd5` | `mac_md5sum` | 微风未定义原始 MAC，绝不发送 `mac` |
| `preCaid`、版本、MD5 | `caid`、`caid_version`、`caid_md5` | 旧 CAID 映射（微风 v1.3 的 CAID 哈希字段名不带 `sum`） |
| `caid`、版本、MD5 | `caid_new`、`caid_new_version`、`caid_new_md5` | 当前 CAID 映射（微风 v1.3 的 CAID 哈希字段名不带 `sum`） |
| `context.ip`、`ipv6`、`ua`、`model` | 同名字段 | 空值省略 |

已有 MD5 只做格式校验和大小写规范化；缺少 MD5 且存在原值时，以 UTF-8 原值计算
小写十六进制 MD5。空值省略。`aaid`、`osv`、`attributes` 以及微风文档未定义的
字段不得发送。新旧 CAID 字段名需在真实上游联调时再次确认，联调前不得放量。

## 响应与失败关闭

微风响应必须是 HTTP 2xx 且可解析为 [微风响应 JSON Schema](weifeng-rta-response-v1.schema.json)
的 `{id:string,code:int}`；`id` 必须与请求 ID 完全一致。`code=0` 映射 `ALLOW`，并将
当前 Profile 下全部有效 `externalTaskId` 写入 `matchedTaskIds`；非 0 映射 `DENY`。

超时记录 `TIMEOUT`；连接失败、非 2xx、非法 JSON 或响应 ID 不一致记录 `ERROR`；
非法 OS、Profile 或字段校验记录 `REJECTED`。这些情况均返回 `DENY`，不自动重试。
只有 Redis、MySQL 或 Stream 不可用才返回现有 503。相同任务和 requestId 沿用既有
幂等和 admission TTL；不同规范化请求复用 requestId 返回 409。

## 网关、隐私与出站安全

正式 `/rta` 别名仅允许网关或明确的内网来源。网关复用旧版 `api.sign`：请求头为
`X-Timestamp`、`X-Nonce`、`X-Sign`，签名原文为 `timestamp+nonce` 的 HMAC-SHA256，
并用 Redis nonce 防重放。Nginx 生产模板中的 RTA location 必须安装实际网关 CIDR
allowlist；IP-UAT、前端站点和 Engine 监听地址不得暴露该别名。签名密钥只由部署环境
保管，不进入数据库、前端或聊天记录。

微风客户端禁止重定向，固定 HTTPS 域名，校验解析结果为公网地址并限制响应体大小。
用户已授权原始设备标识仅向微风适配器出站；上线前仍需完成隐私/合规确认和供应商字段
联调。

## 迁移与发布证据

`V130__add_weifeng_rta_adapter.sql` 仅放宽 `scx_ocpx_rta_profile.adapter_code` CHECK，
不得修改或重放 V128/V129。执行前备份数据库、预览 DDL 并记录 SQL SHA-256；执行后检查
`information_schema`、运行 `verify-database.sh` 和 `make contract-check`，再重新打包并
重启 Boot/Engine。完成签名请求和获授权测试设备 smoke 后，才可将目标任务/Profile
切换为 ACTIVE。异常时先关闭任务/Profile，再关闭 `app.rta.weifeng-enabled`，必要时
恢复 Nginx 路由。
