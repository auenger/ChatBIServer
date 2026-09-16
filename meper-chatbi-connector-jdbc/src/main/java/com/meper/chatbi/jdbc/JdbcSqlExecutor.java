package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.SqlExecutor;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.ExecutionLimits;
import com.meper.chatbi.spi.model.QueryResultData;
import com.meper.chatbi.spi.model.SqlExecutionResult;
import com.meper.chatbi.spi.model.StatementResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * JDBC SQL 执行器（补齐 Chat2DB DefaultSQLExecutor 的缺口）：
 * 逐语句 queryTimeout、maxRows 硬截断、单元格/列数限额、语句级失败即停止。
 *
 * <p>阶段 1 为逐语句 autocommit；错误摘要返回给调用方（完整异常进日志由上层处理）。
 */
public class JdbcSqlExecutor implements SqlExecutor {

    @Override
    public SqlExecutionResult execute(ExecutionContext context,
                                      DataSource dataSource,
                                      List<AnalyzedStatement> statements,
                                      ExecutionLimits limits,
                                      Consumer<Statement> cancelHook) {
        List<StatementResult> results = new ArrayList<>(statements.size());
        try (Connection conn = dataSource.getConnection()) {
            for (AnalyzedStatement analyzed : statements) {
                StatementResult result = executeOne(conn, analyzed, limits, cancelHook);
                results.add(result);
                if (result.error() != null) {
                    break; // 语句级失败即停止（逐语句 autocommit，已执行的语句不回滚）
                }
            }
        } catch (SQLException e) {
            // 连接级失败（获取连接/连接中断）：已完成的语句结果保留，整体由上层标记失败
            results.add(new StatementResult(results.size(), "",
                    analyzedOf(statements, results.size()), false, null, null,
                    0, JdbcConnectionTester.summarize(e)));
        }
        return new SqlExecutionResult(results);
    }

    private static com.meper.chatbi.spi.SqlCategory analyzedOf(List<AnalyzedStatement> statements, int seq) {
        return seq < statements.size() ? statements.get(seq).category() : com.meper.chatbi.spi.SqlCategory.OTHER;
    }

    private StatementResult executeOne(Connection conn, AnalyzedStatement analyzed,
                                       ExecutionLimits limits, Consumer<Statement> cancelHook) {
        long start = System.nanoTime();
        try (Statement stmt = conn.createStatement()) {
            if (cancelHook != null) {
                cancelHook.accept(stmt);
            }
            stmt.setQueryTimeout(limits.queryTimeoutSeconds());
            boolean hasResultSet = stmt.execute(analyzed.sql());
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    QueryResultData data = readData(rs, limits);
                    return new StatementResult(analyzed.seq(), analyzed.sql(), analyzed.category(),
                            true, data, null, elapsedMs(start), null);
                }
            }
            int updateCount = drainResults(stmt);
            return new StatementResult(analyzed.seq(), analyzed.sql(), analyzed.category(),
                    false, null, Math.max(updateCount, 0), elapsedMs(start), null);
        } catch (SQLException e) {
            return new StatementResult(analyzed.seq(), analyzed.sql(), analyzed.category(),
                    false, null, null, elapsedMs(start), JdbcConnectionTester.summarize(e));
        }
    }

    /** 读完剩余结果集/更新计数，返回最后一个有效更新计数（DDL 常为 0，未知为 -1 归 0）。 */
    private int drainResults(Statement stmt) throws SQLException {
        int count = stmt.getUpdateCount();
        while (true) {
            boolean more = stmt.getMoreResults();
            if (!more && stmt.getUpdateCount() == -1) {
                break;
            }
            if (more) {
                try (ResultSet rs = stmt.getResultSet()) {
                    // 阶段 1 忽略多结果集内容，仅确保游标关闭
                }
            } else {
                count = stmt.getUpdateCount();
            }
        }
        return count;
    }

    private QueryResultData readData(ResultSet rs, ExecutionLimits limits) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        boolean columnsTruncated = columnCount > limits.maxColumns();
        int effectiveColumns = Math.min(columnCount, limits.maxColumns());

        List<String> columns = new ArrayList<>(effectiveColumns);
        for (int i = 1; i <= effectiveColumns; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        boolean rowsTruncated = false;
        while (rs.next()) {
            if (rows.size() >= limits.maxRows()) {
                rowsTruncated = true;
                break;
            }
            List<String> row = new ArrayList<>(effectiveColumns);
            for (int i = 1; i <= effectiveColumns; i++) {
                String value = rs.getString(i);
                row.add(truncateCell(value, limits.cellCharLimit()));
            }
            rows.add(row);
        }
        return new QueryResultData(columns, rows, columnsTruncated, rowsTruncated);
    }

    private static String truncateCell(String value, int cellCharLimit) {
        if (value == null || value.length() <= cellCharLimit) {
            return value;
        }
        return value.substring(0, cellCharLimit) + "…(截断)";
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
