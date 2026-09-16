package com.meper.chatbi.dialect.oracle;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

import java.util.Map;

/**
 * Oracle 方言（目标版本 19c / 23ai）。
 *
 * <p>databaseName 字段承载 service name（thin 服务名连接格式）。
 * 阶段 1 仅支持非 TLS thin 连接；REQUIRED（TCPS）显式报错而非静默降级（实施方案 §4 约束）。
 * 分页用 12c+ 的 OFFSET/FETCH 语法（19c 与 23ai 均支持）。
 */
public class OracleDialect implements SqlDialect {

    @Override
    public DatabaseType type() {
        return DatabaseType.ORACLE;
    }

    @Override
    public String buildJdbcUrl(ConnectionSpec spec) {
        if (spec.sslMode() == SslMode.REQUIRED) {
            throw new UnsupportedOperationException("Oracle TCPS(TLS) 连接阶段 1 不支持，请改用扩展参数配置或调整 sslMode");
        }
        StringBuilder url = new StringBuilder("jdbc:oracle:thin:@//")
                .append(spec.host()).append(':').append(spec.port());
        if (spec.databaseName() != null && !spec.databaseName().isBlank()) {
            url.append('/').append(spec.databaseName());
        }
        for (Map.Entry<String, String> e : spec.extendInfo().entrySet()) {
            url.append(e.getKey()).append('=').append(e.getValue()).append('&');
        }
        // 尾部多余的 '&' 对 thin 解析无害，但保持干净
        int len = url.length();
        if (len > 0 && url.charAt(len - 1) == '&') {
            url.setLength(len - 1);
        }
        return url.toString();
    }

    @Override
    public String probeSql() {
        return "SELECT 1 FROM DUAL";
    }

    @Override
    public CapabilityDescriptor capabilities() {
        return new CapabilityDescriptor(false, true, "\"", "OFFSET_FETCH", probeSql());
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String paginate(String sql, int offset, int limit) {
        return SqlDialect.stripTrailingSemicolon(sql)
                + " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
