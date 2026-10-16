/**
 * 控制面存储层：领域 Repository、数据源/策略配置版本（草稿/发布/停用）、Agent/Wiki/Task 状态与审计存储。
 *
 * <p>约束（见架构方案 §3）：
 * <ul>
 *   <li>控制库只保存 MEPER 自身数据与 {@code CredentialRef}（凭据引用），不保存明文口令；</li>
 *   <li>不向业务库写入 MEPER 控制表；</li>
 *   <li>审计日志不记录密码、Token 或完整敏感结果。</li>
 * </ul>
 * <p>V7 DataWiki 六表保存镜像、语义、不可变发布、资源绑定及同步记录；不向业务库写任何 Wiki 数据。
 * <p>V8 保留历史 Wiki，新增数据集、知识空间、默认模型、会话范围及语义来源；发布批次回执写审计。
 */
// Wiki 编辑来源区分标题、资源绑定与会话创建标记，支持发布归属及后续编辑范围检查。
package com.meper.chatbi.storage;
