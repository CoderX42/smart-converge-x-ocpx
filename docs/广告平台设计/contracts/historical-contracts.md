# 历史契约说明

V70 前曾存在 `/open-api/events/v1/*`、RTA 决策、旧 attribution/callback outbox 等规划契约；相关运行模块已经退出当前 ADX 构建，不能再作为已交付接口。当前数据面契约只以本目录的 `openapi.yaml`、`feedback-req-v1.schema.json`、`callback-req-v1.schema.json` 和 `asyncapi.yaml` 为准。

RTA 与 Billing 目前只有 Java Port、数据库审计/账本预留和 `OFF/AUDIT_ONLY` 发布约束，没有对外运行接口。
