# MEPER ChatBI Server：Java 数据库能力架构方案

> 方案草案｜2026-09-15｜依据 `feature/permission-aware-chatbi`、HEAD `8fd3d9bd9` 的当前工作树梳理。当前工作树有未提交改动；本文是能力与目标架构分析，不表示已完成代码迁入或跨数据库验证。

## 1. 结论与产品定位

MEPER ChatBI Server 采用 **Java 服务端作为数据库能力主体**：保留数据库连接、驱动管理、元数据发现、SQL 查询与增删改、事务、DDL/DCL、对象管理、导入导出及方言插件等完整能力目标。B/S 网页、业务系统 API 和 Agent 都通过同一套 Java 领域接口访问能力，不能直接获得 JDBC 连接。

“保留全部能力”指**产品能力目标不主动裁剪**，不等于每个数据库、每种角色或 Agent 默认都能执行所有操作。每个方言要声明支持项；高风险操作由身份、权限、环境、审批和数据库原生权限共同控制。

## 2. 完整数据库能力目录

| 能力域 | MEPER 应提供的能力 | 当前源码可借鉴的边界 | 默认访问路径 |
| --- | --- | --- | --- |
| 数据源与驱动 | 数据源登记、连通测试、SSH/SSL、凭据引用、驱动版本与自定义驱动、连接轮换 | [IDbDataSourceService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbDataSourceService.java)、[JdbcDriverManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/JdbcDriverManager.java) | 管理控制面 |
| 连接与会话 | 连接池、库/Schema 切换、事务、超时、取消、连接健康与回收 | [ConnectionPool](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/ConnectionPool.java)、[IDbConnectionContextService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbConnectionContextService.java) | Java 执行内核 |
| 元数据 | 库、Schema、表、视图、字段、索引、主外键、类型、函数、过程、触发器及 DDL | [IDbMetaData](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IDbMetaData.java)、[IDbTableService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbTableService.java) | 授权过滤后读取 |
| DQL 查询 | SELECT、分页、结果流、聚合、解释计划、结果集元信息、大字段分块 | [DefaultSQLExecutor](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultSQLExecutor.java)、[IDbCellValueService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbCellValueService.java) | 用户/API/Agent 授权后 |
| 高级 SQL 工作台 | 管理员原始 SQL、脚本/多语句、结果集与执行历史；按语句分类执行而不删减 SQL 能力 | [DefaultSQLExecutor](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultSQLExecutor.java)、[IDbSqlExecutionService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbSqlExecutionService.java) | 管理身份；解析、逐语句授权、审批与审计 |
| DML 数据操作 | INSERT、UPDATE、DELETE、批量写入、结果集编辑、事务提交/回滚 | [IDbDmlExecutionService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbDmlExecutionService.java)、[DefaultSQLExecutor](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultSQLExecutor.java) | 授权 + 环境策略；Agent 需额外批准 |
| DDL 对象操作 | 库、Schema、表、字段、索引、视图的创建/修改/删除，复制、截断 | [IDbDatabaseService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbDatabaseService.java)、[IDbTableService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbTableService.java)、[ISqlBuilder](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/ISqlBuilder.java) | 管理控制面 + 变更计划/审批 |
| DCL 与账号 | 账号、授权查看、Grant/Revocation 类操作及能力探测 | [IDbAccountAdminService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbAccountAdminService.java)、[IPlugin](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IPlugin.java) | 专用管理员身份，不向 Agent 默认开放 |
| 例程与迁移 | 函数/过程/触发器查看、调用预览、迁移预览与执行 | [IDbRoutineOperationService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbRoutineOperationService.java)、[IDbMetaData](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IDbMetaData.java) | 能力探测 + 审批 |
| 数据迁移与交付 | SQL/表数据导出、导入任务、流式读取、取消与操作审计 | [IDbDmlExportService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbDmlExportService.java)、[DefaultSQLExecutor](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultSQLExecutor.java) | 后台任务 + 交付前再鉴权 |
| 非关系型能力 | Redis Key CRUD/Scan、其他插件声明的专用命令与元数据 | [IDbRedisKeyService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/db/IDbRedisKeyService.java)、[IKeyOperations](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IKeyOperations.java) | 专用 Connector，不强行套用关系型 SQL |
| 方言与辅助 | SQL 生成、标识符转义、解析、补全、各数据库特殊操作 | [IPlugin](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IPlugin.java)、[ISqlBuilder](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/ISqlBuilder.java) | 各方言插件声明支持项 |

当前 `chat2db-community-plugins` 下有多种数据库插件目录，但目录存在**不等于上述每项能力都已可用**。MEPER 要建立“数据库 × 操作 × 版本”的能力矩阵，通过方言测试确定 `SUPPORTED / PARTIAL / UNSUPPORTED`；不得以默认实现或接口方法数推断实际可用性。

## 3. Java 技术栈与模块划分

第一版建议以 **Java 17、Spring Boot 3.x、Maven 多模块** 为服务端基线。Java 17 与 Spring Boot 3.5 系列兼容；Spring Boot 对 JDBC `DataSource`、连接池和 Spring JDBC 的支持可用于控制库与业务库的连接管理，但业务库动态 SQL、元数据和方言操作仍需保留直接 JDBC/插件能力。[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[SQL 数据库指南](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html)、[JDBC Driver 接口](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Driver.html)。实际依赖版本在新仓库建项时锁定并测试，不机械继承旧 BOM。

```text
meper-chatbi-start                  组装、配置、健康检查
├─ meper-chatbi-web                 B/S API、Trusted API、MCP 适配；无 JDBC 直连
├─ meper-chatbi-domain              数据源、对象操作、任务、Agent、Wiki 工作流
├─ meper-chatbi-policy              身份映射、表/字段/行/操作决策、审批
├─ meper-chatbi-query-enforcement   SQL/对象操作分类、规划、权限校验、结果治理
├─ meper-chatbi-connector-spi       Connector、Dialect、Capability、ExecutionContext 契约
├─ meper-chatbi-connector-jdbc      驱动加载、DataSource/连接池、事务、执行、取消
├─ meper-chatbi-dialect-*           数据库特定连接/元数据/SQL/对象管理实现
├─ meper-chatbi-control-storage     领域 Repository、配置版本、审计与任务存储
├─ meper-chatbi-session-wiki        授权语义目录与有限会话历史
└─ meper-chatbi-agent               Agent 定义、Run、工具入口与交付
```

模块方向：`web → domain/policy/query`，`domain/query → connector-spi`，`jdbc/dialect → connector-spi`，`storage → domain storage contracts`，`start` 负责装配。接口不返回 HTTP DTO；方言 SQL 不放在 Web/Domain。该方向借鉴当前仓库 [Java 模块边界](../Chat2DB-permission-aware-chatbi/spec/code/server/java-module-boundaries.md)，但 MEPER 应重新命名契约和数据模型，而非保留旧命名空间。

### 存储与连接分离

- **控制库**：保存数据源元信息、`CredentialRef`、身份模型、权限域、策略草稿/发布版本、Agent、Wiki、Task/Run 与审计。建议关系型存储；具体数据库在基础设施设计时确定。
- **业务库**：只通过 Connector 读取或执行被授权操作；不写入 MEPER 的控制表。读、写、DDL/账号管理应使用不同数据库凭据或不同连接角色。
- **连接池**：按租户、数据源、数据库用户/凭据版本、访问角色和方言隔离；限制总连接数、空闲数、等待时间和泄漏时间。Spring Boot 3.5 的 JDBC Starter 默认包含 HikariCP，但动态多数据源池仍需显式管理。[官方连接池说明](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html)。

## 4. 统一操作管线

所有 DQL、DML、DDL、DCL、例程、导入导出和非关系型操作走统一入口，不存在“管理员接口/Agent 工具直接调用 JDBC”的旁路。

```text
可信调用身份 → ExecutionContext(租户/主体/权限域/数据源/用途)
→ 操作分类与目标资源解析 → 方言能力探测
→ 用户策略 ∩ Agent 上限 ∩ Session/Task 范围 ∩ 环境/系统上限
→ SQL AST 或结构化对象计划校验 → 必要时审批/变更窗口
→ 获取对应凭据角色与连接 → 执行/超时/取消/事务
→ 字段裁剪/脱敏/大值治理 → 交付前再鉴权 → 审计
```

操作入口分三类：

1. `QueryOperation`：以查询与元数据为主，可供网页/API/Agent 使用；Agent 初始默认只读。
2. `DataMutationOperation`：增删改与批量写入；必须有操作级权限、事务策略、幂等与必要审批。
3. `AdministrationOperation`：DDL/DCL、数据库对象删除、账号权限、导入导出以及高级原始 SQL 脚本；必须有专用身份、结构化计划或逐语句解析、风险确认、审计与可恢复方案。无法可靠解析目标资源的语句不得靠“管理员已登录”直接执行。DDL 的回滚能力因方言而异，不能承诺一律回滚。

`Connection.setReadOnly(true)` 只可作为连接提示，不是授权机制；真正边界是策略决策、受限数据库凭据、操作分类和执行前校验。事务控制可用 Spring 抽象，但跨动态业务库连接和方言命令必须定义具体事务边界。[Spring 事务文档](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)。

## 5. 权限模型与 HTML 方案的关系

静态 HTML 所展示的“外部主体 → 业务用户/角色 → 权限域 → 表策略 → 字段矩阵”适合作为 MEPER 的控制面流程。Java 模型建议有 `AccessModel`、`PermissionDomain`、`ResourcePolicy`、`PolicySnapshot`、`OperationApproval`、`OperationAudit`。表策略至少覆盖 `SELECT / INSERT / UPDATE / DELETE`、行规则、可读/筛选/新增/修改/脱敏字段、最大行数、草稿/发布/停用与 revision。

需要明确一个**兼容差异**：当前 [AuthorizationResourcePolicy](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/model/authorization/AuthorizationResourcePolicy.java) 注释中，“空可读字段列表”表示全部可读；已部署的 MEPER 静态 HTML 示例采用“空列表默认拒绝”。新产品应选择显式语义、写入版本化契约，并在任何历史策略迁入前做转换与兼容测试，不能静默改变授权范围。

人类管理员可以通过审批使用完整操作能力；业务 API 依据主体和操作策略使用其被授权子集；Agent 的工具权限始终是主体权限的收窄，不得因为产品保留 DDL/DML 能力就默认开放给 Agent。

## 6. 实施原则

1. **能力完整、权限最小**：数据库操作不删减，但每项可独立启停、审批、审计和按方言探测。
2. **先合同、后迁入**：先定义 MEPER Java 契约和验收矩阵，再改造复用现有源码。
3. **插件独立**：MySQL、PostgreSQL、Oracle、Redis 等实现保持在所属方言模块；无统一语义时显式报告不支持。
4. **无隐式请求状态**：每次调用携带不可篡改的 `ExecutionContext`，不依赖桌面控制台 ID 或进程 `ThreadLocal` 传递授权。
5. **不接生产数据验证**：使用命名本地测试数据库与夹具；写入/删库/账号操作需专用可恢复测试环境。

具体源码对应关系、阶段安排和验收条件见 [Chat2DB-Java能力借鉴实施方案.md](./Chat2DB-Java能力借鉴实施方案.md)。
