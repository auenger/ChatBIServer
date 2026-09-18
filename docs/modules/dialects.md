# meper-chatbi-dialect-mysql / sqlserver / postgresql / oracle（方言族）

> 能力包：A 连接器内核｜阶段：P1 ✅（P6 扩展新方言）｜依赖：connector-spi
> 四个模块结构同构，契约共享，本文档按方言分节；模块约束原文见各自 `package-info.java`。

## 1. 定位与边界

每个方言模块：一个 `XxxDialect` 实现 + 内置官方 JDBC 驱动。方言 SQL（URL 拼装、分页改写、标识符引用、探活、命名空间布局、系统库过滤）**只允许出现在方言模块与 connector-jdbc**，不得上移到 domain / web。

- 注册方式：`META-INF/services/com.meper.chatbi.spi.SqlDialect`（ServiceLoader），互不依赖；
- 驱动版本：mysql-connector-j / mssql-jdbc / postgresql 由 Spring Boot BOM 管理，ojdbc17 由根 pom `ojdbc.version` 锁定；
- **不做自定义驱动 JAR 上传**；
- 能力逐方言探测：不支持的能力显式返回（如 Oracle `sslMode=REQUIRED` 抛 `UnsupportedOperationException`），不得因接口存在而宣称支持。

## 2. 共享契约

实现 `SqlDialect`（方法语义见 [connector-spi.md](./connector-spi.md) §2.2）+ 每方言一份 `XxxDialectTest`（引用符/URL/分页单测）与 `XxxDriverTest`（Testcontainers 真实库矩阵）。

## 3. 各方言行为速查

| 行为 | MySQL | PostgreSQL | SQL Server | Oracle |
| --- | --- | --- | --- | --- |
| URL 骨架 | `jdbc:mysql://host:port/db?sslMode=…&characterEncoding=utf8&extendInfo…` | `jdbc:postgresql://host:port/db?sslmode=…` | `jdbc:sqlserver://host:port;encrypt=…;databaseName=…` | `jdbc:oracle:thin:@//host:port/service` |
| SSL 参数 | `sslMode`；非 REQUIRED 补 `allowPublicKeyRetrieval=true` | `sslmode`：PREFERRED/REQUIRED/DISABLED → prefer/require/disable | `encrypt=false/true`；非 DISABLED 补 `trustServerCertificate=true` | TCPS 不支持，REQUIRED 显式抛错 |
| 探活 | `SELECT 1` | `SELECT 1` | `SELECT 1` | `SELECT 1 FROM DUAL` |
| 引用符 | `` `id` ``（内部 `` ` ``→` `` ` ``） | `"id"`（`"`→`""`） | `[id]`（`]`→`]]`） | `"id"`（`"`→`""`） |
| 分页 | `LIMIT_OFFSET` | `LIMIT_OFFSET` | `OFFSET_FETCH` | `OFFSET_FETCH` |
| 命名空间 | `CATALOG_IS_DATABASE` | `SCHEMA_BASED` | `SCHEMA_BASED` | `SCHEMA_BASED` |
| 系统库过滤 | `information_schema`、`mysql`、`performance_schema`、`sys` | `template0`、`template1` | `master`、`tempdb`、`model`、`msdb` | 未启用额外过滤（用户默认连接指定 schema） |

安全规则（全部方言适用）：连接失败保留原错误，**禁止补 `useSSL=false` 类自动退化**；TLS 由管理员显式配置。

## 4. 版本矩阵（测试即记录）

| 数据库 | 目标兼容 | 测试镜像 | 已知事项 |
| --- | --- | --- | --- |
| MySQL | 5.7、8.4.x | `mysql:5.7`、`mysql:8.4.9` | mysql-connector-j 9.7.0 官方不再声明支持 5.7，实测连通正常；若发现协议级问题降级驱动到 8.0.33 |
| PostgreSQL | 16 | `postgres:16-alpine` | — |
| SQL Server | 2019、2022 | `mcr.microsoft.com/mssql/server:2019-latest`、`2022-latest` | — |
| Oracle | 19c、23ai | `gvenzl/oracle-free:23-slim`；19c 需 `container-registry.oracle.com/database/standard:19.3.0` | ojdbc17 官方认证服务端 19c+；19c 首启 10 分钟以上 |

矩阵可用系统属性覆盖：`-Dmeper.test.mysql.images`、`-Dmeper.test.sqlserver.images`、`-Dmeper.test.oracle.images`。

## 5. P6 方言扩展：新方言接入步骤

1. 新建 `meper-chatbi-dialect-<db>` 模块，依赖 connector-spi，驱动内置并锁版本；
2. 实现 `SqlDialect` 全部方法；能力不支持项显式返回 / 抛 `UnsupportedOperationException`，不静默降级；
3. 注册 ServiceLoader 文件；`DatabaseType` 增加枚举值（连带根 pom 版本管理）；
4. 补 `XxxDialectTest` + `XxxDriverTest`（镜像版本写进测试代码，`disabledWithoutDocker = true`）；
5. 更新本文档 §3/§4、根 README 版本矩阵、`DataSourceController` 支持类型清单（如需要）。

## 6. 完成定义（DoD）

- 每方言：引用符转义、URL 拼装（含 SSL 三态）、分页改写、命名空间布局、系统库过滤均有单测；真实库矩阵（连接/元数据/分页/执行）全绿；
- 能力声明与实测一致（不得虚报 SUPPORTED）。
