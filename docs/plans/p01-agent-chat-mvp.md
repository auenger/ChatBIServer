# 0.1 Agent Chat MVP 规划（chat + NL2SQL + 执行）

> 状态：**已确认（2026-09-23），开发中**。批次 A→D 顺序推进，每批全绿提交。
> 定位：P5 之前的最小横切验证——打通「自然语言 → 模型 → SQL → 受控执行 → 结果」闭环；同时提前落定 Spring AI 集成与模型配置存储（P5 批次 1 的前置）。
> 借鉴：Chat2DB 参考（branch feature/permission-aware-chatbi）的基本 chat 能力，调研结论见附录。

## 0. 范围

**做**：模型配置管理（独立设置页，OpenAI 兼容 + Anthropic 两种协议）、chat 对话（SSE 流式）、3 个数据工具（schema 发现 ×2 + 查询执行 ×1）与内置图表技能读取工具、SELECT 自动执行、前端模型设置页与对话页（表格复制 + GPT-Vis 图表）。

**不做**（留 P5）：AgentDefinition 管理与工具开关、Run 持久化状态机、`wiki_find`/`propose_*` 工具、`Purpose.AGENT` 语义切换（0.1 暂用 `Purpose.WORKBENCH`）。**不做 P2 执法**——0.1 在 `BOOTSTRAP_ADMIN_UNRESTRICTED` 下运行（标记照常携带）；P2 完成后 `execute_query` 经 `WorkbenchService` 自动获得权限执法，无需改动。

## 1. 交互模式（已确认）

普通聊天模式（Chat2DB 同型）：模型带 3 工具按需发现 schema，生成 SQL 后**SELECT 自动执行**返回数据；非 SELECT（DML/DDL/TCL/OTHER）**拦截并以 SQL 块回显**，引导用户去工作台手动执行。

## 2. 技术方案

| 项 | 方案 |
| --- | --- |
| 框架 | **Spring AI 1.1.8**（`spring-ai-bom`；1.1.x 支持 Boot 3；2.0.x 需 Boot 4 不采用——与锁定 Boot 3.5.16 兼容的最后主线，升级只动 `gateway` 子包） |
| 双协议 | `spring-ai-starter-model-openai` + `spring-ai-starter-model-anthropic`；**手动构建 ChatModel**（排除 starter 自动配置，避免启动期依赖 key——Chat2DB `AiModelFactory` 同法），按 provider 配置动态创建 |
| 模型配置存储 | 控制库 **`V2__model_provider.sql`**；apiKey AES-256-GCM（`MEPER_MASTER_KEY`）加密，页面/日志/API **不回显** |
| 执行通道 | `execute_query` → `WorkbenchService.execute`（审计、执行记录、`ExecutionLimits` 全保留）——**agent 零 JDBC**（红线） |
| 安全白名单 | `SqlClassifier` 分类：仅 `SELECT` 自动执行；maxRows 固定 **200**；非 SELECT 回显不执行 |
| 会话 | 内存（`TokenStore` 模式，重启失效），0.1 不持久化；窗口裁剪最近 N 轮 |
| 审计 | `AGENT_CHAT`（对话）/ `AGENT_TOOL_CALL`（工具调用），复用 `AuditRepository` |
| Spring AI 隔离 | 类型不出 agent 模块 `gateway` 子包（对齐 p5-agent-design.md §3） |

## 3. 模型配置设计（已确认：独立设置页）

- 表 `meper_model_provider`：name（用户起名）、protocol（`OPENAI_COMPATIBLE` / `ANTHROPIC`）、base_url、model、apiKey 密文+IV、参数 JSON（temperature 等）、enabled；
- 页面可添加多条配置（用户意图：2 个 OpenAI 兼容 + 1 个 Anthropic；不硬性限 3 条）；
- 管理操作：增删改、启停、**连通测试**（发一条最小补全请求）；key 永不回显（测试也不回传）；
- 对话页下拉选择当前使用的 provider 配置。

## 4. 批次

| # | 批次 | 交付 | 验证 |
| --- | --- | --- | --- |
| A | 依赖与模型配置存储 | 根 pom `spring-ai-bom 1.1.8` + agent 模块双 starter；`V2__model_provider.sql` + `ModelProviderRepository`（密文落库）；`ModelProviderService`（agent 模块，加密/脱敏）；管理 API（CRUD/启停/测试） | 全绿 + API 手验 |
| B | 工具与执行 | `AgentTool` 最简 SPI + `list_tables` / `get_table_schema` / `execute_query` 三实现；SELECT-only 白名单 + maxRows 200；执行经 `WorkbenchService` | 全绿 + 单测 |
| C | chat 链路与 API | `AgentChatService`（内存会话 + SSE 事件流）+ system prompt（借鉴 Chat2DB，禁执行非白名单语义改写）+ `POST /api/agent/chat/stream` + 审计 | 全绿 + SSE 手验 |
| D | 前端 | webapp：模型设置页（协议选择/key 掩码）+ 对话页（provider 下拉、SSE 消费、SQL 块展示、结果表格复用现有渲染） | 手验闭环 |

## 5. 红线对照（0.1 全部满足）

| 红线 | 0.1 落法 |
| --- | --- |
| agent 不持有 JDBC/凭据 | 工具只调 domain `WorkbenchService`/`MetadataService`；pom 无 connector-jdbc 依赖 |
| 凭据不落日志/明文 | apiKey 仅密文落库；API 不回显；审计 detail 不含 key |
| 前端不可自填授权 | 模型 key 只从控制库来（服务端加密）；不提供前端传 key 通道（与 Chat2DB 不同） |
| 执法状态显式 | chat/工具响应携带 `BOOTSTRAP_ADMIN_UNRESTRICTED` 标记（P2 后自动变 `ENFORCED`） |
| 模块依赖 | agent → domain/control-storage/query-enforcement；Spring AI 类型不出 `gateway` 子包 |

## 6. 迁移序号说明

0.1 占用 **`V2__model_provider.sql`**（模型配置表）、**`V3__chat_session.sql`**（对话消息表）、**`V4__chat_message_usage.sql`**（usage 列）、**`V5__chat_message_reasoning.sql`**（思维链列）；P2 权限模型顺延为 **`V6__permission_model.sql`**。规则不变：只增不改。

## 7. 已决策记录（2026-09-23，与用户确认）

1. **SELECT 自动执行**：模型生成 SELECT 直接执行返回数据；非 SELECT 拦截回显；
2. **前端纳入 0.1**：模型设置页 + 对话页；
3. **模型配置独立页面**：支持 OpenAI 兼容与 Anthropic 两种协议（意图配置 2 个 OpenAI 兼容 + 1 个 Anthropic），apiKey 服务端 AES-256-GCM 加密存储、永不回显；
4. **对话持久化（2026-09-23 追加）**：消息落控制库 `V3__chat_session.sql`（`meper_chat_message`），内存窗口 + 库全量；会话列表/消息分页 API；**P2 权限模型顺延 V4**；
5. **上下文比例（2026-09-23 追加）**：流式结束提取模型返回的真实 usage（prompt/completion tokens），以 `usage` 事件返回前端，按 **100 万 token 窗口**显示占用比例；工具活动不落库；
6. **思维链渲染与落库（2026-09-23 追加）**：reasoning 模型的思考过程以 `reasoning` 事件流式渲染（灰色折叠块，正文出现后收起，icon 不用 emoji），并随 ASSISTANT 消息落库回放。
7. **双协议思考归一化（2026-10-09 修复）**：同时兼容 OpenAI `reasoning_content`（Spring AI 元数据 `reasoningContent`）与 Anthropic `thinking_delta`（含智谱 Anthropic 兼容端点）；同帧思考/正文及多 generation 均保留，思考不得混入正文。既有错误落库的历史消息不按自然语言猜测拆分。
8. **图表交付（2026-10-09 追加）**：`get_chart_skill` 按需加载趋势/对比/占比规则；模型输出 `GPT-Vis` JSON 代码块。
   前端以 GPT-Vis 1.0.2 按需渲染 8 类统计图，最多 200 数据点，不接收外部 URL 或任意执行代码。
   流式等待、错误兜底、数据查看/复制与历史回放共用渲染器；数据来源为受控查询或用户明确提供的数据。

## 8. 完成定义（DoD）

- 登记数据源 → 模型配置 → 对话「查询 orders 表前 10 行」全链路走通（真实库手验）；
- 非 SELECT（如 `DELETE ...`）被拦截且 SQL 原文回显，不产生业务库副作用；
- maxRows 200 生效；审计含 `AGENT_CHAT` / `AGENT_TOOL_CALL`；API 响应不出现明文 key；
- `meper.ai` 配置缺省（无 provider 配置）时服务正常启动，chat 端点返回明确「未配置模型」错误；
- 全量 `mvn -B -ntp package` 全绿；web 模块无 spring-ai 类型（静态检查项）。

## 附录：Chat2DB 调研结论（2026-09-23）

- 入口 `POST /api/v3/ai/chat/stream`（Spring MVC SseEmitter），请求带数据源上下文与 `questionType`（NL_2_SQL / 普通聊天）；
- schema 不预注入 prompt，靠工具按需发现（`list_all_tables` → `get_tables_schema` 返回 DDL）；NL2SQL 模板强调只输出 SQL、禁止自行执行；
- 模型调用基于 Spring AI（openai/anthropic 双 starter），`AiModelFactory` 按请求构建 ChatModel，`Flux<ChatResponse>` → SSE；
- 执行双层：NL2SQL 只回显；普通聊天 `execute_sql` 自动执行 SELECT（pageSize 200/上限 500 + 脱敏），非查询 SQL 拦截回显；
- key 优先级：前端传 → 用户配置（AES-GCM）→ 环境变量（MEPER 不采纳「前端传 key」）。
