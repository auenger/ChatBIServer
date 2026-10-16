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

    /** 数据集跨类型矩阵：mysql:8.4 × postgres:16-alpine。 */
    @Container
    static final org.testcontainers.containers.PostgreSQLContainer<?> FINANCE =
            new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("wiki_finance");

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

    @Autowired com.meper.chatbi.domain.DatasetService datasets;
    @Autowired com.meper.chatbi.domain.AnalysisService analysis;
    @Autowired com.meper.chatbi.storage.repository.DatasetRepository scopeRepository;
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

    /** MySQL 8.4 实库验证：业务结构不被 Wiki 编辑改变，草稿/发布隔离、引用与失效闭环。 */
    @Test
    @Order(5)
    void dataWikiLifecycleAndSchemaDrift() {
        long ds=createDatasource("it-wiki").get("id").asLong();
        String base="/api/datasources/"+ds+"/wiki";
        String ns=BIZ.getDatabaseName();
        var setup=exchange(HttpMethod.POST,"/api/workbench/execute",token,Map.of("datasourceId",ds,
                "sql","CREATE TABLE wiki_orders (id INT PRIMARY KEY, paid_amount BIGINT COMMENT 'original amount') COMMENT='original orders'; INSERT INTO wiki_orders VALUES(1,200)"));
        assertEquals("SUCCESS",read(setup).get("status").asText());
        assertEquals(401,exchange(HttpMethod.GET,base+"/pages",null,null).getStatusCode().value());
        var synced=exchange(HttpMethod.POST,base+"/sync",token,Map.of("namespace",ns,"table","wiki_orders"));
        assertEquals(200,synced.getStatusCode().value(),synced.getBody());
        assertEquals(1,read(synced).get("added").asInt());
        JsonNode page=null;for(var candidate:read(exchange(HttpMethod.GET,base+"/pages",token,null)))if(candidate.get("type").asText().equals("TABLE"))page=candidate;
        assertNotNull(page);
        long id=page.get("id").asLong();long obj=page.get("objectId").asLong();
        String pageUrl=base+"/pages/"+id;
        var command=Map.of("expectedVersion",page.get("draftVersion").asLong(),"title","订单业务说明",
                "content","测试订单不计收入", "semantics",Map.of("grain","一行一个订单"),
                "columns",Map.of("paid_amount",Map.of("businessName","实付金额","description","实际支付金额","unit","分")),
                "bindings",List.of());
        var saved=exchange(HttpMethod.PUT,pageUrl,token,command);
        assertEquals(200,saved.getStatusCode().value(),saved.getBody());
        assertEquals(0,read(exchange(HttpMethod.GET,base+"/search?query="+java.net.URLEncoder.encode("实际支付金额",java.nio.charset.StandardCharsets.UTF_8),token,null)).get("hits").size(),"草稿不得参与搜索");
        assertEquals(409,exchange(HttpMethod.PUT,pageUrl,token,command).getStatusCode().value(),"旧编辑版本拒绝覆盖");
        long version=read(saved).get("page").get("draftVersion").asLong();
        var published=exchange(HttpMethod.POST,pageUrl+"/publish",token,Map.of("expectedVersion",version));
        assertEquals(200,published.getStatusCode().value(),published.getBody());
        assertEquals(1,read(published).get("page").get("publishedRevision").asInt());
        var search=read(exchange(HttpMethod.GET,base+"/search?query=paid_amount",token,null));
        assertTrue(search.get("hits").size()>0);
        String path=null;for(var hit:search.get("hits"))if(hit.get("type").asText().equals("TABLE"))path=hit.get("path").asText();assertNotNull(path);
        var readPage=read(exchange(HttpMethod.GET,base+"/read?path="+path+"&revision=1",token,null));
        assertTrue(readPage.get("content").asText().contains("单位：分"));
        // 再次同步无结构变化，不覆盖人工说明、不制造复核状态。
        assertEquals(0,read(exchange(HttpMethod.POST,base+"/sync",token,Map.of("namespace",ns,"table","wiki_orders"))).get("changed").asInt());
        var retained=read(exchange(HttpMethod.GET,pageUrl,token,null));
        assertEquals("分",retained.get("object").get("columns").get(1).get("semantics").get("unit").asText());
        assertFalse(retained.get("page").get("needsReview").asBoolean());
        // 文档显式绑定字段；跨数据源引用被拒绝。
        var created=exchange(HttpMethod.POST,base+"/pages",token,Map.of("type","METRIC","title","净收入","content","收入按支付时间统计",
                "bindings",List.of(Map.of("objectId",obj,"column","paid_amount"))));
        assertEquals(200,created.getStatusCode().value(),created.getBody());
        long metric=read(created).get("page").get("id").asLong();
        assertEquals(200,exchange(HttpMethod.POST,base+"/pages/"+metric+"/publish",token,Map.of("expectedVersion",1)).getStatusCode().value());
        long other=createDatasource("it-wiki-other").get("id").asLong();
        assertEquals(404,exchange(HttpMethod.GET,"/api/datasources/"+other+"/wiki/pages/"+id,token,null).getStatusCode().value());
        assertEquals(400,exchange(HttpMethod.POST,"/api/datasources/"+other+"/wiki/pages",token,Map.of("type","RULE","title","bad","content","bad","bindings",List.of(Map.of("objectId",obj,"column","")))).getStatusCode().value());
        // Wiki 编辑未修改原表备注/列备注或业务数据。
        var detail=read(exchange(HttpMethod.GET,"/api/datasources/"+ds+"/metadata/table?namespace="+ns+"&table=wiki_orders",token,null));
        assertEquals("original orders",detail.get("remarks").asText());
        assertEquals("original amount",detail.get("columns").get(1).get("remarks").asText());
        var data=read(exchange(HttpMethod.POST,"/api/workbench/execute",token,Map.of("datasourceId",ds,"sql","SELECT paid_amount FROM wiki_orders")));
        assertEquals("200",data.get("statements").get(0).get("resultData").get("rows").get(0).get(0).asText());
        // 失败采集不删除旧镜像，任务可追踪。
        assertEquals(400,exchange(HttpMethod.POST,base+"/sync",token,Map.of("namespace","nonexistent_wiki_namespace")).getStatusCode().value());
        assertEquals("FAILED",read(exchange(HttpMethod.GET,base+"/sync-runs",token,null)).get(0).get("status").asText());
        assertEquals(1,read(exchange(HttpMethod.GET,base+"/objects",token,null)).size());
        // 真实结构变化令表说明与关联指标同时退出检索，历史版本保持不变。
        exchange(HttpMethod.POST,"/api/workbench/execute",token,Map.of("datasourceId",ds,"sql","ALTER TABLE wiki_orders MODIFY paid_amount DECIMAL(18,2)"));
        assertEquals(1,read(exchange(HttpMethod.POST,base+"/sync",token,Map.of("namespace",ns,"table","wiki_orders"))).get("changed").asInt());
        for(var hit:read(exchange(HttpMethod.GET,base+"/search",token,null)).get("hits"))assertEquals("STRUCTURE",hit.get("type").asText());
        assertEquals(404,exchange(HttpMethod.GET,base+"/read?path="+path,token,null).getStatusCode().value());
        var history=read(exchange(HttpMethod.GET,pageUrl+"/revisions",token,null));
        assertTrue(history.get(0).get("content").asText().contains("单位：分"));
        var current=read(exchange(HttpMethod.GET,pageUrl,token,null));
        assertTrue(current.get("page").get("needsReview").asBoolean());
        assertEquals(200,exchange(HttpMethod.POST,pageUrl+"/publish",token,Map.of("expectedVersion",current.get("page").get("draftVersion").asLong())).getStatusCode().value());
        assertEquals(409,exchange(HttpMethod.GET,base+"/read?path="+path+"&revision=1",token,null).getStatusCode().value(),"过期版本不应再提供 Agent");
        var active=read(exchange(HttpMethod.GET,pageUrl,token,null));
        assertEquals(2,active.get("page").get("publishedRevision").asInt());
        assertEquals(200,exchange(HttpMethod.POST,pageUrl+"/restore",token,Map.of("revision",1,"expectedVersion",active.get("page").get("draftVersion").asLong())).getStatusCode().value());
        assertEquals(200,exchange(HttpMethod.POST,pageUrl+"/disable",token,Map.of("disabled",true)).getStatusCode().value());
        for(var hit:read(exchange(HttpMethod.GET,base+"/search",token,null)).get("hits"))assertEquals("STRUCTURE",hit.get("type").asText());
    }

    @Test
    @Order(5)
    void datasetCrossDatabaseAndDefaultModelKnowledgeEditing() throws Exception {
        long sales=createDatasource("it-dataset-sales").get("id").asLong();
        exchange(HttpMethod.POST,"/api/workbench/execute",token,Map.of("datasourceId",sales,"sql","CREATE TABLE dataset_orders(id INT PRIMARY KEY, channel VARCHAR(40), paid_amount BIGINT); INSERT INTO dataset_orders VALUES(1,'web',12000),(2,'store',8000)"));
        try(var connection=DriverManager.getConnection(FINANCE.getJdbcUrl(),FINANCE.getUsername(),FINANCE.getPassword());var statement=connection.createStatement()){
            statement.execute("CREATE TABLE refunds(order_id INT,refund_amount BIGINT); INSERT INTO refunds VALUES(1,2000); CREATE TABLE hidden_finance(secret INT)");
        }
        var financeResponse=exchange(HttpMethod.POST,"/api/datasources",token,Map.of("name","it-dataset-finance","type","POSTGRESQL","host",FINANCE.getHost(),"port",FINANCE.getMappedPort(5432),"databaseName",FINANCE.getDatabaseName(),"username",FINANCE.getUsername(),"password",FINANCE.getPassword(),"sslMode","DISABLED"));
        assertEquals(200,financeResponse.getStatusCode().value(),financeResponse.getBody());long finance=read(financeResponse).get("id").asLong();
        String salesRoot="/api/datasources/"+sales+"/wiki";String financeRoot="/api/datasources/"+finance+"/wiki";
        assertEquals(200,exchange(HttpMethod.POST,salesRoot+"/sync",token,Map.of("namespace",BIZ.getDatabaseName(),"table","dataset_orders")).getStatusCode().value());
        assertEquals(200,exchange(HttpMethod.POST,financeRoot+"/sync",token,Map.of("namespace","public")).getStatusCode().value());
        assertTrue(read(exchange(HttpMethod.GET,salesRoot+"/search",token,null)).get("hits").size()>0,"结构无需逐表确认即可检索");
        var body=Map.of("name","销售经营数据集","description","跨MySQL与PostgreSQL","enabled",true,"expectedRevision",0,"members",List.of(
                Map.of("datasourceId",sales,"alias","sales","resources",List.of(Map.of("namespace",BIZ.getDatabaseName(),"table","dataset_orders"))),
                Map.of("datasourceId",finance,"alias","finance","resources",List.of(Map.of("namespace","public","table","refunds")))));
        var created=exchange(HttpMethod.POST,"/api/datasets",token,body);assertEquals(200,created.getStatusCode().value(),created.getBody());long dataset=read(created).get("dataset").get("id").asLong();
        var scope=datasets.resolve("admin","DATASET",dataset);
        var orders=analysis.query("admin",scope,"sales",BIZ.getDatabaseName(),"SELECT id,channel,paid_amount FROM dataset_orders");
        var refunds=analysis.query("admin",scope,"finance","public","SELECT order_id,SUM(refund_amount) AS refunded FROM refunds GROUP BY order_id");
        var workspace=new com.meper.chatbi.domain.ResultWorkspace();var left=workspace.add(orders);var right=workspace.add(refunds);
        var joined=workspace.join(left.handle(),right.handle(),"id","order_id","LEFT","MANY_TO_ONE");
        var net=workspace.calculate(joined.handle(),"net_cents","left.paid_amount","SUBTRACT","right.refunded",null,"ZERO");
        var yuan=workspace.calculate(net.handle(),"net_yuan","net_cents","DIVIDE",null,"100","REJECT");
        var total=workspace.aggregate(yuan.handle(),List.of(),List.of("net_yuan"),false,"REJECT");
        assertEquals("180",total.sample().get(0).get(0));assertEquals(2,total.sources().size());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->analysis.query("admin",scope,"finance","public","SELECT * FROM public.hidden_finance"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->analysis.query("admin",scope,"finance","public","SELECT r.* FROM public.refunds r JOIN public.hidden_finance h ON 1=1"));
        JsonNode tablePage=null;for(var candidate:read(exchange(HttpMethod.GET,salesRoot+"/pages",token,null)))if(candidate.get("type").asText().equals("TABLE"))tablePage=candidate;
        long pageId=tablePage.get("id").asLong();long spaceId=tablePage.get("spaceId").asLong();long objectId=tablePage.get("objectId").asLong();
        var batches=exchange(HttpMethod.POST,salesRoot+"/publish-batch",token,List.of(Map.of("pageId",pageId,"expectedVersion",1),Map.of("pageId",pageId,"expectedVersion",1)));
        assertEquals(200,batches.getStatusCode().value());assertTrue(read(batches).get("outcomes").get(0).get("success").asBoolean());assertFalse(read(batches).get("outcomes").get(1).get("success").asBoolean(),"批次明确报告版本冲突而非吞掉部分失败");
        long expected=read(exchange(HttpMethod.GET,salesRoot+"/pages/"+pageId,token,null)).get("page").get("draftVersion").asLong();
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        var documentId=new java.util.concurrent.atomic.AtomicLong();var documentVersion=new java.util.concurrent.atomic.AtomicLong();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",http->{
            http.getRequestBody().readAllBytes();int step=requests.incrementAndGet();String function;Map<String,Object> arguments;
            if(step==1){function="read_knowledge_draft";arguments=Map.of("pageId",pageId);}
            else if(step==2){function="apply_knowledge_updates";arguments=Map.of("patches",List.of(Map.of("pageId",pageId,"expectedVersion",expected,"semantics",Map.of("grain","一行一个订单"),"columns",Map.of("paid_amount",Map.of("businessName","实付金额","unit","分")),"evidence","实付金额单位为分")));}
            else if(step==3||step==17){function="create_knowledge_document";arguments=Map.of("type","METRIC","title","测试收入口径","content","收入金额单位为分，按订单粒度统计。","bindings",List.of(Map.of("objectId",objectId,"column","paid_amount")),"evidence","实付金额单位为分");}
            else if(step==5||step==13||step==15){function="read_knowledge_draft";arguments=Map.of("pageId",documentId.get());}
            else if(step==6||step==9){function="apply_knowledge_updates";arguments=Map.of("patches",List.of(Map.of("pageId",documentId.get(),"expectedVersion",documentVersion.get(),"title",step==6?"修正收入口径":"仅标题修改","evidence","修正标题")));}
            else if(step==7||step==11){function="apply_knowledge_updates";arguments=Map.of("patches",List.of(Map.of("pageId",documentId.get(),"expectedVersion",documentVersion.get()+(step==7?1:0),"bindings",List.of(Map.of("objectId",objectId,"column",step==7?"":"paid_amount")),"evidence","修正关联资源")));}
            else {function="";arguments=Map.of();}
            boolean toolStep=java.util.Set.of(1,2,3,5,6,7,9,11,13,15,17).contains(step);
            Map<String,Object> delta=toolStep?Map.of("role","assistant","tool_calls",List.of(Map.of("index",0,"id","edit-"+step,"type","function","function",Map.of("name",function,"arguments",json.writeValueAsString(arguments))))):Map.of("role","assistant","content","已保存表字段说明与收入指标草稿，请统一确认发布。");
            String stream="data: "+json.writeValueAsString(Map.of("id","fixture","object","chat.completion.chunk","created",1,"model","fixture","choices",List.of(Map.of("index",0,"delta",delta))))+"\n\n"+
                    "data: "+json.writeValueAsString(Map.of("id","fixture","object","chat.completion.chunk","created",1,"model","fixture","choices",List.of(Map.of("index",0,"delta",Map.of(),"finish_reason",toolStep?"tool_calls":"stop"))))+"\n\ndata: [DONE]\n\n";
            byte[] bytes=stream.getBytes(java.nio.charset.StandardCharsets.UTF_8);http.getResponseHeaders().set("Content-Type","text/event-stream");http.sendResponseHeaders(200,bytes.length);try(var out=http.getResponseBody()){out.write(bytes);}
        });server.start();
        try{
            var modelResponse=exchange(HttpMethod.POST,"/api/model-providers",token,Map.of("name","wiki-edit-fixture","protocol","OPENAI_COMPATIBLE","baseUrl","http://127.0.0.1:"+server.getAddress().getPort(),"model","fixture","apiKey","fixture-key","enabled",true));
            long model=read(modelResponse).get("id").asLong();assertEquals(200,exchange(HttpMethod.POST,"/api/model-providers/"+model+"/default",token,null).getStatusCode().value());
            var editing=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId","wiki-edit-integration","spaceId",spaceId,"pageIds",List.of(pageId),"message","一行一个订单，实付金额单位为分。请补充表字段说明，并创建收入指标。"));
            assertEquals(200,editing.getStatusCode().value(),editing.getBody());assertTrue(editing.getBody().contains("WIKI_DRAFT_CHANGE:"),editing.getBody());assertEquals(4,requests.get());
            var edited=read(exchange(HttpMethod.GET,salesRoot+"/pages/"+pageId,token,null));assertEquals("分",edited.get("object").get("columns").get(2).get("semantics").get("unit").asText());assertTrue(edited.get("page").get("dirty").asBoolean());
            documentId.set(scopeRepository.createdPages("wiki-edit-integration").iterator().next());
            documentVersion.set(read(exchange(HttpMethod.GET,salesRoot+"/pages/"+documentId.get(),token,null)).get("page").get("draftVersion").asLong());
            var continuation=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId","wiki-edit-integration","spaceId",spaceId,"pageIds",List.of(pageId),"focusPageIds",List.of(documentId.get()),"message","修正标题，并修正关联资源。"));
            assertEquals(200,continuation.getStatusCode().value(),continuation.getBody());assertEquals(8,requests.get());assertFalse(continuation.getBody().contains("失败："),continuation.getBody());
            assertTrue(continuation.getBody().contains("知识标题"));assertTrue(continuation.getBody().contains("关联资源"));
            var continued=read(exchange(HttpMethod.GET,salesRoot+"/pages/"+documentId.get(),token,null));assertEquals("修正收入口径",continued.get("page").get("title").asText());assertEquals("",continued.get("bindings").get(0).get("column").asText());
            var itemIds=scopeRepository.changedPages("wiki-edit-integration");assertEquals(2,itemIds.size());List<Map<String,Object>> items=new java.util.ArrayList<>();for(long id:itemIds){var v=read(exchange(HttpMethod.GET,salesRoot+"/pages/"+id,token,null));items.add(Map.of("pageId",id,"expectedVersion",v.get("page").get("draftVersion").asLong()));}
            var publish=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/publish-batch",token,Map.of("sessionId","wiki-edit-integration","items",items));assertEquals(200,publish.getStatusCode().value(),publish.getBody());for(var item:read(publish).get("outcomes"))assertTrue(item.get("success").asBoolean(),item.toString());
            // 新会话仅修改标题/绑定也必须独立进入发布名单，而不是依赖此前正文来源记录。
            for(String session:List.of("wiki-title-only","wiki-bindings-only")){
                documentVersion.set(read(exchange(HttpMethod.GET,salesRoot+"/pages/"+documentId.get(),token,null)).get("page").get("draftVersion").asLong());
                var result=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId",session,"spaceId",spaceId,"pageIds",List.of(documentId.get()),"message","修正标题，修正关联资源。"));
                assertEquals(200,result.getStatusCode().value(),result.getBody());assertFalse(result.getBody().contains("失败："),result.getBody());assertEquals(java.util.Set.of(documentId.get()),scopeRepository.changedPages(session));
                long version=read(exchange(HttpMethod.GET,salesRoot+"/pages/"+documentId.get(),token,null)).get("page").get("draftVersion").asLong();
                var published=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/publish-batch",token,Map.of("sessionId",session,"items",List.of(Map.of("pageId",documentId.get(),"expectedVersion",version))));
                assertEquals(200,published.getStatusCode().value(),published.getBody());assertTrue(read(published).get("outcomes").get(0).get("success").asBoolean(),published.getBody());
            }
            var focused=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId","wiki-focus-all","spaceId",spaceId,"pageIds",List.of(),"focusPageIds",List.of(pageId),"message","关注订单表，同时检查收入指标。"));
            assertEquals(200,focused.getStatusCode().value(),focused.getBody());assertFalse(focused.getBody().contains("失败："),"关注表不应限制同库文档读取");
            var restricted=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId","wiki-new-restricted","spaceId",spaceId,"pageIds",List.of(pageId),"message","继续检查收入指标。"));
            assertEquals(200,restricted.getStatusCode().value(),restricted.getBody());assertTrue(restricted.getBody().contains("页面不属于本次知识编辑范围"),"固定范围不能读其他会话创建的文档");
            long foreignPage=0;for(var p:read(exchange(HttpMethod.GET,financeRoot+"/pages",token,null)))if(p.get("type").asText().equals("TABLE")){foreignPage=p.get("id").asLong();break;}
            assertEquals(400,exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("spaceId",spaceId,"focusPageIds",List.of(foreignPage),"message","越界关注")).getStatusCode().value());
            var historyConflict=exchange(HttpMethod.POST,"/api/agent/chat/stream",token,Map.of("sessionId","wiki-edit-integration","scopeType","DATASET","scopeId",dataset,"message","继续分析"));assertEquals(409,historyConflict.getStatusCode().value(),"编辑历史不得串到跨库分析会话");
            assertEquals(409,exchange(HttpMethod.DELETE,"/api/model-providers/"+model,token,null).getStatusCode().value(),"删除默认模型需先明确改默认绑定");
            var alternative=exchange(HttpMethod.POST,salesRoot+"/spaces",token,Map.of("name","业务文档替代空间"));assertEquals(200,alternative.getStatusCode().value());long alternativeId=read(alternative).get("id").asLong();
            var alternateEdit=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/stream",token,Map.of("sessionId","wiki-alternate-edit","spaceId",alternativeId,"message","实付金额单位为分。建立收入指标。"));
            assertEquals(200,alternateEdit.getStatusCode().value(),alternateEdit.getBody());assertFalse(alternateEdit.getBody().contains("失败："),alternateEdit.getBody());
            long alternatePage=scopeRepository.createdPages("wiki-alternate-edit").iterator().next();long alternateVersion=read(exchange(HttpMethod.GET,"/api/wiki-spaces/"+alternativeId+"/pages/"+alternatePage,token,null)).get("page").get("draftVersion").asLong();
            var alternatePublish=exchange(HttpMethod.POST,"/api/agent/chat/knowledge/publish-batch",token,Map.of("sessionId","wiki-alternate-edit","items",List.of(Map.of("pageId",alternatePage,"expectedVersion",alternateVersion))));
            assertEquals(200,alternatePublish.getStatusCode().value(),alternatePublish.getBody());assertTrue(read(alternatePublish).get("outcomes").get(0).get("success").asBoolean(),alternatePublish.getBody());
            assertEquals(400,exchange(HttpMethod.POST,financeRoot+"/default-space",token,Map.of("spaceId",alternativeId)).getStatusCode().value(),"默认知识不能跨数据库绑定");
            assertEquals(200,exchange(HttpMethod.POST,salesRoot+"/default-space",token,Map.of("spaceId",alternativeId)).getStatusCode().value());
            for(var p:read(exchange(HttpMethod.GET,salesRoot+"/pages",token,null)))assertTrue(java.util.Set.of("TABLE","STRUCTURE").contains(p.get("type").asText())||p.get("id").asLong()==alternatePage,"替代文档空间不应继续继承原基础空间的业务文档");
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->datasets.checkCurrent("admin",scope),"默认知识切换后旧分析范围指纹失效");
        }finally{server.stop(0);}
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
