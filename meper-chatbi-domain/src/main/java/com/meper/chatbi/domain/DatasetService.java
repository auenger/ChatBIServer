package com.meper.chatbi.domain;

import com.meper.chatbi.storage.model.DatasetRecords.*;
import com.meper.chatbi.storage.repository.*;
import com.meper.chatbi.wiki.WikiService;
import com.meper.chatbi.wiki.WikiModels.Partition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 数据集管理与分析作用域签发：资源配置不授权，主体检查与查询执法仍是独立边界。 */
@Service
public class DatasetService {
    public record MemberCommand(long datasourceId,String alias,List<Resource> resources,Long wikiSpaceId) { }
    public record SaveCommand(String name,String description,long expectedRevision,boolean enabled,List<MemberCommand> members) { }
    public record DatasetView(Dataset dataset,List<Member> members,long wikiSpaceId) { }
    public record SourceScope(String alias,long datasourceId,List<Resource> resources,boolean unrestricted,Long wikiSpaceId,com.meper.chatbi.spi.DatabaseType databaseType) { }
    public record AnalysisScope(String type,long id,String version,List<SourceScope> sources,List<Partition> knowledge) { }
    private final DatasetRepository repo;
    private final WikiRepository mirror;
    private final WikiService wiki;
    private final DataSourceService dataSources;
    private final AuditRepository audit;
    public DatasetService(DatasetRepository repo,WikiRepository mirror,WikiService wiki,DataSourceService dataSources,AuditRepository audit) {
        this.repo=repo;this.mirror=mirror;this.wiki=wiki;this.dataSources=dataSources;this.audit=audit;
    }
    private void check(String principal) {wiki.checkPrincipal(principal);}
    public List<DatasetView> list(String principal) {check(principal);return repo.list().stream().map(d->new DatasetView(d,repo.members(d.id()),wiki.datasetSpace(principal,d.id()))).toList();}
    public DatasetView get(String principal,long id) {check(principal);return new DatasetView(repo.get(id),repo.members(id),wiki.datasetSpace(principal,id));}
    @Transactional
    public DatasetView create(String principal,SaveCommand c) {
        check(principal);var members=validate(principal,c);long id=repo.create(c.name().trim(),c.description(),c.enabled(),principal);
        repo.createSpace(c.name()+" Wiki",null,id,true,principal);repo.members(id,members);
        audit.insert("default",principal,"DATASET_CREATE","DATASET",String.valueOf(id),Map.of("members",members.size()));return get(principal,id);
    }
    @Transactional
    public DatasetView update(String principal,long id,SaveCommand c) {
        check(principal);repo.lock(id);var members=validate(principal,c);
        repo.update(id,c.expectedRevision(),c.name().trim(),c.description(),c.enabled(),principal);repo.members(id,members);
        audit.insert("default",principal,"DATASET_UPDATE","DATASET",String.valueOf(id),Map.of("members",members.size()));return get(principal,id);
    }
    private List<Member> validate(String principal,SaveCommand c) {
        if(c.name()==null||c.name().isBlank()||c.name().length()>100)throw new IllegalArgumentException("数据集名称须为1至100字");
        if(c.description()!=null&&c.description().length()>8000)throw new IllegalArgumentException("描述过长");
        if(c.members()==null||c.members().size()>20)throw new IllegalArgumentException("数据集最多20个成员数据库");
        Set<Long> sources=new HashSet<>();Set<String> aliases=new HashSet<>();List<Member> result=new ArrayList<>();
        for(var m:c.members()) {
            wiki.authorizeDatasource(principal,m.datasourceId());dataSources.get(m.datasourceId());
            if(!sources.add(m.datasourceId())||m.alias()==null||!m.alias().matches("[\\p{L}\\p{N}_-]{1,64}")||!aliases.add(m.alias()))throw new IllegalArgumentException("成员数据库重复或别名无效/重复");
            if(m.resources()==null||m.resources().isEmpty()||m.resources().size()>1000)throw new IllegalArgumentException("请选择已同步的具体表，最多1000项");
            var available=mirror.objects(m.datasourceId());Set<Resource> unique=new HashSet<>();
            for(var r:m.resources())if(r==null||!unique.add(r)||available.stream().noneMatch(o->o.state().equals("ACTIVE")&&o.namespace().equals(r.namespace())&&o.name().equals(r.table())))throw new IllegalArgumentException("资源重复或尚未同步/已消失");
            if(m.wikiSpaceId()!=null&&!Objects.equals(repo.space(m.wikiSpaceId()).datasourceId(),m.datasourceId()))throw new IllegalArgumentException("成员知识空间必须属于该数据库");
            result.add(new Member(0,0,m.datasourceId(),m.alias(),List.copyOf(m.resources()),m.wikiSpaceId()));
        }
        return result;
    }
    public AnalysisScope resolve(String principal,String type,long id) {
        check(principal);if("DATABASE".equals(type)) {
            var profile=dataSources.get(id);long selected=wiki.defaultSpace(principal,id);
            var parts=wiki.sourcePartitions(principal,id);
            if(profile.type()==com.meper.chatbi.spi.DatabaseType.MYSQL&&profile.databaseName()!=null&&!profile.databaseName().isBlank()){
                Set<Long> objects=new HashSet<>();for(var o:mirror.objects(id))if(o.namespace().equals(profile.databaseName()))objects.add(o.id());
                parts=parts.stream().map(p->new Partition(p.prefix(),p.spaceId(),Set.copyOf(objects),p.allowedTypes())).toList();
            }
            return new AnalysisScope(type,id,hash("DATABASE:"+id+":"+selected+":"+profile.databaseName()),List.of(new SourceScope("database",id,List.of(),true,selected,profile.type())),parts);
        }
        if(!"DATASET".equals(type))throw new IllegalArgumentException("分析范围须为 DATABASE 或 DATASET");
        var d=repo.get(id);if(!d.enabled())throw new IllegalStateException("数据集已停用");
        var members=repo.members(id);if(members.isEmpty())throw new IllegalStateException("数据集尚未配置成员数据库");
        List<SourceScope> sources=new ArrayList<>();List<Partition> partitions=new ArrayList<>();Set<Long> allObjects=new HashSet<>();StringBuilder signature=new StringBuilder("DATASET:"+id+":"+d.revision());
        for(var m:members) {
            wiki.authorizeDatasource(principal,m.datasourceId());dataSources.get(m.datasourceId());
            long selected=m.wikiSpaceId()==null?wiki.defaultSpace(principal,m.datasourceId()):m.wikiSpaceId();
            if(!Objects.equals(repo.space(selected).datasourceId(),m.datasourceId()))throw new IllegalStateException("成员知识绑定已失效");
            Set<Long> allowed=new HashSet<>();for(var o:mirror.objects(m.datasourceId()))if(o.state().equals("ACTIVE")&&m.resources().contains(new Resource(o.namespace(),o.name())))allowed.add(o.id());
            allObjects.addAll(allowed);long base=wiki.baseSpace(principal,m.datasourceId());
            partitions.add(new Partition("/sources/"+m.alias(),base,Set.copyOf(allowed),base==selected?null:Set.of("TABLE","STRUCTURE")));
            if(selected!=base)partitions.add(new Partition("/sources/"+m.alias(),selected,Set.copyOf(allowed)));
            sources.add(new SourceScope(m.alias(),m.datasourceId(),m.resources(),false,selected,dataSources.get(m.datasourceId()).type()));signature.append('|').append(m.alias()).append(':').append(m.datasourceId()).append(':').append(selected).append(':').append(m.resources());
        }
        partitions.add(new Partition("/dataset",wiki.datasetSpace(principal,id),Set.copyOf(allObjects)));
        return new AnalysisScope(type,id,hash(signature.toString()),List.copyOf(sources),List.copyOf(partitions));
    }
    public void checkCurrent(String principal,AnalysisScope scope) {if(!scope.version().equals(resolve(principal,scope.type(),scope.id()).version()))throw new IllegalStateException("分析范围或知识绑定已变化，请开启新会话");}
    private static String hash(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
