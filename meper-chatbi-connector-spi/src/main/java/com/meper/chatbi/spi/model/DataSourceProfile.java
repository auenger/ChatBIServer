package com.meper.chatbi.spi.model;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;

import java.util.Map;

/**
 * 数据源持久配置。不含任何明文密码 —— 凭据只以 {@link CredentialRef}（版本引用）出现。
 *
 * @param id                数据源 ID
 * @param name              显示名
 * @param type              数据库类型
 * @param host              主机
 * @param port              端口
 * @param databaseName      库名（MySQL 为 database；Oracle 为 service name）
 * @param username          数据库用户
 * @param sslMode           TLS 策略
 * @param extendInfo        扩展连接参数（含未来 SSH 字段预留；键值不敏感信息）
 * @param credentialVersion 当前生效凭据版本
 */
public record DataSourceProfile(
        long id,
        String name,
        DatabaseType type,
        String host,
        int port,
        String databaseName,
        String username,
        SslMode sslMode,
        Map<String, String> extendInfo,
        long credentialVersion) {
}
