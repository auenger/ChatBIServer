package com.meper.chatbi.agent.gateway;

import com.meper.chatbi.agent.chat.ChatEvent;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/** 模型帧归一化：协议相关的思考内容与回答正文分别交付，避免思考混入回答与历史。 */
public final class ChatResponseEvents {

    private ChatResponseEvents() {
    }

    /** 一帧可包含多个 generation，或同时包含 OpenAI reasoning 与正文，全部按顺序交付。 */
    public static List<ChatEvent> from(String sessionId, ChatResponse response) {
        if (response == null) {
            return List.of();
        }
        List<ChatEvent> events = new ArrayList<>();
        for (var generation : response.getResults()) {
            var output = generation.getOutput();
            if (output == null) {
                continue;
            }
            var metadata = output.getMetadata();
            String text = output.getText();
            // Spring AI 1.1.8 将 Anthropic thinking_delta 的 thinking 放在 text 中，
            // 并携带 signature 键（增量阶段值可为 null 或空串）；普通 text 不带此键。
            if (metadata.containsKey("signature")) {
                if (text != null && !text.isEmpty()) {
                    events.add(ChatEvent.reasoning(sessionId, text));
                }
                continue;
            }
            Object reasoning = metadata.get("reasoningContent");
            if (reasoning instanceof String s && !s.isEmpty()) {
                events.add(ChatEvent.reasoning(sessionId, s));
            }
            if (text != null && !text.isEmpty()) {
                events.add(ChatEvent.token(sessionId, text));
            }
        }
        return events;
    }
}
