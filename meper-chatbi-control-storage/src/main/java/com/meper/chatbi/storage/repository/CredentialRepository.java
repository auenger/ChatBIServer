package com.meper.chatbi.storage.repository;

import com.meper.chatbi.storage.security.AesGcmCipherService.EncryptedSecret;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 凭据版本存取（密文 + IV；主密钥不落库）。
 * 轮换语义：新版本 ACTIVE，其余版本 RETIRED，见 {@link #markRetiredExcept}。
 */
@Repository
public class CredentialRepository {

    private final JdbcClient jdbc;

    public CredentialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前生效凭据（版本 + 密文）。 */
    public record ActiveCredential(long version, EncryptedSecret secret) {
    }

    public void insert(long datasourceId, long version, EncryptedSecret secret) {
        jdbc.sql("""
                        INSERT INTO meper_credential (datasource_id, version, ciphertext, iv, status)
                        VALUES (:ds, :v, :ct, :iv, 'ACTIVE')
                        """)
                .param("ds", datasourceId)
                .param("v", version)
                .param("ct", secret.ciphertext())
                .param("iv", secret.iv())
                .update();
    }

    public void markRetiredExcept(long datasourceId, long activeVersion) {
        jdbc.sql("""
                        UPDATE meper_credential SET status = 'RETIRED'
                        WHERE datasource_id = :ds AND version <> :v
                        """)
                .param("ds", datasourceId)
                .param("v", activeVersion)
                .update();
    }

    public Optional<ActiveCredential> findActive(long datasourceId) {
        return jdbc.sql("""
                        SELECT version, ciphertext, iv FROM meper_credential
                        WHERE datasource_id = :ds AND status = 'ACTIVE'
                        ORDER BY version DESC LIMIT 1
                        """)
                .param("ds", datasourceId)
                .query((rs, i) -> new ActiveCredential(
                        rs.getLong("version"),
                        new EncryptedSecret(rs.getBytes("ciphertext"), rs.getBytes("iv"))))
                .optional();
    }
}
