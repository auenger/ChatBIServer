package com.meper.chatbi.domain;

import com.meper.chatbi.jdbc.ConnectionPoolRegistry;
import com.meper.chatbi.jdbc.JdbcConnectionTester;
import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SslMode;
import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;
import com.meper.chatbi.spi.model.CredentialRef;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.storage.repository.AuditRepository;
import com.meper.chatbi.storage.repository.CredentialRepository;
import com.meper.chatbi.storage.repository.DataSourceProfileRepository;
import com.meper.chatbi.storage.security.AesGcmCipherService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 数据源管理领域服务：登记、连通测试、能力查看、凭据轮换、删除。
 *
 * <p>安全约束：密码只在内存解密（char[]），API 与日志不回显；
 * 轮换生成新凭据版本并立即逐出旧连接池；所有操作写审计。
 */
@Service
public class DataSourceService {

    /** 登记/临时测试命令（密码为明文入参，仅在服务内即时消费）。 */
    public record DatasourceCommand(
            String name,
            com.meper.chatbi.spi.DatabaseType type,
            String host,
            int port,
            String databaseName,
            String username,
            String password,
            SslMode sslMode,
            Map<String, String> extendInfo) {
    }

    /** 连通测试结果（preConnect 模式：临时连接、不入池、用完即关）。 */
    public record TestResult(boolean success, String message, long latencyMs) {
    }

    private final DataSourceProfileRepository profiles;
    private final CredentialRepository credentials;
    private final AuditRepository audits;
    private final AesGcmCipherService cipher;
    private final ConnectionPoolRegistry poolRegistry;

    public DataSourceService(DataSourceProfileRepository profiles,
                             CredentialRepository credentials,
                             AuditRepository audits,
                             AesGcmCipherService cipher,
                             ConnectionPoolRegistry poolRegistry) {
        this.profiles = profiles;
        this.credentials = credentials;
        this.audits = audits;
        this.cipher = cipher;
        this.poolRegistry = poolRegistry;
    }

    public DataSourceProfile register(String principal, DatasourceCommand command) {
        if (profiles.existsByName(command.name())) {
            throw new IllegalArgumentException("数据源名称已存在: " + command.name());
        }
        long version = 1;
        var secret = cipher.encrypt(command.password.toCharArray());
        long id = profiles.insert(new DataSourceProfile(0, command.name(), command.type(),
                command.host(), command.port(), command.databaseName(), command.username(),
                command.sslMode(), command.extendInfo(), version), principal);
        credentials.insert(id, version, secret);
        audits.insert("default", principal, "DATASOURCE_CREATE", "DATASOURCE", String.valueOf(id),
                Map.of("name", command.name(), "type", command.type().code()));
        return profiles.findById(id).orElseThrow();
    }

    public List<DataSourceProfile> list() {
        return profiles.findAll();
    }

    public DataSourceProfile get(long id) {
        return profiles.findById(id)
                .orElseThrow(() -> new NoSuchElementException("数据源不存在: " + id));
    }

    /** 用已存凭据测试连通性。 */
    public TestResult test(String principal, long id) {
        DataSourceProfile profile = get(id);
        char[] password = activePassword(profile);
        var result = JdbcConnectionTester.test(ConnectionSpec.of(profile, password), 8);
        audits.insert("default", principal, "DATASOURCE_TEST", "DATASOURCE", String.valueOf(id),
                Map.of("success", result.success(), "latencyMs", result.latencyMs()));
        return new TestResult(result.success(), result.message(), result.latencyMs());
    }

    /** 用临时配置测试连通性（不落库；登记前预检场景）。 */
    public TestResult testTemporary(String principal, DatasourceCommand command) {
        var spec = new ConnectionSpec(command.type(), command.host(), command.port(),
                command.databaseName(), command.username(), command.password.toCharArray(),
                command.sslMode(), command.extendInfo());
        var result = JdbcConnectionTester.test(spec, 8);
        audits.insert("default", principal, "DATASOURCE_TEST_TEMP", "DATASOURCE", null,
                Map.of("success", result.success(), "type", command.type().code()));
        return new TestResult(result.success(), result.message(), result.latencyMs());
    }

    public CapabilityDescriptor capabilities(long id) {
        DataSourceProfile profile = get(id);
        return DialectRegistry.get(profile.type()).capabilities();
    }

    /** 凭据轮换：新版本 ACTIVE + 旧版本 RETIRED + 逐出旧池。 */
    public DataSourceProfile rotateCredential(String principal, long id, String newPassword) {
        DataSourceProfile profile = get(id);
        long newVersion = profile.credentialVersion() + 1;
        var secret = cipher.encrypt(newPassword.toCharArray());
        credentials.insert(id, newVersion, secret);
        credentials.markRetiredExcept(id, newVersion);
        profiles.updateCredentialVersion(id, newVersion);
        poolRegistry.evict(id);
        audits.insert("default", principal, "DATASOURCE_ROTATE", "DATASOURCE", String.valueOf(id),
                Map.of("newVersion", newVersion));
        return get(id);
    }

    public void delete(String principal, long id) {
        get(id);
        poolRegistry.evict(id);
        profiles.deleteById(id);
        audits.insert("default", principal, "DATASOURCE_DELETE", "DATASOURCE", String.valueOf(id), Map.of());
    }

    /** 解密当前 ACTIVE 凭据（内存 char[]，用后不持有）。 */
    public char[] activePassword(DataSourceProfile profile) {
        CredentialRepository.ActiveCredential active = credentials.findActive(profile.id())
                .orElseThrow(() -> new IllegalStateException("数据源缺少可用凭据: " + profile.id()));
        return cipher.decrypt(active.secret());
    }

    /** 懒创建/复用连接池（key = datasourceId + credentialVersion）。 */
    public javax.sql.DataSource poolFor(DataSourceProfile profile) {
        return poolRegistry.poolFor(profile, activePassword(profile));
    }

    public ConnectionPoolRegistry poolRegistry() {
        return poolRegistry;
    }

    /** 供工作台执行使用：校验凭据存在性（CredentialRef 语义保留给 P2 策略层）。 */
    public CredentialRef credentialRef(DataSourceProfile profile) {
        return new CredentialRef(profile.id(), profile.credentialVersion(), CredentialRef.Status.ACTIVE);
    }
}
