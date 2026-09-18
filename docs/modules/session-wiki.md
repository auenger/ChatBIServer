# meper-chatbi-session-wiki

> 能力包：E 会话语义｜阶段：P5 ❌（现仅 package-info 约束）｜依赖：暂无（目标：+ control-storage；消费 policy 过滤契约）
> 模块约束原文：`com.meper.chatbi.wiki.package-info`

## 1. 定位与边界

授权范围内的**语义目录**与**有限会话历史**：为网页与 Agent 提供表/字段的业务语义上下文（这张表是什么、字段口径是什么、常用查询是什么），让 NL2SQL / Agent 检索有据可依。

硬约束（package-info 既定）：
- 目录内容必须经**授权过滤**——主体只能检索其权限域覆盖的对象语义（借鉴 Chat2DB `ITrustedQueryService` 的身份/权限交集思路）；
- 会话历史有**预算上限**（条数/长度/保留期），不是无限聊天记录；
- 语义目录的存在**不扩大**任何主体的权限：语义只描述，不授权。

## 2. 目标契约（P5 细化；依赖 C 包的权限过滤接口定型）

```java
interface WikiCatalogService {
    /** 按主体可见范围检索语义条目（表/字段/口径/FAQ）。 */
    List<SemanticEntry> find(WikiQuery query);
    /** 管理面：语义条目登记/更新/停用（管理员）。 */
    SemanticEntry upsert(SemanticEntryCommand command);
}
record WikiQuery(String tenantId, String subject, long datasourceId,
                 String namespace, String keyword, int limit)
```

- 交付入口：网页语义面板、Agent 的 `wiki_find` 工具（P5，经 agent 模块装配）；
- 过滤实现依赖 query-enforcement 的 `MetadataFilter` / 权限域数据（C 包交付后接入）。

## 3. 依赖的模块契约

| 依赖 | 消费点 |
| --- | --- |
| policy / 权限域数据 | 可见范围过滤 |
| query-enforcement | `MetadataFilter`（对象级过滤复用） |
| control-storage | 语义条目与会话历史表（P5 迁移） |

## 4. 演进与开放决策点

- 语义条目来源：人工登记为主，LLM 辅助生成需管理员确认后入库（模型输出不直接生效）；
- 会话历史的存储位置与脱敏要求（历史含查询文本，需按审计同标准脱敏）；
- 与 Agent 模块的边界：检索工具的工具壳在 agent 模块，本模块只提供目录服务。

## 5. 完成定义（DoD）

- 无权限对象的语义不可检索（跨权限域用例测试）；
- 会话历史预算生效（超限淘汰/拒绝写入可配置）；
- 管理面改动写审计。
