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
                              String namespace, String message) {
    }

    private final AgentChatService chatService;
    private final ChatSessionRepository chatSessions;

    public AgentChatController(AgentChatService chatService, ChatSessionRepository chatSessions) {
        this.chatService = chatService;
        this.chatSessions = chatSessions;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,
                             @RequestBody ChatRequest request) {
        if (request.providerId() == null || request.datasourceId() == null
                || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("providerId、datasourceId、message 均不能为空");
        }
        SseEmitter emitter = new SseEmitter(300_000L);
        Flux<ChatEvent> events = chatService.chatStream(principal, request.sessionId(),
                request.providerId(), request.datasourceId(), request.namespace(), request.message());
        events.subscribe(
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
    public List<SessionSummaryView> sessions(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal) {
        return chatSessions.listSessions(principal).stream()
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
