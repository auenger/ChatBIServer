# Chat2DB Java 能力借鉴实施方案（MEPER ChatBI Server）

> 计划草案｜2026-09-15｜目标：保留现有 Java 数据库能力，并把它们改造成受权限控制的独立 B/S 服务。总体架构见 [Java数据库能力架构方案.md](./Java数据库能力架构方案.md)。

## 1. 源码借鉴清单与处理方式

| 源能力 | 主要定位 | 借鉴方式 | 新服务需补的边界 |
| --- | --- | --- | --- |
| [JdbcDriverManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/JdbcDriverManager.java) | 外部 Driver JAR 加载与连接 | 抽成 `DriverArtifactRegistry` + `JdbcConnectionFactory` | 上传/下载审批、校验和、版本、签名或可信来源；隔离不可信 JAR；禁止连接失败自动降级 SSL |
| [ConnectionPool](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/ConnectionPool.java) | 连接复用与回收 | 保留健康检查、代际失效的思路；池实现重新选型 | 多租户/凭据版本隔离、总连接上限、等待与泄漏监控、轮换时失效 |
| [ConnectInfo](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/model/datasource/ConnectInfo.java) | 数据源与运行时连接状态 | 拆成持久 `DatasourceProfile`、`CredentialRef`、一次性 `ConnectionLease` | 去掉控制台 ID、内存明文密码、连接对象混在配置中的模型 |
| [Chat2DBContext](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/Chat2DBContext.java) | 插件注册与连接线程上下文 | 保留 ServiceLoader/Registry 思路；请求状态重新定义 | 用显式 `ExecutionContext`，不以 `ThreadLocal` 传递用户/权限/Agent 数据 |
| [IPlugin](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IPlugin.java)、[IDbMetaData](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IDbMetaData.java)、[ISqlBuilder](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/ISqlBuilder.java) | 方言扩展、元数据、SQL 生成 | 保留接口分层与方言归属 | 加 `CapabilityDescriptor`，把 DQL/DML/DDL/DCL/非关系型支持程度显式化；移除不属于数据库服务的 UI 能力 |
| [DefaultDBManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultDBManager.java) | JDBC、SSH、对象管理与导出 | 拆成 `ConnectionAdapter`、`ObjectOperationAdapter`、`ExportAdapter` | 建库/删库/截断/账号管理不与普通查询共用无约束入口 |
| [DefaultSQLExecutor](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultSQLExecutor.java) | SQL、分页、流式结果、写入、事务、取消 | 分阶段抽 `SqlExecutor`/`ResultStreamer`/`MutationExecutor` | 执行前权限、行/列规则、超时、最大结果、事务、幂等、审计与再鉴权 |
| 领域 DB 服务 | 数据源、表、数据库、DML、例程、账号等业务调用 | 以现有接口做功能盘点，重新定义 MEPER Domain API | 不导入桌面/工作区语义；Web 不直连 SPI 或存储实现 |
| 授权模型 | 外部主体、表/字段/行/操作权限、Agent 上限 | 借鉴 [AccessModelConfig](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/model/authorization/AccessModelConfig.java) 与 [AuthorizationResourcePolicy](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/model/authorization/AuthorizationResourcePolicy.java) 的字段 | 多用户身份信任、权限默认值、策略版本、撤权与 SQL 交叉验证 |
| Session Wiki / Agent | 授权语义目录和数据分析编排 | 保留 [IAiWikiSessionService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/llmwiki/IAiWikiSessionService.java) 的会话边界与 [ITrustedQueryService](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/service/authorization/ITrustedQueryService.java) 的身份/权限交集思路 | Agent 工具仅经 `QueryEnforcement`，不得持有业务库连接或凭据 |

每个真正迁入的文件必须重新检查 import、运行时状态、第三方依赖和回归测试。

## 2. 能力保持与接口划分

### 2.1 Java 接口建议

```java
interface ConnectorRegistry { CapabilityDescriptor capabilities(String dbType, String version); }
interface DataSourceService { DataSourceProfile register(...); ConnectionTestResult test(...); }
interface MetadataService { DatabaseTree inspect(ExecutionContext context, ResourceSelector selector); }
interface QueryService { QueryResult execute(ExecutionContext context, QueryPlan plan); }
interface MutationService { MutationResult execute(ExecutionContext context, MutationPlan plan); }
interface ObjectAdminService { OperationPreview preview(ExecutionContext context, ObjectChange change); OperationRun execute(...); }
interface AccountAdminService { AccountPreview preview(...); AccountRun execute(...); }
interface ExportImportService { TaskRun submit(ExecutionContext context, TransferPlan plan); }
```

这只是合同草图，不规定 DTO 或方法签名。`ExecutionContext` 必须由服务端依据已验证身份生成，至少含租户、Subject、权限域、数据源、访问角色、Agent/Session/Task 范围、策略版本、相关 ID 和幂等键；前端不能自行填写“已授权”。

### 2.2 必须覆盖的操作矩阵

为每种目标数据库建立以下验收表，逐格记录 `SUPPORTED / PARTIAL / UNSUPPORTED` 以及测试类、凭据要求和审批等级：

1. 连接：JDBC URL、驱动、SSL/SSH、Catalog/Schema 切换、轮换、连接关闭。
2. 查询：单表/多表、聚合、分页、流式结果、取消、超时、大字段，以及管理员高级 SQL/多语句脚本的分类与逐语句授权。
3. 写入：INSERT/UPDATE/DELETE、批量、事务提交/回滚、并发与幂等。
4. 对象：数据库、Schema、表、列、索引、视图的建改删与复制/截断。
5. 管理：账号/Grant、函数/过程/触发器、迁移与调用。
6. 数据交付：导入、导出、后台任务、失败恢复与审计。
7. 专项：Redis Key CRUD/Scan、其他非 SQL 插件能力。

第一批已确定 4 个关系型目标数据库：**MySQL、SQL Server、PostgreSQL、Oracle**（2026-09-15 决策）。JDBC 驱动内置到对应方言模块，不做自定义驱动 JAR 上传；集成测试以 Testcontainers + Docker 镜像提供数据库（mysql:8.4、mssql/server:2022-latest、postgres:16-alpine、gvenzl/oracle-free:23-slim）。四库先行形成完整样板，再逐方言扩展。**阶段顺序只决定上线节奏，不从目标清单删除 DML、DDL 或管理能力。**

## 3. 实施阶段与完成条件

| 阶段 | 主要工作 | 完成条件 |
| --- | --- | --- |
| G0：盘点 | 完成源码/第三方组件清单、目标数据库与能力矩阵、旧语义差异表 | 迁入范围有明确清单；无秘密或生产数据复制 |
| P1：Java 连接器内核 | 新 Maven reactor、Connector SPI、Driver Registry、DataSource/Secret、池、SSL/SSH、连通测试 | 凭据不泄露；池按租户/访问角色/凭据版本隔离；轮换与驱动卸载可测试 |
| P2：查询和权限 | 元数据过滤、身份模型、权限域、表/字段/行策略、SQL 分类与受控查询 | 网页/API/Agent 无执行旁路；撤权与策略版本变化拒绝交付；分页/取消/超时生效 |
| P3：数据增删改 | INSERT/UPDATE/DELETE、批量编辑、事务、幂等、行/列写权限与审批 | 可重复测试提交/回滚；部分失败有明确语义；Agent 不因工具存在而自动获得写权限 |
| P4：对象与账号全能力 | DDL/DCL、库/Schema/表/视图、例程、账号、迁移、导入导出与管理员高级 SQL 脚本 | 方言能力探测准确；逐语句授权、变更计划、审批、审计、可恢复演练完成 |
| P5：Session Wiki 与 Agent | Session Wiki、Agent 配置、Run、工具范围、交付前再鉴权 | Agent 查询与写入都经过同一授权入口；历史/知识预算有限；模型输出不扩权 |
| P6：方言扩展 | 逐一迁入或独立实现更多插件与 Redis 等非 JDBC 能力 | 每个插件有操作矩阵与目标版本测试；不支持项明确返回 |

建议采用模块化单体启动，以接口保持模块边界；查询执行、导入导出或 Agent Run 出现独立扩容、安全区隔离需求时再拆 Worker。不要把微服务拆分当成源码迁移的第一步。

## 4. 必须先改造的具体问题

| 风险 | 源码依据 | MEPER 处理要求 |
| --- | --- | --- |
| 连接失败后 SSL 退化 | [JdbcDriverManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/JdbcDriverManager.java) 的 MySQL 重试路径会补 `useSSL=false` | 移除自动退化；TLS 由管理员明确配置，失败保留原错误 |
| 自定义驱动是可执行代码 | 同文件的 `URLClassLoader` 与 [DbJdbcDriverServiceImpl](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-core/src/main/java/ai/chat2db/community/domain/core/impl/db/DbJdbcDriverServiceImpl.java) 的本地上传/下载配置 | 驱动来源、校验和、上传授权、版本与卸载审计；高信任/低信任驱动进程隔离。ClassLoader 不是安全沙箱 |
| 池与身份键适合桌面，不适合多租户 | [ConnectionPool](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/ConnectionPool.java) 为进程级静态池；[ConnectInfo](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/model/datasource/ConnectInfo.java) 的键含 `consoleId` | 改为数据源/租户/数据库凭据/访问角色/版本隔离；不复用撤权或轮换前连接 |
| 请求绑定是隐式状态 | [Chat2DBContext](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/sql/Chat2DBContext.java) 使用 `ThreadLocal<ConnectInfo>` | 服务端显式传递 `ExecutionContext`；异步任务、线程池及 Agent 工具不能依赖 ThreadLocal |
| 默认管理器混合高危操作 | [DefaultDBManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/DefaultDBManager.java)、[IDbManager](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IDbManager.java) 同时暴露连接与删库等操作 | 划分查询、写入、对象管理、账号管理接口；高危操作强制计划、审批与专用凭据 |
| 字段空列表语义不同 | [AuthorizationResourcePolicy](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-domain/chat2db-community-domain-api/src/main/java/ai/chat2db/community/domain/api/model/authorization/AuthorizationResourcePolicy.java) 中空 `readableColumns` 是全部可读；MEPER HTML 演示为空即拒绝 | 决定新契约并版本化；旧策略若迁入必须显式转换，比较前后有效权限 |
| 局部方言/插件差异 | [IPlugin](../Chat2DB-permission-aware-chatbi/chat2db-community-server/chat2db-community-spi/src/main/java/ai/chat2db/spi/IPlugin.java) 存在默认能力，插件模块众多 | 每个方言逐项探测、逐项测试；不把接口存在误写成所有数据库均支持 |

## 5. API 与控制面草案

- 数据源控制面：`POST /api/datasources`、`POST /api/datasources/{id}/test`、`GET /api/datasources/{id}/capabilities`、驱动注册/退役与凭据轮换。
- 人类数据库工作台：保留原始 SQL 和多语句脚本能力，但查询/变更请求统一为 `preview → approve（如需要）→ execute → status`，逐语句分类和授权；不得再提供无权限检查的直通 SQL URL。
- 用户权限配置：身份模型、权限域、真实主体目录、表字段矩阵、策略预检、草稿/发布/停用、有效权限模拟。
- Agent 工具：`wiki_find`、`inspect_table`、`query` 及受控的 `propose_mutation`/`propose_object_change`；Agent 提案不等于执行许可。
- 审计：记录身份、资源、操作类别、方言、策略版本、审批、事务结果与交付状态；日志不记录密码、Token 或完整敏感结果。

## 6. 验证计划

- **静态合同检查**：模块依赖方向、Web 不直连 JDBC、所有数据库方言 SQL 在对应插件、没有可绕过 `QueryEnforcement` 的执行路径。
- **单元测试**：驱动装卸、连接键与凭据轮换、无 SSL 退化、权限交集、空字段语义、草稿/发布 revision、SQL AST/对象目标分类、审计脱敏。
- **夹具集成测试**：仅用命名本地/测试数据库覆盖 SELECT/INSERT/UPDATE/DELETE、事务提交/回滚、建改删对象、账号操作和导入导出；破坏性用例配独立数据库与恢复脚本。
- **方言合同测试**：每个目标 DB 的元数据、标识符转义、分页、写入、DDL、例程与不支持返回值，按数据库版本记录矩阵。
- **安全回归**：跨租户池复用、撤权后连接与结果、Agent 直连旁路、隐藏列/WHERE 字段、JOIN/CTE/子查询行规则、长任务取消、过期结果交付。
- **部署验证**：B/S 服务与 Worker 的监听和控制库隔离、资源上限、健康检查、审计链；不以纯 shell 检查冒充真实数据库操作测试。

本文创建时没有执行 Maven/Yarn、数据库写入或任何迁入验证。后续每阶段应记录执行的测试命令、非零测试数、方言覆盖和仍未验证的数据库版本。

## 7. 下一步最小可执行工作包

1. ~~选第一批目标数据库~~ 已定：MySQL 5.7/8.4.x、SQL Server 2019/2022、PostgreSQL 16、Oracle 19c/23ai；驱动内置、测试用 Testcontainers + Docker（2026-09-15/16）。待办：冻结完整操作矩阵与风险级别（连接/查询/写入/DDL/账号逐格）。
2. 创建 Java 17 / Spring Boot 3.x / Maven 模块骨架，只定义 MEPER 契约与测试夹具。
3. 逐项改造迁入现有源码，按 MEPER 合同补齐测试。
4. 先打通“可信身份 → 表字段权限 → JDBC 查询/写入 → 审计”，再逐步启用 DDL/DCL 与 Agent 提案。

**本方案目标是尽量保留 Java 数据库能力，不承诺直接复制旧模块就能安全上线。**
