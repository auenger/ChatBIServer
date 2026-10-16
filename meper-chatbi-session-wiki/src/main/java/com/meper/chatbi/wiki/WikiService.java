package com.meper.chatbi.wiki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.spi.model.*;
import com.meper.chatbi.storage.repository.*;
import com.meper.chatbi.storage.model.WikiRecords.*;
import com.meper.chatbi.wiki.WikiModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.meper.chatbi.storage.model.DatasetRecords.*;

/** DataWiki 语义管理与受限文件视图，零业务库连接；仅已发布、结构有效内容交付 Agent。 */
@Service
public class WikiService {
    private final WikiRepository repo;
    private final WikiAccess access;
    private final AuditRepository audit;
    private final ObjectMapper json;
    private final DatasetRepository datasets;
    private final WikiBatchPublisher batchPublisher;
    public WikiService(WikiRepository repo,WikiAccess access,AuditRepository audit,ObjectMapper json,DatasetRepository datasets,@org.springframework.context.annotation.Lazy WikiBatchPublisher batchPublisher) {
        this.repo=repo;this.access=access;this.audit=audit;this.json=json;this.datasets=datasets;this.batchPublisher=batchPublisher;
    }
    public void checkPrincipal(String principal){access.checkPrincipal(principal);}
    public void authorizeDatasource(String principal,long ds) {access.check(principal,ds);}
    public void authorize(String principal,long spaceId) {access.checkPrincipal(principal);datasets.space(spaceId);}
    @Transactional
    public long baseSpace(String principal,long ds) {
        authorizeDatasource(principal,ds);repo.lockDatasource(ds);
        var found=datasets.spaces().stream().filter(x->Objects.equals(x.datasourceId(),ds)&&x.base()).findFirst();
        if(found.isPresent())return found.get().id();
        long id=datasets.createSpace("数据库 Wiki",ds,null,true,principal);datasets.defaultSpace(ds,id);return id;
    }
    @Transactional
    public long defaultSpace(String principal,long ds) {baseSpace(principal,ds);return datasets.defaultSpace(ds);}
    public long datasetSpace(String principal,long id) {access.checkPrincipal(principal);datasets.get(id);return datasets.spaces().stream().filter(x->Objects.equals(x.datasetId(),id)).findFirst().orElseThrow(()->new IllegalStateException("数据集知识空间不存在")).id();}
    public long sourcePageSpace(String principal,long ds,long page) {authorizeDatasource(principal,ds);var p=repo.page(page);if(p.datasourceId()!=ds)throw new NoSuchElementException("Wiki 页面不存在");return p.spaceId();}
    @Transactional
    public List<PageRecord> sourcePages(String principal,long ds) {
        long base=baseSpace(principal,ds),selected=defaultSpace(principal,ds);var list=new ArrayList<>(repo.pages(base).stream().filter(p->selected==base||Set.of("TABLE","STRUCTURE").contains(p.type())).toList());if(selected!=base)list.addAll(repo.pages(selected));return list;
    }
    public List<PageRecord> pages(String principal,long ds) {authorize(principal,ds);return repo.pages(ds);}
    public List<SyncRecord> runs(String principal,long ds) {authorizeDatasource(principal,ds);return repo.runs(ds);}
    public List<ObjectView> objects(String principal,long space) {authorize(principal,space);var owner=datasets.space(space);
        if(owner.datasourceId()!=null)return repo.objects(owner.datasourceId()).stream().map(this::objectView).toList();
        List<ObjectRecord> found=new ArrayList<>();for(var m:datasets.members(owner.datasetId()))for(var o:repo.objects(m.datasourceId()))if(matches(m,o))found.add(o);
        return found.stream().map(this::objectView).toList();}
    private boolean matches(Member member,ObjectRecord object) {return member.resources().stream().anyMatch(r->r.namespace().equals(object.namespace())&&r.table().equals(object.name()));}
    private ObjectView objectView(ObjectRecord obj) {
        return new ObjectView(obj,map(obj.semantics()),repo.columns(obj.id()).stream().map(c->new ColumnView(c.name(),c.ordinal(),read(c.structure(),ColumnInfo.class),map(c.semantics()),c.state())).toList());
    }
    private PageRecord checked(String principal,long ds,long id) {
        authorize(principal,ds);var p=repo.page(id);
        if(p.spaceId()!=ds) throw new NoSuchElementException("Wiki 页面不存在");return p;
    }
    public PageView page(String principal,long ds,long id) {
        var p=checked(principal,ds,id);
        var object=p.objectId()==null?null:objectView(repo.object(p.objectId()));
        return new PageView(p,object,repo.bindings(id),render(p),p.publishedRevision()==0?null:repo.revision(id,p.publishedRevision()),datasets.origins(id));
    }
    public List<RevisionRecord> revisions(String principal,long ds,long id) {checked(principal,ds,id);return repo.revisions(id);}
    public long beginSync(String principal,long ds,String ns,String table) {authorizeDatasource(principal,ds);return repo.beginSync(ds,ns,table,principal);}
    public void failSync(long id) {repo.finishSync(id,"FAILED","结构采集失败，未覆盖上次镜像；请检查数据源连接和范围后重试");}
    @Transactional
    public SyncResult sync(String principal,long ds,String ns,String target,List<TableDetail> details,long runId) {
        authorizeDatasource(principal,ds);
        long space=baseSpace(principal,ds);
        // 控制库数据源行锁序列化同步/编辑/发布，业务库读取已在事务外完成。
        repo.lockDatasource(ds);
        Map<String,ObjectRecord> old=new HashMap<>();
        for(var o:repo.objects(ds)) if(o.namespace().equals(ns)) old.put(o.name(),o);
        int added=0,changed=0,missing=0;Set<String> seen=new HashSet<>();
        for(var detail:details) {
            seen.add(detail.table());String structure=write(detail);String fp=hash(structure);var before=old.get(detail.table());
            long id=repo.upsertObject(ds,ns,detail.table(),detail.type(),detail.remarks(),structure,fp);
            if(before==null) added++;else if(!before.fingerprint().equals(fp)||!before.state().equals("ACTIVE")) {changed++;repo.markReview(id);}
            repo.resetColumns(id);int ordinal=0;
            for(var col:detail.columns()) repo.upsertColumn(id,col.name(),++ordinal,write(col));
            if(before==null) {
                long page=repo.createPage(ds,space,id,"TABLE","/schemas/"+segment(ns)+"/tables/"+segment(detail.table())+".md",detail.table(),"",principal);
                repo.bindings(page,List.of(new Binding(id,"")));repo.clearDirty(page);
            }
            // 纯结构快照自动发布，不混入未复核的人工/AI 语义。
            var structurePage=repo.pages(space).stream().filter(p->Objects.equals(p.objectId(),id)&&p.type().equals("STRUCTURE")).findFirst();
            long structureId=structurePage.isPresent()?structurePage.get().id():repo.createPage(ds,space,id,"STRUCTURE","/schemas/"+segment(ns)+"/structure/"+segment(detail.table())+".md",detail.table()+" 数据库结构","",principal);
            repo.bindings(structureId,List.of(new Binding(id,"")));
            var p=repo.lockPage(structureId);
            if(p.publishedRevision()==0||before==null||!before.fingerprint().equals(fp)||!before.state().equals("ACTIVE"))
                repo.publish(p,render(p),write(List.of(Map.of("objectId",id,"column","","fingerprint",fp))),write(Map.of("content","")),principal);
        }
        for(var o:old.values()) if((target==null||target.isBlank()||target.equals(o.name()))&&!seen.contains(o.name())&&o.state().equals("ACTIVE")) {repo.missingObject(o.id());missing++;}
        var result=new SyncResult(runId,added,changed,missing,details.size());repo.finishSync(runId,"SUCCESS",write(result));
        audit(principal,ds,"WIKI_SYNC",runId);return result;
    }
    private void lockDatasource(long space) {var s=datasets.space(space);if(s.datasourceId()!=null)repo.lockDatasource(s.datasourceId());else datasets.lock(s.datasetId());}
    @Transactional
    public PageView create(String principal,long ds,CreateCommand c) {
        authorize(principal,ds);lockDatasource(ds);
        if(!Set.of("METRIC","GLOSSARY","RELATIONSHIP","RULE").contains(c.type())) throw new IllegalArgumentException("文档类型无效");
        String title=text(c.title(),255);if(title.isBlank()) throw new IllegalArgumentException("请输入标题");
        validateBindings(ds,c.bindings());
        String folder=switch(c.type()) {case "METRIC"->"metrics";case "GLOSSARY"->"glossary";case "RELATIONSHIP"->"relationships";default->"rules";};
        long id=repo.createPage(datasets.space(ds).datasourceId(),ds,null,c.type(),"/"+folder+"/"+UUID.randomUUID()+".md",title,text(c.content(),60000),principal);
        repo.bindings(id,c.bindings()==null?List.of():c.bindings());audit(principal,ds,"WIKI_CREATE",id);return page(principal,ds,id);
    }
    @Transactional
    public PageView save(String principal,long ds,long id,SaveCommand c) {
        checked(principal,ds,id);lockDatasource(ds);var p=repo.lockPage(id);
        if(p.type().equals("STRUCTURE"))throw new IllegalArgumentException("结构版本由同步自动管理");
        if(p.draftVersion()!=c.expectedVersion()) throw new IllegalStateException("页面已更新，请重新加载后编辑");
        String title=text(c.title(),255);if(title.isBlank()) throw new IllegalArgumentException("请输入标题");
        if(p.objectId()!=null) {
            var obj=repo.object(p.objectId());if(!obj.state().equals("ACTIVE")) throw new IllegalStateException("对象已消失，说明保留供查阅");
            repo.objectSemantics(obj.id(),write(semantics(c.semantics())));
            if(c.columns()!=null) for(var e:c.columns().entrySet()) repo.columnSemantics(obj.id(),e.getKey(),write(semantics(e.getValue())));
        } else {validateBindings(ds,c.bindings());repo.bindings(id,c.bindings()==null?List.of():c.bindings());}
        repo.save(id,c.expectedVersion(),title,text(c.content(),60000),principal);audit(principal,ds,"WIKI_SAVE",id);return page(principal,ds,id);
    }
    @Transactional
    public PageView publish(String principal,long ds,long id,long expected) {
        checked(principal,ds,id);lockDatasource(ds);var p=repo.lockPage(id);
        if(p.type().equals("STRUCTURE"))throw new IllegalArgumentException("结构版本由同步自动管理");
        if(p.draftVersion()!=expected) throw new IllegalStateException("页面已更新，请先重新预览发布差异");
        var bindings=repo.bindings(id);validateBindings(ds,bindings);
        if(bindings.isEmpty()) throw new IllegalArgumentException("发布前请关联至少一个数据对象");
        String content=render(p);if(content.length()>200000) throw new IllegalArgumentException("页面过长，请缩小文档范围");
        var stamps=bindings.stream().map(b->Map.of("objectId",b.objectId(),"column",b.column(),"fingerprint",repo.object(b.objectId()).fingerprint())).toList();
        Map<String,Object> edit=new HashMap<>();edit.put("content",p.draftContent());
        if(p.objectId()!=null) {var o=objectView(repo.object(p.objectId()));edit.put("semantics",o.semantics());
            var columns=new TreeMap<String,Map<String,String>>();for(var c:o.columns()) if(c.state().equals("ACTIVE")) columns.put(c.name(),c.semantics());edit.put("columns",columns);}
        repo.publish(p,content,write(stamps),write(edit),principal);audit(principal,ds,"WIKI_PUBLISH",id);return page(principal,ds,id);
    }
    @Transactional
    public PageView restore(String principal,long ds,long id,long revision,long expected) {
        var p=checked(principal,ds,id);lockDatasource(ds);p=repo.lockPage(id);
        if(p.type().equals("STRUCTURE"))throw new IllegalArgumentException("结构版本由同步管理，不能恢复语义");
        var old=repo.revision(id,revision);List<Binding> refs=new ArrayList<>();
        for(var n:tree(old.bindings())) refs.add(new Binding(n.get("objectId").asLong(),n.get("column").asText()));
        validateBindings(ds,refs);repo.bindings(id,refs);
        var edit=tree(old.editState());String body=edit.path("content").asText();
        if(p.objectId()!=null) {
            var obj=repo.object(p.objectId());if(!obj.state().equals("ACTIVE")) throw new IllegalStateException("对象已消失，不能恢复编辑");
            repo.objectSemantics(obj.id(),write(edit.path("semantics")));
            for(var col:repo.columns(obj.id())) if(col.state().equals("ACTIVE")&&edit.path("columns").has(col.name()))
                repo.columnSemantics(obj.id(),col.name(),write(edit.path("columns").get(col.name())));
        }
        repo.save(id,expected,old.title(),body,principal);audit(principal,ds,"WIKI_RESTORE",id);return page(principal,ds,id);
    }
    @Transactional
    public PageView disable(String principal,long ds,long id,boolean disabled) {var current=checked(principal,ds,id);if(current.type().equals("STRUCTURE"))throw new IllegalArgumentException("结构版本由同步管理");lockDatasource(ds);repo.disable(id,disabled,principal);audit(principal,ds,"WIKI_DISABLE",id);return page(principal,ds,id);}
    private void validateBindings(long ds,List<Binding> bindings) {
        if(bindings==null) return;if(bindings.size()>100) throw new IllegalArgumentException("关联资源最多 100 项");
        Set<Binding> unique=new HashSet<>();
        for(var b:bindings) {
            if(b==null||b.column()==null||!unique.add(b)) throw new IllegalArgumentException("关联资源无效或重复");
            var o=repo.object(b.objectId());if(!boundObject(ds,o)||!o.state().equals("ACTIVE")) throw new IllegalArgumentException("关联对象不存在于当前数据源");
            if(!b.column().isEmpty()&&repo.columns(o.id()).stream().noneMatch(c->c.name().equals(b.column())&&c.state().equals("ACTIVE"))) throw new IllegalArgumentException("关联字段不存在: "+b.column());
        }
    }
    private boolean boundObject(long spaceId,ObjectRecord o) {
        var s=datasets.space(spaceId);if(s.datasourceId()!=null)return s.datasourceId()==o.datasourceId();
        return datasets.members(s.datasetId()).stream().anyMatch(m->m.datasourceId()==o.datasourceId()&&matches(m,o));
    }
    private String render(PageRecord p) {
        StringBuilder b=new StringBuilder("# "+p.title()+"\n\n");
        if(p.objectId()!=null) {
            var o=objectView(repo.object(p.objectId()));
            b.append("## 数据对象\n").append(o.object().namespace()).append('.').append(o.object().name()).append(" · ").append(o.object().type()).append("\n\n");
            if(o.object().remarks()!=null) b.append("数据库原始备注：").append(o.object().remarks()).append("\n\n");
            if(!p.type().equals("STRUCTURE"))appendSemantics(b,o.semantics());b.append("\n## 字段说明\n");
            for(var col:o.columns()) if(col.state().equals("ACTIVE")) {
                b.append("\n### ").append(col.name()).append("\n类型：").append(col.structure().typeName()).append("；可空：").append(col.structure().nullable()).append("；主键：").append(col.structure().primaryKey()).append("\n");
                if(col.structure().remarks()!=null) b.append("数据库原始备注：").append(col.structure().remarks()).append('\n');
                if(!p.type().equals("STRUCTURE"))appendSemantics(b,col.semantics());
            }
            b.append("\n## 补充业务规则\n");
        }
        return b.append(p.draftContent()).toString();
    }
    private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("businessName","业务名称"),Map.entry("description","业务描述"),Map.entry("grain","数据粒度"),Map.entry("aliases","别名"),Map.entry("unit","单位"),Map.entry("enums","枚举含义"),Map.entry("timeRule","时间口径"),Map.entry("filterRule","过滤规则"),Map.entry("notes","注意事项"));
    private void appendSemantics(StringBuilder b,Map<String,String> s) {s.entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(e->!e.getValue().isBlank()).forEach(e->b.append(LABELS.getOrDefault(e.getKey(),e.getKey())).append("：").append(e.getValue()).append('\n'));}
    private Map<String,String> semantics(Map<String,String> input) {
        if(input==null) return Map.of();var safe=new TreeMap<String,String>();
        for(var e:input.entrySet()) {if(!LABELS.containsKey(e.getKey())) throw new IllegalArgumentException("未知语义字段");safe.put(e.getKey(),text(e.getValue(),8000));}return safe;
    }
    /** 先限定主体/数据源及有效发布集合，再匹配路径和文本；从不搜索草稿。 */
    public SearchResult search(String principal,long ds,String query,String pattern,int cursor,int limit) {
        authorize(principal,ds);if(cursor<0||cursor>100000) throw new IllegalArgumentException("分页游标无效");
        if(query!=null&&query.length()>200) throw new IllegalArgumentException("搜索词最多 200 字符");
        var matcher=WikiSearch.glob(pattern);int safe=Math.min(Math.max(limit,1),20);List<SearchHit> hits=new ArrayList<>();
        String needle=query==null?"":query.toLowerCase(Locale.ROOT);int index=0;boolean more=false;
        for(var p:repo.pages(ds)) {
            if(!available(p)||!matcher.matcher(p.path()).matches()) continue;
            var r=repo.revision(p.id(),p.publishedRevision());if(!validRevision(r)) continue;
            String[] lines=r.content().split("\n",-1);
            for(int i=0;i<lines.length;i++) {
                if(!needle.isEmpty()&&!lines[i].toLowerCase(Locale.ROOT).contains(needle)) continue;
                if(index++<cursor) {if(needle.isEmpty()) break;else continue;}
                if(hits.size()==safe) {more=true;break;}
                hits.add(new SearchHit(p.id(),p.path(),r.title(),p.type(),r.revision(),i+1,lines[i].substring(0,Math.min(lines[i].length(),300)),p.spaceId(),p.datasourceId()));
                if(needle.isEmpty()) break;
            }
            if(more) break;
        }
        return new SearchResult(hits,more?cursor+hits.size():null,more);
    }
    public ReadResult read(String principal,long ds,String path,Long revision,int start,int count) {
        authorize(principal,ds);WikiSearch.glob(path);
        var p=repo.pages(ds).stream().filter(x->x.path().equals(path)&&available(x)).findFirst().orElseThrow(()->new NoSuchElementException("已发布知识不存在或需复核"));
        var r=repo.revision(p.id(),revision==null?p.publishedRevision():revision);
        if(!validRevision(r)) throw new IllegalStateException("引用版本的结构已变化，请重新检索知识");
        if(start<1||count<1) throw new IllegalArgumentException("行号和读取长度须大于零");
        String[] lines=r.content().split("\n",-1);int from=Math.min(start-1,lines.length),to=Math.min(lines.length,from+Math.min(count,100));
        StringBuilder b=new StringBuilder();int bytes=0;
        for(int i=from;i<to;i++) {if(bytes+lines[i].length()>16000) {to=i;break;}b.append(i+1).append(": ").append(lines[i]).append('\n');bytes+=lines[i].length();}
        return new ReadResult(p.id(),path,r.title(),r.revision(),start,lines.length,b.toString(),to<lines.length,p.spaceId(),p.datasourceId());
    }
    public List<Partition> sourcePartitions(String principal,long ds) {
        long base=baseSpace(principal,ds),selected=defaultSpace(principal,ds);
        List<Partition> result=new ArrayList<>();result.add(new Partition("",base,null,base==selected?null:Set.of("TABLE","STRUCTURE")));if(base!=selected)result.add(new Partition("",selected,null));return result;
    }
    public SearchResult sourceSearch(String principal,long ds,String query,String pattern,int cursor,int limit) {return searchPartitions(principal,sourcePartitions(principal,ds),query,pattern,cursor,limit,null);}
    public ReadResult sourceRead(String principal,long ds,String path,Long revision,int start,int count) {
        for(var part:sourcePartitions(principal,ds))if(repo.pages(part.spaceId()).stream().anyMatch(p->p.path().equals(path)&&available(p)&&typeAllowed(part,p)))return read(principal,part.spaceId(),path,revision,start,count);
        throw new NoSuchElementException("正式知识不存在或需复核");
    }
    /** 清单由服务端生成，仅固定版本的有效内容可在本轮被发现/读取。 */
    public Map<Long,Long> manifest(String principal,List<Partition> partitions) {
        Map<Long,Long> result=new LinkedHashMap<>();
        for(var part:partitions) {authorize(principal,part.spaceId());for(var p:repo.pages(part.spaceId()))if(allowed(part,p)&&available(p)&&validRevision(repo.revision(p.id(),p.publishedRevision())))result.put(p.id(),p.publishedRevision());}
        return result;
    }
    private boolean allowed(Partition part,PageRecord page) {
        return allowed(part,page,page.publishedRevision());
    }
    private boolean typeAllowed(Partition part,PageRecord page){return part.allowedTypes()==null||part.allowedTypes().contains(page.type());}
    private boolean allowed(Partition part,PageRecord page,long revision){
        if(!typeAllowed(part,page))return false;
        if(part.allowedObjects()==null)return true;
        if(revision==0)return false;
        for(var ref:tree(repo.revision(page.id(),revision).bindings()))if(!part.allowedObjects().contains(ref.path("objectId").asLong()))return false;
        return true;
    }
    public SearchResult searchPartitions(String principal,List<Partition> parts,String query,String pattern,int cursor,int limit,Map<Long,Long> manifest) {
        if(cursor<0||cursor>100000)throw new IllegalArgumentException("分页游标无效");
        if(query!=null&&query.length()>200)throw new IllegalArgumentException("搜索词过长");
        var glob=WikiSearch.glob(pattern);String needle=query==null?"":query.toLowerCase(Locale.ROOT);int safe=Math.min(Math.max(limit,1),20),index=0;List<SearchHit> hits=new ArrayList<>();boolean more=false;
        outer:for(var part:parts) {authorize(principal,part.spaceId());for(var p:repo.pages(part.spaceId())) {
            if(!available(p)||!typeAllowed(part,p)||(manifest!=null&&!manifest.containsKey(p.id())))continue;
            String path=part.prefix()+p.path();if(!glob.matcher(path).matches())continue;
            long revision=manifest==null?p.publishedRevision():manifest.get(p.id());if(!allowed(part,p,revision))continue;var r=repo.revision(p.id(),revision);if(!validRevision(r))continue;
            var lines=r.content().split("\n",-1);
            for(int i=0;i<lines.length;i++) {if(!needle.isEmpty()&&!lines[i].toLowerCase(Locale.ROOT).contains(needle))continue;
                if(index++<cursor){if(needle.isEmpty())break;else continue;}
                if(hits.size()==safe){more=true;break outer;}
                hits.add(new SearchHit(p.id(),path,r.title(),p.type(),revision,i+1,lines[i].substring(0,Math.min(300,lines[i].length())),p.spaceId(),p.datasourceId()));if(needle.isEmpty())break;
            }
        }}
        return new SearchResult(hits,more?cursor+hits.size():null,more);
    }
    public ReadResult readPartitions(String principal,List<Partition> parts,Map<Long,Long> manifest,String path,Long revision,int start,int count) {
        for(var part:parts) {
            if(!path.startsWith(part.prefix()+"/"))continue;
            String original=path.substring(part.prefix().length());
            var found=repo.pages(part.spaceId()).stream().filter(p->p.path().equals(original)&&available(p)&&typeAllowed(part,p)&&(manifest==null?allowed(part,p):manifest.containsKey(p.id())&&allowed(part,p,manifest.get(p.id())))).findFirst();
            if(found.isEmpty())continue;var p=found.get();long fixed=manifest==null?p.publishedRevision():manifest.get(p.id());
            if(revision!=null&&revision!=fixed)throw new IllegalStateException("版本不属于当前分析清单，请重新检索");
            var r=read(principal,part.spaceId(),original,fixed,start,count);return new ReadResult(r.pageId(),path,r.title(),r.revision(),r.startLine(),r.totalLines(),r.content(),r.truncated(),r.spaceId(),r.datasourceId());
        }
        throw new NoSuchElementException("知识不在当前分析范围或需复核");
    }
    /** 每页独立事务：失败项保留草稿，成功项明确回执，不能隐藏部分失败。 */
    public PublishBatch batchSourcePublish(String principal,long ds,List<PublishItem> items) {
        authorizeDatasource(principal,ds);if(items==null||items.isEmpty()||items.size()>200)throw new IllegalArgumentException("请选择1至200项");
        String batch=UUID.randomUUID().toString();List<PublishOutcome> result=new ArrayList<>();
        for(var item:items)try {long space=sourcePageSpace(principal,ds,item.pageId());batchPublisher.publish(principal,space,item);result.add(new PublishOutcome(item.pageId(),true,"已发布"));}catch(RuntimeException e){result.add(new PublishOutcome(item.pageId(),false,e.getMessage()));}
        auditBatch(principal,batch,result,sourcePartitions(principal,ds).stream().map(Partition::spaceId).toList());return new PublishBatch(batch,result);
    }
    public PublishBatch batchSpacePublish(String principal,long space,List<PublishItem> items) {
        authorize(principal,space);if(items==null||items.isEmpty()||items.size()>200)throw new IllegalArgumentException("请选择1至200项");
        String batch=UUID.randomUUID().toString();List<PublishOutcome> result=new ArrayList<>();for(var item:items)try{batchPublisher.publish(principal,space,item);result.add(new PublishOutcome(item.pageId(),true,"已发布"));}catch(RuntimeException e){result.add(new PublishOutcome(item.pageId(),false,e.getMessage()));}
        auditBatch(principal,batch,result,List.of(space));return new PublishBatch(batch,result);
    }
    public List<PageRecord> partitionPages(String principal,List<Partition> parts) {
        Map<Long,PageRecord> result=new LinkedHashMap<>();
        for(var part:parts){authorize(principal,part.spaceId());for(var p:repo.pages(part.spaceId()))if(editablePartition(part,p))result.put(p.id(),p);}
        return List.copyOf(result.values());
    }
    private boolean editablePartition(Partition part,PageRecord p){
        if(!typeAllowed(part,p))return false;
        if(part.allowedObjects()==null)return true;
        if(part.prefix().equals("/dataset"))return true;
        if(p.objectId()!=null)return part.allowedObjects().contains(p.objectId());
        var refs=repo.bindings(p.id());return !refs.isEmpty()&&refs.stream().allMatch(b->part.allowedObjects().contains(b.objectId()));
    }
    public PublishBatch batchPartitionPublish(String principal,List<Partition> parts,List<PublishItem> items){
        if(items==null||items.isEmpty()||items.size()>200)throw new IllegalArgumentException("请选择1至200项");
        Set<Long> allowed=new HashSet<>();partitionPages(principal,parts).forEach(p->allowed.add(p.id()));List<PublishOutcome> result=new ArrayList<>();
        for(var item:items)try{if(!allowed.contains(item.pageId()))throw new IllegalArgumentException("页面不属于当前数据集");var p=repo.page(item.pageId());batchPublisher.publish(principal,p.spaceId(),item);result.add(new PublishOutcome(item.pageId(),true,"已发布"));}catch(RuntimeException e){result.add(new PublishOutcome(item.pageId(),false,e.getMessage()));}
        String batch=UUID.randomUUID().toString();auditBatch(principal,batch,result,parts.stream().map(Partition::spaceId).toList());return new PublishBatch(batch,result);
    }
    private void auditBatch(String principal,String batch,List<PublishOutcome> outcomes,List<Long> spaces){audit.insert("default",principal,"WIKI_PUBLISH_BATCH","WIKI_BATCH",batch,Map.of("spaceIds",spaces,"outcomes",outcomes));}
    public List<AuditRepository.WikiBatchAudit> batches(String principal,long space){authorize(principal,space);return audit.wikiBatches(principal).stream().filter(b->{for(var id:tree(b.detail()).path("spaceIds"))if(id.asLong()==space)return true;return false;}).toList();}
    private boolean available(PageRecord p) {return p.publishedRevision()>0&&!p.disabled()&&!p.needsReview();}
    private boolean validRevision(RevisionRecord r) {
        for(var n:tree(r.bindings())) {ObjectRecord obj;try{obj=repo.object(n.get("objectId").asLong());}catch(NoSuchElementException e){return false;}if(!obj.state().equals("ACTIVE")||!obj.fingerprint().equals(n.get("fingerprint").asText())) return false;}return true;
    }
    private com.fasterxml.jackson.databind.JsonNode tree(String raw) {try{return json.readTree(raw);}catch(Exception e){throw new IllegalStateException("Wiki 内容解析失败",e);}}
    private Map<String,String> map(String raw) {try{return json.readValue(raw,new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException("语义内容解析失败",e);}}
    private <T>T read(String raw,Class<T> type) {try{return json.readValue(raw,type);}catch(Exception e){throw new IllegalStateException("结构内容解析失败",e);}}
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Wiki 内容编码失败",e);}}
    private static String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String segment(String s) {return URLEncoder.encode(s,StandardCharsets.UTF_8).replace("+","%20").replace(".","%2E");}
    private static String text(String s,int max) {if(s==null)return "";if(s.length()>max)throw new IllegalArgumentException("内容超过长度上限 "+max);return s;}
    private void audit(String principal,long ds,String action,long id) {audit.insert("default",principal,action,"DATAWIKI",String.valueOf(id),Map.of("datasourceId",ds));}
}
