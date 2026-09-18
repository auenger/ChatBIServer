# meper-chatbi-query-enforcement

> 能力包：C 权限建模｜阶段：P2（现仅完成分类，执法未开始）｜依赖：connector-spi + policy 决策契约（已决策放开，见 §4）
> 模块约束原文：`com.meper.chatbi.query.package-info`

## 1. 定位与边界

查询与操作执法层：统一操作管线的落地位置。**所有** DQL/DML/DDL/DCL、例程、导入导出与非关系型操作必须经本层，不存在旁路——管理员接口与 Agent 工具同样不得直连 JDBC。

- `Connection.setReadOnly(true)` 只是连接提示，不是授权机制；真正的边界是：策略决策（policy）+ 操作分类 + 目标资源解析 + 执行前校验 + 结果治理；
- 本层消费 policy 的 `PolicyDecisionService` 契约（见 [policy.md](./policy.md) §3），自身**不实现权限语义**；
- 现有 `SqlClassifier` 只做结构与类别判定，不做授权判断。

## 2. 现有契约（P1 已实现）

**`SqlClassifier`**：
- `analyze(DatabaseType, script) → List<AnalyzedStatement>`：主路径 Druid 解析（DbType 按方言），语句文本为规范化输出（语义等价）；解析失败退化为「尊重引号的分号切分 + 首关键字分类」；
- `format(DatabaseType, script)`：方言格式化，不连业务库、不改变权限语义；
- 分类映射：SELECT／INSERT-UPDATE-DELETE-MERGE→DML／CREATE-ALTER-DROP-TRUNCATE→DDL／BEGIN-COMMIT-ROLLBACK-SAVEPOINT→TCL／其余→OTHER。

## 3. 目标契约（P2 新增）

### 3.1 `StatementResolver` — 目标资源解析
输入 `AnalyzedStatement` + `DatabaseType`，输出语句涉及的资源：

```java
record ResolvedResource(String namespace, String table, String alias, Set<String> columns)
enum Resolution { RESOLVED, UNRESOLVED }        // UNRESOLVED = 无法可靠解析目标 → 默认拒绝
```

- 支持 SELECT / INSERT / UPDATE / DELETE；SELECT 需覆盖 JOIN、CTE（WITH）、子查询——列出**全部**涉及表；
- `SqlCategory.OTHER`（USE、SHOW、存储过程调用等）与解析歧义一律 `UNRESOLVED` → 默认拒绝（不放行「管理员已登录」的语句，见架构方案 §4 AdministrationOperation 原则）；
- 列级信息尽力提取；提取不全时以 `requestedColumns=空`（请求全部列）进入决策，由「空=拒绝」兜底。

### 3.2 `EnforcementPipeline` — 执法管线
逐语句执行，插在 domain 的 `classifier.analyze` 之后、`executor.execute` 之前：

```text
解析目标资源（3.1）
→ 逐资源调 policy.decide（DecisionQuery）
→ 合并：任一资源 DENY → 整句拒绝；行规则跨多表无法合并 → 拒绝
→ 可执行语句标记决策附件（allowedColumns / maskedColumns / rowFilter / maxRows / policyRevision）
→ （SELECT 行规则）改写注入 WHERE 或标记结果后过滤（见下）
→ 执行（connector-jdbc，domain 编排）
→ 结果治理（3.3）→ 交付前 revision 再校验 → 审计
```

行规则落地策略（P2）：单表 SELECT 且 rowFilter 可安全实例化 → 改写注入 `WHERE`；其余情况（JOIN 多行规则、表达式含不支持构造）**拒绝执行**，不做静默放行。

### 3.3 结果治理（ResultGovernance）
- 列裁剪：结果集列与 `allowedColumns` 求交（防御性二次裁剪，不只靠改写）；
- 脱敏：`maskedColumns` 按策略脱敏（P2 内置占位脱敏，如 `***`；格式化脱敏规则后续版本化）；
- 限行：`maxRows` 与 `ExecutionLimits` 取更严者；
- 交付前再校验：交付时点的 `policyRevision` 与决策时不一致（策略被改/停用）→ **拒绝交付**，不返回部分结果。

### 3.4 `MetadataFilter` — 元数据授权过滤
供 domain `MetadataService` 使用：`filterNamespaces(...) / filterTables(...)`，使库表树只显示授权对象。原则：元数据过滤是体验与最小暴露，**不是**安全边界——安全边界始终在执行前校验。

### 3.5 拒绝语义（API 层透传）
区分三类，不得笼统 403：`DENY_BY_POLICY`（策略拒绝，带 matchedPolicyId）／`UNRESOLVED_TARGET`（目标无法解析）／`POLICY_REVISION_CHANGED`（交付前再校验失败）。

## 4. 依赖的模块契约

- connector-spi：`AnalyzedStatement` / `SqlCategory` / `DatabaseType`；
- policy（**已决策放开依赖**，2026-09-18）：消费 `PolicyDecisionService` / `DecisionQuery` / `TableDecision`；契约类型定义在 policy，本模块为其消费方（与 web→policy 一致）。注意本模块允许依赖需随之更新为 connector-spi + policy；
- 不依赖 connector-jdbc / dialect-* / domain / web（执行仍由 domain 编排）。

## 5. 能力矩阵 / 覆盖范围（P2 验收用例方向）

安全回归（实施方案 §6 对应项）：
- JOIN / CTE / 子查询的行规则与列约束（涉及表逐一决策）；
- 隐藏列经表达式引用（`SELECT secret+1`）被拒绝或裁剪；
- WHERE 引用非 filterable 列 → 拒绝；
- 撤权后：执行前拒绝；已执行未交付的结果因 revision 变化拒绝交付；
- UNRESOLVED 语句（存储过程调用、方言特殊语句）默认拒绝；
- 跨租户 / 跨权限域请求拒绝。

## 6. 演进（能力包 D，P3）

- 写操作执法：结构化 DML 计划（表 + 定位主键 + 行数据）逐列校验 insertable/updatable、行规则作用于 UPDATE/DELETE 定位；
- 原始 SQL 写语句：按策略「拒绝」或「转审批」（与 policy 审批状态机对齐）；
- 批量写入的部分失败语义治理。

## 7. 完成定义（DoD）

- 管线全覆盖单测 + `FullLinkIntegrationTest` 扩展真实库执法链路（含撤权、脱敏、限行）；
- 无旁路：静态检查确认 web/domain 无绕过本层的执行路径；
- 三类拒绝语义在 API 响应中可区分；
- 与 policy.md 契约一致（同一 contractVersion）。
