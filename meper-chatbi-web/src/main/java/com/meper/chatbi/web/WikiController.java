package com.meper.chatbi.web;

import com.meper.chatbi.domain.WikiSyncService;
import com.meper.chatbi.wiki.WikiService;
import com.meper.chatbi.wiki.WikiModels;
import com.meper.chatbi.wiki.WikiModels.*;
import com.meper.chatbi.storage.model.WikiRecords.*;
import com.meper.chatbi.web.auth.AuthInterceptor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** DataWiki API：身份来自认证会话，资源范围与发布规则在服务层校验。 */
@RestController
@RequestMapping("/api/datasources/{ds}/wiki")
public class WikiController {
    private final WikiService wiki;
    private final WikiSyncService sync;
    private final com.meper.chatbi.wiki.WikiSpaceService spaces;
    public WikiController(WikiService wiki,WikiSyncService sync,com.meper.chatbi.wiki.WikiSpaceService spaces) {this.wiki=wiki;this.sync=sync;this.spaces=spaces;}
    @GetMapping("/spaces") public com.meper.chatbi.wiki.WikiSpaceService.SourceSpaces spaces(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long ds){return spaces.source(p,ds);}
    public record SpaceCommand(String name) { }
    @PostMapping("/spaces") public com.meper.chatbi.storage.model.DatasetRecords.Space createSpace(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long ds,@RequestBody SpaceCommand c){return spaces.create(p,ds,c.name());}
    public record DefaultSpaceCommand(long spaceId) { }
    @PostMapping("/default-space") public void defaultSpace(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long ds,@RequestBody DefaultSpaceCommand c){spaces.setDefault(p,ds,c.spaceId());}
    @GetMapping("/pages") public List<PageRecord> pages(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds) {return wiki.sourcePages(principal,ds);}
    @GetMapping("/objects") public List<ObjectView> objects(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds) {return wiki.objects(principal,wiki.baseSpace(principal,ds));}
    @GetMapping("/pages/{id}") public PageView page(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id) {return wiki.page(principal,wiki.sourcePageSpace(principal,ds,id),id);}
    @PostMapping("/pages") public PageView create(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@RequestBody CreateCommand command) {return wiki.create(principal,wiki.defaultSpace(principal,ds),command);}
    @PutMapping("/pages/{id}") public PageView save(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id,@RequestBody SaveCommand command) {return wiki.save(principal,wiki.sourcePageSpace(principal,ds,id),id,command);}
    public record VersionCommand(long expectedVersion) { }
    @PostMapping("/pages/{id}/publish") public PageView publish(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id,@RequestBody VersionCommand c) {return wiki.publish(principal,wiki.sourcePageSpace(principal,ds,id),id,c.expectedVersion());}
    @GetMapping("/pages/{id}/revisions") public List<RevisionRecord> revisions(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id) {return wiki.revisions(principal,wiki.sourcePageSpace(principal,ds,id),id);}
    public record RestoreCommand(long revision,long expectedVersion) { }
    @PostMapping("/pages/{id}/restore") public PageView restore(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id,@RequestBody RestoreCommand c) {return wiki.restore(principal,wiki.sourcePageSpace(principal,ds,id),id,c.revision(),c.expectedVersion());}
    public record DisableCommand(boolean disabled) { }
    @PostMapping("/pages/{id}/disable") public PageView disable(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@PathVariable long id,@RequestBody DisableCommand c) {return wiki.disable(principal,wiki.sourcePageSpace(principal,ds,id),id,c.disabled());}
    public record SyncCommand(String namespace,String table) { }
    @PostMapping("/sync") public SyncResult sync(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@RequestBody SyncCommand c) {return sync.sync(principal,ds,c.namespace(),c.table());}
    @GetMapping("/sync-runs") public List<SyncRecord> runs(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds) {return wiki.runs(principal,ds);}
    @PostMapping("/publish-batch") public WikiModels.PublishBatch batch(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@RequestBody List<WikiModels.PublishItem> items) {return wiki.batchSourcePublish(principal,ds,items);}
    @GetMapping("/search") public SearchResult search(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="/**") String pattern,@RequestParam(defaultValue="0") int cursor,@RequestParam(defaultValue="20") int limit) {return wiki.sourceSearch(principal,ds,query,pattern,cursor,limit);}
    @GetMapping("/read") public ReadResult read(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String principal,@PathVariable long ds,@RequestParam String path,@RequestParam(required=false) Long revision,@RequestParam(defaultValue="1") int start,@RequestParam(defaultValue="100") int count) {return wiki.sourceRead(principal,ds,path,revision,start,count);}
}
