# meper-chatbi-connector-jdbc

> 能力包：A 连接器内核｜阶段：P1 ✅｜依赖：connector-spi
> 模块约束原文：`com.meper.chatbi.jdbc.package-info`

## 1. 定位与边界

JDBC 执行内核：驱动加载、动态连接池、连通测试、标准元数据读取、SQL 执行。是**唯一**允许出现 JDBC API 与驱动调用的非方言模块。不依赖 Spring；由 domain 的 `DomainConfig` 装配为 bean。

禁止：方言特定 SQL 出现在本模块（方言行为一律经 `DialectRegistry` 取 `SqlDialect`）；持有身份/权限状态。

## 2. 对外契约（已实现）

### 2.1 `BuiltinDriverRegistry`
启动时预加载 4 个内置驱动（驱动 JAR 由方言模块携带，本模块不管理驱动上传）。**不做自定义驱动 JAR 上传**；未来若引入，必须先满足实施方案 §4 的来源/校验/隔离要求。

### 2.2 `ConnectionPoolRegistry`
HikariCP 动态池注册表。

- **池键 `PoolKey(datasourceId, credentialVersion)`**：结构化、不含任何控制台/会话语义；凭据轮换必然产生新键。
- `poolFor(profile, password)` → `computeIfAbsent` 建池；池名 `meper-ds-{id}-v{version}`。
- `evict(datasourceId)`：轮换/删除后逐出该数据源全部旧池（generation 失效思想）；撤权/轮换后不得复用旧连接。
- 池上限由配置 `meper.workbench.pool-max-size`（默认 5）控制。

### 2.3 `JdbcConnectionTester`
连通测试：经方言 `buildJdbcUrl` 建短连接执行 `probeSql()`，返回成功/失败与延迟；失败保留原始错误（**禁止 SSL 失败自动退化重试**）。

### 2.4 `JdbcMetadataReader`
JDBC 标准元数据读取，全部经方言适配：

| 能力 | 说明 |
| --- | --- |
| 命名空间 | MySQL 走 catalog（= database），其余走 schema（按 `namespaceLayout()`） |
| 系统库过滤 | 按 `systemNamespaceNames()` 在对应层级过滤 |
| 表/视图 | 支持名称模式匹配 |
| 表结构 | 列（类型/可空/默认值/备注/自增）、主键、索引 |
| 表数据 | 方言 `paginate` 分页 + 精确总数；带 `ExecutionLimits`（超时/最大行/列/单元格截断） |

### 2.5 `JdbcSqlExecutor`
`SqlExecutor` 实现：逐语句执行 `AnalyzedStatement`，逐语句 `queryTimeout` 与 `maxRows` 硬截断；单元格字符与列数限额；语句级失败即停止；错误信息摘要脱敏；`cancelHook` 接收当前 `Statement` 供外部取消（当前传 null，异步化属 P3）；逐语句 autocommit。

## 3. 依赖的模块契约

- `SqlDialect` / `DialectRegistry` / `DatabaseType`：所有方言行为入口；
- `ExecutionContext`：随执行传递，写审计/超时归属；
- `ExecutionLimits`：限额来源；
- `ConnectionSpec`：连接参数（`char[]` 密码用后不残留）。

## 4. 能力矩阵 / 覆盖范围

测试（Testcontainers + Docker，无 Docker 自动跳过，见 AGENTS.md）：
- `ConnectionPoolRegistryTest`：池键隔离、轮换逐出；
- `JdbcSqlExecutorTest`（配 `TestMySqlDialect` 夹具）：执行/限额/失败停止；
- `JdbcConnectionTesterTest`：探活与错误保留；
- 四方言 `*DriverTest`：真实库版本矩阵（MySQL 5.7/8.4.9、SQL Server 2019/2022、PostgreSQL 16、Oracle 23ai；Oracle 19c 需自备镜像）。

## 5. 演进

| 阶段 | 变更 |
| --- | --- |
| P2 | 无行为变更；执行路径被 domain 接入决策后，本模块只接收「已放行」的调用（不感知策略） |
| P3 | 事务边界（跨语句事务、提交/回滚）、异步取消（cancelHook 实装）、批量写入执行 |
| P4 | 对象操作执行支持（DDL 专用入口，配合审批）；导入导出的流式读取 |

## 6. 完成定义（DoD）

- 池按 `datasourceId + credentialVersion` 隔离、轮换逐出有测试；
- 所有 SQL 经方言产出，无硬编码方言分支；
- 限额（行/列/单元格/超时）在真实库上有验证；
- 错误信息不泄漏凭据与完整结果。
