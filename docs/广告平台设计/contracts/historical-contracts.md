# 历史契约说明

V70 前曾存在 `/open-api/events/v1/*`、RTA 决策、旧 attribution/callback outbox 等规划契约；相关运行模块已经退出当前 ADX 构建，不能再作为已交付接口。当前数据面契约只以本目录的 `openapi.yaml`、`feedback-req-v1.schema.json`、`callback-req-v1.schema.json` 和 `asyncapi.yaml` 为准。

RTA 目前仍只有 Java Port 且发布强制 `OFF`。Billing 的“仅预留”描述已被 V72 取代：当前管理面提供幂等充值/流水查询，数据面提供 `OFF/AUDIT_ONLY/CPC` 点击账务；它不是合作方公开 API，生产启用边界见 `OCPX点击计费与充值账务说明.md`。
