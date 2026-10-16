package com.meper.chatbi.storage.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;

/** 审计日志（只写；详情必须脱敏 —— 不含凭据、密码、完整结果集）。 */
@Repository
public class AuditRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AuditRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insert(String tenantId, String subject, String action,
                       String resourceType, String resourceId, Map<String, Object> detail) {
        jdbc.sql("""
                        INSERT INTO meper_audit_log (tenant_id, subject, action, resource_type, resource_id, detail)
                        VALUES (:tenant, :subject, :action, :resourceType, :resourceId, :detail)
                        """)
                .param("tenant", tenantId)
                .param("subject", subject)
                .param("action", action)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("detail", toJson(detail))
                .update();
    }

    public record WikiBatchAudit(String batchId,String occurredAt,String detail) { }
    public java.util.List<WikiBatchAudit> wikiBatches(String subject) {
        return jdbc.sql("SELECT resource_id,occurred_at,detail FROM meper_audit_log WHERE subject=:subject AND action='WIKI_PUBLISH_BATCH' ORDER BY id DESC LIMIT 100")
                .param("subject",subject).query((rs,n)->new WikiBatchAudit(rs.getString("resource_id"),rs.getString("occurred_at"),rs.getString("detail"))).list();
    }
    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{\"detailSerializeError\":true}";
        }
    }
}
