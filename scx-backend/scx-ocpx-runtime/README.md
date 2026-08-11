# scx-ocpx-runtime

`scx-ocpx-runtime` 是 SCX OCPX 引擎的运行时核心。它被 `scx-ocpx-engine` 装配，负责 Feedback/Callback 接入后的校验、可靠排队、事实持久化、双向适配、投递、重试、扣量、计费端口调用和审计。

## 主要职责

- 校验 HMAC、时间戳、nonce、幂等键、路由状态和渠道限流。
- 将 Feedback 写入分区 Redis Streams，由消费者批量持久化到 MySQL。
- 保存协议快照、callback 引用、投递任务、投递尝试和 DLQ。
- 通过 `UpstreamAdapter` 与 `DownstreamAdapter` 完成上下游协议转换。
- 使用租约和 `FOR UPDATE SKIP LOCKED` 支持多实例投递任务抢占。
- 执行固定扣量决策、CPC/AUDIT_ONLY 计费端口以及小时报表增量。
- 清理超过保留期的协议快照和 callback 引用。

## 不承担的职责

- 不作为独立可执行 JAR 启动；启动入口位于 `scx-ocpx-engine`。
- 不承载管理端登录、Sa-Token、菜单、配置编辑或人工运营页面。
- 不为单个渠道重新实现线程池、队列、重试、事务和账务流水。
- 不在浏览器或日志中暴露渠道密钥、设备原始标识和完整 callback 参数。

## 运行链路

```text
下游 Feedback
  -> 鉴权/幂等/限流
  -> Redis Stream
  -> MySQL 事实与投递任务
  -> 上游 Adapter
  -> 上游 Callback
  -> 扣量决策与下游任务
  -> 下游 Adapter
```

运行参数由 `scx-ocpx-engine/src/main/resources/application-*.yml` 与共享的
`ocpx-protocol-crypto.yml` 提供，不依赖 `/etc/scx/scx.env` 或运行时环境变量占位符。

本地 `dev` 环境的协议密钥保存在 Git 忽略的
`src/main/resources/ocpx-local-secrets.yml` 中。该文件为 `payload-aes-key`、
`device-hmac-key` 和 `callback-token-key` 分别配置独立随机值，由管理面和独立引擎
共同加载；不得提交、输出或在三个用途之间复用密钥。生产 profile 使用既有生产配置，
更换前必须先完成密文迁移与签名切换设计。

## 开发与验证

```bash
cd scx-backend
mvn -pl scx-ocpx-runtime -am test
```

涉及公网路径或签名原文时，还要执行：

```bash
make contract-check
```

本模块当前仍与管理面共享 MySQL/Redis。修改表、队列键、路由快照、账务或报表写入时，必须同步检查 `scx-ocpx-admin`、前向迁移和运行中心，不得形成未声明的双写语义。
