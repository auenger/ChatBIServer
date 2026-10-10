package com.meper.chatbi.agent.gateway;

import com.meper.chatbi.agent.chat.ChatEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatResponseEventsTest {

    @Test
    void openAiSameFramePreservesReasoningAndAnswer() {
        var response = new ChatResponse(List.of(generation("业务表有 orders", Map.of(
                "reasoningContent", "先查询表目录"))));
        assertEquals(List.of(ChatEvent.reasoning("s", "先查询表目录"),
                ChatEvent.token("s", "业务表有 orders")), ChatResponseEvents.from("s", response));
    }

    @Test
    void anthropicThinkingUsesSignatureKeyEvenBeforeSignatureArrives() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("signature", null);
        var response = new ChatResponse(List.of(generation("先查询表目录", metadata),
                generation("业务表有 orders", Map.of())));
        assertEquals(List.of(ChatEvent.reasoning("s", "先查询表目录"),
                ChatEvent.token("s", "业务表有 orders")), ChatResponseEvents.from("s", response));
    }

    @Test
    void skipsEmptySignatureRedactedThinkingAndToolFrames() {
        var tool = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call", "function", "list_tables", "{}"))).build();
        var response = new ChatResponse(List.of(generation("", Map.of("signature", "sig")),
                generation(null, Map.of("data", "redacted")), new Generation(tool)));
        assertEquals(List.of(), ChatResponseEvents.from("s", response));
        assertEquals(List.of(), ChatResponseEvents.from("s", null));
        assertEquals(List.of(), ChatResponseEvents.from("s", new ChatResponse(List.of())));
    }

    @Test
    void handlesInterleavedThinkingAcrossToolRoundsWithoutReclassifyingOrdinaryText() {
        var response = new ChatResponse(List.of(
                generation("查询目录", Map.of("signature", "")),
                generation("正在查询", Map.of("reasoningContent", "")),
                generation("读取工具结果", Map.of("signature", "sig")),
                generation("查询完成", Map.of())));
        assertEquals(List.of(ChatEvent.reasoning("s", "查询目录"), ChatEvent.token("s", "正在查询"),
                ChatEvent.reasoning("s", "读取工具结果"), ChatEvent.token("s", "查询完成")),
                ChatResponseEvents.from("s", response));
    }

    private static Generation generation(String text, Map<String, Object> metadata) {
        return new Generation(AssistantMessage.builder().content(text).properties(metadata).build());
    }
}
