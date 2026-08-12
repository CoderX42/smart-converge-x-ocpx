# SCX OCPX v2 对外契约包

本目录中的当前契约服务于四实体双向转发引擎。交付给下游媒体研发时，请同时提供：

1. `../OCPX下游渠道API接入文档.md`
2. `openapi.yaml`
3. `feedback-req-v2.schema.json`
4. `callback-req-v2.schema.json`
5. `scx-ocpx-v2.postman_collection.json`
6. `../uat/sign_request.py`
7. `../uat/verify_request.py`

另行通过安全渠道提供沙箱域名、`mediaCode`、启用的 `monitor_task_id`、配置键对应的凭据版本和鉴权材料。不要把真实账号或 Secret 写入数据库、流程日志、文档、Postman 集合、工单或 Git。

## 文件用途

| 文件 | 用途 |
|---|---|
| `openapi.yaml` | 下游调用的 feedback API、Coco callback 参考及 SCX 主动投递的下游 webhook |
| `feedback-req-v2.schema.json` | 下游上报请求体 JSON Schema；业务关系仅以 `monitor_task_id` 派生 |
| `callback-req-v2.schema.json` | SCX 向下游投递请求体 JSON Schema |
| `scx-ocpx-v2.postman_collection.json` | 可导入 Apifox/Postman；包含媒体 Feedback、广告主 Callback 和公共 CLICK 路由示例 |
| `../uat/sign_request.py` / `verify_request.py` | 生成 feedback 签名、验证捕获的 callback 签名 |
| `historical-contracts.md` | 已退出接口说明，不属于当前接入能力 |

## 校验

仓库根目录执行：

```bash
make contract-check
```

当前契约是同步转发：Feedback 只有在上游成功接收后返回 202，Callback 只有在下游成功接收后返回广告主 Adapter 定义的成功响应。当前版本没有队列、自动重试、幂等事实或 DLQ。
