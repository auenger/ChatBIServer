package com.meper.chatbi.dialect.sqlserver;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

import java.util.Locale;
import java.util.Map;

/**
 * SQL Server 方言（目标版本 2019 / 2022）。
 *
 * <p>mssql-jdbc 12.x 默认 encrypt=true，这里按数据源 sslMode 显式设置避免歧义。
 * 阶段 1 REQUIRED 不校验服务器证书（trustServerCertificate=true），
 * 证书校验属后续 TLS 策略细化；OFFSET-FETCH 语法要求 ORDER BY，
 * 无 ORDER BY 的 SELECT 以 {@code ORDER BY (SELECT NULL)} 补齐（结果顺序不保证，与语义一致）。
 */
public class SqlServerDialect implements SqlDialect {

    private static final String NO_ORDER = "ORDER BY (SELECT NULL)";

    @Override
    public DatabaseType type() {
        return DatabaseType.SQLSERVER;
    }

    @Override
    public String buildJdbcUrl(ConnectionSpec spec) {
        StringBuilder url = new StringBuilder("jdbc:sqlserver://")
                .append(spec.host()).append(':').append(spec.port())
                .append(";encrypt=").append(spec.sslMode() == SslMode.DISABLED ? "false" : "true");
        if (spec.sslMode() != SslMode.DISABLED) {
            url.append(";trustServerCertificate=true");
        }
        if (spec.databaseName() != null && !spec.databaseName().isBlank()) {
            url.append(";databaseName=").append(spec.databaseName());
        }
        for (Map.Entry<String, String> e : spec.extendInfo().entrySet()) {
            url.append(';').append(e.getKey()).append('=').append(e.getValue());
        }
        return url.toString();
    }

    @Override
    public String probeSql() {
        return "SELECT 1";
    }

    @Override
    public CapabilityDescriptor capabilities() {
        return new CapabilityDescriptor(false, true, "[]", "OFFSET_FETCH", probeSql());
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier.replace("]", "]]") + "]";
    }

    @Override
    public String paginate(String sql, int offset, int limit) {
        String base = SqlDialect.stripTrailingSemicolon(sql);
        String paging = " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
        return hasOrderBy(base) ? base + paging : base + " " + NO_ORDER + paging;
    }

    private boolean hasOrderBy(String sql) {
        return sql.toUpperCase(Locale.ROOT).matches("(?s).*\\bORDER\\s+BY\\b.*");
    }
}
