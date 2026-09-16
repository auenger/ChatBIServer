package com.meper.chatbi.storage.model;

import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;
import com.meper.chatbi.spi.SqlCategory;

import java.time.Instant;
import java.util.List;

/** 执行历史记录（不含结果集数据，只含行数/影响行数等元信息）。 */
public record ExecutionRecord(
        long id,
        long datasourceId,
        String subject,
        Purpose purpose,
        String correlationId,
        String sqlText,
        int statementCount,
        Status status,
        String error,
        EnforcementState enforcementState,
        Instant startedAt,
        Instant finishedAt,
        List<ExecutionStatementRecord> statements) {

    public enum Status { SUCCESS, FAILED, PARTIAL }

    /** @param rowCount 查询返回行数（截断后）；@param truncated 结果被限额截断 */
    public record ExecutionStatementRecord(
            int seq,
            String sql,
            SqlCategory category,
            boolean query,
            Integer rowCount,
            Integer updateCount,
            boolean truncated,
            long durationMs,
            String status,
            String error) {
    }
}
