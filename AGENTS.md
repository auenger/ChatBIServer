# AGENTS.md — AI 协作指南

面向在此仓库工作的 AI 编码代理（Claude Code 等）与人类协作者。改动前请先读本文与两份设计文档。

## 项目是什么

MEPER ChatBI Server：以 Java 服务端为数据库能力主体的 ChatBI 后端。所有数据库访问（网页 / API / Agent）统一走 Java 领域接口，任何入口都不直接持有 JDBC 连接。

- [Java数据库能力架构方案.md](./Java数据库能力架构方案.md) — 目标架构（做什么、边界在哪）
- [Chat2DB-Java能力借鉴实施方案.md](./Chat2DB-Java能力借鉴实施方案.md) — 实施路径（怎么做、怎么验收）

Chat2DB 参考源码位于同级目录 `../Chat2DB-permission-aware-chatbi`（只读参考，branch `feature/permission-aware-chatbi`）。

## 当前阶段与边界

- **P1 核心链路已实现**：connector-spi 契约（ExecutionContext/CapabilityDescriptor/SqlDialect/SqlExecutor）、4 方言（URL/分页/探活/元数据命名空间布局）、HikariCP 池注册表（key = datasourceId + credentialVersion，轮换逐出）、SqlClassifier（Druid 拆分分类）、**JdbcMetadataReader（JDBC 标准元数据：库表树/表结构/表数据分页，MySQL 走 catalog、其余走 schema，系统库按方言过滤）**、数据源管理 + 工作台 + 元数据 API、AES-256-GCM 凭据加密（主密钥 `MEPER_MASTER_KEY` 缺失即拒绝启动）、控制库 Flyway schema（meper_ 前缀 5 表）、React+antd 前端（webapp/：登录/数据源管理/**workspace 库表树+表数据+表结构+查询**/独立工作台）。
- **执法状态恒为 BOOTSTRAP_ADMIN_UNRESTRICTED**：阶段 1 无策略约束，响应显式携带该标记；ExecutionContext 只能由 domain 的 ExecutionContextFactory 签发，P2 接入后在 WorkbenchService/DataSourceService 的执行路径插入策略决策，不得在 Controller 层加判断。
- 尚未迁入任何 Chat2DB 源码；迁入按实施方案 §3 的阶段推进，不要跳阶段引入半成品能力。
- **首批目标数据库已定（2026-09-15，2026-09-16 补版本矩阵）**：MySQL 5.7/8.4.x、SQL Server 2019/2022、PostgreSQL 16、Oracle 19c/23ai。JDBC 驱动内置在对应 dialect 模块（版本：mysql-connector-j / mssql-jdbc / postgresql 由 Spring Boot BOM 管理，ojdbc17 由根 pom `ojdbc.version` 锁定）；**不做自定义驱动 JAR 上传**，若未来引入必须先满足实施方案 §4 的驱动来源/校验/隔离要求。
- **MySQL 5.7 + mysql-connector-j 9.7.0**：官方不再声明支持 5.7，但实测连通正常（Testcontainers 验证）。方言实现里不得因此使用 5.7 独有行为；若发现协议级问题，降级驱动到 8.0.33。
- 集成测试用 Testcontainers + Docker，版本矩阵写在各 `*DriverTest` 的 `images()` 方法里；Oracle 19c 需自行提供镜像（`-Dmeper.test.oracle.images`，见 README）。测试类标注 `@Testcontainers(disabledWithoutDocker = true)`，无 Docker 自动跳过。
- 不要在本仓库引入生产数据库配置或任何真实凭据；测试只用 Docker 容器或命名本地测试库夹具。

## 构建与测试

```bash
# 全量构建 + 测试（提交前必须全绿）
mvn -B -ntp package

# 只测某模块及其依赖
mvn -B -ntp test -pl meper-chatbi-web -am

# 启动服务
mvn -B -ntp spring-boot:run -pl meper-chatbi-start
```

方言集成测试需要 Docker（无 Docker 自动跳过）；首次运行拉取镜像较慢，可用 `docker pull` 预热。新增方言相关测试时保持 `disabledWithoutDocker = true`，并把被测镜像版本写进测试代码（版本即矩阵记录）。

注意：本机 Maven 运行在 JDK 25 上，但编译目标锁定 Java 17（`maven.compiler.release=17`），不要改动。依赖版本由根 pom 的 `spring-boot-starter-parent`（当前 3.5.16）统一管理；新增第三方依赖前先确认根 pom 是否已覆盖。

## 模块地图与依赖硬规则

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| meper-chatbi-connector-spi | Connector/Dialect/Capability/ExecutionContext 契约 | 无内部依赖、无 Spring/Web/驱动 |
| meper-chatbi-connector-jdbc | 驱动加载、连接池、事务、执行、取消 | connector-spi |
| meper-chatbi-dialect-mysql | MySQL 方言 + 内置 mysql-connector-j | connector-spi |
| meper-chatbi-dialect-sqlserver | SQL Server 方言 + 内置 mssql-jdbc | connector-spi |
| meper-chatbi-dialect-postgresql | PostgreSQL 方言 + 内置驱动 | connector-spi |
| meper-chatbi-dialect-oracle | Oracle 方言 + 内置 ojdbc17 | connector-spi |
| meper-chatbi-policy | 身份映射、表/字段/行/操作策略、审批 | （暂无内部依赖） |
| meper-chatbi-query-enforcement | SQL/对象操作分类、权限校验、结果治理 | connector-spi |
| meper-chatbi-domain | 数据源、对象操作、任务、Agent、Wiki 工作流 | connector-spi |
| meper-chatbi-control-storage | Repository、配置版本、审计与任务存储 | （暂无内部依赖） |
| meper-chatbi-session-wiki | 授权语义目录、有限会话历史 | （暂无内部依赖） |
| meper-chatbi-agent | Agent 定义、Run、工具入口与交付 | （暂无内部依赖） |
| meper-chatbi-web | B/S API、Trusted API、MCP 适配 | domain、policy、query-enforcement |
| meper-chatbi-start | 装配、配置、健康检查 | 全部模块 |

禁止（静态合同检查项，实施方案 §6）：

- `web` 的 pom 出现 `connector-jdbc` / `dialect-*` 依赖，或任何直接 JDBC 调用
- `connector-spi` 出现 Spring Web、JDBC 驱动依赖
- 方言特定 SQL 出现在 web / domain
- 出现绕过 QueryEnforcement 直达执行层的调用路径

## 关键工程约束（迁移 Chat2DB 代码前必读）

1. **无隐式请求状态**：不用 `ThreadLocal` 传递身份/权限/连接；统一显式 `ExecutionContext`（服务端生成，前端不可自填授权）。
2. **权限默认拒绝**：MEPER 语义为"空字段列表 = 拒绝"，与 Chat2DB"空列表 = 全部可读"不同；策略语义变更必须版本化，历史策略迁入前显式转换。
3. **禁止 SSL 失败自动退化**：不得在连接失败后补 `useSSL=false` 重试；TLS 由管理员显式配置，失败保留原错误。
4. **凭据安全**：凭据不进日志与审计；控制库只存 `CredentialRef`，不存明文口令；撤权/轮换后不得复用旧连接。
5. **能力逐方言探测**：不因接口存在而宣称支持；不支持显式返回 UNSUPPORTED。
6. **Agent 边界**：Agent 工具仅经 QueryEnforcement 执行，不持有业务库连接/凭据；提案不等于执行许可；Agent 权限是主体权限的收窄。
7. **高危操作隔离**：DDL/DCL/删库/账号管理不与普通查询共用无约束入口，需计划、审批与专用凭据。

## 迁入 Chat2DB 源码的规则

- 只迁能力语义，不迁桌面/工作区语义（`consoleId`、进程级静态池等）。
- 命名空间一律用 `com.meper.chatbi.*`，不保留 `ai.chat2db`。
- 每个迁入文件重新检查 import、运行时状态、第三方依赖，并补对应测试；来源文件在 PR/说明中标注原路径。

## 代码风格

- 与现有代码一致：中文 Javadoc、模块约束写在 `package-info.java`、构造器注入、Java 17 特性（record、switch 表达式）优先。
- 先合同后迁入：新能力先定义接口与测试夹具（含操作矩阵中的 UNSUPPORTED 语义），再写实现。
- 每个模块的 package-info 是该模块的约束声明，改模块行为时同步更新。

## 完成定义

- `mvn -B -ntp package` 全绿（编译 + 测试）。
- 新能力有对应测试；方言相关用例按「数据库 × 操作 × 版本」记录覆盖。
- 不以纯 shell 检查冒充真实数据库操作测试。
- 代码与两份设计文档不一致时，要么改文档、要么在说明中写明差异，不留静默偏离。
