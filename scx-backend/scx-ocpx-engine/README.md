# scx-ocpx-engine

`scx-ocpx-engine` 是独立启动的 OCPX 数据面应用。当前实现按
`/Users/karl/code/sgyz-ocpx/vientiane-rtb` 的通用 `general/union` 链路迁移，
只做 Java 17 / Spring Boot 3、SCX 包名、字符串主键/表名和本机脱敏配置适配。

## 四实体与请求 ID

| Query 字段 | SCX 实体主键 | 表 |
|---|---|---|
| `chid` | `OcpxMedia.id` | `scx_ocpx_media` |
| `mid` | `OcpxMonitorTask.id` | `scx_ocpx_monitor_task` |
| `dspid` | `OcpxAdvertiser.id` | `scx_ocpx_advertiser` |
| `linkid` | `OcpxPromotionLink.id` | `scx_ocpx_promotion_link` |

这四个字段始终是 `String`，直接承载 SCX 的 `VARCHAR(32)` 主键，不嵌入实体对象。
四个实体位于 `com.yzytech.scx.ocpx.entity`，供 `scx-ocpx-admin` 复用。

## 运行与接口

应用使用 Spring MVC + Undertow，默认监听 `10000`，Servlet context path 为
`/ocpx`，健康检查为 `GET /ocpx/actuator/health`。

- `GET /ocpx/general/feedback`：接收媒体曝光/点击，保持原
  `event/request_id/callback_url/caid/idfa/imei/oaid/ip/os/ts/ua` 等 Query、宏清理、
  请求 ID MD5 回退和成功 JSON。
- `GET /ocpx/union/callback`：接收广告主转化，保持 `ext/s/event_type/event_time`；
  成功返回空 `200`，解析异常返回 `500`。

正向链路为：Feedback 参数转换 → Redis 去重计数 → Feedback 队列 → 按
`OcpxAdvertiser.bidUrl` 替换宏 → 生成 AES `ext` 回调地址 → 使用
`credentialConfigKey` 追加 MD5 `signature` → 广告主 GET。

反向链路为：解密 `ext` → Callback 去重 → Callback 队列 → 按
`linkid + chid` 读取 Redis 扣量数组 → 对允许回传的数据解码原 `callback_url`、
追加 `event_type` 后执行媒体 GET。

配置由四个 Mapper 启动加载到 `ConcurrentHashMap` 索引。Admin 写入
`r:da:refresh` 后，Engine 的分钟任务等待五秒、重载四索引并删除标记。

## Redis、落库和报表

保留原 Redis Key：`r:u:rpe:`、`r:cl:rpe:`、`r:da:report:`、
`r:da:report:cb:`、`r:da:refresh`、`adm:u:dsp:radio:`、
`adm:u:dsp:original:`、`adm:u:dsp:fixed:`。

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
- 两个 Manager 均使用 `500/500` 固定线程、容量 `100` 和
  `CallerRunsPolicy`，异步异常按原方式吞掉。
- Redis 扣量数组长度固定为 `11`，读改写非原子；第 12 类及更高 Action 仍会越界。
- List 消费没有事务、processing 队列、所有者锁校验、重试或 DLQ。
- 不迁移爱奇艺、百度、OPPO、华为、快手等合作方专用 Controller、枚举或协议。

## 运行配置

公共配置位于 `application.yml`，默认激活 `dev`：

- `application-dev.yml`：本机 MySQL、Redis、回调域名和本机联调 AES Key；IntelliJ
  不指定 profile 时也使用该配置。
- `application-prod.yml`：线上 MySQL、Redis 和公网回调域名；生产数据库密码、Redis
  密码和 AES Key 必须由生产使用者在部署机私下替换后重新打包。

配置全部使用 yml 字面量，不依赖环境变量占位符。生产启动必须显式激活 `prod`：

```bash
java -jar scx-ocpx-engine-exec.jar --spring.profiles.active=prod
```

本地 `dev` 启动前必须确保 `127.0.0.1:3306` 的 MySQL 和 `127.0.0.1:6379` 的
Redis 已启动，并已创建 SCX 所需表。仓库不保存旧项目真实密钥。

打包后，普通 `scx-ocpx-engine.jar` 供 Admin 依赖；带 `exec` classifier 的
`scx-ocpx-engine-exec.jar` 为独立应用。

```bash
cd scx-backend
mvn -pl scx-ocpx-engine -am test
mvn -pl scx-ocpx-admin -am test
```
