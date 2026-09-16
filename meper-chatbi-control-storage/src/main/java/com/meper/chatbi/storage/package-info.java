/**
 * 控制面存储层：领域 Repository、数据源/策略配置版本（草稿/发布/停用）、Agent/Wiki/Task 状态与审计存储。
 *
 * <p>约束（见架构方案 §3）：
 * <ul>
 *   <li>控制库只保存 MEPER 自身数据与 {@code CredentialRef}（凭据引用），不保存明文口令；</li>
 *   <li>不向业务库写入 MEPER 控制表；</li>
 *   <li>审计日志不记录密码、Token 或完整敏感结果。</li>
 * </ul>
 */
package com.meper.chatbi.storage;
