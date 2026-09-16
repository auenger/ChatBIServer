package com.meper.chatbi.dialect.mysql;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.ConnectionSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlDialectTest {

    private final MySqlDialect dialect = new MySqlDialect();

    private ConnectionSpec spec(SslMode sslMode) {
        return new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.MYSQL,
                "10.0.0.8", 3307, "mepordb", "svc_user", "x".toCharArray(), sslMode, Map.of());
    }

    @Test
    void buildJdbcUrlWithSslModes() {
        assertEquals("jdbc:mysql://10.0.0.8:3307/mepordb?sslMode=DISABLED&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                dialect.buildJdbcUrl(spec(SslMode.DISABLED)));
        assertEquals("jdbc:mysql://10.0.0.8:3307/mepordb?sslMode=REQUIRED&characterEncoding=utf8",
                dialect.buildJdbcUrl(spec(SslMode.REQUIRED)));
        assertTrue(dialect.buildJdbcUrl(spec(SslMode.PREFERRED)).contains("sslMode=PREFERRED"));
    }

    @Test
    void extendInfoAppendedAndEncoded() {
        ConnectionSpec s = new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.MYSQL,
                "h", 3306, "db", "u", "x".toCharArray(), SslMode.DISABLED, Map.of("connectTimeout", "3000"));
        assertTrue(dialect.buildJdbcUrl(s).endsWith("&connectTimeout=3000"));
    }

    @Test
    void paginateAndQuote() {
        assertEquals("SELECT a FROM t LIMIT 50 OFFSET 100", dialect.paginate("SELECT a FROM t;", 100, 50));
        assertEquals("`order`", dialect.quoteIdentifier("order"));
        assertEquals("`a``b`", dialect.quoteIdentifier("a`b"));
        assertEquals("SELECT 1", dialect.probeSql());
        assertTrue(dialect.capabilities().supportsDatabase());
    }

    @Test
    void registeredViaServiceLoader() {
        assertInstanceOf(MySqlDialect.class, DialectRegistry.get(com.meper.chatbi.spi.DatabaseType.MYSQL));
    }
}
