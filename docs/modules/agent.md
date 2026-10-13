# meper-chatbi-agent

> P2 当前设计依据：[2026-10-13 适配修订](../plans/p2-permission-adaptation-20261013.md)。本文旧 contractVersion 1、版本校验、权限入口与批次条款如有冲突，以修订稿为准；设计修订不代表运行时已实现。

> 能力包：F Agent｜0.1 对话、模型、知识工具及受限跨库分析已实现；完整 P5 的 AgentDefinition/Run/审批待开发（2026-10-13）。实际依赖：domain、control-storage、query-enforcement、connector-spi、session-wiki。
> [P5 设计](../plans/p5-agent-design.md)保留目标契约；当前实现与已知差异见[进度对齐](../plans/progress-alignment-20261013.md)，包括 Spring AI 尚未完全隔离到 gateway 子包。
> 模块约束原文：`com.meper.chatbi.agent.package-info`

## 1. 定位与边界

Agent 定义、Run 编排、工具范围与交付。Agent 是**受收窄权限的调用入口**，不是特权通道：

- Agent 工具仅经 **QueryEnforcement** 执行，不持有业务库连接或凭据；
- Agent 工具权限永远是主体权限的**收窄**；Agent 初始默认只读；
- **提案不等于执行许可**：`propose_mutation` / `propose_object_change` 只产生待审批提案，交付前需再鉴权；
- 模型输出不得扩大权限（模型说"我有权限"无效）；历史/知识预算有限。

## 2. 目标契约（P5 细化；依赖 C 包决策接口与审批流定型）

```java
interface AgentRunService {
    AgentRun start(AgentDefinition def, RunRequest request);   // Run 编排、工具循环
}
interface AgentTool {                                           // 全部工具的唯一执行约束
    ToolResult invoke(ToolContext context, ToolArgs args);
}
```

工具清单（实施方案 §5）：

| 工具 | 执行路径 |
| --- | --- |
| `wiki_find` | session-wiki 目录检索（授权过滤） |
| `inspect_table` | 元数据（经 `MetadataFilter`，仅可见对象） |
| `query` | QueryEnforcement 管线（只读，`maxRows` 收窄） |
| `propose_mutation` / `propose_object_change` | 生成 `OperationApproval` 提案，不执行 |

- `AgentDefinition`：工具范围（主体权限的子集）、maxRows/超时/轮次预算、模型与提示词配置；
- 收窄模型：`有效权限 = 主体策略决策 ∩ Agent 定义上限`，交集计算发生在每次工具调用（经 QueryEnforcement），不预计算缓存长期有效的权限。

## 3. 依赖的模块契约

| 依赖 | 消费点 |
| --- | --- |
| query-enforcement | 全部数据访问工具的唯一执行路径 |
| policy | 主体决策 + 审批提案落库 |
| session-wiki | `wiki_find` |
| domain | 元数据/工作台用例的 Agent 视图入口（`Purpose.AGENT`） |
| control-storage | Agent 定义 / Run 记录 / 提案表 |

## 4. 演进与已决策/开放点

**0.1 内置图表技能（2026-10-09）**：在三个只读数据工具之外增加 `get_chart_skill`，
仅从固定资源目录读取 trend/comparison/proportion 三个技能。模型按需读取绘图规范后，
将真实查询结果（或用户明确提供且注明来源的数据）输出为 `GPT-Vis` JSON 代码块。
支持 8 种统计图；无数据或查询失败不生成图表，截断或样本结果不得冒充全量占比。
本工具不查询数据库，不引入网络渲染服务，现有执行与权限边界不变。

**0.1 思考输出兼容（2026-10-09）**：gateway 的 `ChatResponseEvents` 将 OpenAI 兼容协议的
`reasoningContent` 元数据与 Anthropic 协议的 thinking generation（Spring AI 1.1.8 中
带 `signature` 键，增量值可为空）统一为 `reasoning` 事件；正文独立为 `token`。
处理帧内全部 generation，同帧 reasoning 与正文都保留。思考单独落库供折叠回放，
不写入回答正文或后续回答历史；智谱的 Anthropic 兼容端点与原生 Anthropic 共用该规则。

**已决策（2026-09-23，详见 [../plans/p5-agent-design.md](../plans/p5-agent-design.md)）**：

- **编排路线**：内置编排为主 Runtime（服务端工具循环）；外部 Agent CLI 桥接（Claude Code / Codex 等）作为未来 `AgentRuntimeProvider` 适配器，不改变工具面与执法边界；
- **编排框架**：Spring AI，隔离在 `gateway` 子包（类型不进领域契约）；
- **模型网关**：兼容 **OpenAI 兼容 + Anthropic 原生**两种协议，`ModelProviderProfile` 配置化切换；不引入独立网关服务——模型调用不触库、凭据经 AES-256-GCM 加密存储、零暴露；
- **审批预留**：`meper_operation_approval.via_agent` 列随 P2 批次 2 DDL 落列。

仍开放：

- 提案的差异化审批（按目标资源路由审批人）——依赖 policy 审批状态机成熟度；
- Agent 写权限的默认关闭策略与开启审批（P5 前不开放写工具）。

## 5. 完成定义（DoD）

- 全部工具经 QueryEnforcement：无任何工具直连 connector-jdbc（静态检查）；
- 收窄验证：主体可读但 Agent 定义未含的对象，工具不可达；
- 提案-审批-执行闭环：提案不产生任何业务库副作用；
- Run 历史有预算上限；审计记录工具调用与交付状态。


## DataWiki 首版（2026-10-12）

实现边界、接口、页面与验收见 [DataWiki 实施计划](../plans/datawiki-implementation.md)。本阶段提前交付语义目录，执法状态仍为 BOOTSTRAP_ADMIN_UNRESTRICTED；权限 P2 的多用户资源过滤未切换。


## 数据集与对话式 Wiki（2026-10-12）

本轮增加默认模型、知识空间绑定、批量发布与对话草稿，分析支持受限跨库查询和固定服务端合并。边界与验收见 [第二轮实现](../plans/dataset-knowledge-v2.md)。权限执法仍为引导管理员阶段。
