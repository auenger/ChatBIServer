package com.meper.chatbi.agent.gateway;
import com.meper.chatbi.agent.chat.KnowledgeEditService;
import com.meper.chatbi.domain.DatasetService;
import com.meper.chatbi.domain.DatasetService.AnalysisScope;
import com.meper.chatbi.storage.repository.WikiRepository;
import com.meper.chatbi.storage.model.WikiRecords.Binding;
import com.meper.chatbi.wiki.WikiModels.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.*;

/** 仅知识编辑会话可见，页面范围由服务端生成，不能发布或写业务库。 */
public class KnowledgeEditTools {
 private final String principal,session,userMessage;private final long space;private final Set<Long> allowed;private final Set<Long> objects;
 private final KnowledgeEditService edit;private final DatasetService datasets;private final AnalysisScope scope;private final WikiRepository pages;private final ScopedToolSupport support;
 public KnowledgeEditTools(String principal,String session,String userMessage,long space,Set<Long> allowed,Set<Long> objects,
         KnowledgeEditService edit,DatasetService datasets,AnalysisScope scope,WikiRepository pages,ScopedToolSupport support){this.principal=principal;this.session=session;this.userMessage=userMessage;this.space=space;this.allowed=new HashSet<>(allowed);this.objects=objects;this.edit=edit;this.datasets=datasets;this.scope=scope;this.pages=pages;this.support=support;}
 @Tool(description="列出本次允许编辑的表说明/文档及草稿版本，数据库结构版本不能编辑。")
 public String list_knowledge_drafts(){return support.invoke("list_knowledge_drafts",()->{datasets.checkCurrent(principal,scope);return allowed.stream().map(pages::page).map(p->Map.of("pageId",p.id(),"spaceId",p.spaceId(),"title",p.title(),"type",p.type(),"draftVersion",p.draftVersion())).toList();});}
 @Tool(description="按表名或字段名查找当前范围的结构镜像，返回最多20个objectId和字段名，供文档绑定使用，不读取业务数据。")
 public String find_knowledge_objects(String query){return support.invoke("find_knowledge_objects",()->{datasets.checkCurrent(principal,scope);if(query!=null&&query.length()>200)throw new IllegalArgumentException("关键词过长");String needle=query==null?"":query.toLowerCase(java.util.Locale.ROOT);return objects.stream().map(pages::object).filter(o->o.name().toLowerCase(java.util.Locale.ROOT).contains(needle)||pages.columns(o.id()).stream().anyMatch(c->c.name().toLowerCase(java.util.Locale.ROOT).contains(needle))).limit(20).map(o->java.util.Map.of("objectId",o.id(),"datasourceId",o.datasourceId(),"namespace",o.namespace(),"table",o.name(),"columns",pages.columns(o.id()).stream().filter(c->c.state().equals("ACTIVE")).map(c->c.name()).toList())).toList();});}
 @Tool(description="读取指定草稿、当前表字段结构和现有语义。修改前必须读取草稿版本。")
 public String read_knowledge_draft(long pageId){return support.invoke("read_knowledge_draft",()->{datasets.checkCurrent(principal,scope);return edit.read(principal,pageId,allowed);});}
 @Tool(description="按变更清单补充多张表/文档，只保存草稿，不发布、不改业务库。每项带pageId和读取到的expectedVersion；semantics/columns是增量键值，未提供字段保留；title/content为null则保留。evidence引用当前用户原话，猜测内容留为AI建议。单次最多20页，失败项保留现状并返回原因。")
 public String apply_knowledge_updates(List<KnowledgeEditService.Patch> patches){return support.invoke("apply_knowledge_updates",()->{
  datasets.checkCurrent(principal,scope);if(patches==null||patches.isEmpty()||patches.size()>20)throw new IllegalArgumentException("本次更新须为1至20页");
  List<Object> result=new ArrayList<>();for(var patch:patches)try{if(patch.bindings()!=null&&patch.bindings().stream().anyMatch(b->!objects.contains(b.objectId())))throw new IllegalArgumentException("关联对象不属于编辑范围");var change=edit.patch(principal,session,userMessage,patch,allowed);support.detail("WIKI_DRAFT_CHANGE:",change);result.add(change);}catch(RuntimeException e){result.add(Map.of("pageId",patch.pageId(),"success",false,"message",e.getMessage()));}return result;
 });}
 @Tool(description="创建指标METRIC/术语GLOSSARY/关联RELATIONSHIP/规则RULE文档草稿，显式绑定资源，objectId来自读取到的表结构镜像；不发布。")
 public String create_knowledge_document(String type,String title,String content,List<Binding> bindings,@ToolParam(required=false) String evidence){return support.invoke("create_knowledge_document",()->{
  datasets.checkCurrent(principal,scope);if(bindings==null||bindings.isEmpty()||bindings.stream().anyMatch(b->!objects.contains(b.objectId())))throw new IllegalArgumentException("请选择当前范围的关联资源");
  var change=edit.create(principal,session,userMessage,space,new CreateCommand(type,title,content,bindings),evidence);allowed.add(change.pageId());support.detail("WIKI_DRAFT_CHANGE:",change);return change;
 });}
}
