package com.meper.chatbi.storage.model;

import java.time.Instant;

/** DataWiki 控制库记录，JSON 保留结构事实与人工语义的独立来源。 */
public final class WikiRecords {
    private WikiRecords() { }
    public record ObjectRecord(long id, long datasourceId, String namespace, String name,
            String type, String remarks, String structure, String fingerprint, String semantics,
            String state, Instant syncedAt) { }
    public record ColumnRecord(long id, long objectId, String name, int ordinal,
            String structure, String semantics, String state) { }
    public record PageRecord(long id, long datasourceId, long spaceId, Long objectId, String type, String path,
            String title, String draftContent, long draftVersion, long publishedRevision,
            boolean dirty, boolean needsReview, boolean disabled, String updatedBy, Instant updatedAt) { }
    public record RevisionRecord(long pageId, long revision, String title, String content,
            String bindings, String editState, String publishedBy, Instant publishedAt) { }
    public record Binding(long objectId, String column) { }
    public record SyncRecord(long id, String namespace, String table, String status,
            String detail, String createdBy, Instant startedAt, Instant finishedAt) { }
}
