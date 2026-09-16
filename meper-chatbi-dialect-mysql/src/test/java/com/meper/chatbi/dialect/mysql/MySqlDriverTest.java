package com.meper.chatbi.dialect.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 内置驱动 + Docker 多版本兼容测试。
 *
 * <p>目标版本矩阵：MySQL 5.7 与 8.4.x（2026-09-15 决策）。无 Docker 时自动跳过。
 * 注意：mysql-connector-j 9.x 官方不再声明支持 5.7，本测试同时验证驱动对 5.7 的实际兼容性。
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlDriverTest {

    /** 被测版本矩阵；如需覆盖，可通过 -Dmeper.test.mysql.images=a,b 替换。 */
    static Stream<String> images() {
        return Stream.of(System.getProperty("meper.test.mysql.images", "mysql:5.7,mysql:8.4.9").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    @Test
    void builtinDriverIsRegistered() throws Exception {
        Class<?> driverClass = Class.forName("com.mysql.cj.jdbc.Driver");
        assertTrue(DriverManager.drivers().anyMatch(driverClass::isInstance),
                "mysql-connector-j 应随模块内置并通过 SPI 自动注册");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("images")
    void canConnectAndSelect(String image) throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>(image)) {
            mysql.start();
            try (Connection conn = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertTrue(conn.getMetaData().getDatabaseMajorVersion() >= 5);
            }
        }
    }
}
