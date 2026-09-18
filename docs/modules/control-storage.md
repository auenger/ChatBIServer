# meper-chatbi-control-storage

> 能力包：B 基本数据库操作（运行表，P1 ✅）｜能力包 C 权限建模（策略表，P2 待开发）｜依赖：connector-spi
> 模块约束原文：`com.meper.chatbi.storage.package-info`

## 1. 定位与边界

控制库（MySQL 8.4，`meper_` 前缀）的唯一持久化模块：Repository、凭据加密、Flyway schema。存**控制面数据**（数据源档案、凭据密文、执行与审计记录、以及 P2 起的身份与策略），**永不存业务库数据**。

- 凭据只存 `CredentialRef` 与密文；明文口令不落库、不进日志；
- 审计不含凭据与完整结果（`detail` JSON 列只放脱敏上下文）；
- schema 演进只走 Flyway 迁移文件（`db/migration`），**已发布迁移不改写**，变更一律新增 `Vn__xxx.sql`；
- 被消费方式：仓储接口直接被 domain（及 P2 起的 policy，依赖放开已决策，见 policy.md §7）注入使用；本模块不依赖任何领域模块。

## 2. 对外契约（已实现，P1）

### 2.1 Schema（V1__init.sql，5 表）

| 表 | 内容 | 关键约束 |
| --- | --- | --- |
| `meper_datasource_profile` | 数据源档案 | **无密码列**；`credential_version` 随轮换递增；name 唯一 |
| `meper_credential` | 凭据密文 | `(datasource_id, version)` 唯一；status ACTIVE/RETIRED；AES-256-GCM `ciphertext + iv` |
| `meper_execution` | 执行记录 | 含 `enforcement_state`、`correlation_id`、status SUCCESS/FAILED/PARTIAL |
| `meper_execution_statement` | 逐语句结果 | 级联删除；category / row_count / update_count / truncated |
| `meper_audit_log` | 审计 | occurred_at 索引；action / resource_type / detail(JSON, 脱敏) |

### 2.2 Repository

| Repository | 方法面 |
| --- | --- |
| `DataSourceProfileRepository` | 登记 / 列表 / 详情 / 删除 / 凭据版本递增 |
| `CredentialRepository` | 按 `(datasourceId, version)` 存取密文；ACTIVE/RETIRED 状态 |
| `ExecutionRepository` | `insert(context, datasourceId, sql, result, start, finish)`；`findById`；`list(datasourceId, size, offset)` |
| `AuditRepository` | `insert(tenantId, subject, action, resourceType, resourceId, detail)` |

### 2.3 `AesGcmCipherService`
AES-256-GCM 加解密；主密钥来自环境变量 `MEPER_MASTER_KEY`，**缺失即拒绝启动**；主密钥更换后旧密文不可解密（需重新登记或轮换凭据，见根 README）。

## 3. 依赖的模块契约

- `ExecutionContext` / `SqlExecutionResult` 等 SPI 模型：执行记录落库需要；这是本模块唯一内部依赖。

## 4. 能力包 C 交付物（P2，策略表 V2 迁移）

> 冻结前提：[policy.md](./policy.md) §3 的模型与决策契约。字段清单以 policy.md 为准，本节只定模块侧交付。

- `V2__permission_model.sql`（暂名）：身份表、角色/身份-角色映射、权限域、资源策略（含 revision / status DRAFT-PUBLISHED-DISABLED）、策略发布快照、审批单；
- 对应 Repository：身份、权限域、策略草稿/发布/停用、快照读取（按 revision）、审批；
- 仓储测试：空字段语义（默认拒绝下的存储形态）、revision 单调、草稿不可被决策读取；
- 迁移规则不变：只增不改。

## 5. 演进（其余能力包）

| 阶段 | 变更 |
| --- | --- |
| P3（DML） | 变更任务表（结构化 DML 计划、审批关联、幂等键）、事务执行记录 |
| P4 | 对象变更计划/审批、导入导出任务表 |
| P5 | Agent 定义 / Run / 会话历史（有限预算）表 |

## 6. 完成定义（DoD）

- 仓储读写有单测（当前：`StorageRepositoriesTest`、`AesGcmCipherServiceTest`）；
- 凭据密文随机 IV、认证标签校验失败显式报错；
- 每次能力包引入的新表有迁移测试（Flyway 从零跑到最新）；
- 审计与日志无凭据、无完整敏感结果（评审 + 抽查测试）。
