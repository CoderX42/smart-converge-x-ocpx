# SCX OCPX Engine 重建状态说明

## 当前源码状态

`scx-ocpx-engine` 已清空原双向转发实现，当前只保留四实体和最小 Spring Boot 启动骨架：

| 实体 | 业务含义 | 数据库表 |
|---|---|---|
| `OcpxAdvertiser` | 上游广告主 | `scx_ocpx_advertiser` |
| `OcpxPromotionLink` | 推广链接 | `scx_ocpx_promotion_link` |
| `OcpxMedia` | 下游媒体 | `scx_ocpx_media` |
| `OcpxMonitorTask` | 监测任务 | `scx_ocpx_monitor_task` |

四个类直接位于 `com.yzytech.scx.ocpx`，由 `scx-ocpx-admin` 的 Mapper、Service 和 Controller 复用。

启动类为 `com.yzytech.scx.ocpx.ScxOcpxEngineApplication`，默认监听 `10000`，WebFlux context path 为 `/ocpx`，健康检查为 `GET /ocpx/actuator/health`。

## 当前明确不存在

- Feedback、Callback、监测跳转等机器接口。
- 协议 DTO、Adapter SPI 或合作方适配器。
- 鉴权、签名、凭据解析、外呼和失败补偿。
- Engine profile 凭据配置、日志配置和协议密钥资源。
- 除启动与默认配置验证外的数据面业务测试。

历史 OpenAPI、JSON Schema、ADR 和模块规格只能作为后续需求输入，不能据此声称数据面已实现。

## 重建边界

后续重新实现数据面时，应基于这四个实体重新明确机器接口、上下游协议、安全凭据、幂等与失败语义、数据保留、容量、部署和验收门禁。未经新的实现任务与验证，不恢复历史运行代码。
