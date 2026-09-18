# MEPER ChatBI Server

以 **Java 服务端作为数据库能力主体** 的 ChatBI 后端。数据库连接、驱动管理、元数据发现、SQL 查询与增删改、事务、DDL/DCL、对象管理、导入导出及方言插件等完整能力收敛在服务端；B/S 网页、业务系统 API 与 Agent 通过同一套 Java 领域接口访问，**不直接获得 JDBC 连接**。

> 当前状态（2026-09-17）：**P1 核心链路与数据库操作台已实现**。包括数据源管理、Chat2DB 式库表树、对象列表、表数据/结构查看、SQL 智能编辑、预检执行和历史记录。当前执法状态仍为 `BOOTSTRAP_ADMIN_UNRESTRICTED`，表/字段/行权限在 P2 接入。

## 设计文档

| 文档 | 内容 |
| --- | --- |
| **[docs/README.md](./docs/README.md)** ⭐ | **文档核心索引**：全部文档的地址、定位、一句话描述与按场景阅读路径（Agent 与人类统一入口） |
| [Java数据库能力架构方案.md](./Java数据库能力架构方案.md) | 目标架构、能力目录、技术栈、统一操作管线、权限模型 |
| [Chat2DB-Java能力借鉴实施方案.md](./Chat2DB-Java能力借鉴实施方案.md) | 源码借鉴清单、必须改造的风险、阶段计划（G0→P6）、验证计划 |
| [docs/modules/](./docs/modules/README.md) | 模块开发文档：每模块契约/边界/能力矩阵/DoD，按能力包组织（独立拆分开发的工作界面） |

## 当前已实现功能

### 数据源与连接

- 引导管理员登录、退出和当前用户查询。
- MySQL、SQL Server、PostgreSQL、Oracle 四种内置 JDBC 方言与驱动。
- 数据源登记、列表、详情、删除、连通测试、能力查询和凭据轮换。
- AES-256-GCM 凭据加密；控制库不保存明文密码，API 不回显密码。
- HikariCP 动态连接池，按 `datasourceId + credentialVersion` 隔离；凭据轮换后逐出旧池。
- 方言负责 JDBC URL、标识符引用、分页、探活、系统命名空间过滤与 Catalog/Schema 布局。

### 数据库操作台（`/workspace`）

- Chat2DB 式数据源树：数据源 → 库/Schema → 表/视图分组 → 对象，按需懒加载。
- 展开箭头和双击节点均可展开/收起；表节点双击直接打开表数据。
- 已加载对象搜索、刷新、新建查询、查看结构等快捷操作。
- 表/视图对象列表，支持搜索、分页、刷新，以及打开数据、查看结构、新建查询。
- 表数据分页、精确总行数、结果内搜索、行选择、复制与 CSV 下载。
- 表结构展示列类型、可空、默认值、备注、主键、自增和索引信息。
- 多标签页工作区；菜单切换后标签页、SQL 草稿、数据源和 Schema 选择不会丢失。SQL 草稿写入 `sessionStorage`，查询结果在当前页面会话内保留。

### SQL 编辑与执行

- Monaco SQL 编辑器，支持当前选区执行和 `Ctrl/Cmd + Enter` 快速运行。
- 基于真实元数据的库/Schema、表、视图、字段与表别名补全。
- 四种方言的关键字、常用函数提示，以及 SELECT/INSERT/UPDATE/DELETE/CTE 模板。
- SQL 本地结构检查：未闭合引号、注释、括号及多余右括号标记。
- UPDATE/DELETE 缺少 WHERE 条件时给出风险提醒。
- 服务端按目标方言格式化 SQL，支持完整文档或选区格式化及 `Shift + Alt + F`。
- `preview → confirm → execute` 两阶段执行；SQL 修改后自动作废旧预检，避免确认错误语句。
- 多语句分类（SELECT/DML/DDL/TCL/OTHER）、逐语句结果、失败停止、最大返回行数和单元格截断。
- 执行状态、耗时、结果表格和最近 100 条执行历史；预检、格式化、执行均写审计记录。

### 元数据与验证

- JDBC 标准元数据读取：命名空间、表/视图、列、主键、索引和备注。
- 表数据读取统一使用方言分页，带查询超时、最大行/列和单元格字符限制。
- Testcontainers 覆盖 MySQL 5.7/8.4.x、SQL Server 2019/2022、PostgreSQL 16 和 Oracle 23ai；Oracle 19c 支持通过参数提供镜像。

### 当前尚未完成

- P2 表/字段/行/操作权限、策略版本、撤权后结果治理与审批链路。
- P3 结构化 DML、批量编辑、事务/幂等和写权限控制。
- P4 DDL/DCL、账号、例程、导入导出等专用管理能力。
- P5 Session Wiki、Agent Run 和授权工具交付。

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
- `query-enforcement → connector-spi`
- `domain → connector-spi / connector-jdbc / query-enforcement / control-storage`（装配与仓储；详见 [docs/modules/domain.md](./docs/modules/domain.md)）
- `connector-jdbc / dialect-* → connector-spi`
- `start` 只做装配，依赖所有模块
- `connector-spi` 不依赖 Spring / Web / 驱动
- 方言 SQL 只出现在 `dialect-*` 与 `connector-jdbc`

## 快速开始

要求：JDK 17+、Maven 3.9+、Docker（控制库/测试）、Node 18+ 与 pnpm（前端）。

```bash
# 1. 控制库（MySQL 8.4；业务库按需再起）
docker run -d --name meper-control -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=meper_control -p 3307:3306 mysql:8.4

# 2. 全量构建 + 测试（当前 58 个测试，方言矩阵用 Testcontainers）
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

### 服务启停速查

```bash
# ── 启动（容器已存在时直接 start，数据保留）──
docker start meper-control meper-biz
MEPER_MASTER_KEY=$(cat /tmp/meper-master-key 2>/dev/null || echo "$(openssl rand -base64 32)") \
  mvn spring-boot:run -pl meper-chatbi-start          # 后端 :8080（注意：主密钥与首次登记时不同，旧数据源凭据将无法解密）
cd webapp && pnpm dev                                  # 前端 :8001（dev 代理 /api → 8080）

# ── 停止服务 ──
pkill -f 'spring-boot:run'    # 后端
pkill -f 'umi dev'            # 前端

# ── 停止/清理容器（数据在容器卷里，rm 会丢业务数据）──
docker stop meper-control meper-biz      # 仅停止
docker rm -f meper-control meper-biz     # 彻底删除
```

> 注意：主密钥（`MEPER_MASTER_KEY`）一旦更换，控制库中已加密的数据源凭据将无法解密，需重新登记或轮换数据源凭据。本地开发建议把密钥固定写入 `.env` 或 shell profile，不要每次随机生成。

只构建/测试单个模块（含其依赖）：

```bash
mvn -B -ntp test -pl meper-chatbi-web -am
```

> 方言集成测试（`*DriverTest`）需要本机 Docker；首次运行会拉取数据库镜像。没有 Docker 时这些测试自动跳过，其余测试不受影响。

## 路线图

| 阶段 | 内容 |
| --- | --- |
| G0 | 盘点：源码清单、目标数据库与能力矩阵 |
| **P1 ✅（基础）** | **连接器内核、4 方言与内置驱动、HikariCP 池注册表、数据源管理、凭据加密、控制库存储；并提前交付元数据 API、数据库操作台和基础 SQL 工作台** |
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
