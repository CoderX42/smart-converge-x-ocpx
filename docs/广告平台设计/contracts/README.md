# SCX OCPX 契约包

本目录描述 SCX 通用 Feedback/Callback、AERIM Callback、微风互动 Callback、引力 Callback、人人视频 Callback 和当前管理端字段边界。

| 文件 | 用途 |
|---|---|
| `openapi.yaml` | `/feedback`、通用/AERIM/微风/引力/人人视频 Callback、媒体回传、创意/素材、可配置计费、账务流水、监测点击测试、下游媒体联调和真实首页摘要 |
| `feedback-req-v2.schema.json` | Feedback Ingress Query 对象 |
| `callback-req-v2.schema.json` | Upstream Callback Query 对象 |
| `weifeng-callback-req-v1.schema.json` | 微风互动 Callback Query 对象 |
| `gravity-callback-req-v1.schema.json` | 引力认证上下文 Callback Query 对象 |
| `renren-video-callback-req-v1.schema.json` | 人人视频认证上下文与事件 Callback Query 对象 |
| `scx-ocpx-v2.postman_collection.json` | 本机 GET 示例 |
| `historical-contracts.md` | 已退出接口，不属于当前能力 |
| `management-v102.md` | V102 管理 API 请求/响应字段和废弃操作边界 |
| `management-v103.md` | 广告主开户、只读数据范围和 V103 接口增量 |
| `management-v104.md` | `ROLE_ADMIN` 五对象 CRUD 和安全删除边界 |
| `management-v107.md` | 共享广告主角色权限、全量数据范围和会话失效契约 |
| `management-v108.md` | 幂等充值、计费配置、真实 Dashboard 和资金隔离契约 |
| `management-v110.md` | 人工余额扣减与管理员资金权限契约 |
| `management-v111.md` | 创意、素材、多链接绑定、点击/转化计费和全量账务流水契约 |
| `management-v112.md` | 管理员监测任务 CLK 测试链接、真实发送和联调详情契约 |
| `management-v113.md` | 广告主计费与媒体回传比例解耦、扣量事实和报表覆盖契约 |
| `management-v114.md` | 下游 Feedback 落库快照查询与管理员真实媒体 Callback 联调契约 |
| `management-v115.md` | 数据库驱动的广告主上游协议与精确允许域名契约 |
| `management-v116.md` | 流程日志管理员详情、规范化下游请求参数留存和原始报文边界 |
| `management-v117.md` | 流程日志结果摘要、脱敏限长响应预览与历史数据边界 |
| `management-v118.md` | 上游 Callback 唯一转化事实查询、扣量状态与数据范围契约 |
| `management-v120.md` | 引力上游点击、Callback、白名单、安全开关及 UAT 边界 |
| `management-v123.md` | 流程日志有界总数、深翻页限制和事件时间索引契约 |
| `management-v124.md` | 人人视频点击模板、精确白名单、认证 Callback 与发布边界 |
| `management-v126.md` | 上游 Callback 转化记录检索索引与生产发布边界 |
| `creative-save-v111.schema.json` | 创意创建/编辑请求 JSON Schema |
| `monitor-task-billing-v111.schema.json` | 监测任务计费字段 JSON Schema |
| `../OCPX首页真实数据与计费闭环V108.md` | V108 事实、账户、计费和首页统计口径 |

`mId/tId/aId/lId` 分别直接使用 `OcpxMedia`、`OcpxMonitorTask`、
`OcpxAdvertiser`、`OcpxPromotionLink` 的 SCX 字符串主键。`aId` 是 SCX 广告主主键；
`advId` 是广告主侧广告 ID，两者不得混用。Postman 只保留占位值，
不得写入真实 AES Key、签名密钥、账号或生产域名。

通用 Feedback/Callback、AERIM Callback、微风互动 Callback、引力 Callback、人人视频 Callback 及媒体回传均使用 GET Query String。Feedback 只有在 MySQL 事实、账务和小时汇总事务提交后才返回成功；
重复事件和余额不足点击不向上游投递；转化余额不足仍继续既有扣量与下游 Callback。Redis List 与 StorageTask 只保留兼容日志用途，不再驱动 Dashboard 或报表。

```bash
make contract-check
```
