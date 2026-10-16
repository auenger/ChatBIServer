package com.meper.chatbi.wiki;
import com.meper.chatbi.spi.model.*;
import com.meper.chatbi.storage.model.WikiRecords.*;
import java.util.*;

/** 管理与检索合同：显式主体、数据源作用域、不可变发布版本。 */
public final class WikiModels {
    private WikiModels() { }
    public record ObjectView(ObjectRecord object, Map<String,String> semantics, List<ColumnView> columns) { }
    public record ColumnView(String name, int ordinal, ColumnInfo structure, Map<String,String> semantics, String state) { }
    public record PageView(PageRecord page, ObjectView object, List<Binding> bindings, String preview, RevisionRecord published, List<Map<String,Object>> origins) { }
    public record SaveCommand(long expectedVersion, String title, String content,
            Map<String,String> semantics, Map<String,Map<String,String>> columns, List<Binding> bindings) { }
    public record CreateCommand(String type, String title, String content, List<Binding> bindings) { }
    public record SearchHit(long pageId, String path, String title, String type, long revision, int line, String excerpt, long spaceId, long datasourceId) {
        public SearchHit(long pageId,String path,String title,String type,long revision,int line,String excerpt) {this(pageId,path,title,type,revision,line,excerpt,0,0);}
    }
    public record SearchResult(List<SearchHit> hits, Integer nextCursor, boolean truncated) { }
    public record ReadResult(long pageId, String path, String title, long revision, int startLine,
            int totalLines, String content, boolean truncated, long spaceId, long datasourceId) {
        public ReadResult(long pageId,String path,String title,long revision,int startLine,int totalLines,String content,boolean truncated) {this(pageId,path,title,revision,startLine,totalLines,content,truncated,0,0);}
    }
    public record Partition(String prefix,long spaceId,Set<Long> allowedObjects,Set<String> allowedTypes) {
        public Partition(String prefix,long spaceId,Set<Long> allowedObjects){this(prefix,spaceId,allowedObjects,null);}
    }
    public record PublishItem(long pageId,long expectedVersion) { }
    public record PublishOutcome(long pageId,boolean success,String message) { }
    public record PublishBatch(String batchId,List<PublishOutcome> outcomes) { }
    public record SyncResult(long runId, int added, int changed, int missing, int total) { }
}
