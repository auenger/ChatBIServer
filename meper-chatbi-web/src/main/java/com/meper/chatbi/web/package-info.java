/**
 * B/S 接入层：网页 API、可信系统 API、MCP 适配。
 *
 * <p>硬性约束（见架构方案 §3）：
 * <ul>
 *   <li>本模块无 JDBC 直连，禁止依赖 connector-jdbc 或 dialect-* 实现；</li>
 *   <li>只能调用 domain/policy/query-enforcement 的接口；</li>
 *   <li>前端不能自行填写「已授权」；ExecutionContext 一律由服务端依据已验证身份生成。</li>
 * </ul>
 */
package com.meper.chatbi.web;
