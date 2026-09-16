/**
 * SQL Server 方言实现：连接、元数据、SQL 生成、对象管理中的 SQL Server 特定行为。
 *
 * <p>约束（见架构方案 §2、实施方案 §4）：
 * <ul>
 *   <li>方言能力必须按「数据库 × 操作 × 版本」逐项探测与测试，不得以接口存在推断可用性；</li>
 *   <li>方言 SQL 只出现在方言模块与 connector-jdbc，不得放入 Web/Domain；</li>
 *   <li>不支持的能力显式返回 UNSUPPORTED，不静默走默认实现。</li>
 * </ul>
 */
package com.meper.chatbi.dialect.sqlserver;
