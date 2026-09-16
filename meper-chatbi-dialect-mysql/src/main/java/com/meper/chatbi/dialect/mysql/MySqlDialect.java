package com.meper.chatbi.dialect.mysql;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL 方言（目标版本 5.7 / 8.4.x）。
 *
 * <p>MySQL 的 schema 与 database 是同一层级，capabilities 只声明 database。
 * TLS 用驱动原生 {@code sslMode} 参数；非 TLS 连接需 {@code allowPublicKeyRetrieval=true}
 * 才能完成 caching_sha2_password 认证（该参数允许客户端向服务端请求 RSA 公钥，
 * 在不可信网络上有中间人风险，故仅在 sslMode != REQUIRED 时附带，可信网络/内网部署为主场景）。
 */
public class MySqlDialect implements SqlDialect {

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
        url.append("?sslMode=").append(sslModeParam(spec.sslMode()));
        if (spec.sslMode() != SslMode.REQUIRED) {
            url.append("&allowPublicKeyRetrieval=true");
        }
        url.append("&characterEncoding=utf8");
        for (Map.Entry<String, String> e : spec.extendInfo().entrySet()) {
            url.append('&').append(encode(e.getKey())).append('=').append(encode(e.getValue()));
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

    private String sslModeParam(SslMode mode) {
        return switch (mode) {
            case PREFERRED -> "PREFERRED";
            case REQUIRED -> "REQUIRED";
            case DISABLED -> "DISABLED";
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
