# scx-ocpx-contract

`scx-ocpx-contract` 是 SCX OCPX 引擎的稳定协议与扩展契约模块。它定义上下游报文、规范化事件和适配器端口，不包含 Web 接入、数据库、Redis、线程池或投递实现。

## 主要职责

- 定义 `FeedbackReqV1`、`CallbackReqV1` 等版本化协议对象。
- 定义 `NormalizedFeedbackEvent`、`NormalizedCallbackEvent` 等内部规范事件。
- 定义 `UpstreamAdapter`、`DownstreamAdapter`、`RtaDecisionPort`、`BillingPort` 扩展端口。
- 定义适配器 HTTP 请求/响应、能力描述和发布路由快照。

## 依赖边界

依赖方向固定为：

```text
scx-ocpx-engine -> scx-ocpx-runtime -> scx-ocpx-contract
```

本模块不得依赖 `scx-ocpx-runtime`、`scx-ocpx-engine`、Spring Web、JDBC、Redis 或具体渠道 SDK。新增渠道时应优先复用现有 DTO 和 SPI；只有公共协议确实需要演进时才修改本模块，并保持版本兼容或显式升级版本。

## 开发与验证

```bash
cd scx-backend
mvn -pl scx-ocpx-contract -am test
```

对外 OpenAPI、AsyncAPI、JSON Schema、Postman 和 UAT 工具位于 `docs/广告平台设计/contracts/` 与 `docs/广告平台设计/uat/`，修改协议后还必须在仓库根目录执行：

```bash
make contract-check
```

真实域名、渠道编码、密钥版本和密钥不得写入本模块、测试夹具或对外契约包。
