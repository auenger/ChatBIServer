package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.ConnectionSpec;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 连通性测试（preConnect 模式，借鉴 Chat2DB：临时连接、不入池、用完即关）。
 *
 * <p>失败时保留驱动原始错误摘要，<b>不做任何参数改写重试</b>（禁止 SSL 自动退化，实施方案 §4）。
 */
public final class JdbcConnectionTester {

    private JdbcConnectionTester() {
    }

    /** @param message 成功为探活确认，失败为脱敏后的驱动错误摘要 */
    public record TestResult(boolean success, String message, long latencyMs) {
    }

    public static TestResult test(ConnectionSpec spec, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        SqlDialect dialect = DialectRegistry.get(spec.type());
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dialect.buildJdbcUrl(spec));
        cfg.setUsername(spec.username());
        cfg.setPassword(new String(spec.password()));
        cfg.setMaximumPoolSize(1);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(timeoutSeconds * 1000L);
        cfg.setValidationTimeout(timeoutSeconds * 1000L);
        cfg.setPoolName("meper-connect-test");
        try (HikariDataSource ds = new HikariDataSource(cfg);
             Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(dialect.probeSql()).close();
            return new TestResult(true, "连接成功（" + dialect.probeSql() + "）",
                    System.currentTimeMillis() - start);
        } catch (SQLException e) {
            return new TestResult(false, summarize(e), System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return new TestResult(false, summarize(e), System.currentTimeMillis() - start);
        }
    }

    /** 错误摘要：单行、限长；完整堆栈只进日志。 */
    public static String summarize(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().replace('\n', ' ').replace('\r', ' ');
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "…(截断)";
        }
        return e.getClass().getSimpleName() + ": " + msg;
    }

    static String summarize(RuntimeException e) {
        // 沿 cause 链取根因：Hikari 等组件会包装驱动异常，用户需要看到真实驱动错误
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage() == null ? "" : cause.getMessage().replace('\n', ' ').replace('\r', ' ');
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "…(截断)";
        }
        return cause.getClass().getSimpleName() + ": " + msg;
    }
}
