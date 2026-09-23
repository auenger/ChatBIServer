package com.meper.chatbi.agent.gateway;

import java.util.List;

/**
 * 模型网关：Spring AI 的隔离层。spring-ai 类型只允许出现在本包实现内，
 * 领域契约（agent / web）只依赖本接口与上述 record（p5-agent-design.md §3）。
 *
 * <p>0.1 提供连通探测与模型列表拉取；chat 流式方法随批次 C 扩展。
 */
public interface ModelGateway {

    /** 发一条最小补全请求验证 provider 配置可用性。 */
    ProbeResult probe(ModelProviderCredential credential);

    /** 拉取该接入点可用模型列表（OpenAI 兼容 / Anthropic 均为 GET /v1/models 语义）。 */
    List<String> listModels(ModelProviderCredential credential);
}
