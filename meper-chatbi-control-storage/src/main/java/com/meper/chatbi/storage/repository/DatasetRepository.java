package com.meper.chatbi.storage.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meper.chatbi.storage.model.DatasetRecords.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.*;

/** 数据集/知识空间/会话作用域仓储，全部在控制库。 */
@Repository
public class DatasetRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public DatasetRepository(JdbcClient jdbc,ObjectMapper json) {this.jdbc=jdbc;this.json=json;}
    private long insert(String query,Map<String,?> params) {
        var key=new GeneratedKeyHolder();jdbc.sql(query).params(params).update(key);
        return ((Number)key.getKeys().values().iterator().next()).longValue();
    }
    private static Map<String,Object> args(Object... pairs) {
        var m=new HashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;
    }
    public List<Dataset> list() {return jdbc.sql("SELECT * FROM meper_dataset ORDER BY id").query((r,n)->new Dataset(r.getLong("id"),r.getString("name"),r.getString("description"),r.getLong("revision"),r.getBoolean("enabled"),r.getString("updated_by"),r.getTimestamp("updated_at").toInstant())).list();}
    public Dataset get(long id) {return list().stream().filter(d->d.id()==id).findFirst().orElseThrow(()->new NoSuchElementException("数据集不存在"));}
    public void lock(long id) {jdbc.sql("SELECT id FROM meper_dataset WHERE id=:id FOR UPDATE").param("id",id).query(Long.class).optional().orElseThrow(()->new NoSuchElementException("数据集不存在"));}
    public long create(String name,String description,boolean enabled,String principal) {return insert("INSERT INTO meper_dataset(name,description,enabled,updated_by) VALUES(:name,:description,:enabled,:principal)",args("name",name,"description",description,"enabled",enabled,"principal",principal));}
    public void update(long id,long expected,String name,String description,boolean enabled,String principal) {
        if(jdbc.sql("UPDATE meper_dataset SET name=:name,description=:description,enabled=:enabled,revision=revision+1,updated_by=:principal WHERE id=:id AND revision=:expected")
                .params(args("name",name,"description",description,"enabled",enabled,"principal",principal,"id",id,"expected",expected)).update()!=1)
            throw new IllegalStateException("数据集已更新，请重新加载");
    }
    public List<Member> members(long id) {return jdbc.sql("SELECT * FROM meper_dataset_member WHERE dataset_id=:id ORDER BY id").param("id",id).query((r,n)->new Member(r.getLong("id"),r.getLong("dataset_id"),r.getLong("datasource_id"),r.getString("alias_name"),resources(r.getString("resources_json")),r.getObject("wiki_space_id",Long.class))).list();}
    public void members(long id,List<Member> members) {
        jdbc.sql("DELETE FROM meper_dataset_member WHERE dataset_id=:id").param("id",id).update();
        for(var m:members) jdbc.sql("INSERT INTO meper_dataset_member(dataset_id,datasource_id,alias_name,resources_json,wiki_space_id) VALUES(:id,:ds,:alias,:resources,:space)")
                .params(args("id",id,"ds",m.datasourceId(),"alias",m.alias(),"resources",write(m.resources()),"space",m.wikiSpaceId())).update();
    }
    public List<Space> spaces() {return jdbc.sql("SELECT * FROM meper_wiki_space ORDER BY id").query((r,n)->new Space(r.getLong("id"),r.getString("name"),r.getObject("datasource_id",Long.class),r.getObject("dataset_id",Long.class),r.getBoolean("is_base"))).list();}
    public Space space(long id) {return spaces().stream().filter(s->s.id()==id).findFirst().orElseThrow(()->new NoSuchElementException("知识空间不存在"));}
    public long createSpace(String name,Long ds,Long dataset,boolean base,String principal) {
        return insert("INSERT INTO meper_wiki_space(name,datasource_id,dataset_id,is_base,created_by) VALUES(:name,:ds,:dataset,:base,:principal)",args("name",name,"ds",ds,"dataset",dataset,"base",base,"principal",principal));
    }
    public long defaultSpace(long ds) {return jdbc.sql("SELECT space_id FROM meper_datasource_wiki_default WHERE datasource_id=:ds").param("ds",ds).query(Long.class).single();}
    public void defaultSpace(long ds,long space) {jdbc.sql("INSERT INTO meper_datasource_wiki_default(datasource_id,space_id) VALUES(:ds,:space) ON DUPLICATE KEY UPDATE space_id=:space").params(args("ds",ds,"space",space)).update();}
    public Optional<ChatScope> chatScope(String session) {return jdbc.sql("SELECT * FROM meper_chat_scope WHERE session_id=:session").param("session",session).query((r,n)->new ChatScope(r.getString("session_id"),r.getString("subject"),r.getString("scope_type"),r.getLong("scope_id"),r.getString("scope_version"),r.getLong("provider_id"),r.getString("namespace_name"),r.getString("mode"),r.getObject("space_id",Long.class))).optional();}
    public void createChatScope(ChatScope s) {jdbc.sql("INSERT INTO meper_chat_scope(session_id,subject,scope_type,scope_id,scope_version,provider_id,namespace_name,mode,space_id) VALUES(:session,:subject,:type,:id,:version,:provider,:ns,:mode,:space)").params(args("session",s.sessionId(),"subject",s.subject(),"type",s.scopeType(),"id",s.scopeId(),"version",s.scopeVersion(),"provider",s.providerId(),"ns",s.namespace(),"mode",s.mode(),"space",s.spaceId())).update();}
    public void deleteChatScope(String session,String subject) {jdbc.sql("DELETE FROM meper_chat_scope WHERE session_id=:session AND subject=:subject").params(args("session",session,"subject",subject)).update();}
    /** 固定页面编辑会话可继续编辑自己创建的文档，仍须与当前可见范围取交集。 */
    public Set<Long> createdPages(String session) {return new HashSet<>(jdbc.sql("SELECT DISTINCT page_id FROM meper_wiki_edit_origin WHERE session_id=:session AND semantic_key='created' AND column_name=''").param("session",session).query(Long.class).list());}
    public Set<Long> changedPages(String session) {return new HashSet<>(jdbc.sql("SELECT DISTINCT page_id FROM meper_wiki_edit_origin WHERE session_id=:session").param("session",session).query(Long.class).list());}
    public List<Map<String,Object>> origins(long page) {return jdbc.sql("SELECT column_name AS columnName,semantic_key AS semanticKey,origin,evidence,session_id AS sessionId FROM meper_wiki_edit_origin WHERE page_id=:page").param("page",page).query((r,n)->{Map<String,Object> m=new HashMap<>();m.put("column",r.getString("columnName"));m.put("key",r.getString("semanticKey"));m.put("origin",r.getString("origin"));m.put("evidence",r.getString("evidence"));m.put("sessionId",r.getString("sessionId"));return m;}).list();}
    public void origin(long page,String column,String key,String origin,String evidence,String session) {jdbc.sql("INSERT INTO meper_wiki_edit_origin(page_id,column_name,semantic_key,origin,evidence,session_id) VALUES(:page,:column,:key,:origin,:evidence,:session) ON DUPLICATE KEY UPDATE origin=:origin,evidence=:evidence,session_id=:session").params(args("page",page,"column",column,"key",key,"origin",origin,"evidence",evidence,"session",session)).update();}
    private List<Resource> resources(String raw) {try{return json.readValue(raw,new TypeReference<List<Resource>>(){});}catch(Exception e){throw new IllegalStateException("数据集范围损坏",e);}}
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
