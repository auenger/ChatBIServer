package com.meper.chatbi.domain;
import com.meper.chatbi.spi.model.QueryResultData;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ResultWorkspaceTest {
 private static AnalysisService.SourceQuery source(List<String> cols,List<List<String>> rows,boolean truncated){return new AnalysisService.SourceQuery(1,"fixture",1,Instant.now(),new QueryResultData(cols,rows,false,truncated));}
 @Test void rejectsTruncatedInputsAndUnprovenJoinCardinality(){
  var work=new ResultWorkspace();var left=work.add(source(List.of("id","value"),List.of(List.of("1","0.1")),false));
  var right=work.add(source(List.of("id","value"),List.of(List.of("1","0.2"),List.of("1","0.3")),false));
  assertThrows(IllegalArgumentException.class,()->work.join(left.handle(),right.handle(),"id","id","LEFT","MANY_TO_ONE"));
  var partial=work.add(source(List.of("value"),List.of(List.of("1")),true));
  assertThrows(IllegalArgumentException.class,()->work.aggregate(partial.handle(),List.of(),List.of("value"),false,"REJECT"));
  assertThrows(IllegalArgumentException.class,()->work.aggregate("another-run",List.of(),List.of("value"),false,"REJECT"));
 }
 @Test void exactDecimalAndExplicitNullSemantics(){
  var work=new ResultWorkspace();var values=work.add(source(List.of("value"),List.of(List.of("0.1"),List.of("0.2")),false));
  assertEquals("0.3",work.aggregate(values.handle(),List.of(),List.of("value"),false,"REJECT").sample().get(0).get(0));
  var nullable=work.add(source(List.of("id","value"),List.of(Arrays.asList(null,"2")),false));
  var same=work.add(source(List.of("id","extra"),List.of(Arrays.asList(null,"3")),false));
  var joined=work.join(nullable.handle(),same.handle(),"id","id","LEFT","ONE_TO_ONE");assertNull(joined.sample().get(0).get(3),"SQL NULL不按空字符串互相匹配");
  assertThrows(IllegalArgumentException.class,()->work.calculate(joined.handle(),"net","left.value","SUBTRACT","right.extra",null,"REJECT"));
  assertEquals("2",work.calculate(joined.handle(),"net","left.value","SUBTRACT","right.extra",null,"ZERO").sample().get(0).get(4));
 }
}
