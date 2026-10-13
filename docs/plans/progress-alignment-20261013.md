# 项目进度与历史规划对齐（2026-10-13）

> 后续验收与适配更新见 [2026-10-13 验收记录](release-acceptance-20261013.md)及 [P2 适配修订](p2-permission-adaptation-20261013.md)；本文原验收结论保留当时语境。

> 文档提交收口补充（2026-10-13）：本次仅提交文档、权限原型及 [通用权限模型设计讨论稿](../design/permission-model-20261013/README.md)等设计材料。DataWiki/数据集实现仍保留在本地未提交工作区，不能仅检出本次文档提交重建已验收包。已有 Docker 包继续使用原 manifest/校验和，不回写快照。临时预览服务与本项目验收/诊断数据库容器已停止；P2 仍未实现。

> 提交前复核：前端 7 项回归、TypeScript 与生产构建通过；后端全量构建两次均在 Oracle JDBC 连接处报 ORA-17800，未通过本轮提交门禁。按用户要求结束测试，仅提交设计与文档，不将此次失败描述为全量构建通过。此前验收记录仍保留其原时点结论；Oracle 连接问题留待后续恢复实现工作时处理。

> 核对范围：当前工作区代码、两份全局设计、模块文档、0.1/P2/P5 历史计划、两轮 DataWiki 留档及 Git 历史。
> 本文记录事实、差异与推进建议；建议不代表已授权实施下一阶段，也不把功能实现等同于全部产品验收完成。

## 1. 当前基线

**当前是引导管理员模式下，具备数据库操作台、AI 分析、DataWiki 和数据集的可部署版本。** 原路线中的 P1 已完成；0.1 与部分原 P5 能力已提前交付；P2 主体权限、P3 结构化写入、P4 专用管理能力和完整 P5 Run/审批尚未完成。

- 最新已提交代码：`ec8325e`（2026-10-10，图表、Markdown 与 Docker）。DataWiki/数据集等大量代码及其留档仍在未提交工作区，包括未跟踪文件。
- 已交付包：`dist/docker/meper-chatbi-0.1.0-20261013-linux-amd64.tar.gz`，含前后端镜像及 Compose。manifest 明确 `sourceIncludesWorkingTreeChanges=true`；只检出该 commit 不能还原此包全部源码。
- 本日实际执行 `mvn -B -ntp package`：79 个测试，0 失败、0 错误、0 跳过。前端 7 项回归、TypeScript 检查、生产构建通过；Compose/Nginx 配置和包校验通过。
- 默认数据库测试覆盖 MySQL 5.7/8.4.9、SQL Server 2019/2022、PostgreSQL 16、Oracle 23ai；不包含需要自备镜像的 Oracle 19c。
- 本日浏览器验证仅覆盖 DataWiki 工具栏的 1440/1280/1024/768 宽度布局与 AI 入口，使用模拟 API。不能据此关闭完整编辑、发布、浮窗拖拽或真实模型验收。
- 10 月 12 日留档中的容器、端口和测试实例属于当时记录，本次未重新确认它们仍运行。

## 2. 原规划与实际进度

| 能力 | 原规划 | 当前证据与状态 | 尚缺什么 |
| --- | --- | --- | --- |
| 连接器、四方言、数据源、SQL 工作台 | P1 | 已实现且有真实数据库测试 | 按需求补操作/版本覆盖，19c 单独验收 |
| 模型配置、双协议、SSE、查询对话 | 0.1，提前验证 P5 | 已有 ModelProviderService、AgentChatService、前端模型/聊天页；历史、工具、usage、reasoning 持久化，含图表 | 外部真实模型的业务问答验收记录 |
| 结构镜像、语义编辑、草稿/发布/版本检索 | 原 E/P5，10 月提前实施 | WikiService/WikiSyncService、V7/V8、DataWiki 页面与生命周期集成测试 | 完整浏览器验收；P2 字段/主体过滤 |
| 多知识空间、默认模型、批量发布、知识编辑对话 | 10 月第二轮 | ScopedChatService/KnowledgeEditService、版本校验、来源记录、独立用户发布；模型协议夹具覆盖 | 真实模型的多轮理解与修正效果、交互验收 |
| 数据集与跨库分析 | 10 月第二轮 | DatasetService/AnalysisService/QueryScopeValidator/ResultWorkspace；真实 MySQL + PostgreSQL 验证净收入 180 元 | 大规模能力与更多业务场景；P2 授权交集 |
| 身份/角色、行列策略、脱敏、撤权治理 | P2 | policy 仍仅 package-info；工厂恒签发 BOOTSTRAP_ADMIN_UNRESTRICTED | 整阶段未落地，旧计划需要适配新入口 |
| 结构化 DML、事务、幂等 | P3 | 尚无专用能力 | 不应把工作台能执行原始 DML 算作 P3 完成 |
| DDL/DCL、账号、例程、导入导出 | P4 | 尚无计划/审批/专用凭据的完整管理通道 | 不应把工作台可执行原始 SQL 算作 P4 完成 |
| AgentDefinition、Run 状态机、审批提案、五层权限交集 | 完整 P5 | 网关、对话、知识工具已有；正式定义/Run/审批体系未交付 | P2 之后接入；写提案依赖 P3/P4 的审批消费 |
| 更多方言、非关系型连接器 | P6 | 当前四种关系库 | 按业务需要推进 |

跨库能力的实际范围：逐库 SELECT 经 Workbench 执行，每次最多 200 行；服务端固定 LEFT/INNER 关联、BigDecimal 运算与分组汇总；工作区最多 24 个结果、单结果 5000 行、累计 100 万字符，模型获得至多 30 行样本。截断结果不能用于完整合并/统计；不承诺跨库事务快照，不是通用联邦 SQL 引擎。

## 3. 文档差异与处理口径

| 差异 | 核对结果 | 对齐方式 |
| --- | --- | --- |
| README 停在 9 月 P1；模块总表称 Agent/Wiki 空壳 | 与当前代码不符 | 更新当前状态和依赖表，历史设计仍保留原日期 |
| 0.1 仍写开发中、会话仅内存、工具不落库 | ChatSessionStore/Repository 与 V3–V6 已实现持久化 | 标记实现已交付、真实模型验收待补；认证 token 内存与聊天持久化分开描述 |
| 权限迁移写成 V2/V3/V4/V6 | 实际 V1–V8 已占用 | 当前新增从 V9 起，开工再检查最新号；历史决策上的旧号只作追溯 |
| DataWiki 首版称不含模型编辑，第二轮已含 | 是先后阶段扩展 | 首版边界注明为历史范围，以第二轮和本基线判断现状 |
| 浮窗早期写活动页自动限定范围，后续改为关注上下文 | 代码用 focusPageIds 与 pageIds 分离 | 当前默认完整知识范围，只有显式选择才收窄 |
| P2 计划称已评审即可开工 | 9 月评审没有覆盖当前 Wiki/数据集/跨库/历史入口 | 保留已决策的授权语义，新增适配设计后再开工 |
| 原 P5 前置全部写 P2/P3 | 只读模型网关、知识和分析已经提前实现 | 继续以能力清单验收；权限和写审批仍按依赖顺序实施 |

迁移事实：V1 运行表，V2 模型，V3 消息，V4 usage，V5 reasoning，V6 工具记录，V7 Wiki，V8 数据集/空间/默认模型/会话范围/来源。

## 4. 代码与架构约束尚未完全一致

这些是本次静态核对确认的差异，当前未修改业务代码，也未据此改变既定架构约束。

1. **Spring AI 子包隔离未完全满足。** AgentChatService、AgentDbTools、ChatSessionStore 位于 `agent.chat`，仍直接 import Spring AI 类型。没有发现它们泄露到其他生产模块，但“仅 gateway 子包允许”的更严格约束未满足。建议把框架适配集中到 gateway，并补可执行的静态边界检查。
2. **元数据访问还不是统一执法路径。** MetadataService 直接获取 JDBC Connection，namespaces/tables/tableDetail/tableData 不接 principal/ExecutionContext，也未走 QueryEnforcement。domain 依赖 connector-jdbc 已获设计允许，但部分 package-info/DoD 仍禁止该依赖或 JDBC import，文档也有冲突。P2 应明确连接生命周期归属、补服务端主体上下文，并覆盖表数据分页与精确总数；只改 Workbench 不足以完成权限。
3. **历史入口需要再授权设计。** WorkbenchService.getExecution/listExecutions 当前未使用 principal 做资源过滤；聊天消息 API 只按主体读持久化内容，尚无撤权后的资源/字段再校验。当前是单一引导管理员，应在开放多用户之前将其纳入 P2。
4. **数据集范围版本不是权限版本。** DatasetService 的指纹检查配置/资源/Wiki 绑定，WikiAccess 仍只判断引导管理员。P2 必须计算“主体权限 ∩ 数据集范围”，并把撤权传播至 Wiki、结果工作区、引用与对话交付。
5. **旧 P2 合同有落码前需要澄清的细节。** TableDecision 只给最高 policyRevision 与策略 ID 串，无法单凭这两个字段逐一比较每条策略的旧版本；应明确每策略版本向量。另有“列解析不全→空 requestedColumns”和“空请求代表全部列”的语义冲突，应显式区分解析失败、SELECT * 与策略允许列为空。保留已确认默认拒绝语义，合同调整按原版本化规则记录。

## 5. 建议推进顺序

### A. 先收口当前交付基线

- 整理并提交当前 DataWiki/数据集代码与文档，形成可从 Git 重建的源码基线；下一次部署包记录精确提交。本次对齐不自动提交。
- 用命名测试库完成浏览器编辑→保存→批量发布→检索→引用，以及浮窗/范围切换/未保存保护验收。
- 使用明确选定的测试模型与夹具，验证“描述业务口径→多页草稿→人工发布→单库/跨库分析→180 元结果”的真实模型闭环，记录 SQL、知识版本、来源与失败场景。
- 处理已确认的框架隔离差异，明确元数据执法接入方案；不要用已有 79 项测试结果代替新权限合同检查。

退出条件：源码、文档、镜像可对应；当前功能有业务验收记录和明确限制。真实模型质量验收与架构适配文档可分别推进。

### B. 重整 P2 范围后实施

保留九月份确认的默认拒绝、多策略最严约束、超管在决策内部短路并审计、解析不了则拒绝、API 先行等原则。先补一份 P2 适配修订：

| 必须覆盖的入口 | 修订应解决的问题 |
| --- | --- |
| 工作台 SQL、元数据、表数据/计数、执行历史 | 显式主体、资源/字段/行校验、结果治理和历史访问 |
| DataWiki 页面、结构知识、搜索、版本、引用 | 按资源/字段授权；不能仅凭文档所在空间判断可见 |
| Wiki 编辑、空间配置、批量发布 | 区分数据读取权、知识编辑权、发布权；默认范围先定清 |
| 数据集配置、成员发现、跨库查询 | 管理权与使用权区分；成员范围只收窄权限 |
| 结果工作区、图表、SSE、聊天历史 | 查询结果进入模型前治理；撤权后后续工具/缓存/历史交付有明确规则 |
| 旧控制库与管理员登录升级 | 从实际 V8 追加迁移；管理员 seed 幂等；旧会话和历史兼容方案 |

建议沿旧八批次拆分，但将 ENFORCED 切换设为所有已开放入口接线后的统一门槛；新增两位普通用户、不同字段/行权限、跨库成员撤权、Wiki 引用失效及旧历史回放的验收矩阵。不要只翻转响应标记。

### C. 权限闭环后按使用目标扩展

- 多人产品使用：P2 后做 P2.5 管理界面，保持原“P2 API 先行”的决定。
- 长任务、运行审计、可恢复交付：优先补 P5 的 AgentDefinition/Run/预算与取消语义。
- 业务写回：再推进 P3 结构化 DML 与审批消费；P4 的高危操作另设专用通道。
- 向量检索、外部 Agent CLI、更多数据库由具体需求决定，当前已有词面检索和四方言基础。

建议下一项具体工作：**当前版本验收收口 + P2 适配修订**。P2 是多人授权使用的前置；单管理员试用可继续用现有版本验证问答质量。

## 6. 证据入口

- 历史方向：[目标架构](../../Java数据库能力架构方案.md)、[实施路线](../../Chat2DB-Java能力借鉴实施方案.md)、[P2](p2-permission-implementation-plan.md)、[P5](p5-agent-design.md)。
- 已实现留档：[0.1](p01-agent-chat-mvp.md)、[DataWiki 首版](datawiki-implementation.md)、[第二轮](dataset-knowledge-v2.md)。
- 执法现状：[ExecutionContextFactory](../../meper-chatbi-domain/src/main/java/com/meper/chatbi/domain/ExecutionContextFactory.java)、[MetadataService](../../meper-chatbi-domain/src/main/java/com/meper/chatbi/domain/MetadataService.java)、[WikiAccess](../../meper-chatbi-session-wiki/src/main/java/com/meper/chatbi/wiki/WikiAccess.java)。
- 范围与合并：[DatasetService](../../meper-chatbi-domain/src/main/java/com/meper/chatbi/domain/DatasetService.java)、[AnalysisService](../../meper-chatbi-domain/src/main/java/com/meper/chatbi/domain/AnalysisService.java)、[ResultWorkspace](../../meper-chatbi-domain/src/main/java/com/meper/chatbi/domain/ResultWorkspace.java)。
- 集成验证：[FullLinkIntegrationTest](../../meper-chatbi-start/src/test/java/com/meper/chatbi/start/FullLinkIntegrationTest.java)，含 Wiki 生命周期、跨库计算及模型协议夹具；本日部署 manifest 见 §1 的同名解压目录。
