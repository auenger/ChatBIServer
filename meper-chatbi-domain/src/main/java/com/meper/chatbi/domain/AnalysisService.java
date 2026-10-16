package com.meper.chatbi.domain;

import com.meper.chatbi.domain.DatasetService.*;
import com.meper.chatbi.query.QueryScopeValidator;
import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.model.QueryResultData;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** 数据集查询统一复用 Workbench，解析和范围检查不能由模型参数绕过。 */
@Service
public class AnalysisService {
 public record SourceQuery(long executionId,String source,long datasourceId,Instant queriedAt,QueryResultData data,com.meper.chatbi.spi.EnforcementState enforcement) {
  public SourceQuery(long executionId,String source,long datasourceId,Instant queriedAt,QueryResultData data){this(executionId,source,datasourceId,queriedAt,data,com.meper.chatbi.spi.EnforcementState.BOOTSTRAP_ADMIN_UNRESTRICTED);}
 }
 private final DatasetService datasets;private final DataSourceService dataSources;private final WorkbenchService workbench;private final MetadataService metadata;
 private final QueryScopeValidator validator=new QueryScopeValidator();
 public AnalysisService(DatasetService datasets,DataSourceService dataSources,WorkbenchService workbench,MetadataService metadata){this.datasets=datasets;this.dataSources=dataSources;this.workbench=workbench;this.metadata=metadata;}
 public SourceScope source(String principal,AnalysisScope scope,String alias){datasets.checkCurrent(principal,scope);return scope.sources().stream().filter(s->s.alias().equals(alias)).findFirst().orElseThrow(()->new IllegalArgumentException("数据库别名不属于当前范围"));}
 public List<String> namespaces(String principal,AnalysisScope scope,String alias){var s=source(principal,scope,alias);if(!s.unrestricted())return s.resources().stream().map(r->r.namespace()).distinct().toList();var profile=dataSources.get(s.datasourceId());var names=metadata.namespaces(s.datasourceId());return profile.type()==com.meper.chatbi.spi.DatabaseType.MYSQL&&profile.databaseName()!=null&&!profile.databaseName().isBlank()?names.stream().filter(n->n.equals(profile.databaseName())).toList():names;}
 public Object tables(String principal,AnalysisScope scope,String alias,String namespace){var s=source(principal,scope,alias);if(!namespaces(principal,scope,alias).contains(namespace))throw new IllegalArgumentException("命名空间不属于当前范围");return metadata.tables(s.datasourceId(),namespace,null).stream().filter(t->s.unrestricted()||s.resources().contains(new com.meper.chatbi.storage.model.DatasetRecords.Resource(namespace,t.name()))).toList();}
 public Object schema(String principal,AnalysisScope scope,String alias,String namespace,String table){var s=source(principal,scope,alias);if(!namespaces(principal,scope,alias).contains(namespace))throw new IllegalArgumentException("命名空间不属于当前数据库范围");if(!s.unrestricted()&&!s.resources().contains(new com.meper.chatbi.storage.model.DatasetRecords.Resource(namespace,table)))throw new IllegalArgumentException("表不属于当前范围");return java.util.Map.of("databaseType",dataSources.get(s.datasourceId()).type(),"table",metadata.tableDetail(s.datasourceId(),namespace,table));}
 public SourceQuery query(String principal,AnalysisScope scope,String alias,String namespace,String sql){var s=source(principal,scope,alias);var profile=dataSources.get(s.datasourceId());
  String ns=namespace;if(ns!=null&&!ns.isBlank()&&!namespaces(principal,scope,alias).contains(ns))throw new IllegalArgumentException("默认命名空间不属于当前范围");
  if(ns==null||ns.isBlank()){var names=namespaces(principal,scope,alias);ns=names.size()==1?names.get(0):null;}
  Set<QueryScopeValidator.Resource> allowed=new HashSet<>();for(var r:s.resources())allowed.add(new QueryScopeValidator.Resource(r.namespace(),r.table()));
  String checked=validator.validate(DialectRegistry.get(profile.type()),profile.databaseName(),ns,sql,allowed,s.unrestricted());
  var result=workbench.execute(principal,s.datasourceId(),checked,200);var statement=result.statements().get(0);
  if(statement.error()!=null)throw new IllegalStateException("查询失败："+statement.error());
  datasets.checkCurrent(principal,scope);
  return new SourceQuery(result.executionId(),alias,s.datasourceId(),Instant.now(),statement.resultData(),result.enforcement());
 }
}
