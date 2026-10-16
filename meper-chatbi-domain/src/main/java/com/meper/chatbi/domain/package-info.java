/**
 * 领域服务层：数据源登记、元数据、对象操作、任务编排、Agent 与 Wiki 工作流。
 *
 * <p>约束（见架构方案 §3）：
 * <ul>
 *   <li>依赖 connector-spi 契约，不直接依赖 JDBC 实现或方言模块；</li>
 *   <li>接口不返回 HTTP DTO；Web 不直连 SPI 或存储实现；</li>
 *   <li>不导入桌面/工作区语义（如 consoleId）。</li>
 * </ul>
 * <p>DataWiki 结构采集只经 MetadataService，完整成功后交由 session-wiki 原子保存镜像，不写业务库。
 * <p>数据集范围由服务端签发；分库查询经 AST 范围校验和 Workbench，金额合并由受限 BigDecimal 工作区完成。
 */
package com.meper.chatbi.domain;
