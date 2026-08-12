# ADR-009：OCPX Engine 采用 GET-only 双向 Adapter 框架

- 状态：Superseded by ADR-010
- 日期：2026-08-12
- 范围：`scx-ocpx-engine` 数据面扩展接口
- 补充：ADR-008 的传统分层与上下游边界

## 背景

Engine 是广告效果事件中转站：下游媒体把点击或曝光交给平台，平台转给上游广告主；上游广告主产生转化后回调平台，平台再回传下游媒体。项目负责人确认该 OCPX 链路的四个方向全部只使用 HTTP GET，并要求参考旧 `sgyz-ocpx` 的双向抽象思路，但不继承其静态枚举路由、Adapter 内直接 HTTP、手工线程池和整包点击参数加密回调方式。

## 决策

- `AbstractUpstreamAdapter` 负责标准点击与广告主协议之间的转换，以及解析广告主转化回调；`AbstractDownstreamAdapter` 负责媒体点击/曝光解析与媒体转化回传协议转换。
- Adapter 只处理协议，不访问 HTTP、Redis 或 MySQL。出站 HTTP 统一经过只暴露 `get` 的 `PartnerGetClient`。
- 入站和出站请求类型不包含 HTTP Method 或 Body，只保留路径、请求头和多值 Query 参数；Query 参数通过 URI Builder 编码，不手工拼接。
- 上下游注册表从 Spring Adapter Bean 构造，按配置中的 `adapterCode` 路由。编码统一转为大写，重复编码启动失败，未知编码直接拒绝，不设置通用默认 Adapter。
- 标准事件只覆盖 `IMPRESSION`、`CLICK`、`ACTIVATE`、`REGISTER`、`PAY`。回调 URL 只携带紧凑点击引用，原点击由 `CallbackReferenceStore` 恢复。
- Redis、配置查询、`scx_ocpx_flow_log` 写入和业务编排只定义接口。本次不增加真实路由、具体合作方实现、Redis Key、数据库表或生产配置。
- `scx-boot` 的组件扫描精确排除 Engine 启动类及 `adapter`、`client`、`config`、`controller`、`dto`、`mapper`、`redis`、`service` 数据面包；继续扫描 `ocpx.admin`，并复用四实体。

## 后果

新增合作方时分别继承一个 Adapter 抽象类并注册 Spring Bean，无需修改中心枚举或 `switch`。GET-only 约束由请求类型、HTTP 端口、静态门禁和架构测试共同固定。基础框架可以独立启动和测试，但在 Controller、Service、Redis、Mapper 与 HTTP 实现补齐前，不能视为双向业务链路已经交付。
