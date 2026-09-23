package com.meper.chatbi.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI 实现：按 provider 配置手动构建 ChatModel（不走 starter 自动配置，
 * 启动期不依赖任何 apiKey —— start 侧以 spring.ai.model.chat=none 关闭自动配置）。
 */
@Component
public class SpringAiModelGateway implements ModelGateway {

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";

    @Override
    public ProbeResult probe(ModelProviderCredential credential) {        long start = System.nanoTime();
        try {
            ChatModel model = build(credential);
            model.call("ping");
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new ProbeResult(true, "连通正常", latency);
        } catch (Exception e) {
            long latency = (System.nanoTime() - start) / 1_000_000;
            return new ProbeResult(false, summarize(e), latency);
        }
    }

    /** 按协议构建 ChatModel；连通测试与 chat 链路共用（返回类型属 agent 模块内实现细节）。 */
    public ChatModel build(ModelProviderCredential credential) {
        String apiKey = credential.apiKey() == null ? "" : new String(credential.apiKey());
        boolean anthropic = "ANTHROPIC".equalsIgnoreCase(credential.protocol());
        Double temperature = doubleParam(credential.params(), "temperature");
        if (anthropic) {
            AnthropicApi api = AnthropicApi.builder()
                    .baseUrl(baseUrl(credential.baseUrl(), DEFAULT_ANTHROPIC_BASE_URL))
                    .apiKey(apiKey)
                    .build();
            AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                    .model(credential.model())
                    .maxTokens(intParam(credential.params(), "maxTokens", 1024));
            if (temperature != null) {
                options.temperature(temperature);
            }
            return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options.build()).build();
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl(credential.baseUrl(), DEFAULT_OPENAI_BASE_URL))
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(credential.model());
        if (temperature != null) {
            options.temperature(temperature);
        }
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options.build()).build();
    }

    /** 拉取 provider 可用模型列表（OpenAI 兼容 GET /v1/models；Anthropic GET /v1/models + x-api-key）。 */
    @Override
    public List<String> listModels(ModelProviderCredential credential) {
        String apiKey = credential.apiKey() == null ? "" : new String(credential.apiKey());
        boolean anthropic = "ANTHROPIC".equalsIgnoreCase(credential.protocol());
        String base = baseUrl(credential.baseUrl(), anthropic ? DEFAULT_ANTHROPIC_BASE_URL : DEFAULT_OPENAI_BASE_URL);
        String url = base.endsWith("/v1") ? base + "/models" : base.replaceAll("/$", "") + "/v1/models";
        RestClient rest = RestClient.create();
        JsonNode body;
        if (anthropic) {
            body = rest.get().uri(url)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .retrieve().body(JsonNode.class);
        } else {
            body = rest.get().uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve().body(JsonNode.class);
        }
        List<String> models = new ArrayList<>();
        JsonNode data = body == null ? null : body.get("data");
        if (data != null && data.isArray()) {
            data.forEach(n -> {
                String id = n.path("id").asText(null);
                if (id != null) {
                    models.add(id);
                }
            });
        }
        return models;
    }

    private static String baseUrl(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured.trim();
    }

    private static Double doubleParam(Map<String, Object> params, String key) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer intParam(Map<String, Object> params, String key, int fallback) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    /** 异常摘要脱敏：截断且不透出请求头细节（apiKey 不会出现在异常消息，双保险仍截断）。 */
    private static String summarize(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
    }
}
