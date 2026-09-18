# meper-chatbi-web

> 能力包：B 基本数据库操作（P1 ✅）｜能力包 C 承接：身份扩展 + 策略管理 API（P2）｜依赖：domain、policy、query-enforcement
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
| 探活 | `GET /api/ping`（免认证） |

语义约定：SQL 修改使旧预检失效（前端配合）；执行响应显式携带 `enforcement` 状态；错误经 `GlobalExceptionHandler` 统一映射。

### 2.2 认证机制（P1 现状）
- `BootstrapAdminAuth`：引导管理员（`admin`，密码来自 `MEPER_BOOTSTRAP_ADMIN_PASSWORD`，默认仅本地开发）；
- `TokenStore`：内存 token（重启失效，单实例语义）；
- `AuthInterceptor`：除 `/api/auth/login`、`/api/ping`、`/actuator/**` 外全部拦截；`principal` 以请求属性向 Controller 传递。

## 3. 依赖的模块契约

| 依赖 | 消费点 |
| --- | --- |
| domain | 全部业务用例（数据源/元数据/工作台）；`ExecutionContext` 一律由 domain 工厂签发，web 不构造 |
| policy（P2） | 策略/身份管理用例（P2 前 policy 为空壳，仅占依赖位） |
| query-enforcement | 拒绝语义透传（`DENY_BY_POLICY` 等，见 query-enforcement.md §3.5） |

## 4. 能力包 C 承接（P2 身份扩展 + 管理 API）

- 登录改造：`Identity` 表校验（bcrypt 类哈希）替换 BootstrapAdmin 硬编码；会话 token 持久化或维持内存（决策随多实例需求）；
- 超级管理员：内置身份随 V2 迁移 seed（初始凭据复用 `MEPER_BOOTSTRAP_ADMIN_PASSWORD` 式环境变量机制）；其请求照常走决策短路与审计（`SUPER_ADMIN_BYPASS`），**web 层不做任何免检特殊分支**；
- 身份管理 API：`/api/identities` CRUD、角色分配；
- 策略管理 API：权限域/资源策略的草稿-发布-停用、有效权限**模拟预检**（干跑 `PolicyDecisionService`）、审批列表/通过/驳回；
- 管理 API 自身的访问控制：P2 以 `admin` 角色拦截（Interceptor 层做**角色**检查是会话语义，不是资源授权；资源授权仍在决策层）。

## 5. 演进（其余能力包）

| 阶段 | 变更 |
| --- | --- |
| P3 | 结构化 DML API（计划预览/提交/状态）、审批发起入口 |
| P4 | 对象/账号管理 API（预览-审批-执行三段式）、导入导出任务 API |
| P5 | Trusted API、MCP 适配（Agent 工具入口，仍经 QueryEnforcement） |

## 6. 完成定义（DoD）

- 无 JDBC import / 无 connector-jdbc 依赖（静态合同检查）；
- 每个端点：认证覆盖、错误映射、审计（预检/格式化/执行/管理操作均已写审计）；
- P2 后：策略拒绝可区分语义（非笼统 403）；引导管理员机制演进为内置超级管理员（跳过策略决策用于测试/应急，见 policy.md §7 已决策 3），原引导管理员环境变量迁移为超级管理员初始凭据。
