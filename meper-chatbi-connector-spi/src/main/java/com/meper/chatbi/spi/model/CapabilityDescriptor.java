package com.meper.chatbi.spi.model;

/**
 * 方言能力声明（架构方案 §2：能力按「数据库 × 操作 × 版本」显式声明，不得以接口存在推断可用性）。
 *
 * <p>阶段 1 只声明连接与分页相关的基础能力；DML/DDL/例程等能力矩阵随 P3/P4 逐格填充。
 *
 * @param supportsDatabase 是否支持 database 层级（MySQL: 是；PG/MSSQL/Oracle 走 schema）
 * @param supportsSchema   是否支持 schema 层级
 * @param identifierQuote  标识符引用符（如 MySQL 反引号）
 * @param paginationStyle  分页风格描述（LIMIT_OFFSET / OFFSET_FETCH）
 * @param probeSql         连通探活 SQL
 */
public record CapabilityDescriptor(
        boolean supportsDatabase,
        boolean supportsSchema,
        String identifierQuote,
        String paginationStyle,
        String probeSql) {
}
