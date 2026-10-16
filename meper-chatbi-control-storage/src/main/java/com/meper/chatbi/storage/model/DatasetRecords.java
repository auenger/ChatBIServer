package com.meper.chatbi.storage.model;
import java.time.Instant;
import java.util.List;

/** 数据集是范围与知识配置，不授予访问权限。 */
public final class DatasetRecords {
    private DatasetRecords() { }
    public record Dataset(long id,String name,String description,long revision,boolean enabled,String updatedBy,Instant updatedAt) { }
    public record Resource(String namespace,String table) { }
    public record Member(long id,long datasetId,long datasourceId,String alias,List<Resource> resources,Long wikiSpaceId) { }
    public record Space(long id,String name,Long datasourceId,Long datasetId,boolean base) { }
    public record ChatScope(String sessionId,String subject,String scopeType,long scopeId,String scopeVersion,
            long providerId,String namespace,String mode,Long spaceId) { }
}
