package com.meper.chatbi.spi.model;

import com.meper.chatbi.spi.SqlCategory;

/**
 * 单条语句的执行结果。
 *
 * @param seq         语句序号
 * @param sql         语句原文
 * @param category    类别
 * @param query       是否为查询（有结果集）
 * @param resultData  查询结果（非查询为 null）
 * @param updateCount DML/DDL 影响行数（查询为 null；DDL 常为 0）
 * @param durationMs  耗时
 * @param error       失败时的错误摘要（已脱敏；成功为 null）
 */
public record StatementResult(
        int seq,
        String sql,
        SqlCategory category,
        boolean query,
        QueryResultData resultData,
        Integer updateCount,
        long durationMs,
        String error) {
}
