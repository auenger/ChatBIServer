# P2 权限适配修订：覆盖 DataWiki、数据集与历史交付

> **后续方向修正（2026-10-13）**：用户明确以外部业务服务接入为主要场景。身份/权限域/迁移与首批实施顺序须先按 [通用权限模型复核](p2-general-permission-model-review-20261013.md)细化；本地账号不再作为业务主体模型的前提。本文全入口执法要求保留，新合同尚未冻结。

> 当前讨论入口：[通用权限模型设计方案](../design/permission-model-20261013/README.md)，包含七维建模、交互图、策略组合真值表与待决策项。本文本地身份、迁移草案和批次按该方向复核后实施。

> 日期：2026-10-13。状态：本轮适配设计完成，尚未实现；不代表运行时已切换 ENFORCED。
> 本文是后续 P2 的实施入口，覆盖旧计划中同名合同、迁移清单、接线范围与实施顺序；[九月计划](p2-permission-implementation-plan.md)保留历史 DDL/分批设计及已确认原则。发生冲突以本文为准。
> 现状证据见[进度对齐](progress-alignment-20261013.md)。当前源码包含 V1–V8；实现前再检查迁移最大版本。

## 1. 保留的决定与本轮变化

保留：默认拒绝、空策略允许列拒绝、多策略操作取并且约束取最严、策略只读正式快照、超管在 PolicyDecisionService 内短路并记录 SUPER_ADMIN_BYPASS、失败即停、无法可靠解析则拒绝、API 先行/管理页面 P2.5、登录 token 暂存内存。P2 不开放普通用户业务写入，不引入外部 IdP 或分布式会话。

本轮变化：合同升为 **2**；明确列引用与输出血缘、逐策略版本和授权集合变化；增加 Wiki/数据集操作授权、元数据与历史再授权、模型输入及 SSE 交付门槛。P2 完成条件覆盖所有已开放入口，不能只完成 Workbench 接线。

## 2. 身份、角色与产品操作

身份由登录认证签发，不接受请求体中的 principal/roles/tenant。每次授权读取当前身份状态、角色关系与版本；token 内角色只能作显示或索引，不能作为长期授权依据。禁用身份、撤销角色、改密应使现有 token 失效；默认管理员 seed 不覆盖已有密码，不允许停用最后一个活动超管。

`admin` 是管理角色，普通 admin 不因此获得业务数据读取权；`superAdmin` 为独立身份属性，只能在决策内部产生显式 bypass。超管仍受操作类型白名单、可解析性、Agent 只读与范围限制、预算和审计约束；不恢复原始高危 SQL 的无约束执行。

通过 `OperationAccessService` 决策产品操作，按主体/角色 + 目标类型/ID 授权。首版不新增复杂角色编辑界面，仍用 API 配置。

| 操作 | 目标 | 普通用户条件 | 管理面默认 |
| --- | --- | --- | --- |
| DATASOURCE_MANAGE / MODEL_MANAGE | 数据源/模型配置 | 不开放 | admin 可管理，服务层复验当前身份与角色 |
| DATASET_MANAGE | 数据集 | 显式操作授权 | admin 可管理；选成员仍不能扩大数据读取权 |
| DATASET_USE | 数据集 | 显式授权 + 成员数据 SELECT 策略 | admin 管理权不隐含使用权 |
| WIKI_READ | 知识空间 | 空间授权 + 当前绑定资源/字段的可见性 | admin 管理权不隐含读取敏感正文 |
| WIKI_EDIT | 知识空间 | 显式编辑授权 + WIKI_READ；绑定目标必须可见 | admin 的默认编辑授予仍需数据访问检查 |
| WIKI_PUBLISH | 知识空间 | 显式发布授权 + WIKI_READ；逐项版本与依赖检查 | 与编辑权分离 |
| WIKI_SPACE_MANAGE / WIKI_SYNC | 空间/数据源 | 不开放 | admin 管理；同步读取镜像必须在受审计的内部管理上下文执行 |
| HISTORY_READ | 自有历史 | 所有者校验 + 当前数据/Wiki权限 + 交付版本校验 | admin 不默认读取他人历史 |

WIKI_EDIT/WIKI_PUBLISH 不隐含业务 INSERT/UPDATE/DELETE。停用/恢复正式知识视为 WIKI_PUBLISH；恢复旧版本到草稿视为 WIKI_EDIT；创建文档需目的空间编辑权。知识编辑 Agent 只获得当前主体允许的编辑操作，工具中仍无发布入口。

首版服务层拒绝读取他人对话/执行历史，包括普通 admin；超管的数据 bypass 不替代所有权校验。跨主体审计正文浏览如需开放，另设独立审批/审计入口，不借 history API 实现。

## 3. 决策合同 v2

### 3.1 决策输入与解析状态

解析与策略分开。`UNRESOLVED` 不传成“空列请求”，直接拒绝。解析产生每张表的四组字段：输出、过滤（含 WHERE/JOIN/HAVING）、分组/排序、表达式依赖。子查询/CTE 的引用递归归属到真实表；派生列/别名携带完整血缘。

```java
record ResourceKey(long datasourceId, String namespace, String table) {}
enum ProjectionMode { EXPLICIT, STAR, NO_OUTPUT_COLUMN } // COUNT(*) 可为 NO_OUTPUT_COLUMN
record ColumnUsage(ProjectionMode projection,
                   Set<String> projected, Set<String> filtered,
                   Set<String> groupedOrOrdered, Set<String> expressionDependencies) {}
record DecisionQuery(String tenantId, String subject, ResourceKey resource,
                     TableOperation operation, ColumnUsage columns) {}
record PolicyVersion(long policyId, long revision) {}
record AuthorizationStamp(long tenantEpoch, long identityVersion,
                          List<PolicyVersion> policies) {}
record TableDecision(boolean allowed, boolean superAdminBypass,
                     Set<String> allowedColumns, Set<String> maskedColumns,
                     Set<String> filterableColumns, String rowFilter,
                     int maxRows, AuthorizationStamp stamp, String denialReason) {}
```

- 所有字段集合非 null。NO_OUTPUT_COLUMN 仍要求当前表存在非空允许列，且执行行规则，不能用 COUNT(*) 绕过表授权。
- STAR 必须用受信实时结构展开；只投影允许列，并保留原有列顺序。不能先取全部列再依赖前端隐藏。
- 显式引用未授权列直接拒绝，不能静默改变用户查询。
- filtered 必须在 filterableColumns 内；分组/排序、表达式依赖必须可读。涉及 masked 列的过滤、关联、分组、排序、计算表达式首版拒绝；纯字段投影只交付占位值。不能通过聚合/布尔探测还原被脱敏字段。
- 输出治理按解析得到的输出血缘，而非结果列名字符串匹配，防止 `secret AS public_name` 绕过；无法证明血缘时拒绝。
- 空 allowedColumns 对非超管恒拒绝。superAdminBypass 是独立标志，空集合自身从不表示放行。
- 行规则只支持可安全绑定参数的单表布尔条件；JOIN/CTE/子查询中涉及行规则时，首版拒绝不支持的形态。无行规则的可解析 JOIN 可使用；后续扩大支持范围需新增四方言合同测试。

### 3.2 授权版本与交付

`tenantEpoch` 是控制库中的单调授权版本。策略发布/停用/删除、权限域/角色成员/操作授权变化、身份状态/密码变化，与 epoch 递增在同一事务提交。P2 可保守地使用租户级 epoch：任何授权变更使在途旧交付失效，性能优化留后续。

记录全部命中 `(policyId, revision)`，不能只存最大 revision。epoch 同时捕获“新策略加入最严合并集合”“新增/撤销权限域”“角色关系改变”等仅比较旧 ID 列表发现不了的变化。策略草稿保存本身不改变有效授权 epoch。

决策在一致的控制库读取视图中生成 stamp；交付前从同一权威库读当前 epoch/身份状态。检查完成后到网络写出的极短竞态，以授权校验线性化时点定义：检查时已提交的撤权必须拒绝，已经完成授权并发出的字节不追溯撤回。不承诺撤销用户已看到或外部模型已接收的数据。

数据集 scopeVersion、Wiki 发布版本/结构有效性仍独立校验；它们不能代替授权 stamp。缓存与结果 handle 绑定主体、会话、scopeVersion、stamp 和资源血缘，每次消费重新检查。

## 4. 统一调用路径与模块依赖

```text
认证主体 → domain 工厂签发 ExecutionContext
→ 产品操作权限 / 服务端范围解析
→ SQL/元数据/知识资源解析 → policy 决策
→ 行列改写与最严限额 → connector 执行
→ 输出血缘裁剪/脱敏 → 当前授权/范围/知识版本复核
→ 模型输入或用户交付 → 审计
```

### 4.1 Workbench 与元数据

- Workbench.preview 使用同一解析/决策干跑，preview 不是执行许可；execute 每次重算。
- MetadataService 的 namespaces/tables/tableDetail/tableData 全部加入服务端 ExecutionContext；同步任务与 Agent 使用明确 Purpose，不能自填主体。
- connector-spi 定义不含 JDBC/Spring 类型的元数据读取契约；connector-jdbc 的适配器负责开关连接和调用 JdbcMetadataReader。domain 只编排，不再直接获取 Connection。DomainConfig 可按现有规则装配实现。
- 表数据与总数通过与 SQL 相同的授权计划：相同 WHERE 行规则、受控投影和绑定参数；count 不能泄露其他行数。分页 SQL 由方言生成。
- namespace/table/detail 的过滤在服务端完成，SQL 补全、结构树与 Agent schema 工具共用；隐藏列的备注、索引/键等关联信息也不得泄露。
- 原始 DML/DDL/DCL/TCL/OTHER 在 P2 通用工作台执行端拒绝并给出类别原因；P3/P4 专用通道尚未实现。超管 bypass 也不跳过入口类别约束。属于相对 BOOTSTRAP 的显式行为收紧，升级说明必须列出。

### 4.2 Wiki

- WikiAccess 变为服务层适配器，消费 policy 的数据/操作决策；不能仅比较 admin 用户名。
- 结构知识按当前允许列重新渲染（不修改不可变原快照）；搜索片段、命中数、路径发现和引用使用同一授权后的视图。
- 自由文本 TABLE/指标/术语/关联规则难以安全逐字段裁剪，首版保守要求对该版本全部依赖表具有完整字段可读且不需脱敏的权限；字段受限者不返回该文档的标题、命中、正文或引用。允许行限制，因为 Wiki 不自动包含业务行；管理员不得把行数据写进共享知识，若需行数据文档需另设敏感数据分类能力。
- 发布版本可见性按其不可变绑定检查，不用后来修改的草稿绑定授权旧版本。无绑定的业务文档不得向普通读者发布/检索。
- 数据集文档要求所有依赖均在成员范围且有当前数据权限。单个文档内不能部分暴露不可分割的自然语言片段。
- 草稿读取/变更、历史恢复、停用、批量发布逐项授权和版本校验；批次允许部分成功，逐项审计，失败项不落正式版。

### 4.3 数据集和结果工作区

- DATASET_USE + 每个实际访问成员的 SELECT 权限 ∩ 成员固定表清单。数据库模式也做主体策略判断。
- 成员发现只显示授权成员/资源；若已存在数据集含未授权成员，普通用户只能看到可见子集，不能获取隐藏成员名称/表数量。显式分析不可见成员返回权限错误；不能将可见子集的统计冒充全数据集完整统计，回答标注“当前授权范围”。
- 编辑成员配置不能授予数据权限；配置更新改变 scopeVersion，旧对话需要重开。
- ResultWorkspace 接收治理后的结果，携带资源及列血缘；join/calculate/aggregate 的新结果继承全部来源 stamp；消费前复核。masked 字段不能用于关联/算术/分组，不能对占位值计算。
- 保持目前 200 行查询、截断拒绝、基数校验和 BigDecimal 算法；P2 不扩大跨库引擎能力。

### 4.4 Agent、SSE、图表与历史

- Analysis/Knowledge 工具在**每次**执行前校验当前授权。Purpose.AGENT 从工厂签发，保留 ANALYSIS/KNOWLEDGE 用途和具体范围。
- 查询结果必须在进入模型前完成治理与授权复核。来源回执也只能含授权对象。
- 每个对话轮次开始固定授权 stamp；每次工具、模型输入、出站 SSE token/reasoning/tool/toolResult/引用/图表/最终答案均检查。epoch 变化后停止继续交付，取消订阅并丢弃未发出的帧，返回 POLICY_REVISION_CHANGED；不会声称已经送入模型的数据能撤回。
- 查询超时、用户停止与 JDBC cancel 的传播契约补齐；取消后不能再落库“已完成”状态，也不能把部分回答当完整结果。
- 历史写入关联完整数据/Wiki来源、原授权 stamp、scopeVersion、用途与完成状态。任一来源不再满足原交付条件，则整条含正文/思考/工具结果的消息拒绝回放；不尝试用正则从自然语言中删除敏感字段。
- 权限版本有变时，P2 保守拒绝旧敏感正文并要求重新查询；列表标题可能含敏感信息，也改为无内容占位。拒绝原因提供稳定代码，不回显隐藏资源名。
- 无血缘/无授权 stamp 的旧 BOOTSTRAP 历史保留，不迁移为 ENFORCED；普通用户只看到“历史需重新授权查询”的占位。原所有者为活动超管时可在独立决策/审计后读取；无自动跨主体历史继承。
- 表格/CSV/图表复制仅消费同一治理结果；后端导出如有新增必须走相同检查，前端不能自行重新调用不受控数据入口。

### 4.5 依赖增量

| 模块 | P2 新增依赖/职责 |
| --- | --- |
| policy | control-storage，纯决策/操作授权/版本；自含合同不导入 JDBC/connector 类型 |
| query-enforcement | policy，解析/计划/血缘/结果治理 |
| domain | policy（身份与操作决策），维持现有 connector 装配依赖；去除连接生命周期 |
| session-wiki | policy（只经授权适配器），禁止反向依赖 domain，避免循环 |
| agent | 复用 domain/query/session-wiki 的授权门槛；框架类型迁入 gateway |
| web | 现有依赖，不加 JDBC；读取历史改经服务而非直接仓储 |

以上是目标依赖，实际 pom 在对应实施批次才调整。补自动化架构合同检查，禁止 Spring AI 类型跨 gateway、web JDBC 直连、新增 domain Connection 调用与循环依赖。

## 5. 存储增量与升级

| 迁移（暂定） | 内容 | 要点 |
| --- | --- | --- |
| V9__permission_model.sql | 旧七张身份/角色/域/策略/快照/审批表 + authorization_epoch + operation_grant | 不修改 V1–V8；operation_grant 绑定主体/角色、动作、目标，默认不隐式授权 |
| V10__authorized_delivery.sql | 执行/对话交付血缘表，关联现有执行 ID/会话+消息序号 | 保存 contractVersion、stamp、scopeVersion、资源/列/Wiki版本依赖、状态；不复制结果正文或凭据 |

策略表在旧草案基础上增加**具体 namespace**，唯一键 `(domain_id, namespace, table_name)`；权限域 namespace='*' 只放宽可匹配范围，不能让相同表名的策略误匹配不同库/schema。策略发布验证目标存在、列有效、rowFilter 可解析；发布事务同时写快照、revision、epoch。

建议 `operation_grant` 字段：tenant_id、subject_type、subject_id、action、target_type、target_id、revision、enabled、created_by/updated_at；自然唯一键覆盖主体+动作+目标。target_id 不支持客户端随意 `*`；如需全局管理由服务内 admin 管理权限规则显式表达，不与普通对象 grant 混用。

授权 epoch 单例按 tenant_id 锁定递增；所有授权写入口共用事务 helper，禁止绕过。关系删除、停用、身份改密也必须覆盖。

升级验证分别运行空库 V1→V10、现有 V8→V10；保留 Wiki、模型、加密凭据、数据集与历史。旧主密钥必须沿用。新增超管 seed 幂等；已有身份不得重置密码。新 token 结构与权限切换使旧登录失效，需要重新登录。

回退只允许在隔离验证的备份上进行：不能回退到 BOOTSTRAP 并继续对多用户开放；不自动删除新表或改写旧 Flyway 校验和。部署切换前保留控制库备份和旧镜像，失败关闭对外流量后恢复经验证的快照。

## 6. 实施批次与 ENFORCED 门槛

| 批次 | 交付 | 出口验证 |
| --- | --- | --- |
| 0 | Spring AI 隔离、架构合同；明确元数据 SPI；当前版本验收基线 | 既有功能/模型协议不回归 |
| 1 | v2 合同、资源/字段血缘、版本向量、拒绝码 | 空列/解析失败/STAR/别名表达式不可混淆 |
| 2 | V9/V10、仓储、epoch/快照事务、历史血缘 | 空库/升级/幂等/并发版本测试 |
| 3 | 主体与操作决策、角色/策略管理 API、模拟预检 | 默认拒绝、最严合并、超管审计、所有授权变更递增 epoch |
| 4 | SQL 解析/改写/治理、Metadata SPI 与计数执法 | 四方言真实表数据、隐藏列/行、JOIN/CTE 边界 |
| 5 | Wiki、数据集、结果工作区接线 | 多主体知识、成员范围、批次冲突、跨库撤权 |
| 6 | Agent 模型输入/SSE/取消、执行和聊天历史再授权 | 模型不收到禁止字段；撤权后无新增帧/缓存交付 |
| 7 | 本地身份登录、超管 seed、全入口集成后一次切换 ENFORCED | 切换测试矩阵全绿、旧历史兼容、升级演练通过 |
| 8 | 发布收口、API 文档/包/迁移说明 | 代码/文档/镜像一一对应；P2.5 留后续 |

批次 1–6 可用测试主体和内部装配验证；生产对外仍只允许引导管理员，不提前开放普通用户或部分 ENFORCED 开关。避免“身份已经开放，Wiki 仍按 admin 名称检查”造成不一致。

ENFORCED 是覆盖状态：执行、元数据、知识、跨库、模型输入、历史和交付全部接线后才由工厂签发。无新增普通用户可达的 BOOTSTRAP 旁路；超管记录 ENFORCED + SUPER_ADMIN_BYPASS。

## 7. 必须通过的验收矩阵

使用 admin（普通管理角色）、superAdmin、alice（订单部分列+仅本部门行）、bob（不同字段/行）、disabledUser；测试数据只用命名容器库。不能只测超管。

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| AUTH-01 | 无身份/禁用身份/旧 token | 拒绝；无模型和业务库调用 |
| AUTH-02 | 普通 admin 读未授权数据 | 拒绝；管理角色不授数据权 |
| AUTH-03 | 超管 SELECT | 同一执法路径、ENFORCED、bypass 审计 |
| AUTH-04 | 撤角色/改密/新加最严策略 | epoch 递增；旧在途交付失效 |
| SQL-01 | 无域/空允许列/策略停用 | DENY_BY_POLICY |
| SQL-02 | STAR、显式列、列别名、secret+1 | 安全投影或拒绝，别名不绕过 |
| SQL-03 | WHERE/JOIN/HAVING/ORDER/GROUP 用隐藏或 masked 列 | 拒绝不支持的推断路径 |
| SQL-04 | 单表行规则+分页+总数 | 相同授权行集合；COUNT 无泄漏 |
| SQL-05 | CTE/子查询/JOIN 全部表解析；未知函数/动态源 | 全部决策或 UNRESOLVED_TARGET |
| SQL-06 | 普通用户/超管提交 DML/DDL/TCL | 通用查询入口均拒绝 |
| SQL-07 | 两策略版本 2/9，较小版本变化为 3 | 任一版本变化拒绝，不能只比最高值 |
| SQL-08 | 同名表处于两个 namespace | 策略不串库 |
| META-01 | 树、补全、列、备注、索引 | 仅授权结构可见 |
| WIKI-01 | 结构页隐藏列、路径/搜索命中/引用 | 同一过滤视图，无侧向泄漏 |
| WIKI-02 | 自由文本依赖未授权字段/旧草稿改绑 | 整页不可见；按发布版本绑定判断 |
| WIKI-03 | 仅 READ / EDIT / PUBLISH 的身份分别操作 | 能力互不隐含；AI 仍不能发布 |
| WIKI-04 | 批次一页无权/版本冲突 | 逐项失败回执，其他成功项审计完整 |
| WIKI-05 | 结构漂移/停用/历史版本引用 | 失效内容退出交付 |
| DATA-01 | 数据集已授权、成员表未授权 | 不可越权，统计标注当前授权范围 |
| DATA-02 | 改成员/默认 Wiki、跨库撤权 | scopeVersion 或 stamp 失效 |
| DATA-03 | masked join/key、截断、重复基数 | 拒绝；无错误金额累计 |
| AGENT-01 | 协议捕获发给模型的输入 | 不含禁止列/未授权知识 |
| AGENT-02 | 工具执行后、模型输入前撤权 | 不向模型发送该结果 |
| AGENT-03 | SSE 途中撤权/停止/超时 | 后续敏感帧停止，状态非完整成功 |
| HIST-01 | 他人历史/本人撤权历史/旧 BOOTSTRAP 历史 | 所有权和版本门槛生效 |
| HIST-02 | 标题、reasoning、toolResult、图表/引用回放 | 与正文同等授权，不能只过滤 content |
| UPG-01 | V8 升级、保留原主密钥与旧知识 | 数据保留、登录重新签发、无隐式授权 |
| UPG-02 | 第二次启动、已有超管改密 | seed 不覆盖密码；最后超管受保护 |
| ARCH-01 | 全入口/框架隔离/模块方向 | 自动检查 + 真实数据库操作测试同时通过 |

默认四方言矩阵沿用当前测试版本；Oracle 19c 另有自备镜像门槛，不能以 23ai 代替其验收。合同和 UI 补充测试只针对本批风险，不用重复无关测试掩盖缺项。

## 8. 本轮落档边界

本轮完成适配方案与当前版本验收记录，未创建权限迁移、身份账号或策略，不切换 ENFORCED。后续从批次 0/1 开工；新增授权语义按 v2 一次落码并同步 package-info/pom/API 文档。外部 IdP、P2.5 管理界面、完整 Run 状态机、业务写审批和细粒度行数据知识分类仍在后续范围。
