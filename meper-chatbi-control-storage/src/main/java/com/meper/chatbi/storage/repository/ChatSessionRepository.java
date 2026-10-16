package com.meper.chatbi.storage.repository;

import com.meper.chatbi.storage.model.ChatMessageRecord;
import com.meper.chatbi.storage.model.ToolCallRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 对话消息仓储：追加写 + 会话列表 + 按 seq 倒序分页（前端懒加载）。
 * 始终以 subject 过滤——主体隔离在存储层兜底。
 */
@Repository
public class ChatSessionRepository {

    /** 会话摘要（列表页）。 */
    public record SessionSummary(String sessionId, String title, long messageCount, String lastAt) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ChatSessionRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public long insert(ChatMessageRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO meper_chat_message
                            (session_id, subject, datasource_id, provider_id, namespace, seq, role, content,
                             session_title, prompt_tokens, completion_tokens, reasoning, tools)
                        VALUES (:sessionId, :subject, :datasourceId, :providerId, :namespace, :seq, :role, :content,
                                :title, :promptTokens, :completionTokens, :reasoning, :tools)
                        """)
                .param("sessionId", record.sessionId())
                .param("subject", record.subject())
                .param("datasourceId", record.datasourceId())
                .param("providerId", record.providerId())
                .param("namespace", record.namespace())
                .param("seq", record.seq())
                .param("role", record.role())
                .param("content", record.content())
                .param("title", record.sessionTitle())
                .param("promptTokens", record.promptTokens())
                .param("completionTokens", record.completionTokens())
                .param("reasoning", record.reasoning())
                .param("tools", toJson(record.tools()))
                .update(keys);
        return ((Number) keys.getKeys().values().iterator().next()).longValue();
    }

    /** 会话列表（最近在前，最多 50 个）。 */
    public List<SessionSummary> listSessions(String subject) {
        return jdbc.sql("""
                        SELECT session_id, MAX(COALESCE(session_title, '')) AS title, COUNT(*) AS cnt,
                               MAX(created_at) AS last_at
                        FROM meper_chat_message
                        WHERE subject = :subject
                        GROUP BY session_id
                        ORDER BY last_at DESC
                        LIMIT 50
                        """)
                .param("subject", subject)
                .query((rs, i) -> new SessionSummary(rs.getString("session_id"), rs.getString("title"),
                        rs.getLong("cnt"), rs.getString("last_at")))
                .list();
    }

    /**
     * 消息分页：beforeSeq 为 null 取最近 limit 条，否则取 seq &lt; beforeSeq 的最近 limit 条；
     * 返回按 seq 正序。subject 过滤兜底隔离。
     */
    public List<ChatMessageRecord> listMessages(String subject, String sessionId, Integer beforeSeq, int limit) {
        String sql = """
                        SELECT id, session_id, subject, datasource_id, provider_id, namespace, seq, role, content,
                               session_title, prompt_tokens, completion_tokens, reasoning, tools, created_at
                        FROM meper_chat_message
                        WHERE subject = :subject AND session_id = :sessionId
                        """ + (beforeSeq == null ? "" : "AND seq < :beforeSeq\n")
                + "ORDER BY seq DESC LIMIT :limit";
        var spec = jdbc.sql(sql)
                .param("subject", subject)
                .param("sessionId", sessionId);
        if (beforeSeq != null) {
            spec = spec.param("beforeSeq", beforeSeq);
        }
        return spec.param("limit", limit)
                .query((rs, i) -> new ChatMessageRecord(rs.getLong("id"), rs.getString("session_id"),
                        rs.getString("subject"), rs.getLong("datasource_id"), rs.getLong("provider_id"),
                        rs.getString("namespace"), rs.getInt("seq"), rs.getString("role"), rs.getString("content"),
                        rs.getString("session_title"), rs.getString("reasoning"), fromJson(rs.getString("tools")),
                        rs.getObject("prompt_tokens") == null ? null : rs.getLong("prompt_tokens"),
                        rs.getObject("completion_tokens") == null ? null : rs.getLong("completion_tokens"),
                        rs.getTimestamp("created_at").toInstant()))
                .list()
                .stream()
                .sorted(java.util.Comparator.comparingInt(ChatMessageRecord::seq))
                .toList();
    }

    private String toJson(List<ToolCallRecord> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ToolCallRecord> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ToolCallRecord>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    public Optional<ChatMessageRecord> firstMessage(String subject, String sessionId) {
        return listMessages(subject, sessionId, null, 1).stream().findFirst();
    }

    public int nextSequence(String subject,String sessionId) {
        return jdbc.sql("SELECT COALESCE(MAX(seq),-1)+1 FROM meper_chat_message WHERE subject=:subject AND session_id=:session")
                .param("subject",subject).param("session",sessionId).query(Integer.class).single();
    }
    public int countMessages(String subject, String sessionId) {
        Long count = jdbc.sql("SELECT COUNT(*) FROM meper_chat_message WHERE subject = :subject AND session_id = :sessionId")
                .param("subject", subject)
                .param("sessionId", sessionId)
                .query(Long.class)
                .single();
        return count.intValue();
    }

    /** 删除整个会话（subject 过滤兜底隔离），返回删除条数。 */
    public int deleteSession(String subject, String sessionId) {
        return jdbc.sql("DELETE FROM meper_chat_message WHERE subject = :subject AND session_id = :sessionId")
                .param("subject", subject)
                .param("sessionId", sessionId)
                .update();
    }
}
