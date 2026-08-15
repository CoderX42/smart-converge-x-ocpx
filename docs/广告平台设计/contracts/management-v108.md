# SCX OCPX 管理 API V108 增量契约

V108 继承 V107 的共享广告主角色，新增 MySQL 真实事件、CPC 账务和 Dashboard 摘要契约。

## 幂等充值

`POST /ocpx/advertisers/{id}/recharge`：

- 请求必填 `requestId` 和正数 `amount`，可选 `remark`；金额最多 14 位整数、6 位小数。
- 返回 `entryId/advertiserId/amount/balanceAfter/occurredAt`。
- 同一广告主和 `requestId` 的相同金额返回首次结果；改变金额拒绝。
- 权限码为 `ocpx:advertiser:recharge`，服务层另外强制真实 `ROLE_ADMIN`。

## 监测任务计费口径

新增或修改监测任务时，`unitPrice` 为 CNY 元值 `DECIMAL(20,6)`，`conversionEvent`
必须为支持的 Callback 事件，默认 `ACTIVATE`。新建任务要求原新增权限和
`ocpx:monitor-task:billing`；更新时只有单价或目标事件发生变化才额外校验该权限。
两类计费口径写入均在服务层强制真实 `ROLE_ADMIN`。

## Dashboard 摘要

`GET /ocpx/dashboard/summary` 接受 `startDate/endDate/advertiserId/promotionLinkId/mediaId/monitorTaskId`。
首次不传日期时，服务端以 `Asia/Shanghai` 当日作为起止日。

返回：

- `businessDate/timezone/startDate/endDate/asOf/dataStatus`；
- 可选 `account`：`advertiserId/availableBalance/todaySpend/rejectedClickCount`；
- 固定九项 `metrics`：`SPEND/IMPRESSION/CLICK/CONVERSION/CTR/CVR/CPM/CPC/CPA`；
- 每项指标包含 `value/unit/status`。分母为 0 时 `value=null,status=ZERO_DENOMINATOR`。

广告主账号的账户块始终固定为自身账户；管理员未筛选广告主时不返回账户块。
`GET /ocpx/dashboard/options` 只返回四类业务对象的 `id/label`，不返回任何余额。
