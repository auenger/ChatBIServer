/**
 * MEPER ChatBI 连接器契约层：Connector、Dialect、Capability、ExecutionContext 等接口与值对象。
 *
 * <p>约束（见 Java数据库能力架构方案.md §3）：
 * <ul>
 *   <li>只定义契约，不含实现；不依赖 Spring、Web 或任何 JDBC 驱动；</li>
 *   <li>Web 与 Domain 只允许依赖本模块的契约，不允许直接依赖 JDBC 实现或方言模块；</li>
 *   <li>契约命名使用 MEPER 命名空间，不保留 ai.chat2db 旧命名。</li>
 * </ul>
 */
package com.meper.chatbi.spi;
