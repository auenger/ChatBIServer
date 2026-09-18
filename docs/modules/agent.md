# meper-chatbi-agent

> 能力包：F Agent｜阶段：P5 ❌（现仅 package-info 约束）｜依赖：暂无（目标：经 query-enforcement 执行；权限为主体权限的收窄）
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

## 4. 演进与开放决策点

- Run 编排是否引入独立模型网关（模型调用不触库，凭据零暴露）；
- 提案的差异化审批（按目标资源路由审批人）——依赖 policy 审批状态机成熟度；
- Agent 写权限的默认关闭策略与开启审批（P5 前不开放写工具）。

## 5. 完成定义（DoD）

- 全部工具经 QueryEnforcement：无任何工具直连 connector-jdbc（静态检查）；
- 收窄验证：主体可读但 Agent 定义未含的对象，工具不可达；
- 提案-审批-执行闭环：提案不产生任何业务库副作用；
- Run 历史有预算上限；审计记录工具调用与交付状态。
