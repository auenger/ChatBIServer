package com.meper.chatbi.agent.definition;

import com.meper.chatbi.agent.gateway.ModelGateway;
import com.meper.chatbi.agent.gateway.ModelProviderCredential;
import com.meper.chatbi.agent.gateway.ProbeResult;
import com.meper.chatbi.storage.model.ModelProviderRecord;
import com.meper.chatbi.storage.repository.ModelProviderRepository;
import com.meper.chatbi.storage.security.AesGcmCipherService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
/**
 * 模型 provider 配置管理（0.1）：CRUD、启停、连通测试。
 * apiKey 只以密文形态经过本服务；任何视图/日志/API 响应不出现明文。
 */
@Service
public class ModelProviderService {

    /** 对外视图：永不含 apiKey。 */
    public record ModelProviderView(Long id, String name, String protocol, String baseUrl,
                                    String model, Map<String, Object> params, boolean enabled, boolean isDefault) {
    }

    /** 登记/更新请求；apiKey 仅登记必填，更新传 null 表示保留原密钥。 */
    public record SaveRequest(String name, String protocol, String baseUrl, String model,
                              String apiKey, Map<String, Object> params, Boolean enabled) {
    }

    private final ModelProviderRepository repository;
    private final AesGcmCipherService cipher;
    private final ModelGateway gateway;

    public ModelProviderService(ModelProviderRepository repository,
                                AesGcmCipherService cipher,
                                ModelGateway gateway) {
        this.repository = repository;
        this.cipher = cipher;
        this.gateway = gateway;
    }

    public long create(SaveRequest request) {
        validate(request, true);
        var record = record(request, request.enabled() == null || request.enabled());
        return repository.insert(record, cipher.encrypt(request.apiKey().toCharArray()));
    }

    @org.springframework.transaction.annotation.Transactional
    public void update(long id, SaveRequest request) {
        repository.lockDefault();
        requireExisting(id);
        validate(request, false);
        var secret = request.apiKey() == null || request.apiKey().isBlank()
                ? null : cipher.encrypt(request.apiKey().toCharArray());
        boolean enabled=request.enabled()==null?repository.findById(id).orElseThrow().enabled():request.enabled();
        checkDefaultChange(id,enabled);
        repository.update(id, record(request, enabled), secret);
    }

    @org.springframework.transaction.annotation.Transactional
    public void setEnabled(long id, boolean enabled) {
        repository.lockDefault();
        requireExisting(id);
        checkDefaultChange(id,enabled);
        repository.setEnabled(id, enabled);
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(long id) {
        repository.lockDefault();
        requireExisting(id);
        checkDefaultChange(id,false);
        repository.delete(id);
    }

    public ModelProviderView get(long id) {
        return view(repository.findById(id).orElseThrow(() -> new NoSuchElementException("模型配置不存在: " + id)));
    }

    public List<ModelProviderView> list() {
        return repository.list().stream().map(this::view).toList();
    }

    public ProbeResult test(long id) {
        var record = repository.findById(id).orElseThrow(() -> new NoSuchElementException("模型配置不存在: " + id));
        return gateway.probe(credential(record, repository.findApiKey(id)
                .orElseThrow(() -> new IllegalStateException("模型配置缺少密钥: " + id))));
    }

    @org.springframework.transaction.annotation.Transactional
    public void setDefault(long id) {repository.setDefault(id);}
    public long resolve(Long requested) {
        Long id=requested==null?repository.defaultId():requested;
        if(id==null) throw new IllegalStateException("请先在模型设置中配置默认模型");
        var record=repository.findById(id).orElseThrow(()->new IllegalStateException("默认模型已不存在，请重新配置"));
        if(!record.enabled()) throw new IllegalStateException("所选模型已停用，请明确选择其他模型");
        return id;
    }
    private void checkDefaultChange(long id,boolean enabled) {
        if(!enabled&&java.util.Objects.equals(repository.defaultId(),id))
            throw new IllegalStateException("请先将其他启用模型设为默认，再停用或删除当前默认模型");
    }
    /** 解密取凭据（批次 C 的 chat 链路复用；仅限服务端内部，禁止透出）。 */
    public ModelProviderCredential credentialOf(long id) {
        var record = repository.findById(id).orElseThrow(() -> new NoSuchElementException("模型配置不存在: " + id));
        if (!record.enabled()) {
            throw new IllegalStateException("模型配置已停用: " + record.name());
        }
        return credential(record, repository.findApiKey(id)
                .orElseThrow(() -> new IllegalStateException("模型配置缺少密钥: " + id)));
    }

    /** 预览可用模型列表：key 优先用入参明文（新建场景），为空时取存量配置的密钥（编辑场景）。 */
    public List<String> previewModels(String protocol, String baseUrl, String apiKey, Long providerId) {
        ModelProtocol.of(protocol);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        char[] key;
        if (apiKey != null && !apiKey.isBlank()) {
            key = apiKey.toCharArray();
        } else if (providerId != null) {
            var secret = repository.findApiKey(providerId)
                    .orElseThrow(() -> new NoSuchElementException("模型配置不存在或缺少密钥: " + providerId));
            key = cipher.decrypt(secret);
        } else {
            throw new IllegalArgumentException("apiKey 或 providerId 必须提供其一");
        }
        try {
            return gateway.listModels(new ModelProviderCredential(protocol, baseUrl, "", key, Map.of()));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("获取模型列表失败: "
                    + (msg.length() > 200 ? msg.substring(0, 200) + "…" : msg));
        }
    }

    private ModelProviderCredential credential(ModelProviderRecord record, AesGcmCipherService.EncryptedSecret secret) {
        char[] apiKey = cipher.decrypt(secret);
        return new ModelProviderCredential(record.protocol(), record.baseUrl(), record.model(),
                apiKey, record.params());
    }

    private void validate(SaveRequest request, boolean creating) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        ModelProtocol.of(request.protocol());
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (request.model() == null || request.model().isBlank()) {
            throw new IllegalArgumentException("model 不能为空");
        }
        if (creating && (request.apiKey() == null || request.apiKey().isBlank())) {
            throw new IllegalArgumentException("apiKey 不能为空");
        }
    }

    private ModelProviderRecord record(SaveRequest request, boolean enabled) {
        return new ModelProviderRecord(null, request.name().trim(), ModelProtocol.of(request.protocol()).name(),
                request.baseUrl().trim(), request.model().trim(), request.params(), enabled, null);
    }

    private ModelProviderView view(ModelProviderRecord record) {
        return new ModelProviderView(record.id(), record.name(), record.protocol(),
                record.baseUrl(), record.model(), record.params(), record.enabled(), java.util.Objects.equals(record.id(),repository.defaultId()));
    }

    private void requireExisting(long id) {
        repository.findById(id).orElseThrow(() -> new NoSuchElementException("模型配置不存在: " + id));
    }
}
