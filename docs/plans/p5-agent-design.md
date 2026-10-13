# P5 Agent 设计（内置编排）

> 状态：**设计定稿（2026-09-23）**；实现排期 P5，前置 P2 全链（执法管线/决策/元数据过滤）与 P3（审批消费）。
> 模块：meper-chatbi-agent（设计时为空壳；当前已实现 0.1 与知识/分析工具）。本文回答并关闭 [agent.md](../modules/agent.md) §4 的编排路线与模型网关开放点。

> 2026-10-13 对齐：本计划保留当时范围和验收记录；后续已实现能力与未完成项见[进度对齐](progress-alignment-20261013.md)。

## 1. 定位与铁律（不变）

> 上游证明「用户是谁」，本服务决定「能访问什么」，Agent 只负责「如何理解问题与组织查询」。

- Agent 工具**仅经 QueryEnforcement** 执行，不持有业务库连接/凭据；
- Agent 权限 = 主体权限的**收窄**；模型输出不得扩大权限；
- **提案 ≠ 执行许可**：`propose_*` 只生成 `OperationApproval`，Agent 不执行写操作；
- 历史与知识预算有限；上下文显式传递，禁 ThreadLocal。

## 2. 路线决策：内置编排为主 Runtime

| 路线 | 结论 |
| --- | --- |
| **A 内置编排（主）** | Java 服务端自建工具循环（LLM + function calling）。部署单体、Run 生命周期与审计闭环可控、可测试（mock LLM）、无外部进程安全面。ChatBI 工具面收敛（5 工具、只读为主），内置 loop 够用 |
| B 外部运行时桥接（未来适配器） | Claude Code / Codex 等 agent CLI 作为推理引擎，本服务作为受控 MCP 服务器 + 审批关卡（Chat2DB `agent-runtime` 的形态）。作为 `AgentRuntimeProvider` 适配器后续接入，**不改变工具面与执法边界** |

两条路线共用：`AgentTool` SPI、权限交集、审批桥、审计、SSE 交付。区别只在「谁驱动工具循环」。

## 3. 技术栈（已决策，2026-09-23）

| 项 | 选型 | 约束/理由 |
| --- | --- | --- |
| 编排框架 | **Spring AI** | 与 Spring Boot 3.5.16 / Maven BOM 零摩擦；`@Tool`/`ToolCallback` 映射 `AgentTool` SPI；官方 `spring-ai-mcp` 支撑路线 B 适配器；Micrometer observations。**类型不进领域契约**——只允许出现在 `gateway` 子包 |
| 模型网关协议 | **OpenAI 兼容 + Anthropic 原生，两种** | `ModelProviderProfile` 配置化（provider/model/baseUrl/参数），不写死厂商；OpenAI 兼容协议同时覆盖 Azure/vLLM/Ollama 等兼容端点 |
| 模型凭据 | 复用 control-storage AES-256-GCM（`CredentialRef` 模式），P5 加 `meper_model_provider` 表；起步期可环境变量 | 零新机制；凭据不出 `gateway` 子包 |
| 模型出站 | agent 模块唯一出站 HTTPS，**不触库、无业务凭据** | agent.md 开放点「独立模型网关」关闭：不引入独立网关服务，`gateway` 子包承担 |
| 流式交付 | Spring MVC `SseEmitter` | 事件协议见 §7.4 |
| 自研隔离层 | `ModelGateway` 接口 + 自有 `ChatRequest/ChatResponse/ToolCall` 模型 | 换框架/升 Spring AI 只动一个子包 |

版本策略：Spring AI 版本随 Spring Boot BOM 对齐，引入前确认与 3.5.16 的兼容矩阵；升级不影响领域契约（隔离层保证）。

## 4. 模块分包与依赖红线

```text
com.meper.chatbi.agent
├─ definition   AgentDefinition（控制面）
├─ run          AgentRunService + Run 状态机 + 预算 + 事件流
├─ tool         AgentTool SPI + 5 工具实现
└─ gateway      ModelGateway + Spring AI 实现（唯一允许 spring-ai import 的子包）

agent → query-enforcement / policy / session-wiki / domain / control-storage
agent ✗→ connector-jdbc / dialect-*（红线，静态检查项）
```

## 5. 核心契约

```java
public interface AgentTool {
    String name();                        // wiki_find / inspect_table / query / propose_mutation / propose_object_change
    String description();                 // 进 LLM 的工具说明
    ToolSchema schema();                  // 参数 JSON Schema
    ToolResult invoke(ToolContext ctx, ToolArgs args);
}

/** 显式上下文；模型侧调用与人类调用走同一执法路径。 */
public record ToolContext(
        ExecutionContext executionContext,   // Purpose.AGENT，由 domain 工厂签发
        AgentDefinition definition,          // Agent 上限：范围/操作/列/maxRows/预算
        AgentRun run,                        // runId、轮次、预算计数器
        AuditSink audit) { }                 // 每次调用写 AGENT_TOOL_CALL 审计
```

`AgentDefinition`（控制面，存储于控制库）：名称、system prompt、`ModelProviderProfile` 引用与模型参数、工具开关（5 工具子集）、最大数据范围（数据源/命名空间/操作上限）、maxRows 上限、轮次/token 预算、Wiki 绑定。

`AgentRun` 状态机：`RUNNING → AWAITING_APPROVAL（提案后可选挂起）→ COMPLETED / FAILED / CANCELLED / BUDGET_EXCEEDED`。

## 6. 权限收窄（五层交集，每次调用现算）

```text
数据库账号原生权限 ∩ 主体实时策略 ∩ Agent 最大范围 ∩ Task/Run 范围 ∩ 当前请求目标
```

与 P2 决策契约的衔接（`DecisionQuery` contractVersion 1 不改）：

- **暴露面收窄**：`inspect_table`/`wiki_find` 可见对象 = `MetadataFilter`（主体决策）**∧** Agent 定义范围——Agent 定义未含的对象，工具不可达；
- **执行面兜底**：`query` 走 P2 执法管线全量决策（模型构造出越界 SQL 也被拒）；
- 不预计算缓存长期有效的权限；revision 变化经执法管线的交付前再校验兜底。

## 7. 运行时行为

### 7.1 Run 循环

```text
用户消息 → ModelGateway.chat(消息 + 工具 schema)
   ← tool_call → AgentTool.invoke（执法）→ 结果注入 → 循环（≤轮次预算）
   ← final answer → 交付（结果治理后）
```

### 7.2 上下文预算

- 会话历史窗口裁剪（system + 最近 N 轮 + 工具结果摘要）；历史/知识预算有限；
- **大结果集不进上下文**：工具结果以「行数 + 列名 + 前 ~20 行样本 + 完整结果引用」注入模型，完整数据走交付通道（防 token 爆炸与二次泄露）。

### 7.3 提案与审批

`propose_*` 仅生成 `OperationApproval`（plan 快照 + 理由 + `applicant` + `via_agent`）落库并返回提案号；审批通过后由 P3 结构化 DML 通道执行（仍走决策与执法）。`meper_operation_approval.via_agent` 列随 P2 批次 2 DDL 预留。

### 7.4 SSE 事件协议（web 层）

```text
event: token | tool_call | tool_result | proposal | final | error   （每事件 runId + seq）
tool_result 一律脱敏后输出；交付写 AGENT_DELIVER 审计
```

### 7.5 Session Wiki 衔接

`wiki_find` 渐进式发现（index → find → read 局部），带 TTL + 权限版本指纹（权限变更后指纹失效、视图重建）；不把全库 schema 塞进上下文。

### 7.6 提示注入缓解

数据与指令通道分离（结果以结构化 payload 注入，不拼接进 system prompt）；工具面无破坏性工具（只读 + 提案）；执法层兜底（注入即使得逞也越不过权限交集）；交付前脱敏与 revision 再校验。

## 8. 已决策记录（2026-09-23，与用户确认）

1. **编排框架 Spring AI**：隔离于 `gateway` 子包，类型不进领域契约；
2. **模型网关协议**：仅兼容 **OpenAI 兼容 + Anthropic 原生**两种，配置化切换，不引入独立网关服务；
3. **路线**：内置编排为主 Runtime，外部 CLI 桥接为未来适配器；
4. **审批表预留**：`meper_operation_approval.via_agent` 随 P2 批次 2 DDL 落列（人类直发为 NULL）。

## 9. 落地批次（P5 内）

| # | 批次 | 交付 | 前置 |
| --- | --- | --- | --- |
| 1 | 模型网关 | `meper_model_provider` 表 + 加密凭据 + `ModelGateway`（Spring AI 封装，双协议）——**已由 0.1 Agent chat MVP 提前交付**（[p01-agent-chat-mvp.md](./p01-agent-chat-mvp.md)） | 无 |
| 2 | 工具与执法接入 | `AgentTool` SPI + `inspect_table`/`query`（复用 P2 管线）+ 暴露面收窄 | **P2** |
| 3 | Run 循环 | `AgentRunService` 状态机 + SSE + 预算 + Run 存储 | 批次 1/2 |
| 4 | 完整工具面 | `wiki_find`（P5 wiki）+ `propose_*`（P3 审批）+ 全链路审计 | 批次 3 + **P3** |

## 10. 完成定义（DoD，与 agent.md §5 一致）

- 全部工具经 QueryEnforcement：无任何工具直连 connector-jdbc（静态检查）；
- 收窄验证：主体可读但 Agent 定义未含的对象，工具不可达；
- 提案-审批-执行闭环：提案不产生任何业务库副作用；
- Run 历史有预算上限；审计记录工具调用与交付状态（`AGENT_TOOL_CALL`/`AGENT_DELIVER`）；
- Spring AI 类型不出 `gateway` 子包（静态检查）。
