# SCX OCPX 管理 API V104 增量契约

V104 继承 V102 的五对象字段契约和 V103 的广告主开户/数据范围，只补齐 `ROLE_ADMIN` 的完整 CRUD；不恢复旧字段、旧动作或兼容别名。

| 资源 | 查询 | 新增 | 修改 | 删除 |
|---|---|---|---|---|
| 广告主 | `GET /ocpx/advertisers`、`GET /{id}`；`ocpx:advertiser:view` | `POST /ocpx/advertisers`；`ocpx:advertiser:add` | `PUT /ocpx/advertisers/{id}`；`ocpx:advertiser:edit` | `DELETE /ocpx/advertisers/{id}`；`ocpx:advertiser:delete` |
| 推广链接 | `GET /ocpx/promotion-links`、`GET /{id}`；`ocpx:promotion-link:view` | `POST /ocpx/promotion-links`；`ocpx:promotion-link:add` | `PUT /ocpx/promotion-links/{id}`；`ocpx:promotion-link:edit` | `DELETE /ocpx/promotion-links/{id}`；`ocpx:promotion-link:delete` |
| 媒体 | `GET /ocpx/media`、`GET /{id}`；`ocpx:media:view` | `POST /ocpx/media`；`ocpx:media:add` | `PUT /ocpx/media/{id}`；`ocpx:media:edit` | `DELETE /ocpx/media/{id}`；`ocpx:media:delete` |
| 监测任务 | `GET /ocpx/monitor-tasks`、`GET /{id}`；`ocpx:monitor-task:view` | `POST /ocpx/monitor-tasks`；`ocpx:monitor-task:add` | `PUT /ocpx/monitor-tasks/{id}`；`ocpx:monitor-task:edit` | `DELETE /ocpx/monitor-tasks/{id}`；`ocpx:monitor-task:delete` |
| 扣量规则 | `GET /ocpx/deduction-rules`、`GET /{id}`；`ocpx:deduction-rule:list` | `POST /ocpx/deduction-rules`；`ocpx:deduction-rule:create` | `PUT /ocpx/deduction-rules/{id}`；`ocpx:deduction-rule:update` | `DELETE /ocpx/deduction-rules/{id}`；`ocpx:deduction-rule:delete` |

广告主和媒体执行物理删除前必须确认不存在业务及历史引用；存在引用时返回失败结果且不删除任何数据。广告主删除还必须在同一数据库事务中注销其托管账号。`ROLE_ADVERTISER` 不拥有上述写权限。
