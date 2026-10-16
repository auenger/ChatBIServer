package com.meper.chatbi.agent.chat;

import com.meper.chatbi.storage.repository.*;
import com.meper.chatbi.wiki.WikiService;
import com.meper.chatbi.wiki.WikiModels.*;
import com.meper.chatbi.storage.model.WikiRecords.Binding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 对话写入仅触及语义草稿，发布不作为模型工具开放。 */
@Service
public class KnowledgeEditService {
 public record Patch(long pageId,long expectedVersion,String title,String content,Map<String,String> semantics,
         Map<String,Map<String,String>> columns,List<Binding> bindings,String evidence) { }
 public record Change(long pageId,long spaceId,long datasourceId,String title,long draftVersion,List<Map<String,String>> changes,String origin) { }
 private final WikiRepository pages;private final DatasetRepository repo;private final WikiService wiki;
 public KnowledgeEditService(WikiRepository pages,DatasetRepository repo,WikiService wiki){this.pages=pages;this.repo=repo;this.wiki=wiki;}
 public PageView read(String principal,long id,Set<Long> allowed){if(!allowed.contains(id))throw new IllegalArgumentException("页面不属于本次知识编辑范围");var p=pages.page(id);return wiki.page(principal,p.spaceId(),id);}
 @Transactional public Change patch(String principal,String session,String userMessage,Patch c,Set<Long> allowed){
  var v=read(principal,c.pageId(),allowed);Map<String,String> semantics=new HashMap<>(v.object()==null?Map.of():v.object().semantics());
  Map<String,Map<String,String>> columns=new HashMap<>();if(v.object()!=null)for(var col:v.object().columns())if(col.state().equals("ACTIVE"))columns.put(col.name(),new HashMap<>(col.semantics()));
  List<Map<String,String>> changes=new ArrayList<>();String evidence=c.evidence()!=null&&c.evidence().length()<=8000&&userMessage.contains(c.evidence())?c.evidence():null;
  String origin=evidence==null?"AI_SUGGESTED":"USER_PROVIDED";
  if(v.object()==null&&((c.semantics()!=null&&!c.semantics().isEmpty())||(c.columns()!=null&&!c.columns().isEmpty())))throw new IllegalArgumentException("业务文档请修改正文与关联资源，不能写表字段语义");
  if(c.semantics()!=null)for(var e:c.semantics().entrySet()){changes.add(diff("表."+e.getKey(),semantics.get(e.getKey()),e.getValue()));semantics.put(e.getKey(),e.getValue());repo.origin(c.pageId(),"",e.getKey(),origin,evidence,session);}
  if(c.columns()!=null)for(var entry:c.columns().entrySet()) {var target=columns.get(entry.getKey());if(target==null)throw new IllegalArgumentException("字段不在当前结构中："+entry.getKey());for(var e:entry.getValue().entrySet()){changes.add(diff(entry.getKey()+"."+e.getKey(),target.get(e.getKey()),e.getValue()));target.put(e.getKey(),e.getValue());repo.origin(c.pageId(),entry.getKey(),e.getKey(),origin,evidence,session);}}
  if(c.title()!=null){changes.add(diff("知识标题",v.page().title(),c.title()));repo.origin(c.pageId(),"","title",origin,evidence,session);}
  if(c.bindings()!=null){if(v.object()!=null)throw new IllegalArgumentException("表说明的关联资源由结构管理，不能修改");changes.add(diff("关联资源",bindingText(v.bindings()),bindingText(c.bindings())));repo.origin(c.pageId(),"","bindings",origin,evidence,session);}
  if(c.content()!=null){changes.add(diff("补充正文",v.page().draftContent(),c.content()));repo.origin(c.pageId(),"","content",origin,evidence,session);}
  String title=c.title()==null?v.page().title():c.title();String content=c.content()==null?v.page().draftContent():c.content();
  var saved=wiki.save(principal,v.page().spaceId(),c.pageId(),new SaveCommand(c.expectedVersion(),title,content,semantics,columns,v.object()!=null||c.bindings()==null?v.bindings():c.bindings()));
  return new Change(saved.page().id(),saved.page().spaceId(),saved.page().datasourceId(),saved.page().title(),saved.page().draftVersion(),changes,origin);
 }
 @Transactional public Change create(String principal,String session,String userMessage,long space,CreateCommand c,String evidence){
  var v=wiki.create(principal,space,c);String verified=evidence!=null&&evidence.length()<=8000&&userMessage.contains(evidence)?evidence:null;String origin=verified==null?"AI_SUGGESTED":"USER_PROVIDED";
  repo.origin(v.page().id(),"","content",origin,verified,session);
  repo.origin(v.page().id(),"","created",origin,verified,session);
  return new Change(v.page().id(),v.page().spaceId(),v.page().datasourceId(),v.page().title(),v.page().draftVersion(),List.of(diff("新建文档","",v.page().draftContent())),origin);
 }
 private static Map<String,String> diff(String field,String before,String after){return Map.of("field",field,"before",snippet(before),"after",snippet(after));}
 private static String bindingText(List<Binding> bindings){return bindings.stream().map(b->"对象 #"+b.objectId()+(b.column().isEmpty()?"（整表）":" · "+b.column())).sorted().collect(java.util.stream.Collectors.joining("；"));}
 private static String snippet(String s){return s==null?"":s.substring(0,Math.min(300,s.length()));}
}
