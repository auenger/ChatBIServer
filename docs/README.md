# MEPER ChatBI Server 文档核心索引

> 面向**人类协作者与 AI 编码代理**的统一文档入口。按「想做什么」查文档；路径均为仓库相对路径；本文档随文档增删同步维护。

## 0. 30 秒速览

| 项 | 状态 |
| --- | --- |
| 项目定位 | 以 Java 服务端为数据库能力主体的 ChatBI 后端；任何入口不直接持有 JDBC 连接 |
| 当前进度 | **P1 完成**（连接器内核 + 基本数据库操作 + 操作台前端）；P2 权限建模未开工，**契约已定稿** |
| 执法状态 | `BOOTSTRAP_ADMIN_UNRESTRICTED`（阶段 1 无策略约束，响应显式携带） |
| 工程规模 | 15 个 Maven 模块 + React 前端（webapp/）；`mvn -B -ntp package` 全绿（58 个测试） |
| 目标数据库 | MySQL 5.7/8.4.x · SQL Server 2019/2022 · PostgreSQL 16 · Oracle 19c/23ai（Testcontainers 矩阵） |
| 能力包 | A 连接器内核 ✅ · B 基本数据库操作 ✅ · C 权限建模 ❌（P2） · D 结构化 DML ❌（P3） · E 会话语义 · F Agent（P5 骨架） |

## 1. 全部文档一览

### 1.1 根目录：全局设计与协作规则

| 文档 | 路径 | 一句话描述 | 什么时候读 |
| --- | --- | --- | --- |
| **文档核心索引** | [docs/README.md](./README.md) | 本文：全部文档的定位、状态与阅读路径 | 找不到该读哪份时 |
| 项目总览 | [../README.md](../README.md) | 已实现功能清单、快速开始、服务启停速查、路线图、版本矩阵 | 首次了解 / 启动项目 |
| AI 协作指南 | [../AGENTS.md](../AGENTS.md) | 模块地图与依赖硬规则、7 条关键工程约束、迁入规则、完成定义 | **AI 代理改动代码前必读** |
| 目标架构 | [../Java数据库能力架构方案.md](../Java数据库能力架构方案.md) | 能力目录（12 域）、统一操作管线、权限模型、模块划分 —— 回答「为什么这样设计、边界在哪」 | 做设计决策 / 评审时 |
| 实施方案 | [../Chat2DB-Java能力借鉴实施方案.md](../Chat2DB-Java能力借鉴实施方案.md) | 阶段计划 G0→P6 与完成条件、必须改造的风险清单、验证计划 —— 回答「怎么做、怎么验收」 | 排阶段计划 / 验收时 |

### 1.2 模块开发文档（`docs/modules/`，独立拆分开发的工作界面）

> 每模块一份：定位边界、对外契约、依赖契约、能力矩阵、完成定义、开放决策点。
> 分索引（能力包映射、依赖图、并行开发时序）：[modules/README.md](./modules/README.md)。

| 模块文档 | 路径 | 一句话描述 | 状态 |
| --- | --- | --- | --- |
| 模块索引 | [modules/README.md](./modules/README.md) | 6 能力包 ↔ 模块映射、实际依赖图、**并行开发交接点与时序**、状态总表 | — |
| connector-spi | [modules/connector-spi.md](./modules/connector-spi.md) | SPI 契约层：ExecutionContext / SqlDialect / SqlExecutor / 18 模型的不变式与演进规则 | ✅ P1 |
| connector-jdbc | [modules/connector-jdbc.md](./modules/connector-jdbc.md) | JDBC 执行内核：池注册表（`datasourceId+credentialVersion`）、元数据读取、执行限额 | ✅ P1 |
| dialects | [modules/dialects.md](./modules/dialects.md) | 四方言族：URL/分页/引用符/系统库速查表、版本矩阵、P6 新方言接入步骤 | ✅ P1 |
| control-storage | [modules/control-storage.md](./modules/control-storage.md) | 控制库持久化：V1 五表、AES-256-GCM 加密；P2 交付 V2 策略表 | ✅ P1 / P2 待开发 |
| domain | [modules/domain.md](./modules/domain.md) | 领域服务：数据源/元数据/工作台用例契约、**P2 接线点精确位置**、P3 演进 | ✅ P1 |
| web | [modules/web.md](./modules/web.md) | REST API 层：21 端点清单、认证机制、P2 身份扩展与策略管理 API | ✅ P1 |
| **policy** | [modules/policy.md](./modules/policy.md) | 权限建模核心：决策契约（`PolicyDecisionService`）、默认拒绝语义、§3.1 多策略叠加、超级管理员短路、§7 已决策记录 | ❌ P2（**契约已定稿**） |
| query-enforcement | [modules/query-enforcement.md](./modules/query-enforcement.md) | 执法管线：目标资源解析（JOIN/CTE→默认拒绝兜底）、行规则注入、结果治理、三类拒绝语义 | ◐ 仅分类 / P2 待开发 |
| session-wiki | [modules/session-wiki.md](./modules/session-wiki.md) | 授权过滤的语义目录与有限会话历史（目标契约 sketch） | 骨架 / P5 |
| agent | [modules/agent.md](./modules/agent.md) | Agent 定义/Run/工具清单、权限收窄模型、提案≠执行许可 | 骨架 / P5 |

### 1.3 代码内约束（与文档同等效力）

| 位置 | 内容 |
| --- | --- |
| 各模块 `package-info.java` | 模块约束声明，改模块行为时与模块文档**同步更新** |
| `meper-chatbi-control-storage/src/main/resources/db/migration/` | Flyway schema（V1 运行表；P2 增 V2 策略表，只增不改） |

## 2. 按场景的阅读路径

| 场景 | 阅读顺序 |
| --- | --- |
| 首次了解项目（人类） | 根 README → 架构方案 §1–2 → modules/README |
| **AI 代理改代码** | AGENTS.md（硬规则）→ 本索引定位模块 → 对应模块文档 → 改动后回写文档与 package-info |
| 启动 / 体验系统 | 根 README「快速开始」+「服务启停速查」 |
| **开发 P2 权限建模** | modules/README §3 时序 → policy.md §3 决策契约 → query-enforcement.md → control-storage.md §4 → web.md §4 |
| 新增数据库方言（P6） | dialects.md §5 接入步骤 + AGENTS.md 版本矩阵规则 |
| 查 API / 表结构 | web.md §2 路由表 / control-storage.md §2 表结构 |
| 排查权限/执法问题 | query-enforcement.md §3.5 拒绝语义 → policy.md §3 判定规则 |

## 3. 关键决策与红线（速查）

**已冻结决策（2026-09-18，详见 [policy.md §7](./modules/policy.md)）**：
1. policy → control-storage 仓储依赖：放开；
2. 同表多策略叠加 =「allow 取并、约束取最严」（列交集 / 脱敏并集 / 行规则 AND / maxRows 最小）；
3. 内置超级管理员可跳过策略决策用于测试——短路必须在 `PolicyDecisionService` 内实现并审计（`SUPER_ADMIN_BYPASS`），不得另开免检执行路径。

**不可违反的工程红线（AGENTS.md 全文为准）**：
- 权限默认拒绝：空字段列表 = 拒绝（与 Chat2DB 相反）；
- `ExecutionContext` 只能由 domain 工厂签发，不用 ThreadLocal 传身份；
- web 无 JDBC 直连；不存在绕过 QueryEnforcement 的执行路径；
- 禁止连接失败自动退化 SSL；凭据不进日志与控制库明文。

## 4. 文档维护规则

- 模块行为变更 → 同步更新：模块文档 + `package-info.java`（+ 本索引的状态列）；
- 方向性决策 → 在对应模块文档设「已决策记录」节（参照 policy.md §7，带日期），不开静默口子；
- 代码与文档不一致 → 要么改代码、要么改文档并写明，不留静默偏离（AGENTS.md 完成定义）；
- 新增文档 → 在本索引 §1 登记并归入合适场景（§2）。
