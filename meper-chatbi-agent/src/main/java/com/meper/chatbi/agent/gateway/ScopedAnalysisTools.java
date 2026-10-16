package com.meper.chatbi.agent.gateway;
import com.meper.chatbi.domain.*;
import com.meper.chatbi.domain.DatasetService.AnalysisScope;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.util.List;

/** 多源分析工具：数据源只能从服务端签发范围中选，结果运算只运行固定算法。 */
public class ScopedAnalysisTools {
 private final String principal;private final AnalysisScope scope;private final AnalysisService analysis;private final DatasetService datasets;
 private final ResultWorkspace results=new ResultWorkspace();private final ScopedToolSupport support;
 public ScopedAnalysisTools(String principal,AnalysisScope scope,AnalysisService analysis,DatasetService datasets,ScopedToolSupport support){this.principal=principal;this.scope=scope;this.analysis=analysis;this.datasets=datasets;this.support=support;}
 @Tool(description="列出当前数据库或数据集成员、数据库类型和明确的表范围；数据库别名是后续查询参数，不能自行添加数据源。")
 public String list_analysis_sources(){return support.invoke("list_analysis_sources",()->{datasets.checkCurrent(principal,scope);return scope.sources();});}
 @Tool(description="列出选定成员的可用库/Schema。")
 public String list_namespaces(@ToolParam(description="成员数据库别名") String source){return support.invoke("list_namespaces("+source+")",()->analysis.namespaces(principal,scope,source));}
 @Tool(description="列出当前分析范围内某个成员数据库的表。")
 public String list_tables(@ToolParam(description="成员数据库别名") String source,@ToolParam(description="库或Schema") String namespace){return support.invoke("list_tables("+source+")",()->analysis.tables(principal,scope,source,namespace));}
 @Tool(description="读取当前结构事实，写 SQL 前验证表与字段。")
 public String get_table_schema(@ToolParam(description="成员数据库别名") String source,@ToolParam(description="库或Schema") String namespace,@ToolParam(description="表名") String table){return support.invoke("get_table_schema("+source+"."+table+")",()->analysis.schema(principal,scope,source,namespace,table));}
 @Tool(description="在选定成员中执行一条只读SELECT，全部表必须属于分析范围，最多200行。尽量在库内聚合。返回结果handle与最多30行样本、查询时间、执行记录和截断状态；完整结果保留在本轮服务端工作区。")
 public String run_query(@ToolParam(description="成员数据库别名") String source,@ToolParam(description="默认库/Schema，建议表名显式限定") String namespace,@ToolParam(description="单条SELECT SQL") String sql){return support.invoke("run_query("+source+")",()->{var r=results.add(analysis.query(principal,scope,source,namespace,sql));support.detail("ANALYSIS_RESULT:",r);return r;});}
 @Tool(description="服务端关联本轮两个完整结果，键按字符串精确匹配，SQL NULL不互相匹配。列名会加left./right.前缀。基数与实际不符拒绝；截断结果拒绝合并。")
 public String join_results(String leftHandle,String rightHandle,String leftKey,String rightKey,
    @ToolParam(description="LEFT或INNER") String mode,@ToolParam(description="ONE_TO_ONE/MANY_TO_ONE/ONE_TO_MANY/MANY_TO_MANY，必须依据知识确认") String cardinality){return support.invoke("join_results",()->{datasets.checkCurrent(principal,scope);var r=results.join(leftHandle,rightHandle,leftKey,rightKey,mode,cardinality);support.detail("ANALYSIS_RESULT:",r);return r;});}
 @Tool(description="服务端BigDecimal计算新列，可计算金额相减、单位换算。右侧列与常量二选一，操作ADD/SUBTRACT/MULTIPLY/DIVIDE，空值口径ZERO/REJECT须明确。")
 public String calculate_column(String handle,String output,String leftColumn,String operator,
    @ToolParam(required=false) String rightColumn,@ToolParam(required=false) String constant,String nullPolicy){return support.invoke("calculate_column",()->{datasets.checkCurrent(principal,scope);var r=results.calculate(handle,output,leftColumn,operator,rightColumn,constant,nullPolicy);support.detail("ANALYSIS_RESULT:",r);return r;});}
 @Tool(description="服务端按列分组汇总金额或计数。sumColumns为数值列，groupColumns可为空，空值口径ZERO/REJECT必须依据业务定义。不允许把截断样本当完整结果汇总。")
 public String aggregate_result(String handle,List<String> groupColumns,List<String> sumColumns,boolean count,String nullPolicy){return support.invoke("aggregate_result",()->{datasets.checkCurrent(principal,scope);var r=results.aggregate(handle,groupColumns,sumColumns,count,nullPolicy);support.detail("ANALYSIS_RESULT:",r);return r;});}
}
