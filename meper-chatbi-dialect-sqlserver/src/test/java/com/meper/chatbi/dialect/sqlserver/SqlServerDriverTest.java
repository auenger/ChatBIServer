package com.meper.chatbi.dialect.sqlserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL Server 内置驱动 + Docker 多版本兼容测试。
 *
 * <p>目标版本矩阵：SQL Server 2019 与 2022（2026-09-15 决策）。无 Docker 时自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class SqlServerDriverTest {

    /** 被测版本矩阵；如需覆盖，可通过 -Dmeper.test.sqlserver.images=a,b 替换。 */
    static Stream<String> images() {
        return Stream.of(System.getProperty("meper.test.sqlserver.images",
                        "mcr.microsoft.com/mssql/server:2019-latest,mcr.microsoft.com/mssql/server:2022-latest")
                        .split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    @Test
    void builtinDriverIsRegistered() throws Exception {
        Class<?> driverClass = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        assertTrue(DriverManager.drivers().anyMatch(driverClass::isInstance),
                "mssql-jdbc 应随模块内置并通过 SPI 自动注册");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("images")
    void canConnectAndSelect(String image) throws Exception {
        try (MSSQLServerContainer<?> mssql =
                     new MSSQLServerContainer<>(DockerImageName.parse(image)).acceptLicense()) {
            mssql.start();
            try (Connection conn = DriverManager.getConnection(
                    mssql.getJdbcUrl(), mssql.getUsername(), mssql.getPassword());
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
