package com.meper.chatbi.storage.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.storage.model.ModelProviderRecord;
import com.meper.chatbi.storage.security.AesGcmCipherService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 模型 provider 配置仓储：CRUD + apiKey 密文独立存取。
 * 明文 key 只在 agent 层经 AesGcmCipherService 解密，本仓储不感知明文。
 */
@Repository
public class ModelProviderRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ModelProviderRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Long defaultId() {
        return jdbc.sql("SELECT provider_id FROM meper_model_default WHERE singleton_id=1")
                .query((rs,n)->rs.getObject(1,Long.class)).optional().orElse(null);
    }
    public void lockDefault() {jdbc.sql("SELECT singleton_id FROM meper_model_default WHERE singleton_id=1 FOR UPDATE").query(Integer.class).single();}
    public void setDefault(long id) {
        jdbc.sql("SELECT singleton_id FROM meper_model_default WHERE singleton_id=1 FOR UPDATE").query(Integer.class).single();
        var provider=findById(id).orElseThrow(()->new NoSuchElementException("模型配置不存在"));
        if(!provider.enabled()) throw new IllegalStateException("默认模型必须已启用");
        jdbc.sql("UPDATE meper_model_default SET provider_id=:id WHERE singleton_id=1").param("id",id).update();
    }
    /** 新增配置，返回生成 id。 */
    public long insert(ModelProviderRecord record, AesGcmCipherService.EncryptedSecret apiKey) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO meper_model_provider
                            (name, protocol, base_url, model, api_key_ciphertext, api_key_iv, params, enabled)
                        VALUES (:name, :protocol, :baseUrl, :model, :ct, :iv, :params, :enabled)
                        """)
                .param("name", record.name())
                .param("protocol", record.protocol())
                .param("baseUrl", record.baseUrl())
                .param("model", record.model())
                .param("ct", apiKey.ciphertext())
                .param("iv", apiKey.iv())
                .param("params", toJson(record.params()))
                .param("enabled", record.enabled())
                .update(keys);
        // MySQL 自增键经驱动返回 BigInteger，统一按 Number 取值
        return ((Number) keys.getKeys().values().iterator().next()).longValue();
    }

    /** 更新配置；apiKey 为 null 表示保留原密钥。 */
    public void update(long id, ModelProviderRecord record, AesGcmCipherService.EncryptedSecret apiKey) {
        AesGcmCipherService.EncryptedSecret secret =
                apiKey != null ? apiKey : findApiKey(id).orElseThrow(() -> new NoSuchElementException("模型配置不存在: " + id));
        int updated = jdbc.sql("""
                        UPDATE meper_model_provider
                        SET name = :name, protocol = :protocol, base_url = :baseUrl, model = :model,
                            params = :params, enabled = :enabled, api_key_ciphertext = :ct, api_key_iv = :iv
                        WHERE id = :id
                        """)
                .param("name", record.name())
                .param("protocol", record.protocol())
                .param("baseUrl", record.baseUrl())
                .param("model", record.model())
                .param("params", toJson(record.params()))
                .param("enabled", record.enabled())
                .param("ct", secret.ciphertext())
                .param("iv", secret.iv())
                .param("id", id)
                .update();
        if (updated == 0) {
            throw new NoSuchElementException("模型配置不存在: " + id);
        }
    }

    public void setEnabled(long id, boolean enabled) {
        int updated = jdbc.sql("UPDATE meper_model_provider SET enabled = :enabled WHERE id = :id")
                .param("enabled", enabled).param("id", id).update();
        if (updated == 0) {
            throw new NoSuchElementException("模型配置不存在: " + id);
        }
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM meper_model_provider WHERE id = :id").param("id", id).update();
    }

    public Optional<ModelProviderRecord> findById(long id) {
        return jdbc.sql("""
                        SELECT id, name, protocol, base_url, model, params, enabled, created_at
                        FROM meper_model_provider WHERE id = :id
                        """)
                .param("id", id)
                .query(ModelProviderRepository::mapRow)
                .optional();
    }

    public List<ModelProviderRecord> list() {
        return jdbc.sql("""
                        SELECT id, name, protocol, base_url, model, params, enabled, created_at
                        FROM meper_model_provider ORDER BY id
                        """)
                .query(ModelProviderRepository::mapRow)
                .list();
    }

    /** 密钥密文（连通测试 / 后续模型调用时由 agent 层解密）。 */
    public Optional<AesGcmCipherService.EncryptedSecret> findApiKey(long id) {
        return jdbc.sql("SELECT api_key_ciphertext, api_key_iv FROM meper_model_provider WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new AesGcmCipherService.EncryptedSecret(
                        rs.getBytes("api_key_ciphertext"), rs.getBytes("api_key_iv")))
                .optional();
    }

    private static ModelProviderRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ModelProviderRecord(rs.getLong("id"), rs.getString("name"),
                rs.getString("protocol"), rs.getString("base_url"), rs.getString("model"),
                fromJson(rs.getString("params")), rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
