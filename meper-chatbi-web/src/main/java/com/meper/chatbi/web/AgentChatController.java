package com.meper.chatbi.web;

import com.meper.chatbi.agent.chat.AgentChatService;
import com.meper.chatbi.agent.chat.ChatEvent;
import com.meper.chatbi.storage.model.ToolCallRecord;
import com.meper.chatbi.storage.repository.ChatSessionRepository;
import com.meper.chatbi.web.auth.AuthInterceptor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent chat API（0.1）：SSE 流式对话 + 持久化会话历史。
 * 事件类型 start / token / tool / usage / final / error；认证由 AuthInterceptor 统一覆盖。
 */
@RestController
@RequestMapping("/api/agent/chat")
public class AgentChatController {

    public record ChatRequest(String sessionId, Long providerId, Long datasourceId,
                              String namespace, String message, String scopeType, Long scopeId) {
    }

    private final AgentChatService chatService;
    private final com.meper.chatbi.agent.chat.ScopedChatService scoped;
    private final ChatSessionRepository chatSessions;

    public AgentChatController(AgentChatService chatService, ChatSessionRepository chatSessions,com.meper.chatbi.agent.chat.ScopedChatService scoped) {
        this.chatService = chatService;
        this.chatSessions = chatSessions;this.scoped=scoped;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                             @RequestBody ChatRequest request) {
        String type=request.scopeType()==null?"DATABASE":request.scopeType();
        Long id=request.scopeId()==null?request.datasourceId():request.scopeId();
        if(id==null||id<=0)throw new IllegalArgumentException("请选择数据库或数据集");
        return emit(scoped.analyze(principal,request.sessionId(),request.providerId(),type,id,request.namespace(),request.message()));
    }
    public record KnowledgeRequest(String sessionId,Long providerId,long spaceId,List<Long> pageIds,List<Long> focusPageIds,String message) { }
    @PostMapping(value="/knowledge/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter knowledge(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@RequestBody KnowledgeRequest r){return emit(scoped.knowledge(principal,r.sessionId(),r.providerId(),r.spaceId(),r.pageIds(),r.focusPageIds(),r.message()));}
    public record KnowledgeBatch(String sessionId,List<com.meper.chatbi.wiki.WikiModels.PublishItem> items) { }
    @PostMapping("/knowledge/publish-batch")
    public com.meper.chatbi.wiki.WikiModels.PublishBatch publishKnowledge(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@RequestBody KnowledgeBatch b){return scoped.publishDrafts(p,b.sessionId(),b.items());}
    @GetMapping("/sessions/{id}/context")
    public com.meper.chatbi.storage.model.DatasetRecords.ChatScope context(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable String id){return scoped.context(p,id);}
    @GetMapping("/knowledge/reference")
    public com.meper.chatbi.wiki.WikiModels.ReadResult reference(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@RequestParam String scopeType,@RequestParam long scopeId,@RequestParam String scopeVersion,@RequestParam long pageId,@RequestParam String path,@RequestParam long revision,@RequestParam(defaultValue="1") int start){return scoped.readReference(p,scopeType,scopeId,scopeVersion,pageId,path,revision,start);}
    private SseEmitter emit(Flux<ChatEvent> events) {
        SseEmitter emitter=new SseEmitter(300_000L);
        var ref=new java.util.concurrent.atomic.AtomicReference<reactor.core.Disposable>();
        Runnable dispose=()->{var subscription=ref.get();if(subscription!=null)subscription.dispose();};
        emitter.onCompletion(dispose);emitter.onTimeout(dispose);emitter.onError(e->dispose.run());
        var subscription=events.subscribe(
                event -> {
                    try {
                        emitter.send(SseEmitter.event().name(event.type())
                                .data(event.data() == null ? "" : event.data(), MediaType.TEXT_PLAIN));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete);
        ref.set(subscription);
        return emitter;
    }

    // ---------- 会话历史（持久化，主体隔离在仓储层兜底） ----------

    public record SessionSummaryView(String sessionId, String title, long messageCount, String lastAt) {
    }

    public record ChatMessageView(int seq, String role, String content, String createdAt,
                                  String reasoning, java.util.List<ToolCallRecord> tools,
                                  Long promptTokens, Long completionTokens) {
    }

    /** 当前主体的会话列表（最近在前，最多 50 个）。 */
    @GetMapping("/sessions")
    public List<SessionSummaryView> sessions(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@RequestParam(defaultValue="ANALYSIS") String mode) {
        return chatSessions.listSessions(principal).stream()
                .filter(s -> {try{return scoped.context(principal,s.sessionId()).mode().equals(mode);}catch(java.util.NoSuchElementException e){return mode.equals("ANALYSIS");}})
                .map(s -> new SessionSummaryView(s.sessionId(), s.title(), s.messageCount(), s.lastAt()))
                .toList();
    }

    /** 会话消息分页：beforeSeq 为空取最近 limit 条，否则取更早一页；按 seq 正序返回。 */
    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageView> messages(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                                          @PathVariable String sessionId,
                                          @RequestParam(required = false) Integer beforeSeq,
                                          @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return chatSessions.listMessages(principal, sessionId, beforeSeq, safeLimit).stream()
                .map(m -> new ChatMessageView(m.seq(), m.role(), m.content(),
                        m.createdAt() == null ? null : m.createdAt().toString(),
                        m.reasoning(), m.tools(), m.promptTokens(), m.completionTokens()))
                .toList();
    }

    /** 删除会话（仅限当前主体自己的会话）。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                              @PathVariable String sessionId) {
        chatService.deleteSession(principal, sessionId);
    }
}
