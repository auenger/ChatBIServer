# meper-chatbi-policy

> 能力包：C 权限建模｜阶段：P2 ❌ 未开始（现仅 package-info 约束）｜依赖：control-storage（仓储，已决策放开，见 §7）
> 模块约束原文：`com.meper.chatbi.policy.package-info`

## 1. 定位与边界

权限与策略层：身份映射、权限域、表/字段/行/操作策略的**决策内核**，以及审批流模型。是纯决策模块——**不做 SQL 解析、不连业务库、不感知 JDBC**；输入决策请求，输出决策结果。

- **默认拒绝**是本模块的第一原则：「空字段列表 = 拒绝」，与 Chat2DB「空列表 = 全部可读」语义相反；
- 策略语义必须版本化（`POLICY_CONTRACT_VERSION`），历史策略迁入前显式转换，不得静默改变授权范围；
- 决策只读**已发布快照**（PUBLISHED revision），草稿（DRAFT）与停用（DISABLED）不参与决策；
- Agent 的工具权限永远是主体权限的收窄（收窄逻辑在 agent 模块，本模块只提供主体决策）。

## 2. 领域模型（P2 交付，存储见 control-storage.md §4）

| 模型 | 要点 |
| --- | --- |
| `Identity` | 本地用户（P2）：username、passwordHash、displayName、status；外部主体映射字段预留（P2 先不接外部 IdP） |
| `Role` / `IdentityRole` | 角色；P2 内置普通角色；`admin` 角色用于管理面访问控制（**不是**免检通道） |
| 超级管理员 | **内置身份**（随 V2 迁移 seed，初始凭据复用引导管理员的环境变量机制）：唯一可跳过策略决策的主体，用于测试与应急；短路在决策接口内实现、审计不豁免（见 §3 判定规则 0） |
| `PermissionDomain` | 权限域：主体/角色 × 数据源 × 命名空间（库/Schema）范围；无匹配权限域 = 无权限 |
| `ResourcePolicy` | 表级策略：`SELECT / INSERT / UPDATE / DELETE` 四操作开关；字段矩阵（可读 readable / 可筛选 filterable / 可新增 insertable / 可修改 updatable / 脱敏 masked）；行规则 rowFilter；maxRows |
| `PolicySnapshot` | 发布快照：revision 单调递增；决策与「交付前再校验」都基于快照 |
| `OperationApproval` | 审批单模型（P2 落表与状态机；P3/P4 的 DML/DDL 消费） |

策略生命周期：`DRAFT →（发布，revision+1）→ PUBLISHED →（停用）→ DISABLED`；同表多策略取「并集授权 ∩ 单策略约束」还是「最严者」，见 §7 决策点 2。

## 3. 对外契约（P2 冻结对象 —— 本节是能力包 C 并行开发的前提）

决策接口是 policy 的唯一出口，消费方为 query-enforcement（执法管线）与 domain（元数据过滤）。请求/响应**自含类型**（不用 SPI 的 `ExecutionContext`），保持模块独立性。以下为契约草案，冻结前可评审调整；冻结后变更须升 `POLICY_CONTRACT_VERSION`。

```java
/** 契约版本：空列表=拒绝 等语义锁定于此版本；语义变更必须升版并写迁移转换。 */
public interface PolicyDecisionService {
    String contractVersion();          // 当前草案："1"
    TableDecision decide(DecisionQuery query);
}

/** 决策请求：主体在某数据源上对某表的某类操作。 */
record DecisionQuery(
        String tenantId,
        String subject,                 // 已验证主体（来自 ExecutionContext.subject）
        long datasourceId,
        String namespace,               // 库/Schema（方言命名空间布局归一化后传入）
        String table,
        TableOperation operation,       // SELECT / INSERT / UPDATE / DELETE
        Set<String> requestedColumns)   // 空/null = 请求全部列 → 触发「空=拒绝」判定

record TableDecision(
        boolean allowed,
        boolean superAdminBypass,       // 超级管理员短路标记；true 时下列列/行/脱敏/限行约束全部不生效（审计可见）
        Set<String> allowedColumns,     // 默认拒绝：仅可使用非空集合；SELECT 时请求列必须 ⊆ allowedColumns；
                                        // superAdminBypass=true 时可为空集，语义为「全部列」
        Set<String> maskedColumns,      // 结果治理用：脱敏列
        Set<String> filterableColumns,  // WHERE/行规则中允许引用的列
        String rowFilter,               // 行规则：方言中立的布尔表达式，占位符 {subject}；null = 无附加行规则
        int maxRows,                    // 0 = 继承系统上限
        long policyRevision,            // 命中快照的最高 revision（交付前再校验依据）
        String matchedPolicyId)         // 审计用：命中策略 ID（多策略逗号分隔）；超级管理员为 SUPER_ADMIN_BYPASS
```

判定规则（默认拒绝语义）：
0. **超级管理员短路**：subject 为内置超级管理员 → 直接返回全量允许（`superAdminBypass=true`，此时 `allowedColumns` 允许为空集、语义为「全部列」，行规则/脱敏/限行均不生效），`matchedPolicyId=SUPER_ADMIN_BYPASS`。短路必须实现在 `PolicyDecisionService` 内部——执法管线、结果治理与审计照常走这条唯一路径，**不得在 policy 之外另开免检执行通道**；
1. 无匹配权限域或策略 → `allowed=false`；
2. 操作开关未开 → `allowed=false`；
3. `operation=SELECT` 且（`allowedColumns` 为空 或 `requestedColumns` 非空但不在 `allowedColumns` 内）→ `allowed=false`；
4. 写操作（INSERT/UPDATE/DELETE）P2 只放行走结构化 DML 通道（P3），原始 SQL 写语句在执法层按策略拒绝或走审批（与 query-enforcement.md §3.3 对齐）。

### 3.1 同表多策略叠加语义（已决策，2026-09-18）

同一 (权限域, namespace, table, operation) 命中多个 PUBLISHED 策略时，按「**allow 取并、约束取最严**」合并：

1. 操作放行 = 任一命中策略开启该操作；全部未开启 → 拒绝；
2. 参与约束合并的只有**开启了该操作**的策略；
3. `allowedColumns` 与 `filterableColumns` 取**交集**；交集为空 → 等效拒绝（「空=拒绝」语义自然延续）；
4. `maskedColumns` 取**并集**；`rowFilter` 为各策略行规则的 **AND** 连接（无法安全合并由执法层拒绝，见 query-enforcement.md §3.2）；
5. `maxRows` 取**最小值**（`0`=继承系统上限按「无穷」参与比较）；
6. `policyRevision` 取命中集合的最高值；交付前再校验须复核 `matchedPolicyId` 列表中的**全部**策略 revision，任一变化即拒绝交付。

行规则（rowFilter）语法约束：P2 限定**单表布尔条件**（列比较 / 常量 / `{subject}` 占位符），不支持子查询；无法安全实例化的行规则一律导致拒绝（执法层负责，见 query-enforcement.md §3.2）。

## 4. 审批流（P2 落模型，P3/P4 消费）

- `OperationApproval`：申请（主体、操作、目标、计划快照、理由）→ 审批（管理员：通过/驳回）→ 关联执行；
- 原则：**提案不等于执行许可**——审批通过也只是解除「需审批」门槛，执行时仍走决策与执法；
- P2 只需：模型、存储、状态机与最小 API（列表/通过/驳回）。

## 5. 依赖的模块契约

- control-storage（§7 决策点 1 放开后）：身份/权限域/策略/快照/审批仓储；
- 不依赖 connector-spi / connector-jdbc / dialect-* / domain / query-enforcement / web。

## 6. 能力矩阵 / 覆盖范围（P2 验收用例方向）

- 空字段列表拒绝；requestedColumns ⊆ allowedColumns 各分支；
- 无权限域 / 未开操作 / DISABLED 策略 → 拒绝；
- revision 演进：撤权（策略停用/删除）后 `decide` 立即反映；
- 多策略叠加：allow 并集、约束最严、交集为空拒绝、revision 复核（§3.1 六条各一用例）；
- 超级管理员短路：全量允许且审计含 `SUPER_ADMIN_BYPASS` 标记；非超级管理员不受影响；
- rowFilter 占位符展开与非法表达式拒绝；
- 审批状态机非法迁移拒绝。

## 7. 已决策记录（2026-09-18，与用户确认）

原三个开放决策点已全部关闭，结论如下：

1. **policy → control-storage 依赖：放开**（仅仓储接口与实现；不引入 connector-spi / JDBC）。决策请求/响应保持自含类型，维持本模块不感知执行层的边界。AGENTS.md 模块地图已同步。
2. **同表多策略叠加语义**：按 §3.1「allow 取并、约束取最严」执行；该语义锁定于 `POLICY_CONTRACT_VERSION "1"`，后续变更须升版并写迁移转换。
3. **超级管理员**：默认存在（V2 迁移 seed），可跳过策略决策用于测试与应急（§3 判定规则 0）。实现红线：短路在决策接口内部完成、审计照常携带 `SUPER_ADMIN_BYPASS`，不得另开免检执行路径；P1 引导管理员的凭据机制迁移为超级管理员初始凭据。

## 8. 完成定义（DoD）

- §3 契约冻结并升版记录；决策内核全覆盖单测（§6 清单）；
- 快照隔离：草稿/停用不可被决策；revision 可作交付前再校验；
- 撤权即时生效测试（决策侧）；
- 超级管理员短路：非超级管理员不受影响，审计含 `SUPER_ADMIN_BYPASS` 标记；
- 多策略叠加：§3.1 六条合并规则各有用例（含交集为空拒绝、AND 行规则）；
- 与 package-info、本文档无静默偏离。
