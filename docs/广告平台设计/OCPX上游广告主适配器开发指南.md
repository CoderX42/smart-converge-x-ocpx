# SCX OCPX 上游广告主适配器开发指南

> 适用版本：V70 / OCPX Contract v1。本文面向 SCX 研发人员，用于接入 Coco 之外的广告主、代理平台或转化归因平台。新增上游不应复制一套接入、队列或重试服务，只实现协议差异并注册为 `UpstreamAdapter`。

## 1. 接入边界

SCX 把稳定流水线与第三方协议分开：

```text
FeedbackReqV1
  -> 标准化、幂等、Redis Stream、MySQL 事实
  -> UpstreamAdapter.buildFeedback()
  -> 上游 feedback API

上游 callback API
  -> /open-api/ocpx/v1/upstreams/{upstreamCode}/callback
  -> ext 校验与原路由快照恢复
  -> UpstreamAdapter.parseCallback()
  -> 扣量决策、CallbackReqV1、下游投递
```

适配器只负责以下协议差异：

- 上游支持哪些事件。
- feedback 使用 GET、JSON POST 或表单 POST。
- 字段、宏、签名和鉴权如何转换。
- 哪些 HTTP 状态和响应体代表业务成功。
- callback 参数如何映射为 SCX 规范事件。
- 上游业务回调唯一键、金额与时间单位如何解释。

适配器不得自行创建线程池、队列、数据库事务、重试任务或扣量记录。这些能力由 `scx-ocpx-runtime` 统一提供。

## 2. 对接前必须取得的协议资料

没有下列信息时只能开发 Mock，不能启用真实外发：

| 分类 | 必须确认的内容 |
|---|---|
| 环境 | 沙箱/生产 base URL、HTTPS 证书、DNS、IP 白名单 |
| feedback | 方法、Content-Type、字段/宏、必填规则、编码、单次/批量 |
| 鉴权 | Header/Query/Body 签名算法、密钥版本、时间窗、防重放规则 |
| 成功语义 | HTTP 状态、业务 code、响应字段；不能默认所有 2xx 都成功 |
| callback | 方法、参数、签名、来源 IP、成功响应、第三方重试策略 |
| 事件 | 点击、激活、注册、留存、付费等事件的精确定义与时间窗口 |
| 唯一键 | callback ID、订单号或事件 ID；是否可能存在同金额多次付费 |
| 金额 | 元/分/厘、币种、含税规则、小数位、退款/冲正 |
| 容量 | QPS、并发、超时、限流响应、日调用量、维护窗口 |
| 合规 | 允许传输的设备标识、哈希算法、数据保留、删除要求 |

每个结论应写入该上游的协议说明或 UAT 记录。口头约定不能作为生产成功条件。

## 3. 命名与配置

- `adapter_code` 使用稳定的大写 ASCII，例如 `TENCENT_ADS`，发布后不得改变语义。
- Java 类建议命名为 `TencentAdsUpstreamAdapter`。
- callback 地址固定为：

```text
https://{SCX_RTB_DOMAIN}/rtb/open-api/ocpx/v1/upstreams/{lowercase-adapter-code}/callback
```

- 广告主记录配置该 `adapter_code`、上报 URL/模板、密钥、必填宏和精确域名白名单。
- 监测链接绑定渠道与广告主。发布后生成不可变路由版本；历史事件继续使用接收时的路由快照。
- 密钥只写入后端配置/管理端密钥字段，接口只返回“已配置”，禁止放入前端、日志、URL 诊断参数或 Git 文档。

## 4. 实现 UpstreamAdapter

接口位于：

```text
scx-backend/scx-ocpx-contract/src/main/java/com/yzytech/scx/ocpx/contract/UpstreamAdapter.java
```

实现类放在：

```text
scx-backend/scx-ocpx-runtime/src/main/java/com/yzytech/scx/ocpx/runtime/adapter/
```

最小骨架：

```java
@Component
public class ExampleUpstreamAdapter implements UpstreamAdapter {

    @Override
    public String code() {
        return "EXAMPLE";
    }

    @Override
    public AdapterCapabilities capabilities() {
        return new AdapterCapabilities(
            "UPSTREAM",
            Set.of(OcpxEventType.CLICK),
            Set.of("POST"),
            Set.of("HMAC"),
            Duration.ofSeconds(3),
            Set.of("__CLICKID__", "__CALLBACK__"),
            true
        );
    }

    @Override
    public boolean supports(OcpxEventType eventType) {
        return eventType == OcpxEventType.CLICK;
    }

    @Override
    public AdapterHttpRequest buildFeedback(
            RouteSnapshot route,
            FeedbackReqV1 feedback,
            String callbackUrl) {
        // 只构造协议请求；不要在这里发 HTTP、写库或启动异步任务。
        throw new UnsupportedOperationException("implement protocol mapping");
    }

    @Override
    public boolean isSuccess(AdapterHttpResponse response) {
        // 同时校验 HTTP 与已确认的业务 code。
        return response.statusCode() == 200;
    }

    @Override
    public NormalizedCallbackEvent parseCallback(
            String callbackRef,
            Map<String, String> parameters) {
        // 校验必填参数、映射事件、生成稳定事件 ID、解释时间和金额。
        throw new UnsupportedOperationException("implement callback mapping");
    }
}
```

Spring 会收集所有 Bean，由 `AdapterRegistry` 按 `code()` 建立不可变 Map。核心流水线不需要增加 `if/else` 或广告主 ID 枚举。

### 4.1 buildFeedback 规则

1. 只处理 `supports()` 声明支持的事件。
2. 参数值逐个进行符合第三方规范的 URL/JSON/表单编码，禁止整条 URL 字符串拼接后再整体编码。
3. 缺失可选宏可置空；缺失必填宏必须抛出受控异常，让统一投递任务进入重试/死亡任务。
4. callback 必须使用传入的 `callbackUrl`，不能自行把完整 `FeedbackReqV1` 放入 URL。
5. callback 的 `ext` 是 SCX 签发的不可猜测引用；上游返回的业务参数不能替代它决定路由。
6. 对 Header/Body 签名时使用广告主当前密钥和版本；不得记录签名原文中的设备标识。
7. 只返回 `AdapterHttpRequest`；SSRF、超时、连接池、熔断、并发隔离和重试由统一 HTTP 层处理。

### 4.2 isSuccess 规则

如果上游采用 `HTTP 200 + {"code":500}` 表示失败，就必须解析响应体并返回 `false`。只有以下内容均明确后才能写成功判断：

- 可接受的 HTTP 状态。
- 业务 code/字段。
- 空响应、非 JSON、重复请求的语义。
- 限流、鉴权失败、参数错误是否可重试。

当前统一任务最多投递 5 次，退避为 1/2/5/10 分钟。若某上游要求其他策略，应先形成 ADR/RFC，不要在适配器内部休眠重试。

### 4.3 parseCallback 规则

- 校验第三方必填参数与签名，但路由关系只能由已验证的 `callbackRef` 恢复。
- 映射到 `OcpxEventType`，不得把未知事件静默映射为任意已知事件。
- `eventId` 优先使用上游稳定 callback ID/订单号，并纳入上游标识；没有唯一键时必须制定可解释的指纹规则。
- 同一订单的重试必须得到同一 `eventId`；不同订单即使金额相同也必须不同。
- `actionTime` 使用上游真实发生时间并明确时区，不应无条件使用服务器接收时间。
- 金额同时保留 `amountRaw`；只有金额单位、币种和精度确认后才填写规范 `amount`。
- 无效请求抛出 `IllegalArgumentException`；入口会返回该上游约定的失败结果，不得先返回成功再异步丢弃。

## 5. 注册与发布

新适配器代码完成后，还需要在 `scx_ocpx_adapter` 注册能力。建议使用新的前向迁移，不修改已发布的 V70：

```sql
INSERT INTO scx_ocpx_adapter
  (adapter_code, adapter_name, direction, status, capabilities_json, create_time, update_time)
VALUES
  ('EXAMPLE', 'Example 广告主', 'UPSTREAM', 'ACTIVE',
   JSON_OBJECT(
     'feedbackMethod', 'POST',
     'callbackMethod', 'GET',
     'auth', 'HMAC',
     'callback', TRUE,
     'timeoutMs', 3000
   ), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  adapter_name=VALUES(adapter_name),
  status=VALUES(status),
  capabilities_json=VALUES(capabilities_json),
  update_time=NOW(3);
```

随后：

1. 管理端创建/更新广告主，选择 `EXAMPLE`。
2. 填写沙箱 URL、密钥、必填宏和精确 host 白名单。
3. 创建监测链接，保持 `DRAFT`。
4. 发布校验通过后生成新 `config_version` 和配置 Outbox。
5. `scx-rtb` 收到通知后原子替换本地路由索引；60 秒轮询兜底。

代码 Bean 与数据库记录必须同时存在。只有数据库记录会在运行时找不到 Bean；只有 Bean 会在管理端发布校验失败。

## 6. 必须补充的自动化测试

每个上游适配器至少覆盖：

- 每一个支持事件及不支持事件。
- 所有宏/字段映射、空值、中文、空格、`+`、`&`、`?` 和 Unicode 编码。
- 鉴权签名固定向量测试。
- 2xx 业务成功、2xx 业务失败、4xx、5xx、超时和异常响应体。
- callback 新增、重复、参数缺失、签名错误、过期/篡改 `ext`。
- 事件类型映射、未知事件、业务时间与时区。
- 多笔同金额付费、重复订单、金额负数/超长/精度/单位。
- 路由发布后变更，历史 callback 仍使用原路由快照。
- 日志中不出现完整 URL、设备 ID、密钥、签名或 callback 参数。

协议单测之外，使用 WireMock 完成 feedback 与 callback 双向集成测试。真实 UAT 不能由 WireMock 替代。

## 7. 沙箱与生产放行顺序

1. 外发关闭：只验证下游 feedback 接收、标准化、入队和落库。
2. 打开上游沙箱 feedback：核对请求、成功语义、限流和重试。
3. 开放上游 callback：保持下游外发关闭，核对事件映射与幂等。
4. 打开下游影子/Mock 回调，核对扣量决策和报表。
5. 单路由灰度真实下游 callback。
6. 完成对账、故障演练、容量验收和双方签字 UAT 后申请生产发布。

数据库迁移、真实外发开关、第三方生产凭据和生产部署均需要单独授权。

## 8. Coco 可复用与不可复用部分

可直接复用：

- `UpstreamAdapter` SPI 与 Spring Bean 注册。
- Redis/MySQL 可靠链路、callback 引用、统一重试、DLQ、扣量与审计。
- HTTPS/域名白名单/私网 DNS/禁止重定向校验。
- `CocoUpstreamAdapter` 的宏替换、URL 编码和测试组织方式。

不能直接照搬：

- Coco 只支持 CLICK 的限制。
- “任意 HTTP 2xx 成功”的临时判断。
- Coco 的事件名称和 `currency_amount`。
- 无业务 callback ID 时使用参数指纹的退化方案。
- Coco 当前无鉴权的能力声明。

每个新上游都必须依据其正式协议重新确认这些差异。
