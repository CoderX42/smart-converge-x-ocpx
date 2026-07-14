# Machine-readable Contracts

本目录保存 API、消息和事件的机器可读契约。当前均为 `draft`，用于冻结结构和建立 CI；技术栈确认后扩展为完整实现契约。

| 文件 | 说明 |
| --- | --- |
| [management.openapi.yaml](management.openapi.yaml) | 管理 API 最小基线。 |
| [event-ingestion.openapi.yaml](event-ingestion.openapi.yaml) | 事件接入与状态查询基线。 |
| [asyncapi.yaml](asyncapi.yaml) | 内部领域事件目录基线。 |
| [event-envelope.schema.json](event-envelope.schema.json) | 统一事件信封 JSON Schema。 |

契约变更必须遵循 `12-API消息与集成规范.md` 和 `19-数据库与数据契约迁移.md`。CI 至少执行语法校验、引用解析、示例校验和向后兼容检查。

