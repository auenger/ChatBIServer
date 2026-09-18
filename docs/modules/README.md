# 模块文档索引 — 按能力包独立开发

> 本目录是各 Maven 模块的**开发级文档**：每个模块一份，写清定位边界、对外契约、依赖契约、能力矩阵与完成定义。
>
> 三层文档关系：
>
> | 文档 | 回答的问题 | 粒度 |
> | --- | --- | --- |
> | [Java数据库能力架构方案.md](../../Java数据库能力架构方案.md) | 为什么这样设计、能力目录与安全边界 | 全局 |
> | [Chat2DB-Java能力借鉴实施方案.md](../../Chat2DB-Java能力借鉴实施方案.md) | 阶段计划（G0→P6）、验收条件、借鉴与改造 | 全局 |
> | **docs/modules/*.md（本目录）** | **拿到一个模块怎么开发、契约是什么、做到什么程度算完成** | 单模块 |
>
> 维护规则：模块行为变更时同步更新对应模块文档与 `package-info.java`；两者与代码不一致时，要么改代码、要么在模块文档「开放决策点」中记录，不留静默偏离。

## 1. 能力包 → 模块映射

「独立拆分开发」的单位是**能力包**；每个能力包落到一个或多个模块。一个能力包通常可以交给一条独立开发线（人或独立 AI 会话）。

| # | 能力包 | 阶段 | 状态 | 模块文档 |
| --- | --- | --- | --- | --- |
> | A | 连接器内核 | P1 | ✅ 完成 | [connector-spi](./connector-spi.md) · [connector-jdbc](./connector-jdbc.md) · [dialects](./dialects.md) |
> | B | 基本数据库操作 | P1 | ✅ 完成 | [domain](./domain.md) · [web](./web.md) · [control-storage](./control-storage.md)（运行表部分） |
> | C | 权限建模 | P2 | ❌ 未开始 | [policy](./policy.md) · [query-enforcement](./query-enforcement.md) · [control-storage](./control-storage.md)（策略表）· [web](./web.md)（身份扩展） |
> | D | 结构化 DML | P3 | ❌ 未开始 | 演进章节：[domain](./domain.md) §6 · [query-enforcement](./query-enforcement.md) §6 · [control-storage](./control-storage.md) §5 |
> | E | 会话语义 | P5 | 骨架 | [session-wiki](./session-wiki.md) |
> | F | Agent | P5 | 骨架 | [agent](./agent.md) |

P4（DDL/DCL/账号/例程/导入导出）不设独立能力包文档，其契约随 P2/P3 落地后在 domain / query-enforcement / dialects 的演进章节展开；P6（方言扩展）见 [dialects](./dialects.md) §5。

## 2. 模块依赖图（实际依赖边，2026-09-18 核对 pom）

```text
                    ┌────────────────────────────────────────────┐
                    │ meper-chatbi-start（Spring Boot 装配、健康检查）│
                    └──────┬─────────────────────────────────────┘
           依赖全部模块 ────┘
   ┌─────────────┐      ┌──────────────────┐
   │    web      │──────▶│      domain      │
   │ B/S API/MCP │      │  领域服务 + 装配   │
   └──┬───┬──────┘      └──┬───┬───┬───────┘
      │   │                │   │   │
      │   └───────▶ policy │   │   └────▶ control-storage
      │      （P2，暂空壳）  │   │            仓储/加密/Flyway
      │                    │   │                 │
      └─▶ query-enforcement│   └▶ connector-jdbc │
          分类（P2 加执法）◀──┘       执行/池/元数据   │
                 │                    │           │
                 └────▶ connector-spi ◀───────────┘
                        契约（ExecutionContext/SqlDialect/SqlExecutor…）
                 connector-spi ◀── dialect-mysql / sqlserver / postgresql / oracle
                                   （ServiceLoader 注册，互不依赖）
```

实际依赖边（pom 已核对）：

| 模块 | 允许依赖 | 备注 |
| --- | --- | --- |
| connector-spi | （无内部依赖） | 不依赖 Spring / Web / 驱动 |
| connector-jdbc | connector-spi | |
| dialect-* | connector-spi | 四方言互不依赖，经 `DialectRegistry`（ServiceLoader）聚合 |
| query-enforcement | connector-spi | |
| domain | connector-spi、connector-jdbc、query-enforcement、control-storage | **注意**：domain 装配连接器/分类器实现类（`DomainConfig`），与旧模块地图「仅 connector-spi」不同，已按实际修正；见 domain.md §7 |
| control-storage | connector-spi | `ExecutionRecord` 关联 `ExecutionContext` |
| policy | control-storage（仓储，P2 起；已决策放开） | 决策契约见 [policy.md](./policy.md) §3（已决策记录见 §7） |
| web | domain、policy、query-enforcement | **禁止**依赖 connector-jdbc / dialect-* / 直接 JDBC |
| start | 全部 | 只做装配与配置 |

静态合同检查项（实施方案 §6，不变）：web 无 JDBC 直连；connector-spi 无 Spring/驱动；方言 SQL 只出现在 dialect-* 与 connector-jdbc；不存在绕过 query-enforcement 的执行路径。

## 3. 并行开发的交接点

模块间只通过「模块文档 §2/§3 定义的契约」交接。契约一旦冻结，双方即可并行：

**P2 权限建模线（建议时序）**：

1. **冻结决策契约**：评审 [policy.md](./policy.md) §3 的 `PolicyDecisionService` / `DecisionQuery` / `TableDecision`。三个方向性决策已于 2026-09-18 确认（policy→control-storage 放开、多策略「allow 取并/约束取最严」、内置超级管理员短路，见 policy.md §7），契约定型（contractVersion 锁定）后即可并行。
2. 并行三条线：
   - 线 1（policy）：决策内核 + 策略模型 + 草稿/发布/停用 + revision；
   - 线 2（control-storage）：V2 迁移（身份/权限域/策略/快照/审批表）+ 仓储实现（依赖线 1 的模型定型，可先用字段清单并行建表）；
   - 线 3（query-enforcement）：`StatementResolver` 目标资源解析 + 结果治理（用线 1 契约的 mock 决策先开发）。
3. **接线**：domain 在 `WorkbenchService` / `MetadataService` 插入决策与过滤（插入点已在代码预留，见 domain.md §4）；`ExecutionContextFactory` 切换 `ENFORCED`。
4. web 补身份登录（替换 BootstrapAdmin）与策略管理 API。

**B/A 包（已完成）** 的扩展（新方言、新数据源能力）同样走「先改契约文档 → 后实现」。

## 4. 各模块状态总表

| 模块 | 状态 | 测试 | 文档 |
| --- | --- | --- | --- |
| connector-spi | ✅ 契约完整 | 随使用方覆盖 | [connector-spi.md](./connector-spi.md) |
| connector-jdbc | ✅ 完成 | 4 测试类 | [connector-jdbc.md](./connector-jdbc.md) |
| dialect-mysql / sqlserver / postgresql / oracle | ✅ 完成 | 各 2（单测 + Testcontainers） | [dialects.md](./dialects.md) |
| query-enforcement | ◐ 部分（仅 SqlClassifier） | 1 测试类 | [query-enforcement.md](./query-enforcement.md) |
| domain | ✅ P1 范围完成 | 随 start 集成测试 | [domain.md](./domain.md) |
| control-storage | ✅ 运行表完成 | 2 测试类 | [control-storage.md](./control-storage.md) |
| policy | ❌ 空壳（仅 package-info） | — | [policy.md](./policy.md) |
| web | ✅ P1 范围完成 | 随 start 集成测试 | [web.md](./web.md) |
| session-wiki | ❌ 空壳 | — | [session-wiki.md](./session-wiki.md) |
| agent | ❌ 空壳 | — | [agent.md](./agent.md) |

全量验证基线：`mvn -B -ntp package`（当前 58 个测试）；方言矩阵见 [dialects.md](./dialects.md) §4。
