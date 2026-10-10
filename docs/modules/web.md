# meper-chatbi-web

> 能力包：B 基本数据库操作（P1 ✅）｜能力包 C 承接：身份扩展 + 策略管理 API（P2）｜依赖：domain、policy、query-enforcement、agent（0.1 起）
> 模块约束原文：`com.meper.chatbi.web.package-info`

## 1. 定位与边界

B/S API 层：把领域服务暴露为 REST API。**禁止**：依赖 connector-jdbc / dialect-*、任何直接 JDBC 调用、在 Controller 层做权限判断（P2 的策略决策在 domain 执行路径，Controller 只透传结果与拒绝语义）。

后续同层演进：Trusted API（业务系统受信调用）、MCP 适配（P5，Agent 工具入口）——先在 domain 契约上加入口，不在本层旁路。

## 2. 对外契约（已实现，P1）

### 2.1 路由清单

| 组 | 端点 |
| --- | --- |
| 认证 `/api/auth` | `POST /login`、`POST /logout`、`GET /me` |
| 数据源 `/api/datasources` | `POST`（登记）、`GET`（列表）、`GET /{id}`、`POST /test`（临时参数测试）、`POST /{id}/test`、`GET /{id}/capabilities`、`POST /{id}/rotate-credential`、`DELETE /{id}` |
| 元数据 `/api/datasources/{id}/metadata` | `GET /namespaces`、`GET /tables`、`GET /table`、`GET /data` |
| 工作台 `/api/workbench` | `POST /preview`、`POST /format`、`POST /execute`、`GET /executions/{id}`、`GET /executions` |
| 模型配置 `/api/model-providers`（0.1） | `POST`、`GET`、`GET /{id}`、`PUT /{id}`、`DELETE /{id}`、`POST /{id}/test`（apiKey 只写不回显） |
| 探活 | `GET /api/ping`（免认证） |

语义约定：SQL 修改使旧预检失效（前端配合）；执行响应显式携带 `enforcement` 状态；错误经 `GlobalExceptionHandler` 统一映射。

### 2.2 认证机制（P1 现状）
- `BootstrapAdminAuth`：引导管理员（`admin`，密码来自 `MEPER_BOOTSTRAP_ADMIN_PASSWORD`，默认仅本地开发）；
- `TokenStore`：内存 token（重启失效，单实例语义）；
- `AuthInterceptor`：除 `/api/auth/login`、`/api/ping`、`/actuator/**` 外全部拦截；`principal` 以请求属性向 Controller 传递。
- CORS：`MEPER_CORS_ALLOWED_ORIGINS` 以逗号配置精确 Origin，默认仅 `http://localhost:8001`；生产同源反向代理无需放开，跨源时禁止使用 `*` 代替受控白名单。

## 3. 依赖的模块契约

| 依赖 | 消费点 |
| --- | --- |
| domain | 全部业务用例（数据源/元数据/工作台）；`ExecutionContext` 一律由 domain 工厂签发，web 不构造 |
| policy（P2） | 策略/身份管理用例（P2 前 policy 为空壳，仅占依赖位） |
| query-enforcement | 拒绝语义透传（`DENY_BY_POLICY` 等，见 query-enforcement.md §3.5） |
| agent（0.1 起） | 模型 provider 配置管理（`ModelProviderService`）；chat 链路批次 C 接入 |

## 4. 能力包 C 承接（P2 身份扩展 + 管理 API）

- 登录改造：`Identity` 表校验（bcrypt 类哈希）替换 BootstrapAdmin 硬编码；会话 token 持久化或维持内存（决策随多实例需求）；
- 超级管理员：内置身份随 V2 迁移 seed（初始凭据复用 `MEPER_BOOTSTRAP_ADMIN_PASSWORD` 式环境变量机制）；其请求照常走决策短路与审计（`SUPER_ADMIN_BYPASS`），**web 层不做任何免检特殊分支**；
- 身份管理 API：`/api/identities` CRUD、角色分配；
- 策略管理 API：权限域/资源策略的草稿-发布-停用、有效权限**模拟预检**（干跑 `PolicyDecisionService`）、审批列表/通过/驳回；
- 管理 API 自身的访问控制：P2 以 `admin` 角色拦截（Interceptor 层做**角色**检查是会话语义，不是资源授权；资源授权仍在决策层）；
- 管理前端页面：**延后 P2.5**，P2 只交付管理 API（2026-09-21 决策，见 [../plans/p2-permission-implementation-plan.md](../plans/p2-permission-implementation-plan.md) §10）。

## 5. 演进（其余能力包）

**AI 对话 Markdown 表格（2026-10-09）**：流式回复与历史回放共用 `MarkdownTable`，
每张表提供「复制表格」，包含表头与全部已显示数据；优先写入 HTML 表格和 TSV 纯文本，
富文本剪贴板不可用时降级为 TSV，便于粘贴到 Excel。单元格中的制表符、换行和引号按 TSV 转义。
按钮使用 antd 小尺寸描边按钮与 CopyOutlined 图标，沿用当前主题。

**AI 对话图表（2026-10-09）**：`ChatMarkdown` 识别 `GPT-Vis` / `vis` 代码块，
`ChatChart` 按需加载 `@antv/gpt-vis` 1.0.2；支持 JSON 与以 `vis <type>` 开头的 DSL，
提供折线、面积、柱状、条形、饼图、散点、雷达、漏斗 8 类。
`chartConfig` 将输入收窄为白名单配置与 1–200 行内联数据，模型自定义样式或外部地址不会透传。
流式内容不完整时显示生成状态，结束后无效配置显示错误及原始图表数据；历史回放复用相同组件。
图表实例卸载时销毁，使用自适应宽度；依赖要求 Node 20+。
G2 5.4.8 的生命周期补丁通过 `pnpm-workspace.yaml` 与 `patches/` 固定：
绘制过程中延后销毁、忽略解绑后的延迟 resize 回调、移除图形前取消动画，避免快速切换会话后的悬空任务。
另固定 G 6.3.1 的插值补丁：取消动画时缺失的属性端点沿用已有值，空向量使用离散切换，
避免已移除的图形属性产生 `Mismatched interpolation arguments` 异常。升级依赖时须重验缩放/卸载场景。

| 阶段 | 变更 |
| --- | --- |
| P3 | 结构化 DML API（计划预览/提交/状态）、审批发起入口 |
| P4 | 对象/账号管理 API（预览-审批-执行三段式）、导入导出任务 API |
| P5 | Trusted API、MCP 适配（Agent 工具入口，仍经 QueryEnforcement） |

## 6. 完成定义（DoD）

- 无 JDBC import / 无 connector-jdbc 依赖（静态合同检查）；
- 每个端点：认证覆盖、错误映射、审计（预检/格式化/执行/管理操作均已写审计）；
- P2 后：策略拒绝可区分语义（非笼统 403）；引导管理员机制演进为内置超级管理员（跳过策略决策用于测试/应急，见 policy.md §7 已决策 3），原引导管理员环境变量迁移为超级管理员初始凭据。
