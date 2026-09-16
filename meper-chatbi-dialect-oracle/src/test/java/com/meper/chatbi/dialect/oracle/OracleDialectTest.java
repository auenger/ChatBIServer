package com.meper.chatbi.dialect.oracle;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.ConnectionSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OracleDialectTest {

    private final OracleDialect dialect = new OracleDialect();

    private ConnectionSpec spec(SslMode sslMode) {
        return new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.ORACLE,
                "ora.host", 1521, "FREEPDB1", "app", "x".toCharArray(), sslMode, Map.of());
    }

    @Test
    void buildJdbcUrl() {
        assertEquals("jdbc:oracle:thin:@//ora.host:1521/FREEPDB1", dialect.buildJdbcUrl(spec(SslMode.DISABLED)));
        assertEquals("jdbc:oracle:thin:@//ora.host:1521/FREEPDB1", dialect.buildJdbcUrl(spec(SslMode.PREFERRED)));
    }

    @Test
    void requiredSslExplicitlyUnsupported() {
        // 阶段 1 约束：不支持的能力显式报错，不静默降级（实施方案 §4）
        assertThrows(UnsupportedOperationException.class, () -> dialect.buildJdbcUrl(spec(SslMode.REQUIRED)));
    }

    @Test
    void paginateQuoteProbe() {
        assertEquals("SELECT * FROM t OFFSET 10 ROWS FETCH NEXT 5 ROWS ONLY",
                dialect.paginate("SELECT * FROM t;", 10, 5));
        assertEquals("\"user\"", dialect.quoteIdentifier("user"));
        assertEquals("SELECT 1 FROM DUAL", dialect.probeSql());
        assertInstanceOf(OracleDialect.class, DialectRegistry.get(com.meper.chatbi.spi.DatabaseType.ORACLE));
    }
}
