package com.meper.chatbi.storage.model;

import java.time.Instant;
import java.util.Map;

/**
 * 模型 provider 配置记录（0.1 Agent chat MVP）。
 *
 * <p>不携带 apiKey 明文——密文由 {@code ModelProviderRepository} 独立读写（与凭据同款模式）。
 *
 * @param id       主键
 * @param name     用户可读名称（唯一）
 * @param protocol 协议：OPENAI_COMPATIBLE / ANTHROPIC
 * @param baseUrl  接入点（OpenAI 兼容网关或 Anthropic 端点）
 * @param model    模型名
 * @param params   采样参数（temperature/maxTokens 等，可为空）
 * @param enabled  是否启用
 * @param createdAt 创建时间
 */
public record ModelProviderRecord(
        Long id,
        String name,
        String protocol,
        String baseUrl,
        String model,
        Map<String, Object> params,
        boolean enabled,
        Instant createdAt) {
}
