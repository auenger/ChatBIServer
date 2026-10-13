# MEPER 通用权限模型设计方案（讨论稿）

> 2026-10-13 · 面向外部业务服务 · 未冻结合同、未实现 P2。
> 当前运行态仍是 `BOOTSTRAP_ADMIN_UNRESTRICTED`。本目录的图表示拟实现的设计，不是当前代码运行图。

- [打开讨论入口](index.html)
- [权限模型交互图](permission-model.html) · [结构定义](model.json)
- [请求授权与交付时序](permission-request.html) · [结构定义](request.json)
- [现有配置原型](../../prototypes/general-permission-studio.html)

## 1. 设计依据与当前事实

依据 [通用模型复核](../../plans/p2-general-permission-model-review-20261013.md)、[P2 全入口适配](../../plans/p2-permission-adaptation-20261013.md)、[目标架构](../../../Java数据库能力架构方案.md)、[实施方案](../../../Chat2DB-Java能力借鉴实施方案.md)及现有原型。

本轮核对的代码：`ExecutionContextFactory` 仍签发固定 default 隔离域的 BOOTSTRAP 上下文；`WorkbenchService` 已具备分类、执行与审计链路；policy 模块目前为约束声明，通用策略决策尚未落码。图没有绑定 Git HEAD 来源链接：相关设计及实现存在工作区修改，不能把它们伪装为已提交版本的验证证据。讨论时以本目录设计稿与关联文档为准。

设计目标：接入不同业务服务，主要通过配置身份来源、实体关系、资源映射和策略适配；不要求业务库改表，不强制存在 tenant_id、department_id 或 sys_user。

## 2. 权限的七个建模维度

| 维度 | 核心对象 | 要表达的授权问题 |
| --- | --- | --- |
| 平台隔离与接入信任 | PlatformScope、TrustedClient、Issuer | 哪个应用可以调用？它能使用哪些来源、身份模型和权限域？ |
| 主体与代理调用 | ControlIdentity、SubjectRef、ActorRef | 谁操作、代表谁操作？管理身份和业务身份如何隔离？ |
| 身份属性与关系 | AccessModel、EntityDefinition、RelationshipDefinition、ResolvedSubject | 外部 sub 如何找到业务实体？角色、组、组织和自定义关系如何配置？ |
| 资源与产品操作 | PermissionDomain、ResourceRef、OperationGrant | 可以发现、使用、管理、编辑或发布哪些对象？ |
| 数据访问约束 | ResourcePolicy、ColumnUsage、RowPredicate | 允许哪些操作、字段用途与行范围？怎样脱敏、限额？ |
| 执行范围 | ExecutionScope | 客户端、Agent、数据集、会话和任务怎样收窄已有授权？ |
| 发布、变化与交付 | PublishedSnapshot、AuthorizationStamp、DeliveryLineage | 哪个版本生效？撤权后何时停止执行、模型输入、结果与历史交付？ |

### 2.1 平台隔离与接入信任

建议用 `platformScopeId` 表达控制库配置与会话的隔离边界，区别于业务数据中的租户实体。现有合同中的 tenantId 是否改名，需迁移评审；不能只改名而遗漏旧隔离检查。

TrustedClient 绑定允许的 Issuer、audience、AccessModel、PermissionDomain、操作与资源上限。请求中的外部 tenantId、roles 或 modelId 都不能直接决定平台隔离域或授权。具体 assertion 协议、签名算法、密钥轮换和 jti 存储需在首批接入合同中确定。

### 2.2 主体与代理调用

- ControlIdentity：平台控制面管理员，认证方式可扩展。平台管理角色不自动获得业务数据读取权。
- SubjectRef：`platformScopeId + identitySourceId + externalSub`；身份映射另限定 accessModelId。相同 user001 在 CRM、ERP 中是不同主体。
- ActorRef：实际调用应用、服务账号或代操作人；被代表主体单独记录。代调用权限取经验证的委托边界与主体权限交集。
- SERVICE：无人类主体的任务可使用独立服务主体授权，必须明确用途和来源；不能在服务账号、用户权限之间择宽。

外部声明中的 admin 不映射为平台超管。超级管理员 bypass 仍只在 PolicyDecisionService 内部发生、照常审计，并受入口类别、范围、预算与所有权规则约束。

### 2.3 身份模型与通用关系

AccessModel 包含：来源提供者、主体实体、外部 sub 映射、键与状态字段、类型化属性、实体关系、发布 revision、来源新鲜度策略。

预设实体 USER、SERVICE、ROLE、GROUP、ORG_UNIT、TENANT，可用自定义实体补充项目成员、岗位等关系。它们是逻辑类型，不是固定物理表。首批只支持直接外键及单个关联表路径；递归组织、复杂多跳、自定义表达式在发布时拒绝未支持形态，后续按合同扩展。

目录源与资源源独立：CRM 身份目录可以授权一个报表数据源，也可被多个权限域引用。首批同时保留可信声明属性的类型、来源和用途合同；外部目录服务通过提供者接口后续适配。

身份目录查询由 domain 编排，通过连接器执行发布模型编译出的参数化白名单计划。执行层校验 `IDENTITY_RESOLUTION` 用途、表列范围、行数和超时并审计，不能用临时超管绕过，也不能依赖尚未解析的同一用户权限造成递归。

### 2.4 权限域、资源与产品操作

**建议**：PermissionDomain 是授权配置的适用边界，可绑定一个发布的身份模型、多个客户端与一组资源选择器。每个策略仍指向具体资源；域内跨数据源只是配置组织能力，不产生隐式跨库 SQL 或统一凭据。

同一 AccessModel 可以复用于多个域。首批建议一次执行上下文明确一个域；跨域请求先分别决策，暂不自动合并。身份模型版本、共享资源的域间冲突及旧“一域一数据源”迁移需讨论后确定。

ResourceRef 分别表达：

- 数据对象：datasourceId + catalog/schema + objectType + objectName + 可选 columnName；保留四方言命名空间差异。
- 产品对象：datasetId、wikiSpaceId/documentId、modelProviderId、history owner/session 等。
- 来源依赖：Wiki 发布版本、数据集成员范围、结果和历史中的实际表列血缘。

产品操作授权与数据授权分别计算。例如 DATASET_USE 仍需成员表 SELECT；WIKI_EDIT 不包含 WIKI_PUBLISH，更不包含业务 UPDATE；MODEL_MANAGE 不表示可把任何数据发送到模型。模型使用权限的细粒度操作合同仍待补充，模型输入的数据权限必须执行既有复验要求。

### 2.5 操作、字段用途与行规则

数据操作以 SELECT、INSERT、UPDATE、DELETE 等表达；P2 通用查询入口只启用已支持的查询类别，DML/DDL/DCL 留给后续专用能力和审批。

字段用途需要区别投影、过滤、关联、分组/排序与表达式依赖。首版沿用 P2 合同：过滤受 filterableColumns 约束，分组/排序/表达式依赖必须可读；被脱敏字段只能作为纯投影交付占位值，不能过滤、关联或计算。若要独立配置 JOIN/AGGREGATE 权限，应另行修订合同和四方言测试，不把原型配置当作已经支持。

行规则采用类型化 AST，例如 `EQ(resource.ownerField, subject.businessKey)` 或 `IN(resource.regionField, subject.regionKeys)`。资源字段与主体逻辑属性分别配置，不能拼接不受控 SQL。没有行规则代表没有额外行限制，仍须表列授权；已配置规则的必需属性缺失、类型错误或集合为空则拒绝。

## 3. 策略组合基线与真值表

已确认基线：同一具体资源、同一请求操作、同一适用域内，匹配的已发布策略允许操作取并；约束取最严——允许列交集、过滤列交集、脱敏并集、行规则 AND、maxRows 最小。未匹配的策略不参与；草稿不参与；无允许操作或空允许列拒绝。

此表为设计语义样例，未通过生产授权内核执行；复杂策略适用集合必须在合同测试中固定。

| 情况 | 已发布匹配策略 A / B | 有效行为 |
| --- | --- | --- |
| 无策略 | 无 | 拒绝 |
| 单策略 | 列 {id,amount}；本人行；200 行 | 仅上述列与本人行；上限 200 |
| 两个角色 | A 列 {id,amount}；B 列 {id} | 可读列为 {id} |
| 列无交集 | A 列 {id}；B 列 {amount} | 拒绝 SELECT；不能退化为全部字段 |
| 两个行规则 | A 本人；B 本区域 | 本人 AND 本区域 |
| 无额外行限制 | A 无行规则；B 本区域 | 本区域；无规则视为 TRUE |
| 脱敏与限额 | A 脱敏 phone/200；B 不脱敏/50 | phone 仍脱敏；最大 50 |
| 新增最严策略 | B 新发布并命中当前主体 | 授权 epoch 变化，旧交付失效；按新集合重算 |
| 草稿修改 | B 草稿变更，尚未发布 | 有效授权不变 |
| 执行上限 | 主体允许 200；Agent 100；数据集 50 | 最大 50；各资源范围取交集 |
| 行属性缺失 | 本人规则要求 businessKey，解析结果没有它 | 拒绝；不能删掉规则再执行 |
| 脱敏推断 | phone 可脱敏投影；请求 WHERE/JOIN/GROUP 使用 phone | 首版拒绝该查询 |
| 显式 DENY | 候选新增功能 | 尚未冻结；不把不支持 DENY 的配置发布为有效策略 |
| 跨域策略冲突 | 同资源处于多个域 | 首批建议显式单域执行；跨域合并待确定 |

最严合并意味着增加角色也可能收窄权限。这与许多业务系统的角色并集习惯不同，配置页需要显示有效差异。若希望支持“角色授权并集 + 强制约束交集”两层结构，应明确版本化迁移及完整真值表，不能静默替换现有规则。

## 4. 两个异构系统的配置例子

| 配置 | CRM 示例夹具 | ERP 示例夹具 |
| --- | --- | --- |
| 主体实体 | sys_user.id，外部 sub → external_id | employee.employee_no，外部 sub → login_ref |
| 角色关系 | sys_user_role.user_id → role_id | employee_profile.employee_no → profile_code |
| 逻辑主体键 | businessKey ← sys_user.id | businessKey ← employee.employee_no |
| 可选组织属性 | orgKeys ← 已配置组织关系 | 可不配置；区域由 regionKeys 映射 |
| 资源字段绑定 | orders.owner_user_id = businessKey | invoice.responsible_employee = businessKey |
| 权限域 | CRM 分析域，可含独立报表库 | ERP 分析域，可含账务及知识资源 |

以上表名和角色只是夹具。核心接口不出现 CRM/ERP/销售/财务等专用判断。两系统用相同 sub 也不能串权；相同姓名/email 不自动关联为同一主体。

## 5. 授权戳、撤权和敏感交付

建议 AuthorizationStamp 扩展为以下证据集合，字段签名待合同阶段冻结：

```text
contractVersion
platformScopeId + clientId + subjectRef + accessModelId
controlAuthorizationEpoch
accessModelRevision + 全部命中 policyId/revision
externalIdentityEvidence(providerVersion 或 snapshotDigest, resolvedAt, validUntil)
executionScopeVersion + 资源/字段血缘 + Wiki 发布依赖
```

控制库授权变更原子递增 epoch；外部用户表撤角色不会修改这个 epoch，必须同时检查提供者版本或重新解析规范化身份快照。草稿保存不改变有效权限版本。

首版建议在执行前、进入模型前、最终交付前重新确认身份；无可靠外部版本时优先严格缓冲后复验，再输出敏感回答。需要实时 SSE 的应用可选择失效通知或轮询，但必须声明最大检测窗口与失败关闭行为。具体窗口、预算及默认策略待讨论，不能宣称瞬时撤回。

每次结果 handle 消费和历史回放都检查主体/所有者、范围版本、授权戳及全部来源。Wiki 自由文本按不可变发布依赖保守检查，无法安全逐字段裁剪时整篇不交付。已发送给模型或用户的数据无法追回。

## 6. 模块落点与实施批次

| 模块 | 目标职责 |
| --- | --- |
| web | 应用认证/断言协议适配；不持有 JDBC，不在 Controller 内实现数据授权 |
| domain | 编排主体解析、范围和产品操作、工厂签发上下文、统一调用管线 |
| policy | 纯授权决策、最严合并、操作授权、版本与超管 bypass 审计语义 |
| query-enforcement | SQL/元数据目标解析、用途检查、行列计划、结果血缘与治理 |
| connector-spi / jdbc / dialect | 白名单目录及受控查询执行、凭据与池生命周期、方言能力 |
| control-storage | 发布配置、事务版本、授权快照、审计和交付依赖 |
| session-wiki / agent | 通过授权适配消费结果；检索、工具、模型输入、SSE 和历史接线 |

图上的箭头表达授权对象关系或逻辑调用责任，不代表新增 Maven 依赖。P2 适配中提议的 domain/query/wiki → policy 依赖需在实施批次更新 pom 和 package-info，保持无循环、无 policy JDBC。

1. 冻结首批合同：信任边界、SubjectRef、实体关系子集、权限域、策略适用集合、合并真值表与外部新鲜度。
2. 通用存储与提供者夹具：重新审查 V9/V10 候选 DDL，验证两种异构目录及身份源/资源源分离。
3. 可信 API、主体解析、单表行列执法和元数据过滤；覆盖签名冒用、同名主体、默认拒绝与参数绑定。
4. Wiki、数据集、跨库结果、模型输入、SSE、历史与缓存全部接线；撤权与外部失效真实验证。
5. 原型转配置管理页，空库/升级演练及全入口门禁通过后切换 ENFORCED。

验证使用本地命名夹具与真实数据库操作，覆盖四方言；图形校验仅验证图，不替代权限验收。

## 7. 本次讨论建议顺序

| 议题 | 当前建议 | 需要讨论的影响 |
| --- | --- | --- |
| 权限域 | 一模型可复用多域；一域可含多个资源源；首批单域执行 | 域间冲突、配置组织与旧结构迁移 |
| 策略组合 | 保留已确认最严规则，暂不加入 DENY | 是否需要未来区分授权层与强制约束层 |
| 外部身份失效 | 严格缓冲复验优先；实时流需显式窗口 | 实时体验、解析压力和撤权延迟 |
| 首批实体关系 | 直接外键、单关联表路径与类型化属性 | 组织递归、自定义关系何时扩展 |
| 资源操作 | 数据、Wiki/数据集与控制面动作分别授权 | 模型使用、历史审阅、导出是否新增独立操作 |

## 8. 产物与校验

Archify 已安装为本机 Codex skill，生成时版本 3.0.1。仓库保留设计源 JSON 与生成产物；其他协作者查看 HTML 无需安装该技能。

### 离线查看与预览状态

2026-10-13 文档收口时已停止本项目临时预览服务；18801/18802/18803 当前不提供预览。设计图和配置原型可直接用浏览器打开本地 HTML；在 GitHub 中查看 HTML 文件只显示源码，请下载对应文件后打开。

需要浏览相对链接时，可从仓库根目录手动启动：

```bash
python3 -m http.server 18803 --bind 127.0.0.1 --directory docs
```

访问 `http://127.0.0.1:18803/design/permission-model-20261013/index.html`；结束后按 Ctrl+C 停止。完整方案优先阅读本 Markdown，讨论页中的 Markdown 链接在普通静态服务中可能直接显示源文本。

两张图均通过 showcase 9/9 检查（0 错误、0 警告）及真实 Chrome 浏览器门槛。已查看 1440×900 深色、2048×1320 浅色截图；模型无交叉阻挡，长时序图使用可读的页面纵向滚动。讨论入口在 1440/390 宽度无横向溢出，两张图加载正常、4 个本地文件链接正常、无浏览器脚本错误。

- [交付记录（包含 JSON/HTML SHA-256 与检查状态）](handoff.json)
- [模型图通过回执](permission-model.finalize-summary.json)
- [时序图通过回执](request-check/permission-request.finalize-summary.json)

两张图保留 JSON、独立 HTML、delivery/finalize/browser-check 回执及截图；此前未通过的时序布局回执保存在 attempts/，不代表最终状态。JSON 修改后需重新 finalize，不能手工改已校验 HTML。

本轮仅生成设计材料和文档索引，不属于权限实现、数据库迁移或 Docker 发布。
