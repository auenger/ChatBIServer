# P2 权限建模实施计划

> 状态：**已评审（2026-09-21，§10 决策点全部关闭），可按批次开工**（每批次一个提交单元，`mvn -B -ntp package` 全绿后提交）
> 前提：[policy.md](../modules/policy.md) §3 决策契约已冻结（contractVersion "1"），§7 三条方向性决策已关闭（2026-09-18）。
> 本计划把 [modules/README.md](../modules/README.md) §3 的「三条并行线」转成单线推进的 8 个批次，并落到具体类名、表结构与测试清单。
> 计划中的所有类名、插入点行号均按 2026-09-21 代码核实。

## 0. 范围

**交付**：本地身份体系（登录替换 BootstrapAdmin）、权限域/资源策略/发布快照/审批的存储与决策内核、执法管线（目标解析 → 决策 → 改写 → 结果治理 → revision 再校验）、domain 接线与 `ENFORCED` 切换、策略管理 API、全链路验收测试。

**不交付**（留后续）：外部 IdP 对接（identity 表预留映射字段）、格式化脱敏规则版本化（P2 内置 `***` 占位脱敏）、审批的消费方（P3 DML / P4 DDL 才真正发起审批）、策略管理前端页面（API 先行，见 §10 开放点 5）、持久化会话/多实例（TokenStore 维持内存）。

**总体顺序原则**：行为切换放在最后（批次 6 一次翻转 `ENFORCED`）；此前所有批次都不改变现有 API 行为，任何时刻主干可用。

## 1. 批次总览

| # | 批次 | 模块 | 关键交付 | 验收 |
| --- | --- | --- | --- | --- |
| 1 | 决策契约落码 | policy、query-enforcement | `PolicyDecisionService` / `DecisionQuery` / `TableDecision` / `TableOperation` | 编译全绿，无行为变更 |
| 2 | V6 存储迁移 | control-storage | `V6__permission_model.sql`（7 表）+ 5 个 Repository + 哈希服务 | 迁移测试 + 仓储单测 |
| 3 | 决策内核 | policy | `PolicyDecisionServiceImpl`（默认拒绝 4 规则 + §3.1 叠加 6 规则 + 超管短路）+ `IdentityAuthService` | policy.md §6 清单全覆盖单测 |
| 4 | 身份登录改造 | web | 登录走 identity 表 + 超管 seed + 会话携带角色 | 行为兼容：admin 原账号密码可登录 |
| 5 | 执法管线 | query-enforcement | `StatementResolver` / `EnforcementPipeline` / `ResultGovernance` / `MetadataFilter` | query-enforcement.md §5 清单单测（mock 决策） |
| 6 | 接线与 ENFORCED 切换 | domain、connector-spi | `WorkbenchService` 插管线、`MetadataService` 过滤、`ExecutionContextFactory` 切 `ENFORCED` | 全链路集成测试（超管不受影响） |
| 7 | 策略管理 API | web | `/api/identities`、`/api/permission-domains`、`/api/policies`、`/api/approvals`、模拟预检 | 端点测试 + admin 角色拦截 |
| 8 | 端到端验收 | start | `FullLinkIntegrationTest` 执法扩展 | §9 验收矩阵全过 + DoD 对照 |

依赖关系：1 → 2 → 3 → 4 → 6（6 依赖 3/4/5）；5 只依赖 1（可与 3/4 并行）；7 依赖 3；8 最后。

## 2. 批次 1：决策契约落码

**模块 meper-chatbi-policy**（新增，包 `com.meper.chatbi.policy.decision`）：

```java
public interface PolicyDecisionService {
    String POLICY_CONTRACT_VERSION = "1";
    String contractVersion();
    TableDecision decide(DecisionQuery query);
}

public enum TableOperation { SELECT, INSERT, UPDATE, DELETE }

public record DecisionQuery(
        String tenantId, String subject, long datasourceId,
        String namespace, String table,
        TableOperation operation, Set<String> requestedColumns) { }

public record TableDecision(
        boolean allowed, boolean superAdminBypass,
        Set<String> allowedColumns, Set<String> maskedColumns,
        Set<String> filterableColumns, String rowFilter,
        int maxRows, long policyRevision, String matchedPolicyId) { }
```

与 policy.md §3 逐字段一致（自含类型，不引 SPI）。`TableDecision` 提供两个静态工厂：`allow(...)` 与 `deny(revision, matchedPolicyId)`（deny 时 `allowedColumns` 须为空集，防止误用）。

**pom 变更**：`meper-chatbi-query-enforcement` 增加依赖 `meper-chatbi-policy`（policy 不新增任何依赖，仍仅 package-info 级别）。同步更新 AGENTS.md 模块地图与 modules/README §2 依赖表（query-enforcement 行改为 connector-spi + policy）。

**验收**：全量构建全绿；现有行为零变化。

## 3. 批次 2：V6 存储迁移

**`V6__permission_model.sql`**（沿用 V1 风格：utf8mb4、DATETIME(6)、显式约束命名；tenant_id 统一预留，阶段 1 恒 `default`；**V2 被 0.1 模型配置表、V3 被对话消息表占用**，见 [p01-agent-chat-mvp.md](./p01-agent-chat-mvp.md)）：

```sql
-- 身份
CREATE TABLE meper_identity (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    username      VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL COMMENT 'bcrypt',
    display_name  VARCHAR(128) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    is_super_admin TINYINT(1)  NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_identity PRIMARY KEY (id),
    CONSTRAINT uq_identity_tenant_username UNIQUE (tenant_id, username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 角色 / 身份-角色映射（V2 迁移 seed 内置角色：admin=管理面访问、member=普通用户；均非免检通道）
CREATE TABLE meper_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_tenant_code UNIQUE (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE meper_identity_role (
    identity_id BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    CONSTRAINT pk_identity_role PRIMARY KEY (identity_id, role_id),
    CONSTRAINT fk_ir_identity FOREIGN KEY (identity_id) REFERENCES meper_identity (id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_role     FOREIGN KEY (role_id)     REFERENCES meper_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 权限域：主体(身份或角色) × 数据源 × 命名空间
CREATE TABLE meper_permission_domain (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    subject_type  VARCHAR(16)  NOT NULL COMMENT 'IDENTITY / ROLE',
    subject_id    BIGINT       NOT NULL,
    datasource_id BIGINT       NOT NULL,
    namespace     VARCHAR(255) NOT NULL COMMENT '库/Schema 名，''*'' 表示该数据源全部命名空间',
    created_by    VARCHAR(128) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_permission_domain PRIMARY KEY (id),
    CONSTRAINT uq_domain UNIQUE (tenant_id, subject_type, subject_id, datasource_id, namespace)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 资源策略：每 (权限域, 表) 一条，含四操作开关；决策只读发布快照
CREATE TABLE meper_resource_policy (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    domain_id     BIGINT       NOT NULL,
    table_name    VARCHAR(255) NOT NULL,
    can_select    TINYINT(1)   NOT NULL DEFAULT 0,
    can_insert    TINYINT(1)   NOT NULL DEFAULT 0,
    can_update    TINYINT(1)   NOT NULL DEFAULT 0,
    can_delete    TINYINT(1)   NOT NULL DEFAULT 0,
    column_matrix JSON         NOT NULL COMMENT '[{name,readable,filterable,insertable,updatable,masked}]',
    row_filter    TEXT         NULL COMMENT '方言中立布尔表达式，{subject} 占位符',
    max_rows      INT          NOT NULL DEFAULT 0 COMMENT '0=继承系统上限',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / DISABLED',
    revision      BIGINT       NOT NULL DEFAULT 0,
    created_by    VARCHAR(128) NOT NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_resource_policy PRIMARY KEY (id),
    CONSTRAINT uq_policy_domain_table UNIQUE (domain_id, table_name),
    CONSTRAINT fk_policy_domain FOREIGN KEY (domain_id) REFERENCES meper_permission_domain (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 发布快照：发布事务内复制策略全文；决策只读本表
CREATE TABLE meper_policy_snapshot (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id    VARCHAR(64) NOT NULL DEFAULT 'default',
    policy_id    BIGINT      NOT NULL,
    revision     BIGINT      NOT NULL,
    content      JSON        NOT NULL COMMENT '发布时点策略全文（含操作开关/字段矩阵/行规则/限行）',
    published_by VARCHAR(128) NOT NULL,
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_policy_snapshot PRIMARY KEY (id),
    CONSTRAINT uq_snapshot_policy_revision UNIQUE (policy_id, revision),
    CONSTRAINT fk_snapshot_policy FOREIGN KEY (policy_id) REFERENCES meper_resource_policy (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 审批单（P2 只落模型与状态机，P3/P4 消费）
CREATE TABLE meper_operation_approval (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(64)   NOT NULL DEFAULT 'default',
    applicant     VARCHAR(128)  NOT NULL,
    via_agent     VARCHAR(128)  NULL COMMENT '发起 Agent 定义标识；人类直发为 NULL（P5 预留，2026-09-23 决策）',
    operation     VARCHAR(32)   NOT NULL,
    target        JSON          NULL,
    plan_snapshot JSON          NULL,
    reason        VARCHAR(1024) NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    approver      VARCHAR(128)  NULL,
    decided_at    DATETIME(6)   NULL,
    decision_note VARCHAR(1024) NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_operation_approval PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

**Repository**（风格随 `AuditRepository`：`@Repository` + `JdbcClient` 文本块 SQL）：

| Repository | 方法面 |
| --- | --- |
| `IdentityRepository` | `findByUsername`（含角色）、`insert`、`updateStatus`、`updatePasswordHash`、`list`、`assignRole/revokeRole` |
| `PermissionDomainRepository` | `insert/list/delete`、`match(tenant, subjectIds, datasourceId)`（返回命中域，含 `'*'`） |
| `ResourcePolicyRepository` | `upsert/list/delete`、`publish(policyId)`（事务内 revision+1 并写快照）、`disable`、`findPublishedSnapshots(domainIds, table)` |
| `PolicySnapshotRepository` | `insert`（发布事务内）、`latestByPolicies(policyIds)`（决策读取入口）、`history(policyId)` |
| `OperationApprovalRepository` | `insert/list/pending/decide`（状态机校验：仅 PENDING 可 APPROVED/REJECTED） |

**密码哈希**：新类 `PasswordHashService`（control-storage，`spring-security-crypto` 单依赖，bcrypt，强度默认 10）。放 storage 还是 policy：**定 storage**（属持久化关注点，policy 只消费比对结果）——若评审倾向 policy 可调整，代价是 policy 引入 spring 依赖注解（保持 `@Service` 装配在 domain/web 层亦可）。**此处按 storage 落地**。

**测试**：Flyway 从零跑到最新（新表建表通过）；`PolicyStorageRepositoriesTest`：revision 单调、快照不可变、草稿不可被 `findPublishedSnapshots` 读出、审批非法迁移拒绝、`'*'` 域匹配语义。

## 4. 批次 3：决策内核

**模块 meper-chatbi-policy**（包 `com.meper.chatbi.policy.decision.impl`、`com.meper.chatbi.policy.identity`）：

| 类 | 职责 |
| --- | --- |
| `PolicyDecisionServiceImpl` | 契约实现：按 §4 流程判定 |
| `PolicyMergeEngine` | §3.1 六条叠加规则的纯函数实现（`List<PolicySnapshotView> → TableDecision`） |
| `SubjectResolver` | subject → identityId + roleIds（经 IdentityRepository）；超级管理员标记判定 |
| `IdentityAuthService` | `authenticate(username, rawPassword)`：查身份 + bcrypt 比对 + status 校验；供 web 登录 |
| `RowFilterValidator` | 行规则语法校验（P2：单表布尔条件、列比较/常量/`{subject}`、无子查询）；非法 → 决策侧打标（执法层最终拒绝） |

**判定流程**（`decide`）：

```text
1. SubjectResolver: subject 是超级管理员？
   → 是: return 全量允许(superAdminBypass=true, matchedPolicyId=SUPER_ADMIN_BYPASS, 其余空集)
2. SubjectResolver: 身份不存在或 DISABLED → deny
3. PermissionDomainRepository.match(subjectIds=[identityId]+roleIds, datasourceId)
   → 空 → deny（规则 1：无匹配权限域）
4. namespace 匹配：精确相等或域 namespace='*'；无命中域 → deny
5. findPublishedSnapshots(domainIds, table) → 空 → deny（规则 1：无策略）
6. 过滤出当前 operation 开关开启的快照 → 空 → deny（规则 2）
7. PolicyMergeEngine 合并（§3.1：列交集/脱敏并集/filterable 交集/行规则 AND/maxRows 最小/revision 最高）
   → allowedColumns 交集为空 → deny（规则 3 延续）
8. operation=SELECT 且 requestedColumns 非空但 ⊄ allowedColumns → deny（规则 3）
9. rowFilter 经 RowFilterValidator 校验失败 → deny（不放行无法实例化的行规则）
10. return allow(...)（maxRows=0 语义保持「继承系统上限」）
```

**测试**（对应 policy.md §6/§8，`PolicyDecisionServiceTest`，控制库走 H2/Testcontainers-MySQL）：
超管短路（允许 + `SUPER_ADMIN_BYPASS` + 约束全空）/ 非超管不受影响；无权限域、无策略、操作未开、DISABLED 策略各一拒绝用例；requestedColumns ⊆ / ⊄ / 空请求三分支；§3.1 六条各一用例（含交集为空拒绝、AND 行规则、maxRows 取 min、revision 取 max）；身份 DISABLED 拒绝；撤权（disable 后 `decide` 立即拒绝）；rowFilter 非法表达式拒绝；contractVersion 返回 "1"。

## 5. 批次 4：身份登录改造（web，行为兼容）

| 改动 | 内容 |
| --- | --- |
| `SuperAdminSeeder`（web 或 start，`ApplicationRunner`） | Flyway 后执行：`meper_identity` 无 `is_super_admin=1` 行时插入——username 取现有配置 `meper.bootstrap-admin.username`，password_hash 取 `MEPER_BOOTSTRAP_ADMIN_PASSWORD` 明文的 bcrypt；同时绑定内置 `admin` 角色 + seed `admin`/`member` 内置角色（若无）。**已存在则不覆盖**（避免每次启动重置用户改过的密码） |
| `AuthController.login` | 改调 `IdentityAuthService.authenticate`；成功签发 token；失败同现有 401 语义 |
| `TokenStore.Session` | 扩展为 `(username, identityId, roles, isSuperAdmin, expiresAt)`；`authenticate` 返回 principal 对象（保持对外仍以 username 为 `PRINCIPAL_ATTR`，新增 `ROLES_ATTR` / `IDENTITY_ID_ATTR` 请求属性） |
| `AuthInterceptor` | 透传新属性；`/api/auth/login`、`/api/ping`、`/actuator/**` 白名单不变 |
| `BootstrapAdminAuth` | 删除（被 identity 表 seed 替代）；配置项 `meper.bootstrap-admin.*` 语义改为「超管 seed 初始凭据」 |
| `GET /api/auth/me` | 响应增加 `roles`、`superAdmin` 字段 |

**行为兼容验收**：启动后 admin 原账号密码可登录（现走 seed 身份）；现有 21 端点行为不变；执法状态**仍为** `BOOTSTRAP_ADMIN_UNRESTRICTED`（本批不切）。

**测试**：登录成功/密码错/身份 DISABLED 三用例（web 层 MockMvc 或 start 集成测试）；seeder 幂等（二次启动不重置密码）。

## 6. 批次 5：执法管线（query-enforcement，mock 决策开发）

包 `com.meper.chatbi.query.enforcement`（与现有 `com.meper.chatbi.query` 分类器并存，或新建子包——**定：新建子包**，`SqlClassifier` 保持不动）。

| 类 | 契约（对应 query-enforcement.md §3） |
| --- | --- |
| `StatementResolver` | `resolve(DatabaseType, AnalyzedStatement) → Resolution`；`ResolvedStatement(List<ResolvedResource>, Resolution enum)`。实现：Druid AST（输入为 `AnalyzedStatement.sql` 规范化文本，按 DbType 再解析）：SELECT 用 visitor 收集全部 `SQLExprTableSource`（JOIN/CTE/子查询天然全覆盖）+ select items 中列引用（`*` → requestedColumns 空）；INSERT 取目标表 + 列清单；UPDATE/DELETE 取目标表 + SET/WHERE 列。解析失败、`SqlCategory.TCL`/`OTHER`、方言特殊语句（CALL/EXEC/SHOW）→ `UNRESOLVED` |
| `EnforcementPipeline` | `enforce(DecisionContext, List<AnalyzedStatement>) → EnforcementPlan`。逐语句：UNRESOLVED → 拒绝（`UNRESOLVED_TARGET`）；逐资源 `policyService.decide` → 任一 DENY → 整句拒绝（`DENY_BY_POLICY`，带 matchedPolicyId）；全通过 → 合并各资源决策为**语句级附件**（列交集/脱敏并集/行规则：跨多表行规则无法合并 → 拒绝；maxRows min；revision max + matchedPolicyId 列表） |
| `RowFilterInjector` | 单表 SELECT 且行规则可安全实例化 → Druid 改写注入 WHERE（`{subject}` → 绑定参数）；其余 → 已在管线拒绝，此处不静默放行 |
| `ResultGovernance` | `govern(StatementResult, PolicyAttachment, ExecutionLimits)`：列裁剪（结果集列 ∩ allowedColumns，防御性二次裁剪）、脱敏（maskedColumns → `***`）、限行（`min(policy.maxRows, limits.maxRows)`，policy.maxRows=0 视为不限） |
| `RevisionVerifier` | `verify(matchedPolicyIds, expectedRevisions)`：交付前逐策略复核最新 revision + status，任一不一致 → `PolicyViolationException`（`POLICY_REVISION_CHANGED`） |
| `MetadataFilter` | `filterNamespaces / filterTables`：对每个候选对象干跑 SELECT 决策（单对象成本可接受，元数据接口缓存可后续加）；原则：体验过滤，非安全边界 |
| `PolicyViolationException` | 三类拒绝语义枚举 `ViolationCode { DENY_BY_POLICY, UNRESOLVED_TARGET, POLICY_REVISION_CHANGED }` + matchedPolicyId 附件（供 web `GlobalExceptionHandler` 映射，不笼统 403） |

**pom**：本批零新依赖（Druid 已有）。

**测试**（mock `PolicyDecisionService`，`EnforcementPipelineTest` / `StatementResolverTest` / `ResultGovernanceTest`）：JOIN 两表单表行规则改写正确、跨表行规则拒绝；CTE/子查询全部表被决策；`SELECT secret+1` 隐藏列表达式拒绝或裁剪；WHERE 引用非 filterable 列拒绝；CALL/SHOW → UNRESOLVED 拒绝；列裁剪/脱敏/限行生效；revision 再校验失败拒绝交付；四方言各至少 2 条真实语句解析用例（纯解析，无需 Docker）。

## 7. 批次 6：domain 接线与 ENFORCED 切换（行为翻转点）

| 位置 | 改动 |
| --- | --- |
| `connector-spi` `ExecutionContext` 构造器 | **解除** `ENFORCED` 抛异常限制（L34-36）；`EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED` 枚举值保留（历史执行记录仍含此值），签发路径不再产生 |
| `ExecutionContextFactory` | 注入 `SubjectResolver`（policy）：subject 解析为有效 ACTIVE 身份 → 签发 `ENFORCED`；解析失败 → 抛异常（拒绝签发）。两方法（workbench/datasourceAdmin）同步切换 |
| `WorkbenchService.execute` | 插入点 = L88 `classifier.analyze` 之后、L98 `executor.execute` 之前：`EnforcementPlan plan = pipeline.enforce(...)` → 被拒语句生成带拒绝语义的失败 `StatementResult`（**视同失败语句，遵循现有失败即停语义**，后续语句不执行）；放行语句经 `RowFilterInjector` 改写后执行；结果经 `ResultGovernance` 治理；返回前 `RevisionVerifier` 再校验。`ExecuteResult` 增加 `violations` 字段（三类拒绝语义明细） |
| `WorkbenchService.preview` | 预检增加「决策干跑」：逐语句显示将被策略拒绝的语句与原因（不改变 preview 契约形状，新增可选字段） |
| `MetadataService` | `namespaces/tables/tableDetail` 接 `MetadataFilter`（库表树只显示授权对象）；`tableData` 是执行路径 → 走管线单表 SELECT 决策（requestedColumns=空 → 要求 allowedColumns 非空），拒绝时抛 `PolicyViolationException` |
| `DataSourceService` | **不插决策**：数据源登记/删除/轮换属管理面，由 web 层 admin 角色拦截（批次 7），资源授权仍在决策层（与 web.md §4 一致） |
| `DomainConfig` | 装配 `EnforcementPipeline`、`MetadataFilter` bean（随 `PolicyDecisionServiceImpl` 一起） |

**测试**：start 集成测试扩展——超管登录全链路可用（决策短路）、无权限域普通身份执行任意语句被拒、`enforcement` 响应字段恒为 `ENFORCED`、元数据树只显示授权命名空间。**这一批完成后系统进入「默认拒绝」**：除超管与已配策略者外全部拒绝——这是预期行为翻转，提交说明中显式声明。

## 8. 批次 7：策略管理 API（web）

| 端点组 | 内容 |
| --- | --- |
| `POST/GET/DELETE /api/identities`、`POST /{id}/roles`、`POST /{id}/password` | 身份 CRUD、角色分配、改密（旧密码校验）；不允许停用/删除最后一个超管 |
| `GET/POST/DELETE /api/permission-domains` | 权限域管理（subject 支持 IDENTITY/ROLE） |
| `GET/POST/PUT/DELETE /api/policies`、`POST /{id}/publish`、`POST /{id}/disable`、`GET /{id}/snapshots` | 策略草稿-发布-停用；发布响应返回新 revision |
| `POST /api/policies/simulate` | **模拟预检**：入参 subject/datasource/namespace/table/operation/columns，干跑 `PolicyDecisionService` 返回 `TableDecision`（不写审计执行记录，写审计 simulate 动作） |
| `GET /api/approvals`、`POST /{id}/approve`、`POST /{id}/reject` | 审批最小 API（P2 仅模型闭环） |

**访问控制**：上述全部端点要求 `admin` 角色——`AuthInterceptor` 扩展或在 `WebMvcConfig` 加角色拦截（会话语义，非资源授权；超管身份天然有 admin 角色）。拒绝语义 `FORBIDDEN_ROLE`，与三类策略拒绝区分。

**测试**：非 admin 调管理 API 被拒；策略发布后 `simulate` 返回与决策一致；发布 revision 递增；审批状态机非法迁移 409。

## 9. 批次 8：端到端验收（start，Testcontainers MySQL）

`FullLinkIntegrationTest` 执法扩展矩阵：

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 超管登录 → 任意执行 | 全通；审计含 `SUPER_ADMIN_BYPASS` |
| 2 | 无权限域身份执行任意 SQL | `DENY_BY_POLICY` |
| 3 | 授权部分列 SELECT | 结果仅含授权列，masked 列为 `***` |
| 4 | 请求未授权列 | `DENY_BY_POLICY`（规则 3） |
| 5 | rowFilter 命中 | 仅返回满足行规则行 |
| 6 | maxRows=5 + 请求 100 行 | 最多 5 行 |
| 7 | 执行中撤权（disable 策略）→ 已执行未交付 | `POLICY_REVISION_CHANGED`，不返回部分结果 |
| 8 | 撤权后新请求 | 执行前 `DENY_BY_POLICY` |
| 9 | `CALL proc()` | `UNRESOLVED_TARGET` |
| 10 | 元数据树 | 仅显示授权 namespace/表 |
| 11 | 多策略叠加（两 PUBLISHED 策略） | §3.1 合并语义正确 |
| 12 | 非 admin 调管理 API | `FORBIDDEN_ROLE` |

**DoD 对照**（全部通过后 P2 关闭）：

- policy.md §8：契约冻结已升版记录 ✓ / §6 清单 ✓ / 快照隔离与 revision 再校验 ✓ / 撤权即时生效 ✓ / 超管短路审计 ✓ / §3.1 六条 ✓
- query-enforcement.md §7：管线全覆盖 ✓ / 无旁路静态检查（web/domain 无 JDBC import）✓ / 三类拒绝可区分 ✓ / 同一 contractVersion ✓
- control-storage.md §6：V2 迁移测试 ✓ / 仓储单测 ✓ / 审计无凭据 ✓
- domain.md §8：执法状态不再出现 `BOOTSTRAP_ADMIN_UNRESTRICTED`（新记录）✓ / 无 Controller 层权限判断 ✓
- web.md §6：策略拒绝可区分 ✓ / 引导管理员演进为超管 seed ✓

## 10. 已决策记录（2026-09-21，评审通过，全部关闭）

| # | 决策点 | 结论 |
| --- | --- | --- |
| 1 | 权限域 `'*'` 命名空间通配 | **采纳**：一条配置授权整个数据源；显式授权不违反默认拒绝，精确匹配优先于 `'*'` |
| 2 | `spring-security-crypto`（bcrypt） | **采纳**：`PasswordHashService` 落 control-storage，单依赖无传递 |
| 3 | 被拒语句的脚本语义 | **失败即停 + 返回报错信息**：脚本中某语句被拒即停止执行，响应携带三类拒绝语义明细，后续语句不执行 |
| 4 | Druid 解析不了的方言语句（EXEC 等） | **按契约 UNRESOLVED 拒绝，不开口子**；常用只读语句经批次 5 摸底后 P2.x 版本化放行（评审未提异议，按建议执行） |
| 5 | 策略管理前端页面 | **延后 P2.5**，P2 只交付管理 API |
| 6 | TokenStore 会话 | **维持内存**（评审未提异议，按建议执行）；多实例前再评估持久会话 |
| 7 | 元数据过滤决策开销 | 请求内缓存（评审未提异议，按建议执行）；跨请求缓存留后续 |
| 8 | Agent 技术栈（2026-09-23 补充） | **Spring AI** 内置编排 + 模型网关兼容 OpenAI 兼容/Anthropic 双协议；`meper_operation_approval` 预留 `via_agent` 列（详见 [p5-agent-design.md](./p5-agent-design.md) §8） |
| 9 | 迁移序号顺延（2026-09-23 补充） | 0.1 Agent chat MVP 占用 `V2__model_provider.sql`（模型 provider 配置表）；本计划权限模型迁移为 **`V3__permission_model.sql`**（详见 [p01-agent-chat-mvp.md](./p01-agent-chat-mvp.md)） |

P1.x 前置补丁已完成（2026-09-21，提交 `861925e`）：`JdbcSqlExecutor` 驱动层限行（`setMaxRows(maxRows+1)`）防大结果集物化，探测行判定截断标记；工作台 maxRows 非法值确认映射 400（无需改码）。

## 11. 提交划分

| 批次 | 提交信息（拟） |
| --- | --- |
| 1 | `feat(policy): P2 决策契约类型落码（contractVersion 1）` |
| 2 | `feat(storage): V6 权限模型迁移与仓储（身份/权限域/策略/快照/审批）` |
| 3 | `feat(policy): 决策内核 —— 默认拒绝、多策略叠加、超管短路` |
| 4 | `feat(web): 身份登录接入 identity 表与超管 seed` |
| 5 | `feat(query): 目标资源解析、执法管线与结果治理` |
| 6 | `feat(domain): 执法接线，执法状态切换 ENFORCED` |
| 7 | `feat(web): 身份/权限域/策略/审批管理 API` |
| 8 | `test(start): P2 全链路执法验收矩阵` |

每批次完成后同步更新对应 `docs/modules/*.md` 状态列与 `package-info.java`（文档维护规则）。
