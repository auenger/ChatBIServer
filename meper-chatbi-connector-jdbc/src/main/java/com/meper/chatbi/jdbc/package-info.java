/**
 * JDBC 连接实现层：驱动加载（Driver Registry）、连接池、事务、SQL 执行与取消。
 *
 * <p>改造要求（见 Chat2DB-Java能力借鉴实施方案.md §4）：
 * <ul>
 *   <li>禁止连接失败后自动降级 SSL（不得补 useSSL=false）；TLS 由管理员显式配置；</li>
 *   <li>连接池按 租户/数据源/凭据版本/访问角色 隔离；撤权与凭据轮换后不得复用旧连接；</li>
 *   <li>不使用 ThreadLocal 隐式传递身份与权限，统一使用显式 ExecutionContext。</li>
 * </ul>
 */
package com.meper.chatbi.jdbc;
