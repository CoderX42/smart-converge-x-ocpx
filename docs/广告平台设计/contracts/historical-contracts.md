# 历史契约说明

V70 前曾存在旧版事件入口、外部决策和旧 attribution/callback outbox 等规划契约；相关运行模块已经退出当前 OCPX 构建，不能再作为已交付接口。当前数据面契约只以本目录的 `openapi.yaml`、`feedback-req-v2.schema.json` 和 `callback-req-v2.schema.json` 为准。

外部实时决策、充值账务、扣量、事件配置、路由发布、异步队列、自动重试和失败重放均已退出当前最小版本。
