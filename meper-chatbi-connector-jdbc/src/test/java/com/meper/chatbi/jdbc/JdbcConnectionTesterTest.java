package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.model.ConnectionSpec;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连通测试（preConnect）测试：成功路径 + 明确失败路径（不做参数改写重试）。
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcConnectionTesterTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private ConnectionSpec spec(String host, int port, String password) {
        return new ConnectionSpec(com.meper.chatbi.spi.DatabaseType.MYSQL,
                host, port, MYSQL.getDatabaseName(), MYSQL.getUsername(),
                password.toCharArray(), com.meper.chatbi.spi.SslMode.DISABLED, java.util.Map.of());
    }

    @Test
    void successAgainstContainer() {
        var result = JdbcConnectionTester.test(
                spec(MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT), MYSQL.getPassword()), 5);
        assertTrue(result.success(), result.message());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void wrongPasswordFailsWithoutRetry() {
        var result = JdbcConnectionTester.test(
                spec(MYSQL.getHost(), MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT), "wrong-password"), 5);
        assertFalse(result.success());
        assertTrue(result.message().contains("Access denied"), "摘要应保留根因驱动错误: " + result.message());
    }

    @Test
    void unreachableHostFails() {
        var result = JdbcConnectionTester.test(spec("127.0.0.1", 1, "x"), 3);
        assertFalse(result.success());
    }
}
