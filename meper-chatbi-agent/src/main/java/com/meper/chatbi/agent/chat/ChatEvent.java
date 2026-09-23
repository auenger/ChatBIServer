package com.meper.chatbi.agent.chat;

/**
 * chat 流事件（SSE payload）。type ∈ start / token / reasoning / tool / usage / final / error。
 */
public record ChatEvent(String type, String sessionId, String data) {

    public static ChatEvent start(String sessionId) {
        // data 携带 sessionId：新会话由后端生成 id，前端据此延续多轮
        return new ChatEvent("start", sessionId, sessionId);
    }

    public static ChatEvent token(String sessionId, String text) {
        return new ChatEvent("token", sessionId, text);
    }

    /** 思维链增量（reasoning 模型的思考过程；不进历史与回答正文）。 */
    public static ChatEvent reasoning(String sessionId, String text) {
        return new ChatEvent("reasoning", sessionId, text);
    }

    /** 工具请求摘要（data = "list_tables(meperdb)" 之类）。 */
    public static ChatEvent tool(String sessionId, String summary) {
        return new ChatEvent("tool", sessionId, summary);
    }

    /** 工具结果摘要（data = "返回 3 个对象" / "失败: …"）。 */
    public static ChatEvent toolResult(String sessionId, String summary) {
        return new ChatEvent("toolResult", sessionId, summary);
    }

    /** data = JSON：{"promptTokens":n,"completionTokens":m} —— 最近一轮真实用量（上下文比例计算依据）。 */
    public static ChatEvent usage(String sessionId, long promptTokens, long completionTokens) {
        return new ChatEvent("usage", sessionId, "{\"promptTokens\":" + promptTokens
                + ",\"completionTokens\":" + completionTokens + "}");
    }

    public static ChatEvent finalOf(String sessionId) {
        return new ChatEvent("final", sessionId, null);
    }

    public static ChatEvent error(String sessionId, String message) {
        return new ChatEvent("error", sessionId, message);
    }
}
