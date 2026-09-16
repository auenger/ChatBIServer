package com.meper.chatbi.storage.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.DataSourceProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 数据源配置存取（不含凭据；凭据见 {@link CredentialRepository}）。 */
@Repository
public class DataSourceProfileRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DataSourceProfileRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public long insert(DataSourceProfile profile, String createdBy) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO meper_datasource_profile
                            (name, db_type, host, port, database_name, username, ssl_mode, extend_info, credential_version, created_by)
                        VALUES (:name, :dbType, :host, :port, :databaseName, :username, :sslMode, :extendInfo, :credentialVersion, :createdBy)
                        """)
                .param("name", profile.name())
                .param("dbType", profile.type().code())
                .param("host", profile.host())
                .param("port", profile.port())
                .param("databaseName", profile.databaseName())
                .param("username", profile.username())
                .param("sslMode", profile.sslMode().name())
                .param("extendInfo", toJson(profile.extendInfo()))
                .param("credentialVersion", profile.credentialVersion())
                .param("createdBy", createdBy)
                .update(keys);
        Number idNumber = keys.getKey();
        if (idNumber == null) {
            throw new IllegalStateException("数据源插入未返回主键");
        }
        return idNumber.longValue();
    }

    public Optional<DataSourceProfile> findById(long id) {
        return jdbc.sql("SELECT * FROM meper_datasource_profile WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public List<DataSourceProfile> findAll() {
        return jdbc.sql("SELECT * FROM meper_datasource_profile ORDER BY id")
                .query(this::map)
                .list();
    }

    public boolean existsByName(String name) {
        return jdbc.sql("SELECT COUNT(1) FROM meper_datasource_profile WHERE name = :name")
                .param("name", name)
                .query(Long.class)
                .single() > 0;
    }

    public void updateCredentialVersion(long id, long version) {
        jdbc.sql("UPDATE meper_datasource_profile SET credential_version = :v WHERE id = :id")
                .param("v", version)
                .param("id", id)
                .update();
    }

    public boolean deleteById(long id) {
        return jdbc.sql("DELETE FROM meper_datasource_profile WHERE id = :id")
                .param("id", id)
                .update() > 0;
    }

    private DataSourceProfile map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, String> extendInfo;
        try {
            String json = rs.getString("extend_info");
            extendInfo = json == null ? Map.of()
                    : objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("extend_info 反序列化失败", e);
        }
        return new DataSourceProfile(
                rs.getLong("id"),
                rs.getString("name"),
                databaseType(rs.getString("db_type")),
                rs.getString("host"),
                rs.getInt("port"),
                rs.getString("database_name"),
                rs.getString("username"),
                SslMode.valueOf(rs.getString("ssl_mode")),
                extendInfo,
                rs.getLong("credential_version"));
    }

    private String toJson(Map<String, String> extendInfo) {
        if (extendInfo == null || extendInfo.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extendInfo);
        } catch (Exception e) {
            throw new IllegalStateException("extend_info 序列化失败", e);
        }
    }

    private static DatabaseType databaseType(String code) {
        return Arrays.stream(DatabaseType.values())
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知数据库类型: " + code));
    }
}
