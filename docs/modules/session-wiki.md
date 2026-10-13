# meper-chatbi-session-wiki

> P2 当前设计依据：[2026-10-13 适配修订](../plans/p2-permission-adaptation-20261013.md)。本文旧 contractVersion 1、版本校验、权限入口与批次条款如有冲突，以修订稿为准；设计修订不代表运行时已实现。

> DataWiki 首版，2026-10-12；完整产品计划与人工验收见 [datawiki-implementation.md](../plans/datawiki-implementation.md)。依赖 control-storage（传递 connector-spi），不依赖 domain/JDBC 业务连接。

## 定位与契约

WikiService 管理结构镜像的语义扩展、草稿、资源绑定、不可变发布版本、受限路径检索。WikiSyncService 在 domain 读取元数据并将完整成功的采集交给本模块。结构事实与人工说明分离，不修改原始业务库。

WikiAccess 集中校验已验证主体及数据源：当前仅引导管理员，P2 替换为实时表/字段授权过滤。知识只描述、不授权；不宣称当前已有完整多用户执法。

管理契约：objects/pages/page、create/save/publish/restore/disable、revisions/runs。检索契约：search（glob+字面关键词+分页）、read（path+revision+行范围）。详见 WikiModels。

## 一致性

保存校验 draftVersion；发布历史不可变，包含编辑状态及依赖指纹。结构变化令依赖知识需复核，旧版本不得交付 Agent。同步失败保留上次镜像，消失对象保留说明。历史恢复只恢复语义到新草稿，不回退原库结构。

## 边界与完成条件

所有管理改动记审计。Agent 工具壳在 agent.gateway，范围绑定会话数据源，预算每轮独立。只检索正式有效内容，历史/知识预算有限。当前会话历史仍由 agent.ChatSessionStore 管理；本模块不另建聊天存储。

首版测试：MySQL 8.4 真实结构与控制库隔离、草稿发布隔离、历史恢复、跨数据源与失效检索；模型协议 grep→read；页面人工操作。P2 跨主体字段过滤待其决策内核落地，不以当前管理员入口替代。


## 数据集与对话式 Wiki（2026-10-12）

本轮增加默认模型、知识空间绑定、批量发布与对话草稿，分析支持受限跨库查询和固定服务端合并。边界与验收见 [第二轮实现](../plans/dataset-knowledge-v2.md)。权限执法仍为引导管理员阶段。
