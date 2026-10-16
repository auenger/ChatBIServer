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
class ChartSkillProtocolTest {

    @Test
    void loadsBuiltInSkillAndReturnsChartWithoutDatabaseExecution() throws Exception {
        var mapper = new ObjectMapper();
        List<String> requests = new CopyOnWriteArrayList<>();
        String answer = "```GPT-Vis\n{\"type\":\"column\",\"data\":[{\"category\":\"华东\",\"value\":120}]}\n```";
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Map<String, Object> delta = requests.size() == 1
                    ? Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0,
                            "id", "skill-call", "type", "function", "function", Map.of(
                                    "name", "get_chart_skill", "arguments", "{\"skill\":\"comparison\"}"))))
                    : Map.of("role", "assistant", "content", answer);
            String stream = "data: " + mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion.chunk",
                    "created", 1, "model", "test", "choices", List.of(Map.of("index", 0, "delta", delta)))) + "\n\n"
                    + "data: " + mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion.chunk",
                    "created", 1, "model", "test", "choices", List.of(Map.of("index", 0, "delta", Map.of(),
                            "finish_reason", requests.size() == 1 ? "tool_calls" : "stop")))) + "\n\ndata: [DONE]\n\n";
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
            var service = new AgentChatService(providers, new SpringAiModelGateway(), mock(ChatSessionStore.class),
                    dataSources, metadata, workbench, mock(SqlClassifier.class), mock(AuditRepository.class),
                    mock(ChatSessionRepository.class), mock(com.meper.chatbi.wiki.WikiService.class));
            List<ChatEvent> events = service.chatStream("admin", "chart-test", 2L, 1L, null, "使用我提供的数据画图")
                    .collectList().block(Duration.ofSeconds(10));
            assertEquals(2, requests.size());
            assertTrue(requests.get(1).contains("分类对比"));
            assertTrue(events.stream().anyMatch(e -> "tool".equals(e.type()) && e.data().contains("get_chart_skill(comparison)")));
            assertTrue(events.stream().anyMatch(e -> "toolResult".equals(e.type()) && e.data().equals("已加载图表技能")));
            assertEquals(answer, events.stream().filter(e -> "token".equals(e.type())).map(ChatEvent::data).reduce("", String::concat));
            verifyNoInteractions(metadata, workbench);
        } finally {
            server.stop(0);
        }
    }
}
