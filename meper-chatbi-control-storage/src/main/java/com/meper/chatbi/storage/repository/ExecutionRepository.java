package com.meper.chatbi.storage.repository;

import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.SqlExecutionResult;
import com.meper.chatbi.spi.model.StatementResult;
import com.meper.chatbi.storage.model.ExecutionRecord;
import com.meper.chatbi.storage.model.ExecutionRecord.ExecutionStatementRecord;
import com.meper.chatbi.storage.model.ExecutionRecord.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 执行历史存取（只存元信息与语句文本，不存结果集数据）。 */
@Repository
public class ExecutionRepository {

    private final JdbcClient jdbc;

    public ExecutionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 落一次执行及其逐语句结果，返回 execution id。
     * 状态由结果推导：全成功 SUCCESS、全部失败 FAILED、部分成功 PARTIAL（fail-fast 场景）。
     */
    public long insert(ExecutionContext context, long datasourceId, String sqlText,
                       SqlExecutionResult result, Instant startedAt, Instant finishedAt) {
        List<StatementResult> statements = result.statements();
        long failed = statements.stream().filter(s -> s.error() != null).count();
        Status status = failed == 0 ? Status.SUCCESS
                : failed == statements.size() ? Status.FAILED : Status.PARTIAL;
        String error = statements.stream().filter(s -> s.error() != null)
                .map(StatementResult::error)
                .findFirst()
                .orElse(null);

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO meper_execution
                            (datasource_id, subject, purpose, correlation_id, sql_text, statement_count,
                             status, error, enforcement_state, started_at, finished_at)
                        VALUES (:ds, :subject, :purpose, :correlationId, :sqlText, :statementCount,
                                :status, :error, :enforcement, :startedAt, :finishedAt)
                        """)
                .param("ds", datasourceId)
                .param("subject", context.subject())
                .param("purpose", context.purpose().name())
                .param("correlationId", context.correlationId())
                .param("sqlText", sqlText)
                .param("statementCount", statements.size())
                .param("status", status.name())
                .param("error", error)
                .param("enforcement", context.enforcementState().name())
                .param("startedAt", Timestamp.from(startedAt))
                .param("finishedAt", Timestamp.from(finishedAt))
                .update(keys);
        long executionId = keys.getKey().longValue();

        statements.forEach(s -> jdbc.sql("""
                        INSERT INTO meper_execution_statement
                            (execution_id, seq, sql_text, category, is_query, row_count, update_count,
                             truncated, duration_ms, status, error)
                        VALUES (:eid, :seq, :sql, :category, :isQuery, :rowCount, :updateCount,
                                :truncated, :durationMs, :status, :error)
                        """)
                .param("eid", executionId)
                .param("seq", s.seq())
                .param("sql", s.sql())
                .param("category", s.category().name())
                .param("isQuery", s.query())
                .param("rowCount", s.resultData() == null ? null : s.resultData().rows().size())
                .param("updateCount", s.updateCount())
                .param("truncated", s.resultData() != null
                        && (s.resultData().rowsTruncated() || s.resultData().columnsTruncated()))
                .param("durationMs", s.durationMs())
                .param("status", s.error() == null ? "SUCCESS" : "FAILED")
                .param("error", s.error())
                .update());
        return executionId;
    }

    public Optional<ExecutionRecord> findById(long id) {
        Optional<ExecutionRecord> header = jdbc.sql("SELECT * FROM meper_execution WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> mapHeader(rs))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<ExecutionStatementRecord> statements = jdbc.sql("""
                        SELECT seq, sql_text, category, is_query, row_count, update_count, truncated,
                               duration_ms, status, error
                        FROM meper_execution_statement WHERE execution_id = :eid ORDER BY seq
                        """)
                .param("eid", id)
                .query((rs, i) -> new ExecutionStatementRecord(
                        rs.getInt("seq"),
                        rs.getString("sql_text"),
                        com.meper.chatbi.spi.SqlCategory.valueOf(rs.getString("category")),
                        rs.getBoolean("is_query"),
                        rs.getObject("row_count") == null ? null : rs.getInt("row_count"),
                        rs.getObject("update_count") == null ? null : rs.getInt("update_count"),
                        rs.getBoolean("truncated"),
                        rs.getLong("duration_ms"),
                        rs.getString("status"),
                        rs.getString("error")))
                .list();
        ExecutionRecord h = header.get();
        return Optional.of(new ExecutionRecord(h.id(), h.datasourceId(), h.subject(), h.purpose(),
                h.correlationId(), h.sqlText(), h.statementCount(), h.status(), h.error(),
                h.enforcementState(), h.startedAt(), h.finishedAt(), statements));
    }

    /** 历史列表（只含表头，无语句明细）；datasourceId 为 null 时查全部。 */
    public List<ExecutionRecord> list(Long datasourceId, int limit, long offset) {
        return (datasourceId == null
                ? jdbc.sql("SELECT * FROM meper_execution ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("limit", limit).param("offset", offset)
                : jdbc.sql("SELECT * FROM meper_execution WHERE datasource_id = :ds ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("ds", datasourceId).param("limit", limit).param("offset", offset))
                .query((rs, i) -> mapHeader(rs))
                .list();
    }

    private ExecutionRecord mapHeader(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExecutionRecord(
                rs.getLong("id"),
                rs.getLong("datasource_id"),
                rs.getString("subject"),
                purposeOf(rs.getString("purpose")),
                rs.getString("correlation_id"),
                rs.getString("sql_text"),
                rs.getInt("statement_count"),
                Status.valueOf(rs.getString("status")),
                rs.getString("error"),
                enforcementOf(rs.getString("enforcement_state")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(),
                List.of());
    }

    private static Purpose purposeOf(String name) {
        return Arrays.stream(Purpose.values()).filter(p -> p.name().equals(name)).findFirst()
                .orElse(Purpose.API);
    }

    private static EnforcementState enforcementOf(String name) {
        return Arrays.stream(EnforcementState.values()).filter(e -> e.name().equals(name)).findFirst()
                .orElse(EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);
    }
}
