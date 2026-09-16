package com.meper.chatbi.dialect.postgresql;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

import java.util.Map;

/**
 * PostgreSQL 方言（目标版本 16）。
 */
public class PostgreSqlDialect implements SqlDialect {

    @Override
    public DatabaseType type() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String buildJdbcUrl(ConnectionSpec spec) {
        StringBuilder url = new StringBuilder("jdbc:postgresql://")
                .append(spec.host()).append(':').append(spec.port());
        if (spec.databaseName() != null && !spec.databaseName().isBlank()) {
            url.append('/').append(spec.databaseName());
        }
        url.append("?sslmode=").append(switch (spec.sslMode()) {
            case PREFERRED -> "prefer";
            case REQUIRED -> "require";
            case DISABLED -> "disable";
        });
        for (Map.Entry<String, String> e : spec.extendInfo().entrySet()) {
            url.append('&').append(e.getKey()).append('=').append(e.getValue());
        }
        return url.toString();
    }

    @Override
    public String probeSql() {
        return "SELECT 1";
    }

    @Override
    public CapabilityDescriptor capabilities() {
        return new CapabilityDescriptor(false, true, "\"", "LIMIT_OFFSET", probeSql());
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String paginate(String sql, int offset, int limit) {
        return SqlDialect.stripTrailingSemicolon(sql) + " LIMIT " + limit + " OFFSET " + offset;
    }
}
