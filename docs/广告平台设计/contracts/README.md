# SCX OCPX v1 对外契约包

本目录中的当前契约服务于 V70 双向转发引擎。交付给下游渠道研发时，请同时提供：

1. `../OCPX下游渠道API接入文档.md`
2. `openapi.yaml`
3. `feedback-req-v1.schema.json`
4. `callback-req-v1.schema.json`
5. `scx-ocpx-v1.postman_collection.json`
6. `../uat/sign_request.py`
7. `../uat/verify_request.py`

另行通过安全渠道提供沙箱域名、`channelCode`、已发布 `monitor_id`、密钥版本和 HMAC 密钥。不要把真实密钥写入文档、Postman 集合、工单或 Git。

## 文件用途

| 文件 | 用途 |
|---|---|
| `openapi.yaml` | 下游调用的 feedback API、Coco callback 参考及 SCX 主动投递的下游 webhook |
| `feedback-req-v1.schema.json` | 下游上报请求体 JSON Schema |
| `callback-req-v1.schema.json` | SCX 向下游投递请求体 JSON Schema |
| `scx-ocpx-v1.postman_collection.json` | 可导入 Apifox/Postman；feedback HMAC 自动签名、Coco callback 与业务 code Mock 示例 |
| `../uat/sign_request.py` / `verify_request.py` | 生成 feedback 签名、验证捕获的 callback 签名 |
| `asyncapi.yaml` | SCX 内部 Redis Stream 契约，一般不需要交付下游 |
| `historical-contracts.md` | 已退出接口说明，不属于当前接入能力 |

## 校验

仓库根目录执行：

```bash
make contract-check
```

校验必须通过后才能发布契约包。真实第三方 UAT、目标环境迁移和容量验收仍是独立生产门禁。
