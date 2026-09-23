package com.meper.chatbi.web;

import com.meper.chatbi.agent.definition.ModelProviderService;
import com.meper.chatbi.agent.gateway.ProbeResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型 provider 配置 API（0.1）：CRUD / 启停 / 连通测试。
 * 所有响应不含 apiKey；测试结果只回传摘要与延迟。
 */
@RestController
@RequestMapping("/api/model-providers")
public class ModelProviderController {

    private final ModelProviderService providers;

    public ModelProviderController(ModelProviderService providers) {
        this.providers = providers;
    }

    public record SaveRequest(String name, String protocol, String baseUrl, String model,
                              String apiKey, Map<String, Object> params, Boolean enabled) {
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody SaveRequest request) {
        long id = providers.create(toService(request));
        return Map.of("id", id);
    }

    @GetMapping
    public List<ModelProviderService.ModelProviderView> list() {
        return providers.list();
    }

    @GetMapping("/{id}")
    public ModelProviderService.ModelProviderView get(@PathVariable long id) {
        return providers.get(id);
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @RequestBody SaveRequest request) {
        providers.update(id, toService(request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        providers.delete(id);
    }

    @PostMapping("/{id}/test")
    public ProbeResult test(@PathVariable long id) {
        return providers.test(id);
    }

    public record PreviewModelsRequest(String protocol, String baseUrl, String apiKey, Long providerId) {
    }

    /** 拉取 provider 可用模型列表（OpenAI 兼容 / Anthropic）；key 为空时用存量配置的密钥。 */
    @PostMapping("/preview-models")
    public Map<String, Object> previewModels(@RequestBody PreviewModelsRequest request) {
        return Map.of("models",
                providers.previewModels(request.protocol(), request.baseUrl(), request.apiKey(), request.providerId()));
    }

    private static ModelProviderService.SaveRequest toService(SaveRequest request) {
        return new ModelProviderService.SaveRequest(request.name(), request.protocol(), request.baseUrl(),
                request.model(), request.apiKey(), request.params(), request.enabled());
    }
}
