# meper-chatbi-connector-spi

> 能力包：A 连接器内核｜阶段：P1 ✅｜依赖：无（纯契约，禁止 Spring / Web / JDBC 驱动）
> 模块约束原文：`com.meper.chatbi.spi.package-info`

## 1. 定位与边界

全系统数据库能力的**契约层**：定义执行上下文、方言、执行、能力声明与全部传输模型。上层（domain / query-enforcement / web）只依赖本模块的类型，不依赖任何实现模块。本模块**没有任何行为实现**（`DialectRegistry` 的 ServiceLoader 聚合除外）。

禁止：Spring 依赖、JDBC 驱动依赖、Web/HTTP 类型、任何业务逻辑。

## 2. 契约清单（已实现）

### 2.1 执行上下文与执法状态

**`model.ExecutionContext`**（record）——随调用显式传递的身份与用途，服务端签发：

| 字段 | 说明 |
| --- | --- |
| `tenantId` | 租户（阶段 1 固定 `"default"`） |
| `subject` | 已验证主体（阶段 1 为引导管理员用户名） |
| `purpose` | `Purpose` 枚举 |
| `datasourceId` | 目标数据源 |
| `enforcementState` | `EnforcementState` |
| `correlationId` | 贯穿日志/审计的关联 ID（工厂生成 UUID） |
| `issuedAt` | 签发时间 |

不变式：
- 只能由服务端工厂签发（当前：domain 的 `ExecutionContextFactory`），**前端不可自填授权**；
- 紧凑构造器校验：`subject` 为空抛 `IllegalArgumentException`；`enforcementState == ENFORCED` 抛 `IllegalStateException`（**P2 接入权限体系后解除此限制并同步改本契约**）；
- 禁止 ThreadLocal 传递。

**`EnforcementState`**：`BOOTSTRAP_ADMIN_UNRESTRICTED`（阶段 1 恒定值，必须显式出现在 ExecutionContext 与 API 响应中）｜`ENFORCED`（P2+）。

**`Purpose`**：`DATASOURCE_ADMIN` / `WORKBENCH` / `API` / `AGENT`（P5）。

### 2.2 方言契约

**`SqlDialect`**（接口，ServiceLoader 注册，见 `DialectRegistry`）：

| 方法 | 语义 |
| --- | --- |
| `type()` | 对应 `DatabaseType` |
| `buildJdbcUrl(ConnectionSpec)` | 拼 URL（含 SSL 参数映射；不携带用户名密码） |
| `probeSql()` | 探活 SQL |
| `capabilities()` | `CapabilityDescriptor` 能力声明 |
| `quoteIdentifier(String)` | 标识符引用（含内部转义） |
| `paginate(sql, offset, limit)` | 物理分页改写；无法安全改写返回 null（执行层 maxRows 截断兜底） |
| `namespaceLayout()` | `CATALOG_IS_DATABASE`（MySQL）/ `SCHEMA_BASED`，决定元数据读取层级 |
| `systemNamespaceNames()` | 元数据树中隐藏的系统库名 |
| `stripTrailingSemicolon(sql)` | 静态工具：分页改写前预处理 |

**`DialectRegistry`**：static ServiceLoader 聚合；重复注册同类型方言 fail-fast；未注册类型 `get()` 抛 `UnsupportedOperationException`（能力矩阵记 UNSUPPORTED）。上层只经此处获取方言，**不得直接依赖方言模块**。

### 2.3 执行契约

**`SqlExecutor`**（接口）：`execute(context, dataSource, statements, limits, cancelHook) → SqlExecutionResult`。实现必须满足：逐语句 `queryTimeout`、`maxRows` 硬截断、单元格/列数限额、错误摘要脱敏；阶段 1 逐语句 autocommit、语句级失败即停止（剩余语句不执行）。事务语义属 P3（演进见 connector-jdbc.md §5）。

**`SqlCategory`**：`SELECT` / `DML` / `DDL` / `TCL` / `OTHER`（USE、SHOW、SET、存储过程调用等方言语句归 OTHER）。分类器输出用于 preview 展示与历史记录。

### 2.4 模型（`spi.model`，18 个 record）

| 模型 | 用途 | 关键字段/约束 |
| --- | --- | --- |
| `ConnectionSpec` | 一次性连接参数 | `password` 为 `char[]`；由 profile + 解密密码在内存组装；**不得序列化、不得写日志** |
| `DataSourceProfile` | 持久化数据源档案 | 不含密码，含 `credentialVersion` |
| `CredentialRef` | 凭据引用 | 控制库只存引用不存明文 |
| `CapabilityDescriptor` | 方言能力声明 | supportsDatabase/Schema、identifierQuote、paginationStyle、probeSql；P3/P4 逐格填充 DML/DDL 矩阵 |
| `ExecutionLimits` | 执行限额 | 常量：`HARD_MAX_ROWS=10_000`、`DEFAULT_MAX_ROWS=1_000`、timeout 30s、cell 4_000 字符、maxColumns 100 |
| `AnalyzedStatement` | 分类后单语句 | (seq, text, category)；Druid 规范化文本 |
| `SqlExecutionResult` / `StatementResult` | 执行结果 | 逐语句状态/行数/耗时/错误摘要 |
| `QueryResultData` / `TableDataPage` / `ColumnInfo` / `TableInfo` / `TableDetail` / `IndexInfo` | 元数据与结果数据 | 表数据统一方言分页 |
| `SslMode` / `DatabaseType` | 枚举 | SSL 禁止失败自动退化（连接失败保留原错误） |

## 3. 演进（随能力包的契约变更）

| 阶段 | 变更 |
| --- | --- |
| P2（权限建模） | `ExecutionContext` 预计增加权限域 / 策略 revision 字段（见 policy.md §3）；`EnforcementState.ENFORCED` 构造限制解除；`CapabilityDescriptor` 不变（权限不入能力声明） |
| P3（DML） | `SqlExecutor` 事务/取消语义扩展（异步 cancelHook、事务边界） |
| P4（对象/账号） | 对象管理 / 账号管理契约（`ObjectAdminService` / `AccountAdminService` 方向，实施方案 §2.1 草图）；`CapabilityDescriptor` 填充 DDL/DCL 矩阵 |
| P6（方言扩展） | 无需改契约；新方言只需实现本模块接口（见 dialects.md §5） |

**变更规则**：契约字段的新增/语义变更必须版本化（记录在本文档与模型 Javadoc），并跑全方言回归（`*DriverTest`）。

## 4. 完成定义（DoD）

- 契约改动编译通过且全下游（connector-jdbc、4 方言、query-enforcement、domain）回归绿；
- 每个模型的不变式有对应单测（如 ExecutionContext 的 ENFORCED 拒绝、ConnectionSpec 不落日志靠评审保证）；
- 与 Javadoc / 本文档无静默偏离。
