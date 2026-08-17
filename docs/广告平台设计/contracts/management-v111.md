# SCX OCPX 管理 API V111 增量契约

V111 在 V110 不可变人工扣减流水基础上增加广告主自有图片创意、多对多推广链接绑定、可配置点击/目标转化计费和全量账务流水查询。

## 创意与素材

- `GET/POST /ocpx/creatives`、`GET/PUT/DELETE /ocpx/creatives/{id}` 提供创意管理。
- `GET/POST /ocpx/creatives/assets` 只读取或登记当前广告主自有素材。
- 创建和编辑只接受 `sourceMaterialId`，不接受浏览器提供最终素材 URL、MIME、尺寸或哈希。
- 图片仅支持 JPG/JPEG/PNG/WEBP，最大 20MB、最长边 8192px、总像素不超过 4000 万。
- `GET/PUT /ocpx/creatives/{id}/promotion-links` 使用最终集合语义；空数组解除全部绑定。
- 广告主账号始终按绑定 `shopId` 收口；`ocpx:advertiser:scope:all` 不扩大创意、素材或绑定写权限。
- `scx_ocpx_creative_promotion_link` 只是关系表，不是广告组。

## 计费配置

- 不新增独立计费接口；`POST /ocpx/monitor-tasks` 和 `PUT /ocpx/monitor-tasks/{id}` 增加
  `billingEnabled/billingType/unitPrice/conversionEvent`。
- 新任务默认关闭扣费、点击类型、单价 0、目标事件 `ACTIVATE`。
- 开启扣费时单价必须大于 0；类型 2 只在首次目标转化扣费。
- 计费字段变化要求非广告主账号拥有 `ocpx:monitor-task:billing`；无权限账号只能原样提交当前值。
- 每次变化在业务事务内写不可变前后配置审计；历史事实使用接收时快照，不随改价重算。

## 账务流水

- `GET /ocpx/billing-entries` 支持广告主、任务、类型、状态、业务键和时间范围筛选。
- 流水类型为 `RECHARGE/MANUAL_DEBIT/CLICK_CHARGE/CONVERSION_CHARGE`；状态为
  `POSTED/REJECTED`。
- 广告主账号始终只能读取自身流水；充值和人工扣减仍要求真实管理员权限。
- `MANUAL_DEBIT` 不计入事件消费或 Dashboard 消耗。

V111 不新增 `cId`、创意级监测地址、Engine 创意校验、创意事实或创意报表维度。外部
`creative_id` 继续保持合作方宏字段语义。
