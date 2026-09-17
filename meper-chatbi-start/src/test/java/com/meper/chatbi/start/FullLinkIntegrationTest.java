package com.meper.chatbi.start;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全链路集成测试（双 Testcontainers：控制库 + 业务库，均为 mysql:8.4）：
 * 登录 → 数据源登记 → 连通测试 → 能力查看 → preview → execute → 执行历史 →
 * 凭据轮换（旧密码失效、新密码生效）→ maxRows 截断 → 删除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullLinkIntegrationTest {

    /** base64("0123456789abcdef0123456789abcdef")，32 字节测试主密钥。 */
    private static final String MASTER_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Container
    static final MySQLContainer<?> CONTROL = new MySQLContainer<>("mysql:8.4");

    @Container
    static final MySQLContainer<?> BIZ = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTROL::getJdbcUrl);
        registry.add("spring.datasource.username", CONTROL::getUsername);
        registry.add("spring.datasource.password", CONTROL::getPassword);
        registry.add("meper.security.master-key", () -> MASTER_KEY);
        registry.add("meper.bootstrap-admin.username", () -> "admin");
        registry.add("meper.bootstrap-admin.password", () -> "test-admin-pw");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper json;

    private String baseUrl;
    private String token;

    private void init(int localPort) {
        baseUrl = "http://localhost:" + localPort;
        if (token == null) {
            token = login("admin", "test-admin-pw");
        }
    }

    @Autowired
    void capturePort(@org.springframework.beans.factory.annotation.Value("${local.server.port}") int port) {
        init(port);
    }

    // ---------- HTTP helpers ----------

    private String login(String username, String password) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", username, "password", password));
        assertEquals(200, response.getStatusCode().value());
        return read(response).get("token").asText();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        try {
            String payload = body == null ? null : json.writeValueAsString(body);
            return rest.exchange(baseUrl + path, method, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode createDatasource(String name) {
        var response = exchange(HttpMethod.POST, "/api/datasources", token, Map.of(
                "name", name, "type", "MYSQL",
                "host", BIZ.getHost(), "port", BIZ.getMappedPort(MySQLContainer.MYSQL_PORT),
                "databaseName", BIZ.getDatabaseName(), "username", BIZ.getUsername(),
                "password", BIZ.getPassword(), "sslMode", "DISABLED"));
        assertEquals(200, response.getStatusCode().value());
        return read(response);
    }

    // ---------- 测试用例 ----------

    @Test
    @Order(1)
    void pingAndAuthBoundary() {
        var ping = exchange(HttpMethod.GET, "/api/ping", null, null);
        assertEquals(200, ping.getStatusCode().value());

        var noToken = exchange(HttpMethod.GET, "/api/datasources", null, null);
        assertEquals(401, noToken.getStatusCode().value(), "未登录必须 401");

        var wrongPassword = exchange(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", "admin", "password", "bad"));
        assertEquals(401, wrongPassword.getStatusCode().value());
    }

    @Test
    @Order(2)
    void datasourceLifecycleAndWorkbench() {
        JsonNode profile = createDatasource("it-link");

        // 能力查看
        var caps = exchange(HttpMethod.GET, "/api/datasources/" + profile.get("id").asLong() + "/capabilities",
                token, null);
        assertEquals(200, caps.getStatusCode().value());
        assertEquals("LIMIT_OFFSET", read(caps).get("paginationStyle").asText());

        // 存储凭据连通测试
        var test = exchange(HttpMethod.POST, "/api/datasources/" + profile.get("id").asLong() + "/test",
                token, null);
        assertEquals(200, test.getStatusCode().value());
        assertTrue(read(test).get("success").asBoolean(), read(test).get("message").asText());

        // preview：多语句分类
        String script = """
                CREATE TABLE meper_wt (id INT PRIMARY KEY, name VARCHAR(100));
                INSERT INTO meper_wt VALUES (1, 'a'), (2, 'b');
                SELECT id, name FROM meper_wt ORDER BY id
                """;
        var preview = exchange(HttpMethod.POST, "/api/workbench/preview", token,
                Map.of("datasourceId", profile.get("id").asLong(), "sql", script));
        assertEquals(200, preview.getStatusCode().value());
        JsonNode previewBody = read(preview);
        assertEquals(3, previewBody.get("statements").size());
        assertEquals("BOOTSTRAP_ADMIN_UNRESTRICTED", previewBody.get("enforcement").asText());

        // format：按数据源方言排版，不连接业务库执行 SQL
        var format = exchange(HttpMethod.POST, "/api/workbench/format", token,
                Map.of("datasourceId", profile.get("id").asLong(),
                        "sql", "select id,name from meper_wt where id=1 order by name"));
        assertEquals(200, format.getStatusCode().value());
        assertTrue(read(format).get("sql").asText().contains("ORDER BY name"));

        // execute
        var execute = exchange(HttpMethod.POST, "/api/workbench/execute", token,
                Map.of("datasourceId", profile.get("id").asLong(), "sql", script));
        assertEquals(200, execute.getStatusCode().value());
        JsonNode execBody = read(execute);
        long executionId = execBody.get("executionId").asLong();
        assertTrue(executionId > 0);
        assertEquals("SUCCESS", execBody.get("status").asText());
        assertEquals(3, execBody.get("statements").size());
        assertEquals(2, execBody.get("statements").get(2).get("resultData").get("rows").size());
        assertFalse(execBody.get("statements").get(2).get("resultData").get("rowsTruncated").asBoolean());

        // 执行历史
        var history = exchange(HttpMethod.GET, "/api/workbench/executions/" + executionId, token, null);
        assertEquals(200, history.getStatusCode().value());
        assertEquals("SUCCESS", read(history).get("status").asText());
        assertEquals(3, read(history).get("statements").size());

        var list = exchange(HttpMethod.GET, "/api/workbench/executions?datasourceId="
                + profile.get("id").asLong(), token, null);
        assertEquals(200, list.getStatusCode().value());
        assertTrue(read(list).size() >= 1);
    }

    @Test
    @Order(3)
    void maxRowsTruncation() {
        JsonNode profile = createDatasource("it-truncate");
        long dsId = profile.get("id").asLong();
        exchange(HttpMethod.POST, "/api/workbench/execute", token, Map.of(
                "datasourceId", dsId,
                "sql", "CREATE TABLE meper_tr (id INT); INSERT INTO meper_tr VALUES (1),(2),(3)"));
        var execute = exchange(HttpMethod.POST, "/api/workbench/execute", token, Map.of(
                "datasourceId", dsId,
                "sql", "SELECT id FROM meper_tr ORDER BY id", "maxRows", 2));
        assertEquals(200, execute.getStatusCode().value());
        JsonNode data = read(execute).get("statements").get(0).get("resultData");
        assertEquals(2, data.get("rows").size());
        assertTrue(data.get("rowsTruncated").asBoolean());
    }

    @Test
    @Order(6)
    void credentialRotationInvalidatesOldPassword() throws Exception {
        JsonNode profile = createDatasource("it-rotate");
        long dsId = profile.get("id").asLong();

        // 真实修改业务库口令（模拟轮换场景）
        try (var conn = DriverManager.getConnection(BIZ.getJdbcUrl(), BIZ.getUsername(), BIZ.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("ALTER USER CURRENT_USER IDENTIFIED BY 'rotated-Pw#2026'");
        }

        // 轮换前：新口令尚未在应用登记 —— 用旧口令的临时测试应失败？不，旧口令已失效，临时测试用旧口令失败
        var oldTest = exchange(HttpMethod.POST, "/api/datasources/test", token, Map.of(
                "type", "MYSQL", "host", BIZ.getHost(), "port", BIZ.getMappedPort(MySQLContainer.MYSQL_PORT),
                "databaseName", BIZ.getDatabaseName(), "username", BIZ.getUsername(),
                "password", BIZ.getPassword(), "sslMode", "DISABLED"));
        assertEquals(200, oldTest.getStatusCode().value());
        assertFalse(read(oldTest).get("success").asBoolean(), "旧口令在业务库已失效，测试必须失败");

        // 应用内轮换到新口令
        var rotate = exchange(HttpMethod.POST, "/api/datasources/" + dsId + "/rotate-credential", token,
                Map.of("password", "rotated-Pw#2026"));
        assertEquals(200, rotate.getStatusCode().value());
        assertNotEquals(profile.get("credentialVersion").asLong(),
                read(rotate).get("credentialVersion").asLong(), "轮换必须产生新版本");

        // 存储凭据连通测试成功；工作台可继续执行（新池）
        var test = exchange(HttpMethod.POST, "/api/datasources/" + dsId + "/test", token, null);
        assertEquals(200, test.getStatusCode().value());
        assertTrue(read(test).get("success").asBoolean(), read(test).get("message").asText());

        var execute = exchange(HttpMethod.POST, "/api/workbench/execute", token,
                Map.of("datasourceId", dsId, "sql", "SELECT 1"));
        assertEquals(200, execute.getStatusCode().value());
        assertEquals("SUCCESS", read(execute).get("status").asText());
    }

    @Test
    @Order(5)
    void deleteDatasource() {
        JsonNode profile = createDatasource("it-delete");
        long dsId = profile.get("id").asLong();
        var del = exchange(HttpMethod.DELETE, "/api/datasources/" + dsId, token, null);
        assertEquals(200, del.getStatusCode().value());
        var gone = exchange(HttpMethod.GET, "/api/datasources/" + dsId, token, null);
        assertEquals(404, gone.getStatusCode().value());
    }

    @Test
    @Order(4)
    void metadataTreeTableDetailAndPagedData() {
        JsonNode profile = createDatasource("it-metadata");
        long dsId = profile.get("id").asLong();
        String base = "/api/datasources/" + dsId + "/metadata";
        String database = BIZ.getDatabaseName();

        exchange(HttpMethod.POST, "/api/workbench/execute", token, Map.of(
                "datasourceId", dsId,
                "sql", "CREATE TABLE meta_t (id INT PRIMARY KEY, name VARCHAR(50)); "
                        + "INSERT INTO meta_t VALUES (1,'a'),(2,'b'),(3,'c')"));

        // 命名空间（MySQL=数据库列表）：包含业务库，排除系统库
        var ns = exchange(HttpMethod.GET, base + "/namespaces", token, null);
        assertEquals(200, ns.getStatusCode().value());
        JsonNode names = read(ns);
        boolean hasBiz = false;
        for (JsonNode n : names) {
            assertFalse(List.of("information_schema", "mysql", "performance_schema", "sys")
                    .contains(n.asText()), "系统库必须被过滤");
            hasBiz = hasBiz || n.asText().equals(database);
        }
        assertTrue(hasBiz, "业务库应出现在命名空间列表");

        // 表清单
        var tables = exchange(HttpMethod.GET, base + "/tables?namespace=" + database, token, null);
        assertEquals(200, tables.getStatusCode().value());
        assertTrue(read(tables).toString().contains("meta_t"));

        // 表结构：列 + 主键标记
        var detail = exchange(HttpMethod.GET, base + "/table?namespace=" + database + "&table=meta_t",
                token, null);
        assertEquals(200, detail.getStatusCode().value());
        JsonNode detailBody = read(detail);
        assertEquals("id", detailBody.get("columns").get(0).get("name").asText());
        assertTrue(detailBody.get("columns").get(0).get("primaryKey").asBoolean());

        // 表数据分页：total 精确，页大小生效
        var data = exchange(HttpMethod.GET, base + "/data?namespace=" + database
                + "&table=meta_t&page=2&size=2", token, null);
        assertEquals(200, data.getStatusCode().value());
        JsonNode dataBody = read(data);
        assertEquals(3, dataBody.get("total").asLong());
        assertEquals(1, dataBody.get("data").get("rows").size(), "3 行分 2 页，第 2 页应为 1 行");
    }
}
