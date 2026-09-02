# OCPX MySQL IOPS 治理契约 V125

## 运行行为

- `scx_ocpx_event_fact`、`scx_ocpx_billing_entry` 与 `scx_ocpx_hourly_metric` 继续实时、幂等写入，
  Callback 事实、计费、扣量和下游回传语义不变。
- 已退役的兼容日志与报表队列默认关闭；`scx_ocpx_log_feedback`、
  `scx_ocpx_log_callback`、`scx_ocpx_log_feedback_day`、`scx_ocpx_report_mat_temp` 和
  `scx_ocpx_report_mat` 暂时保留历史数据，不在 V125 中删除。
- 兼容队列显式重新启用时，批次失败只执行有限重试和指数退避；失败批次不裁剪，非法批次可跳过，
  不允许数据库异常触发无间隔热循环。
- 上下游投递使用有界、可配置线程池和调用方反压，默认核心线程 32、最大线程 64、队列 200。
- 失败流程日志全量保留；成功流程日志按稳定请求指纹采样，默认采样率 10%。事实与账务不依赖该采样。

## 清理与索引

- 流程日志与遗留日志清理每十分钟执行；五类清理目标各最多两批，每批最多 1000 行，批次间暂停
  100 毫秒，单轮最多十批。
- V125 为 Feedback、Callback 的 `create_time` 以及 Feedback 日表的 `time_hour_res` 增加清理索引。
- V125 不为已停用的 `report_mat`/`report_mat_temp` 增加索引，不清空、不截断也不删除历史表。

## 发布边界

生产执行 V125 前必须核对部署 JAR、目标库身份和现有索引，完成数据库备份、`EXPLAIN`、在线 DDL
能力审核与低峰审批。发布后至少观察两小时和一个完整 24 小时周期；源码、单元测试和本地 Schema
门禁通过不代表生产 IOPS 目标已经达成。
