# SCX OCPX 第三方 UAT 包

本目录不包含任何凭据或原始设备号。联调人员从受控渠道获得 `mediaCode`、
`monitor_task_id`、`keyVersion` 和密钥，通过环境变量注入示例程序。

## 内容

- `sign_request.py`：Python 3 请求签名示例。
- `SignRequest.java`：Java 17 请求签名示例。
- `hmac_callback_mock.py`：验证 SCX Callback HMAC、正文哈希和媒体侧幂等键的回调模拟器。
- `测试数据与错误注入.md`：合成数据、异常路径和预期结果。
- `排查与签收模板.md`：回执排查步骤及双方签收边界。

## 最小运行方式

```bash
export SCX_MEDIA_KEY_VERSION='1'
export SCX_MEDIA_SECRET='从受控渠道临时注入'
python3 sign_request.py POST /ocpx/open-api/ocpx/v2/media/sandbox_media/feedback request.json

python3 hmac_callback_mock.py
```

签名脚本输出 `X-OCPX-Key-Version`、`X-OCPX-Timestamp`、`X-OCPX-Nonce` 和
`X-OCPX-Signature`。它对原始请求体计算 SHA-256，路径必须与实际发送路径完全一致并包含
`/ocpx`。模拟器默认监听 `127.0.0.1:18080`，只接受 `/postback`。它不会打印密钥或完整请求正文，重复
`Idempotency-Key` 由 SCX 发送给媒体用于媒体侧去重；模拟器在签名不一致时返回 `401`。
