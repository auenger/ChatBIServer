package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.ExecutionLimits;
import com.meper.chatbi.spi.model.StatementResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行器集成测试（Testcontainers MySQL 8.4）：
 * maxRows 截断、单元格截断、queryTimeout、DML 影响行数、语句级失败即停止。
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcSqlExecutorTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static ConnectionPoolRegistry registry;
    static DataSource dataSource;
    static final ExecutionContext CTX =
            ExecutionContext.of("default", "tester", Purpose.WORKBENCH, 99,
                    EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);

    @BeforeAll
    static void setUp() {
        BuiltinDriverRegistry.assertAvailable(com.meper.chatbi.spi.DatabaseType.MYSQL);
        registry = new ConnectionPoolRegistry(3);
        dataSource = registry.poolFor(profile(1), MYSQL.getPassword().toCharArray());
        exec("CREATE TABLE t (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(500))");
    }

    @AfterAll
    static void tearDown() {
        if (registry != null) {
            registry.close();
        }
    }

    static DataSourceProfile profile(long credentialVersion) {
        return new DataSourceProfile(99, "it", com.meper.chatbi.spi.DatabaseType.MYSQL,
                MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getDatabaseName(), MYSQL.getUsername(),
                com.meper.chatbi.spi.SslMode.DISABLED, java.util.Map.of(), credentialVersion);
    }

    static void exec(String sql) {
        new JdbcSqlExecutor().execute(CTX, dataSource,
                List.of(new AnalyzedStatement(0, sql, com.meper.chatbi.spi.SqlCategory.DDL)),
                ExecutionLimits.DEFAULTS, null);
    }

    @Test
    void selectWithRowAndColumnLimits() {
        exec("INSERT INTO t (name) VALUES ('a'),('b'),('c'),('d'),('e')");
        SqlExecutionHolder r = run("SELECT id, name FROM t ORDER BY id",
                new ExecutionLimits(3, 30, 4000, 1));
        StatementResult s = r.statement;
        assertTrue(s.query());
        assertEquals(3, s.resultData().rows().size());
        assertTrue(s.resultData().rowsTruncated());
        assertEquals(1, s.resultData().columns().size());
        assertTrue(s.resultData().columnsTruncated());
    }

    @Test
    void cellTruncationAndNull() {
        String longName = "x".repeat(300);
        exec("INSERT INTO t (name) VALUES ('" + longName + "')");
        StatementResult s = run("SELECT name, NULL AS nothing FROM t WHERE name = '" + longName + "'",
                new ExecutionLimits(10, 30, 10, 10)).statement;
        String cell = s.resultData().rows().get(0).get(0);
        assertEquals(10 + "…(截断)".length(), cell.length());
        assertTrue(cell.endsWith("…(截断)"));
        assertNull(s.resultData().rows().get(0).get(1));
    }

    @Test
    void dmlReportsUpdateCount() {
        StatementResult s = run("UPDATE t SET name = 'upd' WHERE name = 'upd'", ExecutionLimits.DEFAULTS).statement;
        assertFalse(s.query());
        assertNotNull(s.updateCount());
        assertTrue(s.updateCount() >= 0);
        assertNull(s.error());
    }

    @Test
    void queryTimeoutEnforced() {
        StatementResult s = run("SELECT SLEEP(5)", new ExecutionLimits(1000, 1, 4000, 10)).statement;
        assertNotNull(s.error(), "超时必须产生错误");
        assertTrue(s.error().toLowerCase().contains("timeout") || s.error().contains("超时"),
                "错误应提示超时: " + s.error());
    }

    @Test
    void failFastOnStatementError() {
        var result = new JdbcSqlExecutor().execute(CTX, dataSource,
                List.of(new AnalyzedStatement(0, "SELECT 1", com.meper.chatbi.spi.SqlCategory.SELECT),
                        new AnalyzedStatement(1, "SELECT * FROM no_such_table", com.meper.chatbi.spi.SqlCategory.SELECT),
                        new AnalyzedStatement(2, "SELECT 2", com.meper.chatbi.spi.SqlCategory.SELECT)),
                ExecutionLimits.DEFAULTS, null);
        assertEquals(2, result.statements().size(), "失败后剩余语句不执行");
        assertNull(result.statements().get(0).error());
        assertNotNull(result.statements().get(1).error());
        assertFalse(result.allSucceeded());
    }

    private record SqlExecutionHolder(StatementResult statement) {
    }

    private SqlExecutionHolder run(String sql, ExecutionLimits limits) {
        var result = new JdbcSqlExecutor().execute(CTX, dataSource,
                List.of(new AnalyzedStatement(0, sql, com.meper.chatbi.spi.SqlCategory.SELECT)), limits, null);
        return new SqlExecutionHolder(result.statements().get(0));
    }
}
