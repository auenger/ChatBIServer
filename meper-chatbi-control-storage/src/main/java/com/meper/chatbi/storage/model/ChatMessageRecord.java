package com.meper.chatbi.storage.model;

import java.time.Instant;
import java.util.List;

/**
 * 对话消息记录（0.1 持久化；工具活动不落库，仅 USER/ASSISTANT 文本）。
 */
public record ChatMessageRecord(
        Long id,
        String sessionId,
        String subject,
        long datasourceId,
        long providerId,
        String namespace,
        int seq,
        String role,
        String content,
        String sessionTitle,
        String reasoning,
        List<ToolCallRecord> tools,
        Long promptTokens,
        Long completionTokens,
        Instant createdAt) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
}
