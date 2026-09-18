# meper-chatbi-domain

> 能力包：B 基本数据库操作（P1 ✅）｜后续承接：D 结构化 DML（P3）、C 权限建模接线（P2）｜依赖：connector-spi、connector-jdbc、query-enforcement、control-storage
> 模块约束原文：`com.meper.chatbi.domain.package-info`

## 1. 定位与边界

领域服务层：把连接器能力组织成业务用例（数据源管理、元数据、工作台），并对 web 暴露唯一调用面。接口不返回 HTTP DTO；不导入桌面/工作区语义（`consoleId` 等）；不出现方言特定 SQL。

**ExecutionContext 唯一签发点**：`ExecutionContextFactory`。任何调用方（含 Agent、未来 MCP）都从这里拿上下文，禁止别处构造。

## 2. 对外契约（已实现，P1）

### 2.1 `ExecutionContextFactory`
- `workbench(subject, datasourceId)` / `datasourceAdmin(subject, datasourceId)` → 填充 `tenantId="default"`、`Purpose`、随机 `correlationId`，执法状态**硬编码 `BOOTSTRAP_ADMIN_UNRESTRICTED`**（P2 切换点，见 §4）。

### 2.2 `DataSourceService`（数据源控制面）
`register / list / get / test / testTemporary / capabilities / rotateCredential / delete`，以及内部协作出口 `activePassword(profile)`（解密）、`poolFor(profile)`（取池）、`credentialRef(profile)`。
- 凭据：登记即加密落库，API 不回显；轮换 = 新 `credentialVersion` + 旧池 `evict`；
- `TestResult(success, message, latencyMs)`。

### 2.3 `MetadataService`（元数据链路）
`namespaces(datasourceId)` / `tables(dsId, namespace, namePattern)` / `tableDetail(dsId, namespace, table)` / `tableData(dsId, namespace, table, page, size)`。
- 分页：`DEFAULT_PAGE_SIZE=50`，`MAX_PAGE_SIZE=500`；
- 全部经 `JdbcMetadataReader` + 方言（catalog/schema 布局、系统库过滤）。

### 2.4 `WorkbenchService`（SQL 工作台）
`preview(principal, dsId, sql)` → 分类预检（`SqlClassifier.analyze`）+ 审计；
`format(principal, dsId, sql)` → 方言格式化（不连业务库）；
`execute(principal, dsId, sql, maxRows)` → 分类 → 签发上下文 → 解密取池 → `JdbcSqlExecutor` 执行 → 执行记录 + 审计 → `ExecuteResult(executionId, enforcement, status, statements, durationMs)`；
`getExecution / listExecutions` → 历史（page≤100 条）。
- 执行语义：多语句逐条 autocommit、失败即停；状态 SUCCESS/FAILED/PARTIAL。

### 2.5 `DomainConfig`
装配 bean：`ConnectionPoolRegistry`、`JdbcSqlExecutor`、`SqlClassifier`。

## 3. 依赖的模块契约

| 依赖 | 消费点 |
| --- | --- |
| connector-spi | `ExecutionContext` 签发与透传、`CapabilityDescriptor`、执行/元数据模型 |
| connector-jdbc | 池注册表、执行器、元数据读取器（经 `DomainConfig` 装配） |
| query-enforcement | `SqlClassifier` 分类与格式化（P2 起为执法管线） |
| control-storage | 数据源/凭据/执行/审计仓储 |

## 4. 能力包 C 接线点（P2 权限建模，已预留）

改动集中在本模块三个位置，**不得在 Controller 层加权限判断**：

1. `ExecutionContextFactory`：接入身份/权限域解析后签发 `ENFORCED`（需同步解除 connector-spi 的构造限制）；
2. `WorkbenchService.execute`：在 `classifier.analyze` 之后、`executor.execute` 之前插入**策略决策**（经 query-enforcement 执法管线，调 policy），并对结果做治理（列裁剪/脱敏/限行/revision 再校验）；
3. `MetadataService`：接入**元数据授权过滤**（库表树只显示授权对象，经 query-enforcement 的过滤接口）。

## 5. 能力包 D 演进（P3 结构化 DML）

- 新增 `MutationService`：结构化增删改计划（表 + 主键定位 + 行数据），经执法校验写权限（可写列矩阵、行规则）与审批，事务提交/回滚与幂等键管理；
- `WorkbenchService` 的原始 SQL 执行保留（管理员高级 SQL 语义，见架构方案 §4 入口分类），但写操作逐步引导到结构化通道；
- 存储侧新增变更任务表（见 [control-storage.md](./control-storage.md) §5）。

## 6. 能力矩阵 / 覆盖范围

领域层为纯编排，测试策略：单测覆盖纯逻辑（分页裁剪、状态聚合），链路正确性由 `FullLinkIntegrationTest`（start 模块，真实控制库 + Testcontainers 业务库）覆盖。

## 7. 开放决策点

**domain 依赖 connector-jdbc / query-enforcement 实现类**：`DomainConfig` 直接装配 `JdbcSqlExecutor` / `SqlClassifier`，因此 domain 的 pom 依赖这两个实现模块（旧模块地图写的是「仅 connector-spi」，已按实际修正，AGENTS.md 已同步）。
- 现状评价：模块化单体下可接受——装配内聚在 domain，start 只做 Spring Boot 启动；
- 备选：把连接器/执法 bean 的装配移到 start，domain 回到仅依赖 SPI。代价是 start 变厚、契约测试更绕；
- **结论（维持现状）**：若未来拆 Worker 或做独立扩容再重估。

## 8. 完成定义（DoD）

- 新用例有 `ExecutionContext`（经工厂）且写审计；
- 不出现方言 SQL / JDBC import（静态检查项）；
- P2 接线后：无策略数据源（未登记权限域）默认拒绝，执法状态不再出现 `BOOTSTRAP_ADMIN_UNRESTRICTED`（阶段 1 数据除外）。
