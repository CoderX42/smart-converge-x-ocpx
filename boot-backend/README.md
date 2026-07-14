# Smart Converge X Backend

纯净 通用 通用后端基座，保留认证授权、用户权限、会员中心、支付中心、通用订单、开放平台、代码生成与数据看板能力。

> 白标项目：已移除原公司品牌信息。

## 已剔除

- 垂直业务交易模型
- 多商户业务范围、入驻、结算等模型
- 促销与导购相关模型

## 验证

```bash
mvn -pl smart-converge-x-boot -am -DskipTests compile
```

## 本地基础设施

```bash
docker compose up -d smart-converge-x-mysql smart-converge-x-redis smart-converge-x-rocketmq-namesrv smart-converge-x-rocketmq-broker
```
