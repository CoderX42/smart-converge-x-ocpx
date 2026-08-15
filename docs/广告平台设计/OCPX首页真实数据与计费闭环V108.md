# OCPX 首页真实数据与广告主计费闭环 V108

> 状态：源码、前向 SQL、契约和本地自动化实现；不代表目标数据库迁移、UAT、生产部署或重启。

## 口径

- 有效展示、有效点击以 `广告主 + 监测任务 + requestId + 事件类型` 的 MySQL 唯一事实为准。
- 首次有效点击按监测任务 `unit_price`（CNY 元，`DECIMAL(20,6)`）计费。
- 余额充足时在同一事务内扣减账户、写 `POSTED` 流水并增加消费；余额不足时写
  `REJECTED/INSUFFICIENT_BALANCE` 流水，点击仍有效，但消费为 0 且不向上游投递。
- Callback 按接收时监测任务的 `conversion_event` 冻结 `target_conversion`；后续改配置不重算历史。
- Engine 接收时间和 `Asia/Shanghai` 决定业务小时与业务日期。

## 数据真相

V108 新增四张表：

| 表 | 作用 |
|---|---|
| `scx_ocpx_advertiser_account` | 广告主 CNY 可用余额 |
| `scx_ocpx_billing_entry` | 不可变充值和点击扣费流水 |
| `scx_ocpx_event_fact` | 唯一 Feedback/Callback 事实与冻结口径 |
| `scx_ocpx_hourly_metric` | 与事实事务同步维护的小时指标 |

Dashboard、统一小时报表和时段报表都读取 `scx_ocpx_hourly_metric`。旧 Redis List、明细日志和
`scx_ocpx_report_mat*` 仅保留兼容观察用途，不再决定首页或报表数值。

## 接口与权限

- `POST /ocpx/advertisers/{id}/recharge` 请求为 `requestId + amount + remark?`，响应充值流水、金额、
  账后余额和发生时间。同一广告主与 `requestId` 幂等。
- `GET /ocpx/dashboard/summary` 返回服务端业务日期、时区、筛选区间、数据水位、数据状态、可选账户块和
  `SPEND/IMPRESSION/CLICK/CONVERSION/CTR/CVR/CPM/CPC/CPA` 九项指标。
- 分母为 0 的比率返回 `value=null,status=ZERO_DENOMINATOR`；可信空范围的数量和消费返回 0，
  `dataStatus=EMPTY`。
- 充值、单价和目标转化事件只允许真实 `ROLE_ADMIN`。共享 `ROLE_ADVERTISER` 不得获得
  `ocpx:advertiser:recharge` 或 `ocpx:monitor-task:billing`。
- 广告主的全量数据范围只扩大业务指标范围；账户块始终绑定其自身广告主。管理员未选择具体广告主时不返回账户块。

## 删除与迁移边界

- 广告主有非零余额、任何账务流水或事件事实时禁止物理删除。
- V108 预检发现旧报表事实时直接停止；本版本不推断旧事实身份，也不把 Redis 余额回填为账务。
- 发布前必须再次确认目标库旧报表为空，并单独盘点 Redis 非零余额；任一不为空都必须停止并制定审计迁移方案。
- V108 是人工审核的前向 SQL，不自动执行，不读写已退役的 `scx_schema_migration`。

## 验收样例

充值 ¥100、任务单价 ¥2.50，写入 100 个唯一展示、10 个唯一点击、2 个目标转化后：余额 ¥75、消费
¥25、CTR 10%、CVR 20%、CPM ¥250、CPC ¥2.50、CPA ¥12.50。重复事实不能改变账户、流水、小时指标
或投递次数；余额不足点击只增加点击与拒绝数。
