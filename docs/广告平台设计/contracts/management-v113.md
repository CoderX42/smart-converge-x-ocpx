# 广告主计费与媒体回传扣量契约 V113

## 固定口径

- 监测任务的 `billingEnabled`、`billingType`、`unitPrice`、`conversionEvent` 保持兼容；`unitPrice` 只表示广告主计费单价。
- 首次有效点击或首次目标转化依据监测任务配置生成 `CLICK_CHARGE` / `CONVERSION_CHARGE`；媒体扣量不读取或修改广告主余额、消费、账务流水。
- `ratio` 是媒体回传比例，API 使用 `[0,1]` 小数。`0` 全部拦截，`1` 全部放行；未配置或 Redis 中比例非法时按 `1.0` 放行。
- `PASSED` 只表示通过比例判定并进入异步媒体回传提交流程，不表示合作方已经接收。

## 管理接口

- `GET/POST /ocpx/deduction-rules`
- `GET/PUT/DELETE /ocpx/deduction-rules/{id}`
- `GET /ocpx/reports/summary`
- `GET /ocpx/reports/summary:export`

扣量规则请求保留 `mediaId`、`promotionLinkId`、`ratio`。前端以 `0%～100%` 编辑，提交前除以 100；服务端仅接受 `[0,1]`。

报表响应在既有字段基础上增加：

- `callbackCount`
- `deductionPassedCount`
- `deductionDeductedCount`
- `deductionPendingCount`
- `deductionCoverageStart`
- `deductionCoverageStatus=NONE/PARTIAL/FULL`

`basePrice` 的兼容含义明确为广告主消费；`linkRadio` 保留旧字段名，但只表示当前媒体回传比例，不表示金额。

## 事实与历史边界

Callback 首次事实先以 `PENDING` 入库并完成广告主计费，再由 Redis Lua 原子判定，最后条件更新为 `PASSED/DEDUCTED`。只有第一次状态转换增加小时扣量汇总。重复 Callback 不重复计费、计数或提交媒体回传。

V113 之前不回填、不估算扣量事实。查询早于首条新口径判定返回 `NONE`；跨越起始时间返回 `PARTIAL`；完全位于覆盖区间返回 `FULL`。CSV 每行重复输出扣量统计起始时间。
