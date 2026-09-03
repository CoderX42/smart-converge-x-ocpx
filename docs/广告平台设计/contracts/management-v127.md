# 凤凰新闻 CPA 上游协议契约 V127

## 配置边界

- 广告主的 `upstream_protocol` 可选择 `PHOENIX`；协议允许域名由现有“协议与域名”接口维护。
- Phoenix 的唯一允许主机是 `api.feather.ifeng.com`。V127 不预置广告主、推广链接、监测任务、凭据或线上白名单数据。
- 推广链接可选择 Android（`platform=1`）、IOS（`platform=2`）或 Android+IOS（`platform=3`）；渠道号和唤起链接按实际投放填写，协议不对其臆造固定值。
- 点击模板必须是 HTTPS 的精确 `/d/cli` 地址，保留凤凰商务实际分配的静态 `cid/gid/aid/mid/channel`，并通过后端严格宏校验。SCX 不把固定 `aid/mid` 映射为自身的 `aId/mId`。曝光投递使用同一模板的 `/d/imp` 路径并省略 callback。

## 点击投递

- Engine 投递 `IMP` 与 `CLICK`；`REQID` 使用 SCX `request_id`，`OS` 为 `0`（Android）或 `1`（iOS），`TS` 输出 13 位毫秒时间戳。
- Android 必须提供 OAID 与 OAID MD5；iOS 必须提供 CAID，IDFA 可选；缺少 MD5 时由原始标识计算大写 MD5。所有宏值只 URL 编码一次。
- 出站地址必须命中精确白名单，并通过 DNS 公网地址和 SSRF 检查。HTTP 200 且 JSON `code=0` 才记为成功；10001–10007 记录稳定拒绝结论与脱敏响应摘要。

## Callback

公网唯一新增入口为 `GET /ocpx/phoenix/callback?kid=...&ext=...&event_type=...`。`ext` 使用当前或历史 `kid` 的 AES-GCM 密钥环解密，生产环境不提供明文回退；解密后的广告主、推广链接、监测任务、媒体和四实体关系必须仍为 Phoenix 路由。

| `event_type` | SCX 标准动作 |
|---|---|
| 缺省（按 `active`） | `ACTIVATE(1)` |
| `recall` | `EVOKE(2)` |
| `register` | `REGISTER(4)` |
| `pay` / `pay_amount` | `PAYMENT(5)`，金额为空 |
| `next_day` | `NEXT_STAY(7)` |
| `first_order` | `FIRST_ORDER(19)` |
| `download` | `DOWNLOAD(20)` |
| `activity` | `ACTIVITY(21)` |
| `addiction` | `ADDICTION(22)` |

事件原字符串写入 Callback 事实；空字符串和未知值拒绝。合法、重复、非目标事件和扣量成功均返回固定 `{"code":0,"msg":"success","data":""}`；参数/解密/路由错误返回 400，数据库或 Redis 故障返回 500。`scx_ocpx_event_fact` 是唯一事实、计费和扣量权威。

## 数据库与发布

`V127__add_phoenix_upstream_protocol_and_events.sql` 是不可重放的前向 DDL：扩展协议/动作约束，增加小时指标和兼容报表的三个动作列；同步当前 Schema、校验脚本、OpenAPI、JSON Schema 和 Postman。执行前必须备份并人工审批，源码和本地测试不代表目标库已经迁移。

公网 Nginx 只在宝塔生产 OCPX 站点精确开放 `/ocpx/phoenix/callback`，仅允许 GET，`proxy_pass` 不带 URI；不新增 `/d/cli` 或 `/d/imp` 入站路由，也不改 IP-UAT、容器 Nginx 或前端站点。生产配置、迁移、重启和真实合作方 UAT 需分别留存证据。
