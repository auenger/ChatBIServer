package com.meper.chatbi.query;
import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.SqlDialect;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QueryScopeValidatorTest {
 private SqlDialect dialect(){var d=mock(SqlDialect.class);when(d.type()).thenReturn(DatabaseType.MYSQL);when(d.quoteIdentifier(anyString())).thenAnswer(i->"`"+i.getArgument(0)+"`");return d;}
 @Test void resolvesEveryJoinSubqueryAndCteBeforeExecution(){
  var validator=new QueryScopeValidator();var allowed=Set.of(new QueryScopeValidator.Resource("sales","orders"));
  String sql=validator.validate(dialect(),"sales","sales","WITH x AS (SELECT id FROM orders) SELECT * FROM x",allowed,false);
  assertTrue(sql.contains("`sales`.`orders`"));
  assertThrows(IllegalArgumentException.class,()->validator.validate(dialect(),"sales","sales","SELECT * FROM control.meper_credential",Set.of(),true),"数据库模式不能跨到连接账号可读的其他库");
  for(String invalid:new String[]{"SELECT * FROM orders o JOIN hidden h ON o.id=h.id","SELECT * FROM orders WHERE id IN(SELECT id FROM hidden)","WITH x AS(SELECT * FROM hidden) SELECT * FROM x","SELECT * FROM another.orders","SELECT * INTO outfile '/tmp/blocked' FROM orders","SELECT other.COALESCE(id,0) FROM orders","SELECT LOAD_FILE('/tmp/blocked')","SELECT * FROM orders FOR UPDATE","SELECT * FROM orders; DELETE FROM orders"})
   assertThrows(IllegalArgumentException.class,()->validator.validate(dialect(),"sales","sales",invalid,allowed,false),invalid);
 }
}
