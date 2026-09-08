# SQL XML 迁移验证记录

验证日期：2026-09-08。基于当前工作区增量迁移，包含尚未提交的 RTA 实现；不改变外部 API、数据库结构或业务规则。

## 实施范围

- 广告主、媒体、推广链接 Mapper 的 21 处注解 SQL 迁入同名 XML。
- Admin、Engine 新增 17 组 SQL Mapper 接口与 XML，涵盖账户、账务、事实、日志、报表、创意、监测联调、OPPO 回传、RTA 持久化及清理任务。
- 系统角色行锁、广告配置限额查询迁入现有 XML。基础 CRUD 与普通条件构造器继续使用 MyBatis-Plus；不创建空 XML。
- 动态筛选、报表维度、排序、索引提示及表名选择在 XML 内使用固定分支，值采用参数绑定。宽参数写入使用 command record，投影 record 使用显式结果映射。
- 保留服务层权限、事务、锁顺序、受影响行数判断、幂等处理、RTA 批次上限及提交后 ACK。原 JDBC 查询关闭二级缓存并刷新本地缓存，避免迁移后读到过期结果。
- Engine 补齐 Mapper 扫描包与 XML 资源路径。`make business-sql-check` 阻止业务 Java 重新引入注解 SQL、直接 JDBC 和 SQL 拼接片段，已接入两个后端测试入口。

## 验证结果

- `make backend-test`、`make ocpx-engine-test`：通过。
- `make contract-check`、`make schema-ledger-check`、`make business-sql-check`：通过。
- 前端 `pnpm check:type`、`pnpm lint`、`pnpm test:unit`：通过；lint 为 0 error、3 个已有 warning，单测 47 个文件、337 个测试通过。
- XML 加载、动态 SQL 与 Spring Mapper 注册契约：7 个测试通过，覆盖空集合、参数绑定、固定排序分支、锁查询缓存、Engine 扫描及清理限额。
- 隔离 MySQL 集成回归：11 个测试通过，未跳过；覆盖并发充值、重复事件、扣费回滚、RTA 批量重放和统计版本、报表全部维度与粒度、金额与可空字段、流程日志本页加一条查询。测试通过实际 XML Mapper 执行，Admin 使用生产 MyBatis-Plus 拦截器链。
- Boot、Engine Maven 打包通过；逐字节核验 Boot 内 Admin 的 15 份、System 的 22 份，以及 Engine 的 8 份 XML 与源码一致。

服务单测使用 Mock Mapper；测试辅助器从实际 XML 生成 BoundSql，复用原有 SQL 参数与异常场景夹具。数据库集成测试则使用真实 SqlSessionTemplate，不走 Mock 执行路径。

## 隔离数据库回归命令与限制

本次仅启动任务专用、绑定 `127.0.0.1:33317` 的 MySQL 9.6.0 临时实例，使用 `scx_xml_test`、`scx_rta_test_xml` 测试库。未连接开发或生产数据源，未执行环境部署或数据库迁移。MySQL 8.x 的版本兼容性验证尚未执行。

复跑前须在隔离测试实例准备所需当前态表结构；下列命令不会创建 Admin 测试库结构，也不能替代测试库初始化。集成测试只接受显式测试连接，不读取应用 profile。

```bash
mvn -f scx-backend/pom.xml -fae -pl scx-ocpx-admin,scx-ocpx-engine -am test \
  -Dtest=XmlMapperContractTest,EngineXmlMapperContractTest,XmlQueryMysqlIntegrationTest,OcpxAdvertiserAccountMysqlIntegrationTest,OcpxEventFactMysqlIntegrationTest,RtaPersistenceIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-Docpx.mysql.url=jdbc:mysql://127.0.0.1:33317/scx_xml_test?useSSL=false&allowPublicKeyRetrieval=true' \
  '-Drta.mysql.url=jdbc:mysql://127.0.0.1:33317/scx_rta_test_xml?useSSL=false&allowPublicKeyRetrieval=true'
```

本次本机日志保留于 `/tmp/scx-xml-migration/` 的 `backend-gate.log`、`engine-gate.log`、`mysql-tests.log`、`contract-gate.log`、`frontend-type.log`、`frontend-lint.log`、`frontend-tests.log` 和 `package.log`，临时文件不随仓库发布。
