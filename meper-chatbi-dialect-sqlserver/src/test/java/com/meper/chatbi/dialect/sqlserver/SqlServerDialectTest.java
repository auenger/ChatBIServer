package com.meper.chatbi.dialect.sqlserver;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.ConnectionSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SqlServerDialectTest {

    private final SqlServerDialect dialect = new SqlServerDialect();

    private ConnectionSpec spec(SslMode sslMode) {
        return new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.SQLSERVER,
                "db.host", 1433, "meper", "sa", "x".toCharArray(), sslMode, Map.of());
    }

    @Test
    void buildJdbcUrlWithSslModes() {
        assertEquals("jdbc:sqlserver://db.host:1433;encrypt=false;databaseName=meper",
                dialect.buildJdbcUrl(spec(SslMode.DISABLED)));
        assertEquals("jdbc:sqlserver://db.host:1433;encrypt=true;trustServerCertificate=true;databaseName=meper",
                dialect.buildJdbcUrl(spec(SslMode.REQUIRED)));
    }

    @Test
    void paginateAddsOrderByWhenMissing() {
        assertEquals("SELECT a FROM t ORDER BY (SELECT NULL) OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY",
                dialect.paginate("SELECT a FROM t", 20, 10));
        assertEquals("SELECT a FROM t ORDER BY id OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY",
                dialect.paginate("SELECT a FROM t ORDER BY id;", 20, 10));
    }

    @Test
    void quoteAndProbe() {
        assertEquals("[my table]] x]", dialect.quoteIdentifier("my table] x"));
        assertEquals("SELECT 1", dialect.probeSql());
        assertInstanceOf(SqlServerDialect.class,
                DialectRegistry.get(com.meper.chatbi.spi.DatabaseType.SQLSERVER));
    }
}
