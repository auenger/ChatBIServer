package com.meper.chatbi.agent.gateway;

import com.meper.chatbi.agent.chat.ChatEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 真实 Spring AI 解码本地 SSE 夹具；不调用外部模型，不使用真实密钥。 */
class ThinkingProtocolTest {

    @Test
    void anthropicThinkingDeltasBecomeReasoningAndTextDeltasBecomeTokens() throws Exception {
        String stream = event("message_start", """
                {"type":"message_start","message":{"id":"m","type":"message","role":"assistant",
                "model":"glm-test","content":[],"stop_reason":null,"stop_sequence":null,
                "usage":{"input_tokens":12,"output_tokens":0}}}
                """)
                + event("content_block_start", """
                {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}
                """)
                + event("content_block_delta", """
                {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"先查询"}}
                """)
                + event("content_block_delta", """
                {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"表目录"}}
                """)
                + event("content_block_delta", """
                {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"test-signature"}}
                """)
                + event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}")
                + event("content_block_start", """
                {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}
                """)
                + event("content_block_delta", """
                {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"业务表有 orders"}}
                """)
                + event("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":1}")
                + event("message_delta", """
                {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":8}}
                """)
                + event("message_stop", "{\"type\":\"message_stop\"}");
        List<ChatEvent> events = run("ANTHROPIC", "/v1/messages", stream);
        assertEquals("先查询表目录", joined(events, "reasoning"));
        assertEquals("业务表有 orders", joined(events, "token"));
    }

    @Test
    void openAiCompatibleSameDeltaKeepsBothFields() throws Exception {
        String stream = event(null, """
                {"id":"m","object":"chat.completion.chunk","created":1,"model":"deepseek-test",
                "choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"先查询表目录","content":"业务表有 orders"},"finish_reason":null}]}
                """) + event(null, """
                {"id":"m","object":"chat.completion.chunk","created":1,"model":"deepseek-test",
                "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
                """) + event(null, "[DONE]");
        List<ChatEvent> events = run("OPENAI_COMPATIBLE", "/v1/chat/completions", stream);
        assertEquals(List.of(ChatEvent.reasoning("s", "先查询表目录"),
                ChatEvent.token("s", "业务表有 orders")), events);
    }

    private static List<ChatEvent> run(String protocol, String path, String stream) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = stream.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var credential = new ModelProviderCredential(protocol,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "test-key".toCharArray(), Map.of());
            return new SpringAiModelGateway().build(credential).stream(new Prompt("有哪些表"))
                    .concatMapIterable(response -> ChatResponseEvents.from("s", response))
                    .collectList().block(Duration.ofSeconds(10));
        } finally {
            server.stop(0);
        }
    }

    private static String event(String type, String json) {
        return (type == null ? "" : "event: " + type + "\n")
                + "data: " + json.strip().replace("\n", "") + "\n\n";
    }

    private static String joined(List<ChatEvent> events, String type) {
        return events.stream().filter(event -> type.equals(event.type())).map(ChatEvent::data)
                .reduce("", String::concat);
    }
}
