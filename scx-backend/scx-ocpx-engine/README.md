# scx-ocpx-engine

`scx-ocpx-engine` 是独立启动的 OCPX 数据面应用。当前实现按
`/Users/karl/code/sgyz-ocpx/vientiane-rtb` 的通用 Feedback/Callback 链路迁移，
只做 Java 17 / Spring Boot 3、SCX 包名、字符串主键/表名和本机脱敏配置适配。

## 四实体与请求 ID

| Query 字段 | SCX 实体主键 | 表 |
|---|---|---|
| `mId` | `OcpxMedia.id` | `scx_ocpx_media` |
| `tId` | `OcpxMonitorTask.id` | `scx_ocpx_monitor_task` |
| `aId` | `OcpxAdvertiser.id` | `scx_ocpx_advertiser` |
| `lId` | `OcpxPromotionLink.id` | `scx_ocpx_promotion_link` |

这四个字段始终是 `String`，直接承载 SCX 的 `VARCHAR(32)` 主键，不嵌入实体对象。
`aId` 不等于 `adv_id`：前者是 SCX 广告主主键，后者是广告主侧广告 ID。四个短主键
均为必填；缺少任一字段或混入旧路由字段时，Feedback 入口返回 `400`。
四个实体位于独立的 `scx-ocpx-core` 模块，继续使用
`com.yzytech.scx.ocpx.entity` 包名；`scx-ocpx-admin` 与 Engine 分别依赖 Core。

## 功能包结构

Engine 运行代码按业务功能聚合，旧的 `bean/controller/manager/service/dao` 半分层包
不再保留：

```text
com.yzytech.scx.ocpx
├── feedback       # Feedback 入站、处理和广告主转发
├── callback       # Callback 入站、扣量和媒体回传
├── protocol       # Feedback/Callback 链路共用的协议转换
├── report         # 日志模型、Mapper、报表服务和 StorageTask
├── configuration  # 四实体配置 Mapper、内存索引、启动加载和刷新任务
└── shared         # 常量、枚举、Redis 与通用工具
```

Engine 内部类型按 Feedback 入站、上游 Callback、模板 GET 协议和上下游投递职责命名；
Controller 依赖请求适配器抽象，Dispatcher 依赖投递服务抽象。该调整不改变接口路径或
运行行为。分包决策见
`ADR-011-OCPX-Engine业务功能式分包.md`。

## 运行与接口

应用使用 Spring MVC + Undertow，默认监听 `10000`，Servlet context path 为
`/ocpx`，健康检查为 `GET /ocpx/actuator/health`。

- `GET /ocpx/feedback`：接收媒体曝光/点击，保持原
  `event/request_id/callback_url/caid/idfa/imei/oaid/ip/os/ts/ua` 等 Query、宏清理、
  请求 ID MD5 回退和成功 JSON。
- `GET /ocpx/callback`：接收广告主转化，保持 `ext/s/event_type/event_time`；
  成功返回空 `200`，解析异常返回 `500`。
- `GET /ocpx/aerim/callback`：接收 AERIM 的 `action_type/currency_amount`；
  成功、重复和扣量均返回空 `200`，协议参数非法返回 `400`，内部异常返回 `500`。

正向链路为：Feedback 参数转换 → Redis 去重计数 → Feedback 队列 → 按广告主主键
选择协议 → 按 `OcpxAdvertiser.bidUrl + macroParams` 替换宏 → 广告主 GET。通用模板
使用 `credentialConfigKey` 追加 MD5 `signature`；AERIM 只处理点击，生成
`/aerim/callback` 地址，不追加签名，并要求 HTTP/HTTPS 监测域名命中
`ocpx.aerim.allowed-hosts`。白名单只填写域名，不填写协议或路径。

反向链路为：解密 `ext` → Callback 去重 → Callback 队列 → 按
`lId + mId` 读取 Redis 扣量数组 → 对允许回传的数据解码原 `callback_url`、
追加 `event_type` 与可选 `currency_amount` 后执行媒体 GET。AERIM 的九类字符串动作
映射为 SCX 编号 `1/2/4/5/6/7/11/18/19`，金额只允许随 `purchase` 出现。

配置由四个 Mapper 启动加载到 `ConcurrentHashMap` 索引。Admin 写入
`r:da:refresh` 后，Engine 的分钟任务等待五秒、重载四索引并删除标记。

## Redis、落库和报表

Feedback、Callback 和报表队列继续使用 `r:u:rpe:`、`r:cl:rpe:`、
`r:da:report:`、`r:da:report:cb:`、`r:da:refresh`。推广链接与媒体维度的
扣量状态使用 `ocpx:route:ratio:{lId}:{mId}`、
`ocpx:route:original-count:{lId}:{mId}` 和
`ocpx:route:delivered-count:{lId}:{mId}`；不兼容历史 `adm:u:dsp:*` Key。

`StorageTask` 每分钟获取十分钟锁，每次执行 `LRANGE 0 1000`，依次写明细、
日明细和临时报表，全部成功后才 `LTRIM`。数据表为：

- `scx_ocpx_log_feedback`
- `scx_ocpx_log_feedback_day`
- `scx_ocpx_log_callback`
- `scx_ocpx_report_mat_temp`
- `scx_ocpx_report_mat`

Admin 的 `OcpxDataMergeTask` 每分钟合并临时报表为 `mark=0`，每小时第十分钟把
历史 `mark=0` 合并为 `mark=1`。前向 SQL 是
`V97__add_ocpx_legacy_data_layer.sql`，源码变更不会执行数据库变更。

## 有意保留的旧行为

- Feedback 重复事件仍入队并转发上游，只影响去重指标。
- 两个 Delivery Dispatcher 均使用 `500/500` 固定线程、容量 `100` 和
  `CallerRunsPolicy`，异步异常按原方式吞掉。
- Redis 扣量数组长度为 `19`，读取历史 11 位状态时自动补零；读改写仍非原子。
- 单个 List 批次的数据库多表写入使用本地事务；Redis 读取与裁剪之间仍无事务，也没有
  processing 队列、所有者锁校验、持久重试或 DLQ。
- 不迁移爱奇艺、百度、OPPO、华为、快手等合作方专用 Controller、枚举或协议。

## 运行配置

公共配置位于 `application.yml`，默认激活 `dev`：

- `application-dev.yml`：本机 MySQL、Redis、回调域名和本机联调 AES Key；IntelliJ
  不指定 profile 时也使用该配置。
- `application-prod.yml`：线上 MySQL、Redis 和公网回调域名；生产数据库密码、Redis
  密码和 AES Key 必须由生产使用者在部署机私下替换后重新打包。

配置全部使用 yml 字面量，不依赖环境变量占位符。AERIM 的登记示例：

```yaml
ocpx:
  protocols:
    advertisers:
      "<AERIM广告主主键>": AERIM
  aerim:
    # 可选；未配置时沿用 domain.name。仅用于临时 IP-UAT 等 AERIM 专属回调入口。
    callback-base-url: http://42.81.102.181/ocpx
    allowed-hosts:
      - <商务提供的正式监测域名>
```

`credentialConfigKey` 对 AERIM 保持为空；未登记广告主或白名单为空时拒绝转发。修改
profile yml 后必须重新打包并重启。生产登记、V106 执行和发布均需单独授权。

生产启动必须显式激活 `prod`：

```bash
java -jar scx-ocpx-engine-exec.jar --spring.profiles.active=prod
```

本地 `dev` 启动前必须确保 `127.0.0.1:3306` 的 MySQL 和 `127.0.0.1:6379` 的
Redis 已启动，并已创建 SCX 所需表。仓库不保存旧项目真实密钥。

打包后，普通 `scx-ocpx-engine.jar` 是 Engine 模块产物；带 `exec` classifier 的
`scx-ocpx-engine-exec.jar` 为独立应用。Admin 只依赖 `scx-ocpx-core`，不依赖 Engine。

宝塔标准发布入口会校验 `-exec.jar` 中的启动类与 `application-prod.yml`，再将它固定发布为
`build/backend/scx-ocpx-engine.jar`：

```bash
bash scripts/bt-deploy/01-deploy.sh --no-pull engine
bash scripts/bt-deploy/02-manage.sh engine-restart
```

生产 profile 只监听 `127.0.0.1:10000`。公网由 `ocpx` 专用 Nginx 站点精确开放
Feedback、通用 Callback、AERIM Callback 和既有 Open API，不能直接放行 10000 端口。
没有可用公网域名时，可将 `ocpx.aerim.callback-base-url` 临时配置为公网 HTTP IP，并把
`nginx-scx-ocpx-engine-ip-uat-rewrite.conf` 中的精确路由加入该 IP 站点。该配置只用于 UAT；
取得域名和证书后应恢复为 HTTPS 域名。

```bash
cd scx-backend
mvn -pl scx-ocpx-engine -am test
mvn -pl scx-ocpx-admin -am test
```
