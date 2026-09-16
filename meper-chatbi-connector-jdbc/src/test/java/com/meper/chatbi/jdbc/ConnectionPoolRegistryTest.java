package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.model.DataSourceProfile;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接池注册表测试（Testcontainers MySQL 8.4）：
 * 同版本复用、新版本新池、轮换逐出旧池。
 */
@Testcontainers(disabledWithoutDocker = true)
class ConnectionPoolRegistryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private final ConnectionPoolRegistry registry = new ConnectionPoolRegistry(2);

    @AfterAll
    static void cleanupGlobal() {
        // 每测试方法独立 registry，这里无需全局清理
    }

    private DataSourceProfile profile(long credentialVersion) {
        return new DataSourceProfile(7, "it", com.meper.chatbi.spi.DatabaseType.MYSQL,
                MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getDatabaseName(), MYSQL.getUsername(),
                com.meper.chatbi.spi.SslMode.DISABLED, java.util.Map.of(), credentialVersion);
    }

    @Test
    void sameCredentialVersionReusesPool() {
        DataSource p1 = registry.poolFor(profile(1), MYSQL.getPassword().toCharArray());
        DataSource p2 = registry.poolFor(profile(1), MYSQL.getPassword().toCharArray());
        assertSame(p1, p2);
        assertEquals(1, registry.size());
    }

    @Test
    void newCredentialVersionCreatesNewPoolAndEvictClosesOld() {
        HikariDataSource v1 = (HikariDataSource) registry.poolFor(profile(1), MYSQL.getPassword().toCharArray());
        HikariDataSource v2 = (HikariDataSource) registry.poolFor(profile(2), MYSQL.getPassword().toCharArray());
        assertNotSame(v1, v2);
        assertEquals(2, registry.size());

        registry.evict(7);
        assertTrue(v1.isClosed(), "轮换后旧版本池必须关闭");
        assertTrue(v2.isClosed());
        assertEquals(0, registry.size());
    }

    @Test
    void poolCanServeRealConnection() throws Exception {
        DataSource pool = registry.poolFor(profile(3), MYSQL.getPassword().toCharArray());
        try (var conn = pool.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
