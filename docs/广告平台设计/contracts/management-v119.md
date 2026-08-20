# 完整链路请求日志与媒体宏契约 V119

## 链路与数据边界

- 确定性 `traceId` 由 `mId/tId/aId/lId/request_id` 计算，同一点击和后续转化聚合为
  `MEDIA_INGRESS → ADVERTISER_DELIVERY → ADVERTISER_CALLBACK → CALLBACK_ACCOUNTING →
  MEDIA_DEDUCTION → MEDIA_DELIVERY`。
- `scx_ocpx_flow_log` 只保留阶段、结果、安全端点、状态、耗时和宏状态；
  `scx_ocpx_flow_log_detail` 保存规范化参数映射与最多 4096 字符的脱敏响应预览。
  不保存原始 URL、参数顺序、重复参数、签名、Token、密钥、测试令牌或加密 `ext`。
- 请求和响应详情保留 7 天，脱敏阶段元数据保留 90 天；每日使用 Redis 分布式互斥锁分批清理。
  V119 以前的日志不回填，返回 `HISTORICAL_INCOMPLETE`。

## 权限与接口

- `GET /ocpx/flow-logs/{id}/request-detail` 保留旧顶层字段，并新增 `traceId`、
  `coverageStatus` 和 `stages`。接口要求 `ocpx:flow-log:detail`，只授予活动
  `ROLE_ADMIN`，响应为 `Cache-Control: no-store`，每次查看记录写入不含设备值的安全审计。
- 管理员可在七日详情中查看 IDFA、CAID、OAID 等设备原值；不提供原值导出。
- `GET /ocpx/media-macros` 是 Feedback v2 快捷宏目录的唯一管理接口。媒体管理只在新建时默认选中，
  编辑存量媒体时保持其原配置和自定义宏。

## 入站和上游规则

- 正式 `CLK` 必须携带具体、非 `__REQUEST_ID__`、不超过 128 字符且不含控制字符的
  `request_id`，否则返回 400。`IMP` 暂时保留确定性回退并记录兼容告警。
- iOS 上游最终参数至少展开一个 IDFA/IDFA_MD5/CAID 系列标识；Android 至少展开一个
  OAID/IMEI/Android ID/AAID 系列标识。只有 IP 和 UA 均实际展开时才允许兜底。
- 通用模板 GET 按查询参数展开：空值参数省略，未知或残留宏拒绝，值只编码一次；不向合作方模板
  自动追加未配置参数。AERIM、WEIFENG 与通用模板共享宏值和归因标识解析规则。

## 发布边界

V119 SQL 必须在目标库备份和预检后人工执行，然后重新打包并重启 Boot、Engine 和前端。
源码编译、本地测试或合成 HTTP 成功不能替代目标数据库升级、Android/iOS 受控 UAT 或合作方验收。
