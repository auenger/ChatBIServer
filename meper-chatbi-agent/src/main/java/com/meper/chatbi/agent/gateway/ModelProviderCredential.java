package com.meper.chatbi.agent.gateway;

import java.util.Map;

/**
 * 模型调用凭据（已解密）：仅存在于 gateway 调用链路内，禁止落日志。
 *
 * @param protocol OPENAI_COMPATIBLE / ANTHROPIC
 * @param baseUrl  接入点（空则用协议默认端点）
 * @param model    模型名
 * @param apiKey   明文密钥（char[] 用后由调用方决定清理）
 * @param params   采样参数（temperature/maxTokens 等）
 */
public record ModelProviderCredential(
        String protocol,
        String baseUrl,
        String model,
        char[] apiKey,
        Map<String, Object> params) {
}
