package com.meper.chatbi.agent.chat;

import java.util.function.Consumer;

/**
 * 一次对话会话内数据工具的执行上下文（显式传递，无 ThreadLocal）。
 *
 * @param principal   已验证主体（chat 由 web 层认证后传入）
 * @param datasourceId 目标数据源
 * @param namespace   默认库/Schema（MySQL=库名，其余=Schema）
 * @param maxRows     单次查询返回行上限（0.1 固定 200）
 * @param toolNotifier 工具请求通知（推 SSE 的 tool 事件；可为 null）
 * @param toolResultNotifier 工具结果通知（推 SSE 的 toolResult 事件；可为 null）
 */
public record ChatToolContext(
        String principal,
        long datasourceId,
        String namespace,
        int maxRows,
        Consumer<String> toolNotifier,
        Consumer<String> toolResultNotifier) {

    public void notifyTool(String summary) {
        if (toolNotifier != null) {
            toolNotifier.accept(summary);
        }
    }

    public void notifyToolResult(String summary) {
        if (toolResultNotifier != null) {
            toolResultNotifier.accept(summary);
        }
    }
}
