# V115 数据库驱动的上游协议与允许域名配置

## 1. 目标

- 新建或编辑广告主时直接选择 `TEMPLATE_GET`、`AERIM` 或 `WEIFENG`，不再维护 Engine yml 中的广告主 ID 映射。
- AERIM 与 WEIFENG 的 `allowed-hosts` 由管理后台维护，写入 MySQL 后通过配置刷新事件同步给 Engine。
- AERIM 与 WEIFENG 均以 Feedback 的 `lId` 读取推广链接地址；同一广告主可创建多个产品推广链接。

## 2. 管理接口

- `GET /boot/ocpx/protocol-configs`：管理员读取 AERIM、WEIFENG 当前允许域名。
- `PUT /boot/ocpx/protocol-configs/{protocol}/allowed-hosts`：全量替换指定协议允许域名。
- 两个接口都要求 Sa-Token、真实管理员身份和 `ocpx:protocol-config:manage` 权限。
- `allowedHosts: []` 合法，表示该协议全部拒绝；前端保存前必须二次确认。

## 3. 安全约束

- 只接受精确域名，不接受协议头、路径、端口、通配符、IP 地址或单标签主机名。
- Engine 在发起请求前同时校验数据库白名单与 DNS 解析结果；解析到环回、链路本地、站点本地或私网地址时拒绝请求。
- 白名单不存在、广告主协议为空、推广链接不存在或不属于当前广告主时均拒绝上报。
- 上游鉴权凭据仍保留在 Engine 服务端配置，不通过管理接口或浏览器维护。

## 4. 升级规则

- `V115__database_driven_upstream_protocol_config.sql` 新增广告主协议字段和协议允许域名表。
- 增量 SQL 迁移 V114 yml 中已经登记的五个广告主协议，并写入三条现有允许域名；其他存量广告主保持协议为空，升级后必须由管理员显式选择，避免错误上报。
- Engine yml 不再包含 `ocpx.protocols.advertisers`、`ocpx.aerim.allowed-hosts` 或 `ocpx.weifeng.allowed-hosts`。

## 5. 业务模型

- 广告主表示业务、账户和权限主体；推广链接表示具体产品或投放入口。
- 同一 AERIM 广告主可为多个产品创建多条推广链接，各自保存不同的 `/ocpx/c/{token}` URL；Feedback 必须携带对应 `lId`。
- 只有需要独立账户、权限、余额或结算时，才应拆成多个广告主。
