package com.meper.chatbi.storage.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.EnforcementState;
import com.meper.chatbi.spi.Purpose;
import com.meper.chatbi.spi.SqlCategory;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.QueryResultData;
import com.meper.chatbi.spi.model.SqlExecutionResult;
import com.meper.chatbi.spi.model.StatementResult;
import com.meper.chatbi.storage.model.ExecutionRecord;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制库仓储集成测试（Testcontainers MySQL + Flyway 真实迁移）。
 */
@Testcontainers(disabledWithoutDocker = true)
class StorageRepositoriesTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static DataSourceProfileRepository profiles;
    static CredentialRepository credentials;
    static ExecutionRepository executions;
    static AuditRepository audits;
    static JdbcClient jdbc;

    @BeforeAll
    static void init() {
        var ds = new org.springframework.jdbc.datasource.SimpleDriverDataSource();
        try {
            ds.setDriverClass((Class<? extends java.sql.Driver>) Class.forName("com.mysql.cj.jdbc.Driver"));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        ds.setUrl(MYSQL.getJdbcUrl());
        ds.setUsername(MYSQL.getUsername());
        ds.setPassword(MYSQL.getPassword());

        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbc = JdbcClient.create(ds);
        ObjectMapper mapper = new ObjectMapper();
        profiles = new DataSourceProfileRepository(jdbc, mapper);
        credentials = new CredentialRepository(jdbc);
        executions = new ExecutionRepository(jdbc);
        audits = new AuditRepository(jdbc, mapper);
    }

    @AfterAll
    static void done() {
    }

    private DataSourceProfile profile(String name, long credentialVersion) {
        return new DataSourceProfile(0, name, DatabaseType.MYSQL, "10.1.1.5", 3307,
                "meperdb", "svc", SslMode.DISABLED, Map.of("connectTimeout", "3000"), credentialVersion);
    }

    @Test
    void profileInsertAndFindRoundtrip() {
        long id = profiles.insert(profile("ds-roundtrip", 1), "admin");
        assertTrue(id > 0);
        DataSourceProfile loaded = profiles.findById(id).orElseThrow();
        assertEquals("ds-roundtrip", loaded.name());
        assertEquals(DatabaseType.MYSQL, loaded.type());
        assertEquals(3307, loaded.port());
        assertEquals("3000", loaded.extendInfo().get("connectTimeout"));
        assertTrue(profiles.existsByName("ds-roundtrip"));
        assertFalse(profiles.existsByName("nope"));
    }

    @Test
    void credentialVersioning() {
        long dsId = profiles.insert(profile("ds-cred", 1), "admin");
        var v1 = new com.meper.chatbi.storage.security.AesGcmCipherService.EncryptedSecret(
                "cipher-1".getBytes(), "iv-1234567890ab".getBytes());
        var v2 = new com.meper.chatbi.storage.security.AesGcmCipherService.EncryptedSecret(
                "cipher-2".getBytes(), "iv-0987654321ba".getBytes());
        credentials.insert(dsId, 1, v1);
        credentials.insert(dsId, 2, v2);
        credentials.markRetiredExcept(dsId, 2);

        var active = credentials.findActive(dsId).orElseThrow();
        assertEquals(2, active.version());
        assertEquals("cipher-2", new String(active.secret().ciphertext()));
    }

    @Test
    void executionPersistAndRead() {
        long dsId = profiles.insert(profile("ds-exec", 1), "admin");
        ExecutionContext ctx = ExecutionContext.of("default", "admin", Purpose.WORKBENCH, dsId,
                EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);
        var ok = new StatementResult(0, "SELECT id FROM t", SqlCategory.SELECT, true,
                new QueryResultData(List.of("id"), List.of(List.of("1"), List.of("2")), false, true),
                null, 12, null);
        var bad = new StatementResult(1, "DELETE FROM nope", SqlCategory.DML, false, null, null, 3,
                "SQLException: table not found");
        var result = new SqlExecutionResult(List.of(ok, bad));

        Instant start = Instant.now().minusSeconds(2);
        long execId = executions.insert(ctx, dsId, "SELECT id FROM t; DELETE FROM nope;", result, start, Instant.now());

        var loaded = executions.findById(execId).orElseThrow();
        assertEquals(ExecutionRecord.Status.PARTIAL, loaded.status());
        assertEquals(2, loaded.statementCount());
        assertEquals(2, loaded.statements().size());
        assertEquals(2, loaded.statements().get(0).rowCount());
        assertTrue(loaded.statements().get(0).truncated());
        assertEquals("SQLException: table not found", loaded.statements().get(1).error());
        assertEquals(EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED, loaded.enforcementState());

        assertTrue(executions.list(dsId, 10, 0).stream().anyMatch(e -> e.id() == execId));
    }

    @Test
    void auditInsert() {
        long before = jdbc.sql("SELECT COUNT(1) FROM meper_audit_log").query(Long.class).single();
        audits.insert("default", "admin", "DATASOURCE_CREATE", "DATASOURCE", "42",
                Map.of("name", "ds-x", "type", "mysql"));
        long after = jdbc.sql("SELECT COUNT(1) FROM meper_audit_log").query(Long.class).single();
        assertEquals(before + 1, after);
    }
}
