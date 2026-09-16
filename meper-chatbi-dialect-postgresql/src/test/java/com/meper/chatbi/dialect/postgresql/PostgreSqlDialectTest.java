package com.meper.chatbi.dialect.postgresql;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.ConnectionSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PostgreSqlDialectTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    private ConnectionSpec spec(SslMode sslMode) {
        return new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.POSTGRESQL,
                "pg.host", 5433, "meperdb", "app", "x".toCharArray(), sslMode, Map.of());
    }

    @Test
    void buildJdbcUrlWithSslModes() {
        assertEquals("jdbc:postgresql://pg.host:5433/meperdb?sslmode=disable",
                dialect.buildJdbcUrl(spec(SslMode.DISABLED)));
        assertEquals("jdbc:postgresql://pg.host:5433/meperdb?sslmode=require",
                dialect.buildJdbcUrl(spec(SslMode.REQUIRED)));
        assertEquals("jdbc:postgresql://pg.host:5433/meperdb?sslmode=prefer",
                dialect.buildJdbcUrl(spec(SslMode.PREFERRED)));
    }

    @Test
    void paginateQuoteProbe() {
        assertEquals("select * from t LIMIT 100 OFFSET 200", dialect.paginate("select * from t;", 200, 100));
        assertEquals("\"select\"", dialect.quoteIdentifier("select"));
        assertEquals("SELECT 1", dialect.probeSql());
        assertInstanceOf(PostgreSqlDialect.class,
                DialectRegistry.get(com.meper.chatbi.spi.DatabaseType.POSTGRESQL));
    }
}
