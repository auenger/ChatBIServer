package com.meper.chatbi.spi.model;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;

import java.util.Map;

/**
 * 一次性连接参数：由 {@link DataSourceProfile} 与解密后的密码在内存中组装，
 * 不得序列化、不得写日志（密码字段为 char[]，用后由调用方负责不残留语义上的暴露面）。
 */
public record ConnectionSpec(
        DatabaseType type,
        String host,
        int port,
        String databaseName,
        String username,
        char[] password,
        SslMode sslMode,
        Map<String, String> extendInfo) {

    public static ConnectionSpec of(DataSourceProfile profile, char[] password) {
        return new ConnectionSpec(profile.type(), profile.host(), profile.port(), profile.databaseName(),
                profile.username(), password, profile.sslMode(), profile.extendInfo());
    }
}
