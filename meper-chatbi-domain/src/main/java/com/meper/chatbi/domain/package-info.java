/**
 * 领域服务层：数据源登记、元数据、对象操作、任务编排、Agent 与 Wiki 工作流。
 *
 * <p>约束（见架构方案 §3）：
 * <ul>
 *   <li>依赖 connector-spi 契约，不直接依赖 JDBC 实现或方言模块；</li>
 *   <li>接口不返回 HTTP DTO；Web 不直连 SPI 或存储实现；</li>
 *   <li>不导入桌面/工作区语义（如 consoleId）。</li>
 * </ul>
 */
package com.meper.chatbi.domain;
