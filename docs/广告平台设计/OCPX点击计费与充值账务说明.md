# OCPX 点击计费与充值账务说明

## 1. 已实现范围

SCX 使用 `scx_adx_advertiser.balance` 保存广告主可用余额，使用 `scx_ocpx_billing_entry` 保存不可变流水。当前支持：

- 后台充值：管理员/运营人员在广告主列表填写金额，前端生成 16–64 位 `requestId`。
- 充值幂等：账务唯一键为 `RECHARGE:{advertiserId}:{requestId}`，请求重试返回首次结果。
- 点击审计：`AUDIT_ONLY` 为每个首次落库 CLICK 写 `RECORDED` 流水，不修改余额。
- 真实 CPC：`CPC` 以 CLICK `event_id` 为唯一扣费键，按发布路由快照中的 `unit_price` 扣除 CNY 余额。
- 余额不足：写 `REJECTED/INSUFFICIENT_BALANCE` 流水，不修改余额，不创建该点击的上游投递任务。
- 账务查询：`GET /boot/adx/advertisers/{id}/ledger`，管理端“账务”弹窗展示金额、状态、账前/账后余额和操作人。

充值权限为 `adx:account:recharge`，流水查询权限为 `adx:account:read`。V72 默认授予 `ROLE_ADMIN` 和 `ROLE_OPERATOR`；原 `adx:advertiser:write` 不再隐含资金操作权限。

## 2. 原子性与幂等

充值和 CPC 都遵循相同事务顺序：

1. `SELECT ... FOR UPDATE` 锁定目标广告主余额行。
2. 在锁内按 `charge_event_id` 查询已有流水。
3. 已存在时直接返回原状态和账后余额。
4. 不存在时计算账后余额；在同一 MySQL 事务内修改余额并插入唯一流水。

因此并发重试不会重复入账/扣费，事务异常也不会留下“余额已变、流水缺失”或相反的半成功状态。账务唯一索引仍是最终一致性防线。

## 3. 数据面语义

feedback 返回 202 只表示事件已可靠进入 Redis Stream，不表示已经完成扣费或上游投递。异步消费者首次把 CLICK 写入 MySQL 时执行计费：

```text
CLICK 入队
  -> 事实 INSERT IGNORE
  -> OFF / AUDIT_ONLY / CPC
  -> CPC 成功：扣余额 + POSTED 流水 + 上游任务 + cost_amount
  -> CPC 余额不足：REJECTED 流水 + 事件 BILLING_REJECTED，不建上游任务
```

重复 CLICK 因事件事实唯一约束直接退出，不会再次调用计费。转化 callback 的 `amount/amount_raw` 只作为转化事实保存，不参与 CPC 扣费。

## 4. 迁移与生产门禁

V72 会：

- 将广告主余额精度调整为 `DECIMAL(20,6)`。
- 为账务流水增加账前/账后余额、操作人和备注。
- 新增账户查询/充值权限。
- 删除已退出产品边界的汽车线索表、菜单和权限。

V72 源码存在不等于目标数据库已迁移。应用前若需保留旧线索必须先备份。生产启用 CPC 前至少完成：初始余额确认、充值重复请求、重复 CLICK、余额不足、事务回滚、小时成本、停用/恢复路由和账务导出对账；先运行 `AUDIT_ONLY`，再单路由灰度 `CPC`。

当前未实现退款/冲正、跨币种、授信额度和负余额。禁止直接 SQL 修改余额替代冲正；这些能力需要新的不可变反向流水、审批权限和对账规则。
