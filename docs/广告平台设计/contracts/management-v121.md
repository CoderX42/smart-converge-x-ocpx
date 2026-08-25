# OCPX 全量原始请求审计契约 V121

> 状态：已由 V122 退役。本文仅作为已执行 V121 的历史证据，不再代表当前系统能力。

## 采集与保留

- 六阶段为 `MEDIA_INGRESS`、`ADVERTISER_DELIVERY`、`ADVERTISER_CALLBACK`、`CALLBACK_ACCOUNTING`、`MEDIA_DEDUCTION`、`MEDIA_DELIVERY`。
- HTTP 审计保存应用实际看到或发出的 Method、Path、原始 Query、全部多值 Header、Body、Response 与异常；内部阶段保存规范化输入和输出。
- 原文只以 AES-256-GCM 密文保存 30 天，元数据保存 180 天。原文过期后详情返回 `EXPIRED`，不得尝试从其他日志重建。
- MySQL 与本地加密 WAL 同时不可写时继续业务并记录覆盖缺口指标；该边界不等同于绝对零丢失。

## 管理接口

- `GET /ocpx/flow-logs/wire-audits` 分页查询元数据，单页最多 100 条。
- `GET /ocpx/flow-logs/wire-audits/{id}` 解密一条原文，要求活动系统管理员和 `ocpx:flow-log:raw`。
- `GET /ocpx/flow-logs/wire-audits:export` 只接受单个自然日，最多十万条，要求 `ocpx:flow-log:raw:export`，流式返回 ZIP 中的 NDJSON。
- 详情和导出响应禁止缓存，操作写入系统安全审计，系统日志不得包含原始载荷。

## 密钥兼容

- 配置提供活动 `key_id`、密钥映射和历史读取顺序，均为 profile 文件字面量。
- 新生成 Callback URL 带 `kid` 并使用用途隔离的认证加密格式；无 `kid` 时按固定历史密钥顺序读取 Template/AERIM/微风的 AES/ECB 密文和 Gravity V120 AES-GCM 密文。
- 历史密钥在最长归因窗口和该密钥对应的审计原文保留期同时结束前不得移除。
