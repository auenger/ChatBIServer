package com.meper.chatbi.agent.definition;

/**
 * 模型接入协议（0.1 决策：仅两种，见 docs/plans/p01-agent-chat-mvp.md §3）。
 */
public enum ModelProtocol {

    /** OpenAI 兼容协议（含官方及各兼容网关）。 */
    OPENAI_COMPATIBLE,

    /** Anthropic 原生协议。 */
    ANTHROPIC;

    public static ModelProtocol of(String value) {
        for (ModelProtocol p : values()) {
            if (p.name().equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("不支持的模型协议: " + value + "（可选 " + allowed() + "）");
    }

    public static String allowed() {
        return "OPENAI_COMPATIBLE / ANTHROPIC";
    }
}
