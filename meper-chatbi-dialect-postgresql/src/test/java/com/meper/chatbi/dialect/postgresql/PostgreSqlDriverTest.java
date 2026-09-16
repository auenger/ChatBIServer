package com.meper.chatbi.dialect.postgresql;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL 内置驱动 + Docker 数据库冒烟测试。
 *
 * <p>无 Docker 时自动跳过。镜像标签即「数据库 × 操作 × 版本」矩阵中的被测版本。
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlDriverTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void builtinDriverIsRegistered() throws Exception {
        Class<?> driverClass = Class.forName("org.postgresql.Driver");
        assertTrue(DriverManager.drivers().anyMatch(driverClass::isInstance),
                "PostgreSQL JDBC 驱动应随模块内置并通过 SPI 自动注册");
    }

    @Test
    void canConnectAndSelect() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
