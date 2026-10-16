package com.meper.chatbi.domain;

import com.meper.chatbi.spi.model.QueryResultData;
import java.math.*;
import java.util.*;

/** 每次分析独立的结果工作区：不运行任意代码，数值用 BigDecimal，截断数据不得当完整集合合并。 */
public class ResultWorkspace {
 public record Result(String handle,List<String> columns,List<List<String>> sample,int rowCount,boolean truncated,List<AnalysisService.SourceQuery> sources,String operation) { }
 private record Stored(QueryResultData data,List<AnalysisService.SourceQuery> sources,String operation) { }
 private final Map<String,Stored> results=new LinkedHashMap<>();
 public synchronized Result add(AnalysisService.SourceQuery source){if(source.data()==null)throw new IllegalStateException("查询没有结果集");return store(source.data(),List.of(provenance(source)),"QUERY");}
 private static AnalysisService.SourceQuery provenance(AnalysisService.SourceQuery s){return new AnalysisService.SourceQuery(s.executionId(),s.source(),s.datasourceId(),s.queriedAt(),null,s.enforcement());}
 private Stored get(String handle){var r=results.get(handle);if(r==null)throw new IllegalArgumentException("结果引用不属于本轮，请重新查询");if(r.data().rowsTruncated()||r.data().columnsTruncated())throw new IllegalArgumentException("源结果已截断，请先在数据库聚合或缩小范围，不能据此生成完整统计");return r;}
 private Result store(QueryResultData data,List<AnalysisService.SourceQuery> sources,String operation){
  if(results.size()>=24||data.rows().size()>5000)throw new IllegalStateException("分析工作区达到结果/行数预算，请缩小范围");
  if(data.columns().stream().distinct().count()!=data.columns().size())throw new IllegalArgumentException("结果列名重复，请在 SQL 中设置唯一别名");
  long chars=results.values().stream().mapToLong(r->size(r.data())).sum()+size(data);if(chars>1000000)throw new IllegalStateException("分析结果字符预算不足，请聚合后查询");
  String handle=UUID.randomUUID().toString();results.put(handle,new Stored(data,List.copyOf(sources),operation));
  return new Result(handle,data.columns(),data.rows().subList(0,Math.min(30,data.rows().size())),data.rows().size(),data.rowsTruncated()||data.columnsTruncated(),sources,operation);
 }
 private static long size(QueryResultData data){long total=0;for(var row:data.rows())for(String cell:row)total+=cell==null?0:cell.length();return total;}
 private int column(Stored r,String name){int idx=r.data().columns().indexOf(name);if(idx<0)throw new IllegalArgumentException("结果列不存在："+name);return idx;}
 public synchronized Result join(String left,String right,String leftKey,String rightKey,String mode,String cardinality){
  var l=get(left);var r=get(right);if(!Set.of("LEFT","INNER").contains(mode))throw new IllegalArgumentException("关联方式须为 LEFT 或 INNER");
  if(!Set.of("ONE_TO_ONE","MANY_TO_ONE","ONE_TO_MANY","MANY_TO_MANY").contains(cardinality))throw new IllegalArgumentException("请明确关联基数");
  int li=column(l,leftKey),ri=column(r,rightKey);Map<String,List<List<String>>> index=new LinkedHashMap<>();
  for(var row:r.data().rows())if(row.get(ri)!=null)index.computeIfAbsent(row.get(ri),k->new ArrayList<>()).add(row);
  if((cardinality.equals("ONE_TO_ONE")||cardinality.equals("MANY_TO_ONE"))&&index.values().stream().anyMatch(v->v.size()>1))throw new IllegalArgumentException("右侧关联键不唯一，与声明基数不符，请先聚合退款等明细");
  if(cardinality.equals("ONE_TO_ONE")||cardinality.equals("ONE_TO_MANY")){Set<String> keys=new HashSet<>();for(var row:l.data().rows())if(row.get(li)!=null&&!keys.add(row.get(li)))throw new IllegalArgumentException("左侧关联键不唯一，与声明基数不符");}
  List<String> cols=new ArrayList<>();l.data().columns().forEach(c->cols.add("left."+c));r.data().columns().forEach(c->cols.add("right."+c));List<List<String>> rows=new ArrayList<>();
  for(var row:l.data().rows()){var matches=row.get(li)==null?null:index.get(row.get(li));if(matches==null&&mode.equals("LEFT"))matches=Collections.singletonList(new ArrayList<>(Collections.nCopies(r.data().columns().size(),null)));if(matches==null)continue;
   for(var match:matches){if(rows.size()>=5000)throw new IllegalStateException("关联结果过大，请先聚合");var merged=new ArrayList<>(row);merged.addAll(match);rows.add(merged);}}
  return store(new QueryResultData(cols,rows,false,false),mergeSources(l,r),"JOIN "+mode+" "+cardinality);
 }
 public synchronized Result calculate(String handle,String output,String leftColumn,String operator,String rightColumn,String constant,String nullPolicy){
  var r=get(handle);if(output==null||output.isBlank()||r.data().columns().contains(output))throw new IllegalArgumentException("新列名无效或重复");
  int li=column(r,leftColumn);Integer ri=rightColumn==null||rightColumn.isBlank()?null:column(r,rightColumn);
  if(ri==null&&(constant==null||constant.isBlank()))throw new IllegalArgumentException("需提供右侧列或常量");
  if(!Set.of("ZERO","REJECT").contains(nullPolicy))throw new IllegalArgumentException("请明确空值口径 ZERO 或 REJECT");
  List<List<String>> rows=new ArrayList<>();for(var row:r.data().rows()){BigDecimal a=number(row.get(li),nullPolicy),b=number(ri==null?constant:row.get(ri),nullPolicy);
   BigDecimal value=switch(operator){case "ADD"->a.add(b);case "SUBTRACT"->a.subtract(b);case "MULTIPLY"->a.multiply(b);case "DIVIDE"->{if(b.signum()==0)throw new IllegalArgumentException("除数不能为零");yield a.divide(b,16,RoundingMode.HALF_UP);}default->throw new IllegalArgumentException("不支持的计算操作");};
   var next=new ArrayList<>(row);next.add(value.stripTrailingZeros().toPlainString());rows.add(next);}
  var cols=new ArrayList<>(r.data().columns());cols.add(output);return store(new QueryResultData(cols,rows,false,false),r.sources(),"CALCULATE "+output+" "+operator);
 }
 public synchronized Result aggregate(String handle,List<String> groupColumns,List<String> sumColumns,boolean count,String nullPolicy){
  var r=get(handle);if(groupColumns==null||sumColumns==null||groupColumns.size()>10||sumColumns.size()>20||(sumColumns.isEmpty()&&!count))throw new IllegalArgumentException("分组/汇总配置无效");
  if(!Set.of("ZERO","REJECT").contains(nullPolicy))throw new IllegalArgumentException("请明确空值口径 ZERO 或 REJECT");
  var groups=groupColumns.stream().map(c->column(r,c)).toList();var sums=sumColumns.stream().map(c->column(r,c)).toList();Map<List<String>,BigDecimal[]> values=new LinkedHashMap<>();Map<List<String>,Integer> counts=new HashMap<>();
  for(var row:r.data().rows()){List<String> key=new ArrayList<>();for(int i:groups)key.add(row.get(i));var totals=values.computeIfAbsent(key,k->{BigDecimal[] a=new BigDecimal[sums.size()];Arrays.fill(a,BigDecimal.ZERO);return a;});for(int i=0;i<sums.size();i++)totals[i]=totals[i].add(number(row.get(sums.get(i)),nullPolicy));counts.merge(key,1,Integer::sum);}
  List<String> cols=new ArrayList<>(groupColumns);sumColumns.forEach(c->cols.add("sum."+c));if(count)cols.add("row_count");List<List<String>> rows=new ArrayList<>();values.forEach((k,v)->{List<String> row=new ArrayList<>(k);for(var value:v)row.add(value.stripTrailingZeros().toPlainString());if(count)row.add(String.valueOf(counts.get(k)));rows.add(row);});
  return store(new QueryResultData(cols,rows,false,false),r.sources(),"AGGREGATE");
 }
 private static BigDecimal number(String value,String nullPolicy){if(value==null){if(nullPolicy.equals("ZERO"))return BigDecimal.ZERO;throw new IllegalArgumentException("结果包含空值，请确认空值口径");}try{return new BigDecimal(value);}catch(NumberFormatException e){throw new IllegalArgumentException("结果包含非数值内容，不能执行金额计算");}}
 private static List<AnalysisService.SourceQuery> mergeSources(Stored a,Stored b){var all=new LinkedHashMap<Long,AnalysisService.SourceQuery>();a.sources().forEach(x->all.put(x.executionId(),x));b.sources().forEach(x->all.put(x.executionId(),x));return List.copyOf(all.values());}
}
