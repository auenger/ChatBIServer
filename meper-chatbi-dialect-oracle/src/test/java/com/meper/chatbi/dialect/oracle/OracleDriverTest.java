package com.meper.chatbi.dialect.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.oracle.OracleContainer;
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
 * Oracle 内置驱动 + Docker 多版本兼容测试。
 *
 * <p>目标版本矩阵：Oracle 19c 与 23ai（2026-09-15 决策）。无 Docker 时自动跳过。
 *
 * <p>19c 没有免费公开镜像：需要 <code>docker login container-registry.oracle.com</code> 后
 * 拉取 <code>container-registry.oracle.com/database/standard:19.3.0</code>（约 9GB，首次启动 10 分钟以上），
 * 再以 <code>-Dmeper.test.oracle.images=container-registry.oracle.com/database/standard:19.3.0</code> 运行。
 * ojdbc17 官方认证服务端 19c 及以上。
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleDriverTest {

    /** 默认测免费镜像；19c 及其他版本按类注释覆盖 -Dmeper.test.oracle.images。 */
    static Stream<String> images() {
        return Stream.of(System.getProperty("meper.test.oracle.images", "gvenzl/oracle-free:23-slim").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    @Test
    void builtinDriverIsRegistered() throws Exception {
        Class<?> driverClass = Class.forName("oracle.jdbc.OracleDriver");
        assertTrue(DriverManager.drivers().anyMatch(driverClass::isInstance),
                "ojdbc17 应随模块内置并通过 SPI 自动注册");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("images")
    void canConnectAndSelect(String image) throws Exception {
        try (OracleContainer oracle = new OracleContainer(DockerImageName.parse(image))) {
            oracle.start();
            try (Connection conn = DriverManager.getConnection(
                    oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1 FROM DUAL")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
