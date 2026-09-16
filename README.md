# MEPER ChatBI Server

以 **Java 服务端作为数据库能力主体** 的 ChatBI 后端。数据库连接、驱动管理、元数据发现、SQL 查询与增删改、事务、DDL/DCL、对象管理、导入导出及方言插件等完整能力收敛在服务端；B/S 网页、业务系统 API 与 Agent 通过同一套 Java 领域接口访问，**不直接获得 JDBC 连接**。

> 当前状态：**P1 核心链路已实现** —— 数据源管理（登记/测试/能力/轮换）+ **工作区（Chat2DB 式库表树 / 表数据网格 / 表结构）** + SQL 工作台（preview→execute→历史）。引导管理员阶段（无策略约束，P2 接入）。

## 设计文档

| 文档 | 内容 |
| --- | --- |
| [Java数据库能力架构方案.md](./Java数据库能力架构方案.md) | 目标架构、能力目录、技术栈、统一操作管线、权限模型 |
| [Chat2DB-Java能力借鉴实施方案.md](./Chat2DB-Java能力借鉴实施方案.md) | 源码借鉴清单、必须改造的风险、阶段计划（G0→P6）、验证计划 |

## 技术栈

- Java 17（`maven.compiler.release=17`）
- Spring Boot 3.5.x（当前锁定 3.5.16）
- Maven 多模块（Maven 3.9+）

## 模块结构

```text
meper-chatbi-server
├─ meper-chatbi-start               组装、配置、健康检查（Spring Boot 启动入口）
├─ meper-chatbi-web                 B/S API、Trusted API、MCP 适配；无 JDBC 直连
├─ meper-chatbi-domain              数据源、对象操作、任务、Agent、Wiki 工作流
├─ meper-chatbi-policy              身份映射、表/字段/行/操作决策、审批
├─ meper-chatbi-query-enforcement   SQL/对象操作分类、规划、权限校验、结果治理
├─ meper-chatbi-connector-spi       Connector、Dialect、Capability、ExecutionContext 契约
├─ meper-chatbi-connector-jdbc      驱动加载、DataSource/连接池、事务、执行、取消
├─ meper-chatbi-dialect-mysql       MySQL 方言实现（内置 mysql-connector-j）
├─ meper-chatbi-dialect-sqlserver   SQL Server 方言实现（内置 mssql-jdbc）
├─ meper-chatbi-dialect-postgresql  PostgreSQL 方言实现（内置 PostgreSQL JDBC 驱动）
├─ meper-chatbi-dialect-oracle      Oracle 方言实现（内置 ojdbc17）
├─ meper-chatbi-control-storage     领域 Repository、配置版本、审计与任务存储
├─ meper-chatbi-session-wiki        授权语义目录与有限会话历史
└─ meper-chatbi-agent               Agent 定义、Run、工具入口与交付
```

各模块的职责与约束同时写在模块的 `package-info.java` 中。

### 模块依赖方向（硬规则）

- `web → domain / policy / query-enforcement`，**web 无 JDBC 直连**
- `domain / query-enforcement → connector-spi`
- `connector-jdbc / dialect-* → connector-spi`
- `start` 只做装配，依赖所有模块
- `connector-spi` 不依赖 Spring / Web / 驱动
- 方言 SQL 只出现在 `dialect-*` 与 `connector-jdbc`

## 快速开始

要求：JDK 17+、Maven 3.9+、Docker（控制库/测试）、Node 18+ 与 pnpm（前端）。

```bash
# 1. 控制库（MySQL 8.4；业务库按需再起）
docker run -d --name meper-control -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=meper_control -p 3307:3306 mysql:8.4

# 2. 全量构建 + 测试（55 个测试，方言矩阵用 Testcontainers）
mvn -B -ntp package

# 3. 启动后端（主密钥缺失会拒绝启动；首次提交会先 install 到本地仓库）
mvn -B -ntp install -DskipTests
MEPER_MASTER_KEY=$(openssl rand -base64 32) mvn spring-boot:run -pl meper-chatbi-start

# 4. 启动前端（dev 代理 /api → 8080）
cd webapp && pnpm install && pnpm dev    # http://localhost:8001

# 5. 验证
curl http://localhost:8080/api/ping
curl http://localhost:8080/actuator/health
```

默认引导管理员：`admin / meper-admin-2026`（仅限本地开发，用 `MEPER_BOOTSTRAP_ADMIN_PASSWORD` 覆盖）。环境变量：`MEPER_CONTROL_DB_URL/USERNAME/PASSWORD` 可切换控制库。

只构建/测试单个模块（含其依赖）：

```bash
mvn -B -ntp test -pl meper-chatbi-web -am
```

> 方言集成测试（`*DriverTest`）需要本机 Docker；首次运行会拉取数据库镜像。没有 Docker 时这些测试自动跳过，其余测试不受影响。

## 路线图

| 阶段 | 内容 |
| --- | --- |
| G0 | 盘点：源码清单、目标数据库与能力矩阵 |
| **P1 ✅（基础）** | **连接器内核：SPI 契约、4 方言、内置驱动、HikariCP 池注册表（凭据版本隔离+轮换逐出）、连通测试；数据源管理 API；AES-256-GCM 凭据加密；控制库 Flyway schema** |
| P2 | 查询和权限：元数据过滤、身份模型、表/字段/行策略、受控查询 |
| P3 | 数据增删改：DML、批量、事务、幂等、审批 |
| P4 | 对象与账号全能力：DDL/DCL、例程、导入导出、管理员高级 SQL（工作台基础执行已在 P1 提前提供，逐语句 autocommit） |
| P5 | Session Wiki 与 Agent |
| P6 | 方言扩展 |

详细完成条件见[实施方案 §3](./Chat2DB-Java能力借鉴实施方案.md)。

**首批目标数据库（2026-09-15 决策，2026-09-16 补充版本矩阵）**：MySQL、SQL Server、PostgreSQL、Oracle。JDBC 驱动全部内置到对应方言模块（不做自定义驱动 JAR 上传）；集成测试用 Testcontainers + Docker 起库，无 Docker 时自动跳过。

| 数据库 | 目标兼容版本 | 测试镜像 |
| --- | --- | --- |
| MySQL | 5.7、8.4.x | `mysql:5.7`、`mysql:8.4.9` |
| SQL Server | 2019、2022 | `mcr.microsoft.com/mssql/server:2019-latest`、`...:2022-latest` |
| PostgreSQL | 16 | `postgres:16-alpine` |
| Oracle | 19c、23ai | `gvenzl/oracle-free:23-slim`（19c 见下方说明） |

> **MySQL 5.7 兼容性结论**：Spring Boot BOM 管理的 mysql-connector-j 9.7.0 官方不再声明支持 5.7，但实测（Testcontainers `mysql:5.7`）连接与查询均正常。暂保留 9.7.0；若后续在 5.7 上发现协议级问题，降级方案为驱动锁 8.0.33（最后一个官方声明支持 5.7 的版本）。
>
> **Oracle 19c 说明**：ojdbc17 官方认证服务端 19c+。19c 无免费公开镜像，需 `docker login container-registry.oracle.com` 拉取 `database/standard:19.3.0`（约 9GB，首次启动 10 分钟以上），然后运行：
> `mvn test -pl meper-chatbi-dialect-oracle -am "-Dtest=OracleDriverTest" -Dmeper.test.oracle.images=container-registry.oracle.com/database/standard:19.3.0`
>
> 其他方言的矩阵同样支持用系统属性覆盖：`-Dmeper.test.mysql.images`、`-Dmeper.test.sqlserver.images`。

## 安全基线（摘要）

- 权限默认拒绝；空字段列表 = 拒绝（与 Chat2DB 的"空列表 = 全部可读"语义不同，契约需版本化）
- 禁止连接失败后自动降级 SSL
- 不用 ThreadLocal 传身份/权限，统一显式 `ExecutionContext`
- 凭据不落日志；控制库只存 `CredentialRef`，不存明文口令
- Agent 工具仅经 QueryEnforcement 执行，提案不等于执行许可

完整清单见两份设计文档。
