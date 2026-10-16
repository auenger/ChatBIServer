package com.meper.chatbi.agent.chat;

import com.meper.chatbi.storage.model.ChatMessageRecord;
import com.meper.chatbi.storage.model.ToolCallRecord;
import com.meper.chatbi.storage.repository.ChatSessionRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话历史：内存缓存 + 控制库持久化（0.1 起）。
 * 写入双路（内存 + 库）；读取内存优先，miss 时从库回源最近窗口。
 * 上下文窗口按条数裁剪（WINDOW）；token 级预算属 P5。
 */
@Component
public class ChatSessionStore {

    /** 进入模型上下文的最大消息条数（system 之外）。 */
    static final int WINDOW = 20;

    private record Session(String principal, long datasourceId, long providerId, String namespace,
                           Deque<Message> messages) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ChatSessionRepository repository;

    public ChatSessionStore(ChatSessionRepository repository) {
        this.repository = repository;
    }

    /** 追加一条消息：双写内存与库。首条 USER 消息作为会话标题；助手消息可携带该轮 usage 与思维链。 */
    public void append(String sessionId, String principal, long datasourceId, long providerId,
                       String namespace, boolean fromUser, String text,
                       Long promptTokens, Long completionTokens, String reasoning,
                       List<ToolCallRecord> tools) {
        int seq = nextSeq(sessionId, principal, datasourceId, providerId, namespace);
        String title = seq == 0 && fromUser ? truncate(text, 64) : null;
        repository.insert(new ChatMessageRecord(null, sessionId, principal, datasourceId, providerId,
                namespace, seq, fromUser ? ChatMessageRecord.ROLE_USER : ChatMessageRecord.ROLE_ASSISTANT,
                text, title, reasoning, tools, promptTokens, completionTokens, null));
        sessions.compute(sessionId, (k, s) -> {
            Session session = s != null ? s
                    : new Session(principal, datasourceId, providerId, namespace, new ArrayDeque<>());
            if (!session.principal().equals(principal)) {
                throw new NoSuchElementException("会话不存在");
            }
            session.messages().addLast(fromUser ? new UserMessage(text) : new AssistantMessage(text));
            while (session.messages().size() > WINDOW) {
                session.messages().removeFirst();
            }
            return session;
        });
    }

    public void append(String sessionId, String principal, long datasourceId, long providerId,
                       String namespace, boolean fromUser, String text) {
        append(sessionId, principal, datasourceId, providerId, namespace, fromUser, text, null, null, null, null);
    }

    /** 下一序号：内存 size；miss 时按库计数（对齐已有持久化历史）并回填内存窗口。 */
    private int nextSeq(String sessionId, String principal, long datasourceId, long providerId, String namespace) {
        if(!sessions.containsKey(sessionId)&&repository.firstMessage(principal,sessionId).isPresent()) {
            var recent=repository.listMessages(principal,sessionId,null,WINDOW);Deque<Message> messages=new ArrayDeque<>();
            for(var r:recent)messages.addLast(ChatMessageRecord.ROLE_USER.equals(r.role())?new UserMessage(r.content()):new AssistantMessage(r.content()));
            sessions.put(sessionId,new Session(principal,datasourceId,providerId,namespace,messages));
        }
        return repository.nextSequence(principal,sessionId);
    }

    /** 历史消息（窗口内、不含本轮输入）；内存 miss 从库回源。 */
    public List<Message> history(String sessionId, String principal, long datasourceId,
                                 long providerId, String namespace) {
        if (!exists(sessionId, principal)) {
            throw new NoSuchElementException("会话不存在");
        }
        if(!sessions.containsKey(sessionId))nextSeq(sessionId,principal,datasourceId,providerId,namespace);
        Session session = sessions.get(sessionId);
        synchronized (session.messages()) {
            return new ArrayList<>(session.messages());
        }
    }

    public boolean exists(String sessionId, String principal) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            return session.principal().equals(principal);
        }
        return repository.firstMessage(principal, sessionId).isPresent();
    }

    /** 会话删除后失效内存缓存（仅主体自己的缓存条目）。 */
    public void evict(String sessionId, String principal) {
        Session session = sessions.get(sessionId);
        if (session != null && session.principal().equals(principal)) {
            sessions.remove(sessionId);
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
