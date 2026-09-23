package com.meper.chatbi.agent.chat;

import com.meper.chatbi.agent.definition.ModelProviderService;
import com.meper.chatbi.agent.gateway.SpringAiModelGateway;
import com.meper.chatbi.domain.DataSourceService;
import com.meper.chatbi.domain.MetadataService;
import com.meper.chatbi.domain.WorkbenchService;
import com.meper.chatbi.query.SqlClassifier;
import com.meper.chatbi.storage.model.ToolCallRecord;
import com.meper.chatbi.storage.repository.AuditRepository;
import com.meper.chatbi.storage.repository.ChatSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent chat 编排（0.1）：按 provider 构建模型 → 注入持久化会话历史与数据工具 → 流式输出。
 * 流结束提取真实 usage（prompt/completion tokens）以 usage 事件返回并写审计；
 * 全部数据访问经 domain 用例（agent 零 JDBC）。
 */
@Service
public class AgentChatService {

    /** 单次查询行上限（0.1 固定；P5 由 AgentDefinition 收窄）。 */
    public static final int MAX_ROWS = 200;

    private final ModelProviderService providers;
    private final SpringAiModelGateway gateway;
    private final ChatSessionStore sessions;
    private final DataSourceService dataSources;
    private final MetadataService metadata;
    private final WorkbenchService workbench;
    private final SqlClassifier classifier;
    private final AuditRepository audits;
    private final ChatSessionRepository chatMessages;

    public AgentChatService(ModelProviderService providers, SpringAiModelGateway gateway,
                            ChatSessionStore sessions, DataSourceService dataSources,
                            MetadataService metadata, WorkbenchService workbench,
                            SqlClassifier classifier, AuditRepository audits,
                            ChatSessionRepository chatMessages) {
        this.providers = providers;
        this.gateway = gateway;
        this.sessions = sessions;
        this.dataSources = dataSources;
        this.metadata = metadata;
        this.workbench = workbench;
        this.classifier = classifier;
        this.audits = audits;
        this.chatMessages = chatMessages;
    }

    public Flux<ChatEvent> chatStream(String principal, String sessionId, long providerId,
                                      long datasourceId, String namespace, String userMessage) {
        String sid = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        var profile = dataSources.get(datasourceId);
        String ns = namespace == null || namespace.isBlank() ? profile.databaseName() : namespace;
        ChatModel model = gateway.build(providers.credentialOf(providerId));

        // 工具事件经 Sinks 并入主流：工具执行发生在 Spring AI 内部循环（帧流间隙），
        // unicast+buffer 保证订阅前 emit 也不丢
        Sinks.Many<ChatEvent> toolSink = Sinks.many().unicast().onBackpressureBuffer();
        List<ToolCallRecord> toolLog = new java.util.ArrayList<>();
        var ctx = new ChatToolContext(principal, datasourceId, ns, MAX_ROWS,
                summary -> {
                    synchronized (toolLog) {
                        toolLog.add(new ToolCallRecord(summary, null));
                    }
                    toolSink.tryEmitNext(ChatEvent.tool(sid, summary));
                    audits.insert("default", principal, "AGENT_TOOL_CALL", "DATASOURCE",
                            String.valueOf(datasourceId), Map.of("sessionId", sid, "tool", summary));
                },
                summary -> {
                    synchronized (toolLog) {
                        for (int i = toolLog.size() - 1; i >= 0; i--) {
                            if (toolLog.get(i).result() == null) {
                                toolLog.set(i, new ToolCallRecord(toolLog.get(i).request(), summary));
                                break;
                            }
                        }
                    }
                    toolSink.tryEmitNext(ChatEvent.toolResult(sid, summary));
                });
        var tools = new AgentDbTools(ctx, dataSources, metadata, workbench, classifier);

        boolean continuing = sessions.exists(sid, principal);
        List<Message> history = continuing
                ? sessions.history(sid, principal, datasourceId, providerId, ns)
                : List.of();
        sessions.append(sid, principal, datasourceId, providerId, ns, true, userMessage);

        audits.insert("default", principal, "AGENT_CHAT", "DATASOURCE", String.valueOf(datasourceId),
                Map.of("sessionId", sid, "providerId", providerId, "messageLength", userMessage.length()));

        StringBuilder answer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        Flux<ChatEvent> body = ChatClient.builder(model).build().prompt()
                .system(SystemPrompts.CHAT)
                .messages(history)
                .user(userMessage)
                .tools(tools)
                .stream()
                .chatResponse()
                .doOnNext(r -> {
                    Usage u = usageOf(r);
                    if (u != null) {
                        lastUsage.set(u);
                    }
                })
                .mapNotNull(r -> frameOf(sid, r))
                .filter(event -> !event.data().isEmpty())
                .doOnNext(event -> {
                    // 仅正文进回答历史；思维链单独落库供回放
                    if ("token".equals(event.type())) {
                        answer.append(event.data());
                    } else if ("reasoning".equals(event.type())) {
                        thinking.append(event.data());
                    }
                });
        Flux<ChatEvent> main = Flux.concat(Flux.just(ChatEvent.start(sid)), body)
                .onErrorResume(e -> Flux.just(ChatEvent.error(sid, summarize(e))))
                .concatWith(Flux.defer(() -> {
                    Usage u = lastUsage.get();
                    if (answer.length() > 0) {
                        List<ToolCallRecord> toolCalls;
                        synchronized (toolLog) {
                            toolCalls = new java.util.ArrayList<>(toolLog);
                        }
                        sessions.append(sid, principal, datasourceId, providerId, ns, false,
                                answer.toString(),
                                u == null ? null : u.getPromptTokens().longValue(),
                                u == null ? null : u.getCompletionTokens().longValue(),
                                thinking.length() > 0 ? thinking.toString() : null,
                                toolCalls.isEmpty() ? null : toolCalls);
                    }
                    Flux<ChatEvent> usage = u != null
                            ? Flux.just(ChatEvent.usage(sid, u.getPromptTokens(), u.getCompletionTokens()))
                            : Flux.empty();
                    if (u != null) {
                        audits.insert("default", principal, "AGENT_CHAT_USAGE", "DATASOURCE",
                                String.valueOf(datasourceId),
                                Map.of("sessionId", sid, "promptTokens", u.getPromptTokens(),
                                        "completionTokens", u.getCompletionTokens()));
                    }
                    return usage.concatWith(Flux.just(ChatEvent.finalOf(sid)));
                }))
                .doFinally(signal -> toolSink.tryEmitComplete());
        return Flux.merge(toolSink.asFlux(), main);
    }

    /** 每帧转事件：思维链增量 → reasoning 事件；正文增量 → token 事件；其余帧丢弃。 */
    private ChatEvent frameOf(String sid, ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        var output = response.getResult().getOutput();
        String text = output.getText();
        if (text != null && !text.isEmpty()) {
            return ChatEvent.token(sid, text);
        }
        Object reasoning = output.getMetadata().get("reasoningContent");
        if (reasoning instanceof String s && !s.isEmpty()) {
            return ChatEvent.reasoning(sid, s);
        }
        return null;
    }

    private Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage u = response.getMetadata().getUsage();
        return u != null && u.getTotalTokens() != null && u.getTotalTokens() > 0 ? u : null;
    }

    /** 删除会话（库 + 内存缓存），仅限主体自己的会话。 */
    public void deleteSession(String principal, String sessionId) {
        int deleted = chatMessages.deleteSession(principal, sessionId);
        sessions.evict(sessionId, principal);
        if (deleted == 0) {
            throw new NoSuchElementException("会话不存在");
        }
        audits.insert("default", principal, "AGENT_CHAT_DELETE", "DATASOURCE", null,
                Map.of("sessionId", sessionId, "deleted", deleted));
    }

    private static String summarize(Throwable e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
    }
}
