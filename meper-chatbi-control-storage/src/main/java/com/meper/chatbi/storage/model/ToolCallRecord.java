package com.meper.chatbi.storage.model;

/**
 * 单次工具调用摘要（请求 + 结果），随 ASSISTANT 消息持久化与回放。
 */
public record ToolCallRecord(String request, String result) {
}
