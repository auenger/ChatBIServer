package com.meper.chatbi.domain;

import com.meper.chatbi.spi.model.TableDetail;
import com.meper.chatbi.wiki.WikiService;
import com.meper.chatbi.wiki.WikiModels.SyncResult;
import org.springframework.stereotype.Service;
import java.util.ArrayList;

/** 采集业务库结构的领域用例：只调用元数据服务，全部语义写入控制库。 */
@Service
public class WikiSyncService {
    private final MetadataService metadata;
    private final DataSourceService dataSources;
    private final WikiService wiki;
    public WikiSyncService(MetadataService metadata,DataSourceService dataSources,WikiService wiki) {
        this.metadata=metadata;this.dataSources=dataSources;this.wiki=wiki;
    }
    public SyncResult sync(String principal,long ds,String namespace,String table) {
        wiki.authorizeDatasource(principal,ds);dataSources.get(ds);
        if(namespace==null||namespace.isBlank()) throw new IllegalArgumentException("请选择库或 Schema");
        long run=wiki.beginSync(principal,ds,namespace,table);
        try {
            if(!metadata.namespaces(ds).contains(namespace)) throw new IllegalArgumentException("命名空间不存在");
            var tables=metadata.tables(ds,namespace,null);
            var selected=tables.stream().filter(t->table==null||table.isBlank()||t.name().equals(table)).toList();
            if(selected.size()>200) throw new IllegalArgumentException("单次最多同步 200 张表，请缩小采集范围");
            var details=new ArrayList<TableDetail>();
            for(var t:selected) details.add(metadata.tableDetail(ds,namespace,t.name()));
            // 所有结构读取成功后再原子覆盖镜像，失败不把旧对象判成消失。
            return wiki.sync(principal,ds,namespace,table,details,run);
        } catch(RuntimeException e) {wiki.failSync(run);throw e;}
    }
}
