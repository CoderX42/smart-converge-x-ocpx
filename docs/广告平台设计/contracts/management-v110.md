# SCX OCPX 管理 API V110 增量契约

V110 在 V108 的 MySQL 广告主账户和不可变账务流水上增加人工余额扣减，不改变点击计费或报表消费口径。

## 幂等人工扣减

`POST /ocpx/advertisers/{id}/deduct`：

- 请求必填 `requestId`、正数 `amount` 和非空 `remark`；金额最多 14 位整数、6 位小数。
- 返回 `entryId/advertiserId/amount/balanceAfter/occurredAt`。
- 同一广告主和 `requestId` 的相同金额、相同原因返回首次结果；改变金额或原因拒绝。
- 账户行通过 MySQL `FOR UPDATE` 串行化；扣减金额超过当前可用余额时拒绝，不写账户或账务流水。
- 成功流水使用正数金额和 `entry_type=MANUAL_DEBIT` 表达扣减方向，不计入业务消费指标。
- 权限码为 `ocpx:advertiser:deduct`，服务层另外强制真实 `ROLE_ADMIN`；共享广告主角色不能获得该权限。

V110 是人工审核的前向 SQL，不自动执行，不读写已退役的 `scx_schema_migration`。
