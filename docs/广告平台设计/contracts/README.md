# SCX OCPX 契约包

本目录描述 SCX 通用 Feedback/Callback、KIMI POST 例外和当前管理端字段边界。

| 文件 | 用途 |
|---|---|
| `openapi.yaml` | `/feedback`、`/callback`、媒体回传、幂等充值和真实首页摘要 |
| `feedback-req-v2.schema.json` | Feedback Ingress Query 对象 |
| `callback-req-v2.schema.json` | Upstream Callback Query 对象 |
| `scx-ocpx-v2.postman_collection.json` | 本机 GET 示例 |
| `historical-contracts.md` | 已退出接口，不属于当前能力 |
| `management-v102.md` | V102 管理 API 请求/响应字段和废弃操作边界 |
| `management-v103.md` | 广告主开户、只读数据范围和 V103 接口增量 |
| `management-v104.md` | `ROLE_ADMIN` 五对象 CRUD 和安全删除边界 |
| `management-v107.md` | 共享广告主角色权限、全量数据范围和会话失效契约 |
| `management-v108.md` | 幂等充值、计费配置、真实 Dashboard 和资金隔离契约 |
| `../OCPX首页真实数据与计费闭环V108.md` | V108 事实、账户、计费和首页统计口径 |

`mId/tId/aId/lId` 分别直接使用 `OcpxMedia`、`OcpxMonitorTask`、
`OcpxAdvertiser`、`OcpxPromotionLink` 的 SCX 字符串主键。`aId` 是 SCX 广告主主键；
`advId` 是广告主侧广告 ID，两者不得混用。Postman 只保留占位值，
不得写入真实 AES Key、签名密钥、账号或生产域名。

通用 Feedback/Callback 及媒体回传使用 GET Query String；KIMI 的
`/kimi/callback` 是明确的 POST JSON 例外。Feedback 只有在 MySQL 事实、账务和小时汇总事务提交后才返回成功；
重复事件和余额不足点击不向上游投递。Redis List 与 StorageTask 只保留兼容日志用途，不再驱动 Dashboard 或报表。

```bash
make contract-check
```
