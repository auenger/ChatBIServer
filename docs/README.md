# MEPER ChatBI Server 文档核心索引

> 面向**人类协作者与 AI 编码代理**的统一文档入口。按「想做什么」查文档；路径均为仓库相对路径；本文档随文档增删同步维护。

## 0. 30 秒速览

| 项 | 状态 |
| --- | --- |
| 项目定位 | 以 Java 服务端为数据库能力主体的 ChatBI 后端；任何入口不直接持有 JDBC 连接 |
| 当前进度 | **P1、0.1 核心、DataWiki/数据集已实现**；当前验收见下方记录；P2 尚未实现，适配修订已完成 |
| 执法状态 | `BOOTSTRAP_ADMIN_UNRESTRICTED`（阶段 1 无策略约束，响应显式携带） |
| 工程规模 | 14 个子模块（含根项目共 15 个 Maven reactor 项目） + React 前端（webapp/）；`mvn -B -ntp package` 全绿（79 个测试，2026-10-13） |
| 目标数据库 | MySQL 5.7/8.4.x · SQL Server 2019/2022 · PostgreSQL 16 · Oracle 19c/23ai（默认矩阵测 23ai，19c 需自备镜像） |
| 能力包 | A 连接器内核 ✅ · B 基本数据库操作 ✅ · C 权限建模 ❌（P2） · D 结构化 DML ❌（P3） · E DataWiki 已实现 · F 对话/工具已实现，完整 P5 待开发 |

## 1. 全部文档一览

**ChatBI 整体推进规划（2026-10-15，六项方向已确认）**：[打开 HTML](plans/chatbi-roadmap-20261015.html)。展示当前能力、已确认决策、M0–M5 整体路线、模块落点、验收门禁和下一工作包。按“只读优先、模型复用/跨源域/单域执行、最严合并、重读与敏感正文缓冲复验、数据库目录映射、只读阶段运行治理”推进；技术合同、人员与工期待实施细化。

**ChatBI 推进计划讨论页（2026-10-15）**：[打开 HTML](prototypes/chatbi-roadmap-discussion.html)。汇总当前进度、六阶段建议路线与六项待定决策；支持本地保存备注、导出 JSON/Markdown 和打印。属于讨论工作稿，不代表已确认合同或实施排期。

**通用权限交互原型**：[打开 HTML](prototypes/general-permission-studio.html) · [演示路径与边界](prototypes/README.md)。用于讨论外部身份映射与通用授权，全部为本地模拟。

**通用权限模型设计讨论稿（Archify）**：[打开讨论入口](design/permission-model-20261013/index.html) · [完整方案与策略真值表](design/permission-model-20261013/README.md)。包含模型关系与请求授权交互图，标明已确认约束和待讨论决策；表达 P2 目标设计，尚未实现。

P2 身份建模先读 [通用权限模型复核](plans/p2-general-permission-model-review-20261013.md)（外部业务服务、可配置主体映射；新合同待细化）。

P2 全入口执法参考 [适配修订](plans/p2-permission-adaptation-20261013.md)（contractVersion 2；Wiki/数据集/历史全部纳入切换门禁）。

当前事实与下一步建议优先阅读 [2026-10-13 进度对齐](plans/progress-alignment-20261013.md)；带日期的旧设计保留历史语境。

当前版本验收与 Docker/真实模型证据见 [2026-10-13 验收记录](plans/release-acceptance-20261013.md)。

### 1.1 根目录：全局设计与协作规则

| 文档 | 路径 | 一句话描述 | 什么时候读 |
| --- | --- | --- | --- |
| **文档核心索引** | [docs/README.md](./README.md) | 本文：全部文档的定位、状态与阅读路径 | 找不到该读哪份时 |
| 项目总览 | [../README.md](../README.md) | 已实现功能清单、快速开始、服务启停速查、路线图、版本矩阵 | 首次了解 / 启动项目 |
| Docker 部署 | [../deploy/docker/README.md](../deploy/docker/README.md) | 前后端镜像、Compose、配置与升级、SSE 同源代理 | Docker 打包 / 部署 |
| AI 协作指南 | [../AGENTS.md](../AGENTS.md) | 模块地图与依赖硬规则、7 条关键工程约束、迁入规则、完成定义 | **AI 代理改动代码前必读** |
| 目标架构 | [../Java数据库能力架构方案.md](../Java数据库能力架构方案.md) | 能力目录（12 域）、统一操作管线、权限模型、模块划分 —— 回答「为什么这样设计、边界在哪」 | 做设计决策 / 评审时 |
| 实施方案 | [../Chat2DB-Java能力借鉴实施方案.md](../Chat2DB-Java能力借鉴实施方案.md) | 阶段计划 G0→P6 与完成条件、必须改造的风险清单、验证计划 —— 回答「怎么做、怎么验收」 | 排阶段计划 / 验收时 |
| **当前进度对齐** | [plans/progress-alignment-20261013.md](./plans/progress-alignment-20261013.md) | 代码、历史规划、验收边界、已确认差异与后续建议 | 当前进度 / 下一阶段启动前 |
| **数据集与对话式 Wiki** | [plans/dataset-knowledge-v2.md](./plans/dataset-knowledge-v2.md) | 布局收缩、批量发布、默认模型、知识对话与跨库分析 | 第二轮开发和验收 |
| **DataWiki 实施与验收** | [plans/datawiki-implementation.md](./plans/datawiki-implementation.md) | 结构镜像、业务语义、发布版本、按需检索与页面人工验收 | 当前 DataWiki 开发与验收 |
| **P2 实施计划** | [plans/p2-permission-implementation-plan.md](./plans/p2-permission-implementation-plan.md) | P2 权限建模 8 批次落地：类清单、权限表 DDL（当前从 V9 追加）、测试矩阵、行为翻转点、已决策记录（§10） | 历史 v1 基线；与新修订冲突处按修订稿执行 |
| **P5 Agent 设计** | [plans/p5-agent-design.md](./plans/p5-agent-design.md) | 内置编排 Agent 定稿：Spring AI + OpenAI/Anthropic 双协议网关、AgentTool SPI、五层权限交集、SSE 交付、落地批次 | 开发 P5 Agent 前必读（设计定稿 2026-09-23，实现待 P2/P3） |
| **0.1 Agent chat MVP** | [plans/p01-agent-chat-mvp.md](./plans/p01-agent-chat-mvp.md) | 最小闭环：模型配置页（双协议）+ chat SSE + 3 工具 + SELECT 自动执行/非 SELECT 回显；占用 V2–V6 迁移（后续 V7/V8 为 Wiki/数据集） | 核心实现已交付；后续扩展见 DataWiki/数据集计划 |

### 1.2 模块开发文档（`docs/modules/`，独立拆分开发的工作界面）

> 每模块一份：定位边界、对外契约、依赖契约、能力矩阵、完成定义、开放决策点。
> 分索引（能力包映射、依赖图、并行开发时序）：[modules/README.md](./modules/README.md)。

| 模块文档 | 路径 | 一句话描述 | 状态 |
| --- | --- | --- | --- |
| 模块索引 | [modules/README.md](./modules/README.md) | 6 能力包 ↔ 模块映射、实际依赖图、**并行开发交接点与时序**、状态总表 | — |
| connector-spi | [modules/connector-spi.md](./modules/connector-spi.md) | SPI 契约层：ExecutionContext / SqlDialect / SqlExecutor / 18 模型的不变式与演进规则 | ✅ P1 |
| connector-jdbc | [modules/connector-jdbc.md](./modules/connector-jdbc.md) | JDBC 执行内核：池注册表（`datasourceId+credentialVersion`）、元数据读取、执行限额 | ✅ P1 |
| dialects | [modules/dialects.md](./modules/dialects.md) | 四方言族：URL/分页/引用符/系统库速查表、版本矩阵、P6 新方言接入步骤 | ✅ P1 |
| control-storage | [modules/control-storage.md](./modules/control-storage.md) | 控制库持久化：V1 五表、AES-256-GCM 加密；P2 策略表待追加（当前下一号 V9） | ✅ P1 / P2 待开发 |
| domain | [modules/domain.md](./modules/domain.md) | 领域服务：数据源/元数据/工作台用例契约、**P2 接线点精确位置**、P3 演进 | ✅ P1 |
| web | [modules/web.md](./modules/web.md) | REST API 层：21 端点清单、认证机制、P2 身份扩展与策略管理 API | ✅ P1 |
| **policy** | [modules/policy.md](./modules/policy.md) | 权限建模核心：决策契约（`PolicyDecisionService`）、默认拒绝语义、§3.1 多策略叠加、超级管理员短路、§7 已决策记录 | ❌ P2（修订契约 v2，待落码） |
| query-enforcement | [modules/query-enforcement.md](./modules/query-enforcement.md) | 执法管线：目标资源解析（JOIN/CTE→默认拒绝兜底）、行规则注入、结果治理、三类拒绝语义 | ◐ 分类与范围校验 / P2 待开发 |
| session-wiki | [modules/session-wiki.md](./modules/session-wiki.md) | DataWiki 结构镜像、业务语义与发布检索；权限过滤衔接 | DataWiki 首版 / P2 过滤待接 |
| agent | [modules/agent.md](./modules/agent.md) | Agent 定义/Run/工具清单、权限收窄模型、提案≠执行许可（设计定稿见 [plans/p5-agent-design.md](plans/p5-agent-design.md)） | 0.1/知识工具已实现；完整 P5 待开发 |

### 1.3 代码内约束（与文档同等效力）

| 位置 | 内容 |
| --- | --- |
| 各模块 `package-info.java` | 模块约束声明，改模块行为时与模块文档**同步更新** |
| `meper-chatbi-control-storage/src/main/resources/db/migration/` | Flyway schema（V1 运行表；V2 模型 provider 配置、V3 对话消息（0.1）；V7 DataWiki 六表、V8 数据集/知识空间/默认模型；P2 按实际最新序号追加策略表，只增不改） |

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
