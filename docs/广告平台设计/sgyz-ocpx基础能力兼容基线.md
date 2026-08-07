# SCX 对 `sgyz-ocpx` 基础能力兼容基线

## 1. 目标与边界

本基线把 `/Users/karl/code/sgyz-ocpx` 的 OCPX 核心业务语义作为 SCX 最低能力参考。兼容目标是端到端流程和扩展能力等价，不是复制旧系统的每一个私有 URL、参数名或硬编码渠道/DSP ID。

```text
运营配置渠道、广告主、应用链接、监测路由和扣量
  -> 下游媒体上报 SHOW/CLICK
  -> SCX 验签、幂等、可靠入队、事实落库
  -> 按 UpstreamAdapter 转发广告主
  -> 广告主 callback 转为统一转化事件
  -> 转化幂等、扣量决策、事实落库
  -> 按 DownstreamAdapter 回传媒体
  -> 失败重试、死亡任务、人工重放、小时报表
```

## 2. 能力覆盖矩阵

| `sgyz-ocpx` 基础能力 | SCX 实现 | 当前边界 |
|---|---|---|
| 广告主、渠道、应用链接、监测关系 | `scx-adx` 管理面 CRUD 与发布路由 | 已实现；只有 `PUBLISHED` 路由进入数据面 |
| 展示/点击接入 | 标准 HMAC `FeedbackReqV1` POST | 已实现；不直接兼容旧媒体无签名 GET 路径 |
| 展示/点击去重 | Redis Lua 幂等 + MySQL 唯一事实 | 已实现；比旧 Redis Hash 单层去重更可靠 |
| 高频缓冲与落库 | 64 分区 Redis Stream、Consumer Group、Pending/XAUTOCLAIM、DLQ | 已实现；目标容量仍需独立压测 |
| 按广告主协议转发 | `COCO` 与 V71 `TEMPLATE_GET` | 基础流程已覆盖；私有签名/加密/业务 code 需专用适配器 |
| 广告主转化 callback | `/upstreams/{upstreamCode}/callback` + `UpstreamAdapter.parseCallback` | 已实现；`ext` 必须由 SCX 签发并校验 |
| 转化事件词汇 | 激活、唤起、登录、注册、付费、获客、次留、访问、认证、下单、完单、留存/再激活 | V71 已补齐旧系统基础词汇；合作方枚举仍由专用适配器映射 |
| 按比例回传 | 原始 callback 事实 + Redis 原子比例决策 + MySQL 唯一决策 | 已实现；Redis 故障按当前设计放行并审计 |
| 按媒体协议回传 | `STANDARD` HMAC JSON POST、V73 `TEMPLATE_GET_DOWNSTREAM` 与 `DownstreamAdapter` SPI | JSON POST 与通用 GET 宏已覆盖；表单/专用签名需专用适配器 |
| 外发重试与失败治理 | 1/2/5/10 分钟、最多 5 次、DEAD、运行中心人工重放 | 已实现 |
| 报表 | 展示、点击及各转化事件小时聚合、扣量/回传/失败计数 | 已实现；成功 CPC 自动写点击成本，收入仍由外部事实提供 |
| 广告主报表隔离 | 登录账号绑定广告主，服务端注入范围 | 已实现 |
| RTA | `RtaDecisionPort` 扩展口 | 当前为 Disabled，路由发布强制 `OFF`；不宣称兼容百度/趣头条 RTA |
| 余额充值/扣费 | 幂等充值 UI/接口、统一账务流水、AUDIT_ONLY/CPC 点击计费 | 源码已实现；目标库 V72、初始余额、真实流量对账和生产启用未执行 |
| 汽车线索上报 | 已退出 SCX 产品边界 | V72 前向删除表、菜单和权限；历史 V68 仅作迁移审计保留 |

## 3. 当前接口映射

| 方向 | SCX 契约 |
|---|---|
| 下游媒体 -> SCX | `POST /rtb/open-api/ocpx/v1/channels/{channelCode}/feedback` |
| SCX -> 通用/Coco 广告主 | 已发布路由的 `bid_url`，由对应 `UpstreamAdapter` 构造 |
| 广告主 -> SCX | `GET /rtb/open-api/ocpx/v1/upstreams/{upstreamCode}/callback` |
| SCX -> 下游媒体 | 路由的 `downstream_callback_url`，由对应 `DownstreamAdapter` 构造 |

后台监测记录的“展示上报接口”和“点击上报接口”均指向同一个 V70 POST endpoint，事件由 JSON body 的 `event` 区分；它们不再生成旧式 `?monitorId=...&event=...` GET 地址。

## 4. 新合作方接入规则

1. 只有纯 GET 宏替换、HTTP 2xx 成功、基础 query callback 的广告主可使用 `TEMPLATE_GET`。
2. 任何私有签名、AES/RSA、POST body、特殊业务 code、来源验签或事件枚举都必须新增专用 `UpstreamAdapter`。
3. 媒体只能接收通用 HTTPS GET 宏回传时可使用 `TEMPLATE_GET_DOWNSTREAM`；私有签名、表单或业务成功码必须新增专用 `DownstreamAdapter`。
4. 专用适配器必须提供协议文档、固定签名向量、单元测试、WireMock 集成测试和双方 UAT。
5. 仓库已提供仅暴露 `/rtb/open-api/` 的宝塔和容器 Nginx 模板；未在目标环境应用模板、未迁移 V70-V73、外发开关关闭或未完成外部 UAT时，只能称为源码能力，不能称为已上线兼容。

## 5. 放行结论

- 源码基础流程兼容：完成后以本轮测试结果为准。
- 旧系统所有媒体/DSP 协议逐一兼容：未完成，需要按正式协议逐适配器实施。
- RTA：仍为 Disabled；汽车线索不再是交付范围。
- 真实计费：源码已实现，生产未放行；须完成 V72、余额/账务核对、灰度和对账。
- 生产切换：No-Go，必须先完成目标库 V70-V73、应用边缘 `/rtb/open-api/` 模板、真实上下游 UAT、计费与故障恢复演练和容量验收。
