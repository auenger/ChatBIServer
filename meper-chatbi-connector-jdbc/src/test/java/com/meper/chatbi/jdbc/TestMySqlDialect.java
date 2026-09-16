package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

import java.util.Map;

/**
 * 测试夹具方言：产出 MySQL 兼容 URL，使 connector-jdbc 测试无需依赖任何方言模块。
 * 生产环境的真实方言由 dialect-* 模块经 ServiceLoader 提供。
 */
public class TestMySqlDialect implements SqlDialect {

    @Override
    public DatabaseType type() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String buildJdbcUrl(ConnectionSpec spec) {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(spec.host()).append(':').append(spec.port());
        if (spec.databaseName() != null && !spec.databaseName().isBlank()) {
            url.append('/').append(spec.databaseName());
        }
        url.append("?sslMode=").append(spec.sslMode() == SslMode.REQUIRED ? "REQUIRED" : "DISABLED");
        url.append("&allowPublicKeyRetrieval=true&characterEncoding=utf8");
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
        return new CapabilityDescriptor(true, false, "`", "LIMIT_OFFSET", probeSql());
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public String paginate(String sql, int offset, int limit) {
        return SqlDialect.stripTrailingSemicolon(sql) + " LIMIT " + limit + " OFFSET " + offset;
    }
}
