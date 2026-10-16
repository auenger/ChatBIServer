package com.meper.chatbi.web;
import com.meper.chatbi.domain.DatasetService;
import com.meper.chatbi.web.auth.AuthInterceptor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 数据集配置入口，资源与主体校验在领域服务。 */
@RestController @RequestMapping("/api/datasets")
public class DatasetController {
 private final DatasetService datasets;
 private final com.meper.chatbi.wiki.WikiService wiki;
 public DatasetController(DatasetService datasets,com.meper.chatbi.wiki.WikiService wiki){this.datasets=datasets;this.wiki=wiki;}
 @GetMapping public List<DatasetService.DatasetView> list(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p){return datasets.list(p);}
 @GetMapping("/{id}") public DatasetService.DatasetView get(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id){return datasets.get(p,id);}
 @PostMapping public DatasetService.DatasetView create(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@RequestBody DatasetService.SaveCommand c){return datasets.create(p,c);}
 @PutMapping("/{id}") public DatasetService.DatasetView update(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id,@RequestBody DatasetService.SaveCommand c){return datasets.update(p,id,c);}
 @GetMapping("/{id}/scope") public DatasetService.AnalysisScope scope(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id){return datasets.resolve(p,"DATASET",id);}
 @GetMapping("/{id}/wiki/pages") public List<com.meper.chatbi.storage.model.WikiRecords.PageRecord> pages(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id){return wiki.partitionPages(p,datasets.resolve(p,"DATASET",id).knowledge());}
 @GetMapping("/{id}/wiki/objects") public List<com.meper.chatbi.wiki.WikiModels.ObjectView> objects(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id){return wiki.objects(p,wiki.datasetSpace(p,id));}
 @GetMapping("/{id}/wiki/search") public com.meper.chatbi.wiki.WikiModels.SearchResult search(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="/**") String pattern,@RequestParam(defaultValue="0") int cursor){var s=datasets.resolve(p,"DATASET",id);return wiki.searchPartitions(p,s.knowledge(),query,pattern,cursor,20,null);}
 @PostMapping("/{id}/wiki/publish-batch") public com.meper.chatbi.wiki.WikiModels.PublishBatch batch(@RequestAttribute(AuthInterceptor.PRINCIPAL_ATTR) String p,@PathVariable long id,@RequestBody List<com.meper.chatbi.wiki.WikiModels.PublishItem> items){return wiki.batchPartitionPublish(p,datasets.resolve(p,"DATASET",id).knowledge(),items);}
}
