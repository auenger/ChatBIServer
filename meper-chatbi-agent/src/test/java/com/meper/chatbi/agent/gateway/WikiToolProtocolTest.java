package com.meper.chatbi.agent.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.agent.chat.AgentChatService;
import com.meper.chatbi.agent.chat.ChatEvent;
import com.meper.chatbi.agent.chat.ChatSessionStore;
import com.meper.chatbi.agent.definition.ModelProviderService;
import com.meper.chatbi.domain.DataSourceService;
import com.meper.chatbi.domain.MetadataService;
import com.meper.chatbi.domain.WorkbenchService;
import com.meper.chatbi.query.SqlClassifier;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.meper.chatbi.storage.repository.AuditRepository;
import com.meper.chatbi.storage.repository.ChatSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 本地模型协议夹具真实驱动 Spring AI 工具循环，验证技能加载与图表交付，不调用外部模型。 */
class WikiToolProtocolTest {

    @Test
    void discoversAndReadsWikiThroughModelProtocolWithoutSchemaInjection() throws Exception {
        var mapper = new ObjectMapper();
        List<String> requests = new CopyOnWriteArrayList<>();
        String answer = "净收入参考 Wiki v1 的口径";
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String function=requests.size()==1?"wiki_grep":"wiki_read";
            String arguments=requests.size()==1?"{\"query\":\"净收入\"}":"{\"path\":\"/metrics/net.md\",\"revision\":1,\"startLine\":1,\"lineCount\":10}";
            Map<String,Object> delta=requests.size()<3
                    ?Map.of("role","assistant","tool_calls",List.of(Map.of("index",0,"id","wiki-"+requests.size(),"type","function","function",Map.of("name",function,"arguments",arguments))))
                    :Map.of("role","assistant","content",answer);
            String stream = "data: " + mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion.chunk",
                    "created", 1, "model", "test", "choices", List.of(Map.of("index", 0, "delta", delta)))) + "\n\n"
                    + "data: " + mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion.chunk",
                    "created", 1, "model", "test", "choices", List.of(Map.of("index", 0, "delta", Map.of(),
                            "finish_reason", requests.size() < 3 ? "tool_calls" : "stop")))) + "\n\ndata: [DONE]\n\n";
            byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            var providers = mock(ModelProviderService.class);
            when(providers.credentialOf(2L)).thenReturn(new ModelProviderCredential("OPENAI_COMPATIBLE",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test", "fixture-key".toCharArray(), Map.of()));
            var dataSources = mock(DataSourceService.class);
            var profile = mock(DataSourceProfile.class);
            when(dataSources.get(1L)).thenReturn(profile);
            when(profile.databaseName()).thenReturn("fixture");
            var metadata = mock(MetadataService.class);
            var workbench = mock(WorkbenchService.class);
            var wiki=mock(com.meper.chatbi.wiki.WikiService.class);
            when(wiki.sourceSearch("admin",1L,"净收入",null,0,20)).thenReturn(new com.meper.chatbi.wiki.WikiModels.SearchResult(
                    List.of(new com.meper.chatbi.wiki.WikiModels.SearchHit(1L,"/metrics/net.md","净收入","METRIC",1L,1,"口径摘要")),null,false));
            when(wiki.sourceRead("admin",1L,"/metrics/net.md",1L,1,10)).thenReturn(new com.meper.chatbi.wiki.WikiModels.ReadResult(1L,"/metrics/net.md","净收入",1L,1,2,"1: 已确认口径WIKI_FIXTURE",false));
            var service = new AgentChatService(providers, new SpringAiModelGateway(), mock(ChatSessionStore.class),
                    dataSources, metadata, workbench, mock(SqlClassifier.class), mock(AuditRepository.class),
                    mock(ChatSessionRepository.class), wiki);
            List<ChatEvent> events = service.chatStream("admin", "chart-test", 2L, 1L, null, "净收入是什么意思")
                    .collectList().block(Duration.ofSeconds(10));
            assertEquals(3,requests.size());
            assertTrue(!requests.get(0).contains("已确认口径WIKI_FIXTURE"),"首轮不注入 Wiki 全文");
            assertTrue(requests.get(1).contains("/metrics/net.md"));
            assertTrue(requests.get(2).contains("已确认口径WIKI_FIXTURE"));
            assertTrue(events.stream().anyMatch(e->"toolResult".equals(e.type())&&e.data().startsWith("WIKI_REFERENCE:")));
            assertEquals(answer, events.stream().filter(e -> "token".equals(e.type())).map(ChatEvent::data).reduce("", String::concat));
            verifyNoInteractions(metadata, workbench);
        } finally {
            server.stop(0);
        }
    }
}
