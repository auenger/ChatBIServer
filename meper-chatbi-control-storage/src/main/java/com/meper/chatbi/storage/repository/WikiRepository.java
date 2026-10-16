package com.meper.chatbi.storage.repository;

import com.meper.chatbi.storage.model.WikiRecords.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** DataWiki 仓储，仅访问控制库；事务边界由 Wiki 服务定义。 */
@Repository
public class WikiRepository {
    private final JdbcClient jdbc;
    public WikiRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    private JdbcClient.StatementSpec sql(String sql, Map<String, ?> args) {
        return jdbc.sql(sql).params(args);
    }
    private long insert(String sql, Map<String, ?> args) {
        var keys = new GeneratedKeyHolder(); sql(sql, args).update(keys);
        return ((Number) keys.getKeys().values().iterator().next()).longValue();
    }
    private static Map<String,Object> args(Object... pairs) {
        var m = new HashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]);
        return m;
    }
    public void lockDatasource(long ds) {
        sql("SELECT id FROM meper_datasource_profile WHERE id=:ds FOR UPDATE",args("ds",ds)).query(Long.class).optional().orElseThrow(()->new NoSuchElementException("数据源不存在"));
    }
    public List<ObjectRecord> objects(long ds) {
        return sql("SELECT * FROM meper_metadata_object WHERE datasource_id=:ds ORDER BY namespace_name,object_name",args("ds",ds)).query(WikiRepository::object).list();
    }
    public ObjectRecord object(long id) {
        return sql("SELECT * FROM meper_metadata_object WHERE id=:id",args("id",id)).query(WikiRepository::object).optional().orElseThrow(() -> new NoSuchElementException("对象不存在"));
    }
    public long upsertObject(long ds, String ns, String name, String type, String remarks, String structure, String fp) {
        var existing=sql("SELECT id FROM meper_metadata_object WHERE datasource_id=:ds AND namespace_name=:ns AND object_name=:name",args("ds",ds,"ns",ns,"name",name)).query(Long.class).optional();
        if(existing.isEmpty()) return insert("INSERT INTO meper_metadata_object(datasource_id,namespace_name,object_name,object_type,original_remarks,structure_json,fingerprint,semantics_json) VALUES(:ds,:ns,:name,:type,:remarks,:structure,:fp,'{}')",args("ds",ds,"ns",ns,"name",name,"type",type,"remarks",remarks,"structure",structure,"fp",fp));
        long id=existing.get();
        sql("UPDATE meper_metadata_object SET object_type=:type,original_remarks=:remarks,structure_json=:structure,fingerprint=:fp,state='ACTIVE',synced_at=CURRENT_TIMESTAMP(6) WHERE id=:id",args("id",id,"type",type,"remarks",remarks,"structure",structure,"fp",fp)).update();
        return id;
    }
    public void objectSemantics(long id,String json) {sql("UPDATE meper_metadata_object SET semantics_json=:json WHERE id=:id",args("id",id,"json",json)).update();}
    public void missingObject(long id) {sql("UPDATE meper_metadata_object SET state='MISSING' WHERE id=:id",args("id",id)).update();markReview(id);}
    public void markReview(long id) {
        sql("UPDATE meper_wiki_page SET needs_review=1,dirty=1,draft_version=draft_version+1 WHERE page_type<>'STRUCTURE' AND (published_revision>0 OR dirty=1 OR draft_content<>'') AND (object_id=:id OR id IN (SELECT page_id FROM meper_wiki_resource_binding WHERE object_id=:id))",args("id",id)).update();
    }
    public List<ColumnRecord> columns(long id) {
        return sql("SELECT * FROM meper_metadata_column WHERE object_id=:id ORDER BY ordinal_position",args("id",id)).query((rs,n)->new ColumnRecord(rs.getLong("id"),rs.getLong("object_id"),rs.getString("column_name"),rs.getInt("ordinal_position"),rs.getString("structure_json"),rs.getString("semantics_json"),rs.getString("state"))).list();
    }
    public void resetColumns(long id) {sql("UPDATE meper_metadata_column SET state='MISSING' WHERE object_id=:id",args("id",id)).update();}
    public void upsertColumn(long id,String name,int ordinal,String structure) {
        sql("INSERT INTO meper_metadata_column(object_id,column_name,ordinal_position,structure_json,semantics_json) VALUES(:id,:name,:ordinal,:structure,'{}') ON DUPLICATE KEY UPDATE ordinal_position=:ordinal,structure_json=:structure,state='ACTIVE'",args("id",id,"name",name,"ordinal",ordinal,"structure",structure)).update();
    }
    public void columnSemantics(long id,String name,String json) {
        if(sql("UPDATE meper_metadata_column SET semantics_json=:json WHERE object_id=:id AND column_name=:name AND state='ACTIVE'",args("id",id,"name",name,"json",json)).update()!=1) throw new IllegalArgumentException("字段不存在或已消失: "+name);
    }
    public List<PageRecord> pages(long ds) {
        return sql("SELECT * FROM meper_wiki_page WHERE space_id=:ds ORDER BY page_type,path",args("ds",ds)).query(WikiRepository::page).list();
    }
    public PageRecord page(long id) {return sql("SELECT * FROM meper_wiki_page WHERE id=:id",args("id",id)).query(WikiRepository::page).optional().orElseThrow(()->new NoSuchElementException("Wiki 页面不存在"));}
    public void clearDirty(long id) {sql("UPDATE meper_wiki_page SET dirty=0 WHERE id=:id",args("id",id)).update();}
    public PageRecord lockPage(long id) {return sql("SELECT * FROM meper_wiki_page WHERE id=:id FOR UPDATE",args("id",id)).query(WikiRepository::page).optional().orElseThrow(()->new NoSuchElementException("Wiki 页面不存在"));}
    public long createPage(Long ds,long spaceId,Long objectId,String type,String path,String title,String content,String principal) {
        return insert("INSERT INTO meper_wiki_page(datasource_id,space_id,object_id,page_type,path,title,draft_content,updated_by) VALUES(:ds,:space,:objectId,:type,:path,:title,:content,:principal)",args("ds",ds,"space",spaceId,"objectId",objectId,"type",type,"path",path,"title",title,"content",content,"principal",principal));
    }
    public void save(long id,long expected,String title,String content,String principal) {
        int changed=sql("UPDATE meper_wiki_page SET title=:title,draft_content=:content,draft_version=draft_version+1,dirty=1,updated_by=:principal WHERE id=:id AND draft_version=:expected",args("id",id,"expected",expected,"title",title,"content",content,"principal",principal)).update();
        if(changed!=1) throw new IllegalStateException("页面已更新，请重新加载后编辑");
    }
    public void publish(PageRecord p,String content,String bindings,String editState,String principal) {
        sql("INSERT INTO meper_wiki_revision(page_id,revision,title,content,bindings_json,edit_state_json,published_by) VALUES(:id,:rev,:title,:content,:bindings,:editState,:principal)",args("id",p.id(),"rev",p.publishedRevision()+1,"title",p.title(),"content",content,"bindings",bindings,"editState",editState,"principal",principal)).update();
        sql("UPDATE meper_wiki_page SET published_revision=published_revision+1,draft_version=draft_version+1,dirty=0,needs_review=0,disabled=0,updated_by=:principal WHERE id=:id",args("id",p.id(),"principal",principal)).update();
    }
    public void disable(long id,boolean disabled,String principal) {sql("UPDATE meper_wiki_page SET disabled=:disabled,draft_version=draft_version+1,updated_by=:principal WHERE id=:id",args("id",id,"disabled",disabled,"principal",principal)).update();}
    public List<Binding> bindings(long pageId) {return sql("SELECT object_id,column_name FROM meper_wiki_resource_binding WHERE page_id=:id ORDER BY object_id,column_name",args("id",pageId)).query((rs,n)->new Binding(rs.getLong(1),rs.getString(2))).list();}
    public void bindings(long pageId,List<Binding> bindings) {
        sql("DELETE FROM meper_wiki_resource_binding WHERE page_id=:id",args("id",pageId)).update();
        for(var b:bindings) sql("INSERT INTO meper_wiki_resource_binding(page_id,object_id,column_name) VALUES(:page,:object,:column)",args("page",pageId,"object",b.objectId(),"column",b.column())).update();
    }
    public List<RevisionRecord> revisions(long id) {return sql("SELECT * FROM meper_wiki_revision WHERE page_id=:id ORDER BY revision DESC",args("id",id)).query(WikiRepository::revision).list();}
    public RevisionRecord revision(long id,long revision) {return sql("SELECT * FROM meper_wiki_revision WHERE page_id=:id AND revision=:rev",args("id",id,"rev",revision)).query(WikiRepository::revision).optional().orElseThrow(()->new NoSuchElementException("发布版本不存在"));}
    public long beginSync(long ds,String ns,String table,String principal) {
        return insert("INSERT INTO meper_metadata_sync_run(datasource_id,namespace_name,target_table,created_by) VALUES(:ds,:ns,:table,:principal)",args("ds",ds,"ns",ns,"table",table,"principal",principal));
    }
    public void finishSync(long id,String status,String detail) {sql("UPDATE meper_metadata_sync_run SET status=:status,detail=:detail,finished_at=CURRENT_TIMESTAMP(6) WHERE id=:id",args("id",id,"status",status,"detail",detail)).update();}
    public List<SyncRecord> runs(long ds) {return sql("SELECT * FROM meper_metadata_sync_run WHERE datasource_id=:ds ORDER BY id DESC LIMIT 50",args("ds",ds)).query((rs,n)->new SyncRecord(rs.getLong("id"),rs.getString("namespace_name"),rs.getString("target_table"),rs.getString("status"),rs.getString("detail"),rs.getString("created_by"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("finished_at")==null?null:rs.getTimestamp("finished_at").toInstant())).list();}
    private static ObjectRecord object(ResultSet r,int n)throws SQLException {return new ObjectRecord(r.getLong("id"),r.getLong("datasource_id"),r.getString("namespace_name"),r.getString("object_name"),r.getString("object_type"),r.getString("original_remarks"),r.getString("structure_json"),r.getString("fingerprint"),r.getString("semantics_json"),r.getString("state"),r.getTimestamp("synced_at").toInstant());}
    private static PageRecord page(ResultSet r,int n)throws SQLException {return new PageRecord(r.getLong("id"),r.getLong("datasource_id"),r.getLong("space_id"),(Long)r.getObject("object_id",Long.class),r.getString("page_type"),r.getString("path"),r.getString("title"),r.getString("draft_content"),r.getLong("draft_version"),r.getLong("published_revision"),r.getBoolean("dirty"),r.getBoolean("needs_review"),r.getBoolean("disabled"),r.getString("updated_by"),r.getTimestamp("updated_at").toInstant());}
    private static RevisionRecord revision(ResultSet r,int n)throws SQLException {return new RevisionRecord(r.getLong("page_id"),r.getLong("revision"),r.getString("title"),r.getString("content"),r.getString("bindings_json"),r.getString("edit_state_json"),r.getString("published_by"),r.getTimestamp("published_at").toInstant());}
}
