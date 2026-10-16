package com.meper.chatbi.web;
import com.meper.chatbi.wiki.*;
import com.meper.chatbi.wiki.WikiModels.*;
import com.meper.chatbi.storage.model.WikiRecords.*;
import com.meper.chatbi.web.auth.AuthInterceptor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 知识空间可属于数据库或数据集；文档绑定与读取规则由 WikiService 校验。 */
@RestController @RequestMapping("/api/wiki-spaces/{space}")
public class WikiSpaceController {
 private final WikiService wiki;
 public WikiSpaceController(WikiService wiki){this.wiki=wiki;}
 @GetMapping("/publish-batches") public java.util.List<com.meper.chatbi.storage.repository.AuditRepository.WikiBatchAudit> batches(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space){return wiki.batches(p,space);}
 @GetMapping("/pages") public List<PageRecord> pages(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space){return wiki.pages(p,space);}
 @GetMapping("/objects") public List<ObjectView> objects(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space){return wiki.objects(p,space);}
 @GetMapping("/pages/{id}") public PageView page(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id){return wiki.page(p,space,id);}
 @PostMapping("/pages") public PageView create(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@RequestBody CreateCommand c){return wiki.create(p,space,c);}
 @PutMapping("/pages/{id}") public PageView save(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id,@RequestBody SaveCommand c){return wiki.save(p,space,id,c);}
 @PostMapping("/pages/{id}/publish") public PageView publish(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id,@RequestBody WikiController.VersionCommand c){return wiki.publish(p,space,id,c.expectedVersion());}
 @PostMapping("/publish-batch") public PublishBatch batch(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@RequestBody List<PublishItem> items){return wiki.batchSpacePublish(p,space,items);}
 @GetMapping("/pages/{id}/revisions") public List<RevisionRecord> revisions(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id){return wiki.revisions(p,space,id);}
 @PostMapping("/pages/{id}/restore") public PageView restore(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id,@RequestBody WikiController.RestoreCommand c){return wiki.restore(p,space,id,c.revision(),c.expectedVersion());}
 @PostMapping("/pages/{id}/disable") public PageView disable(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@PathVariable long id,@RequestBody WikiController.DisableCommand c){return wiki.disable(p,space,id,c.disabled());}
 @GetMapping("/search") public SearchResult search(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="/**") String pattern,@RequestParam(defaultValue="0") int cursor){return wiki.search(p,space,query,pattern,cursor,20);}
 @GetMapping("/read") public ReadResult read(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long space,@RequestParam String path,@RequestParam(required=false) Long revision,@RequestParam(defaultValue="1") int start,@RequestParam(defaultValue="100") int count){return wiki.read(p,space,path,revision,start,count);}
}
