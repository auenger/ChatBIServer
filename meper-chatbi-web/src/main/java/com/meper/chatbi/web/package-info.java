/**
 * B/S 接入层：网页 API、可信系统 API、MCP 适配。
 *
 * <p>硬性约束（见架构方案 §3）：
 * <ul>
 *   <li>本模块无 JDBC 直连，禁止依赖 connector-jdbc 或 dialect-* 实现；</li>
 *   <li>只能调用 domain/policy/query-enforcement 的接口；</li>
 *   <li>前端不能自行填写「已授权」；ExecutionContext 一律由服务端依据已验证身份生成。</li>
 *   <li>跨源 Origin 采用显式白名单，生产环境不使用通配符放开。</li>
 * </ul>
 * <p>DataWiki API 调用 domain/session-wiki 服务，身份来自认证会话；资源与发布校验在服务层，不直接访问业务库。
 * <p>数据库/数据集选择与默认模型由服务端解析；知识编辑发布由用户单独确认批次，控制器不直接访问业务库。
 */
// 知识编辑请求的 focusPageIds 仅提供关注上下文，pageIds 显式收窄编辑范围。
package com.meper.chatbi.web;
