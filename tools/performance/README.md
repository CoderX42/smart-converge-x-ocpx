# OCPX 最小同步链路压测说明

`ocpx-feedback.k6.js` 只用于已授权的隔离环境。当前 Feedback 会同步等待广告主响应，因此压测结果同时受上游 Mock/广告主容量和网络延迟影响，不能再沿用历史 Redis Stream 吞吐结论。

脚本默认 10 QPS、30 秒，只接受 HTTP 202。密钥通过 k6 进程环境临时提供，不写入 Git 或报告。任何更高并发、长时间运行或真实合作方目标都需要单独授权。

`jmeter/results/` 是旧异步架构的历史证据，不属于当前最小版本验收结果；对应旧 JMeter 执行脚本已删除。

当前压测后的最小对账口径是：

```text
HTTP 202 数 = scx_ocpx_flow_log 中本批次 FEEDBACK/SUCCESS 数
HTTP 502 数 = scx_ocpx_flow_log 中本批次 FEEDBACK/FAILED 数
```
