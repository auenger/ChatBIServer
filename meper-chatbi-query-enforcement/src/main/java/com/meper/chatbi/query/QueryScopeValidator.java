package com.meper.chatbi.query;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.meper.chatbi.spi.SqlDialect;
import java.util.*;

/** 分析入口的显式资源范围校验：无法解析则拒绝，不使用分类器 fallback 授权。 */
public final class QueryScopeValidator {
    public record Resource(String namespace,String table) { }
    private static final Set<String> FUNCTIONS=Set.of("COALESCE","NULLIF","ROUND","TRUNC","ABS","DATE","DATE_TRUNC","DATE_FORMAT","EXTRACT","TO_CHAR","TO_DATE","TO_NUMBER","CONCAT","LOWER","UPPER","LENGTH","CHAR_LENGTH","SUBSTRING","SUBSTR","TRIM","REPLACE","LEFT","RIGHT","DATEADD","DATEDIFF","YEAR","MONTH","DAY","ISNULL","NVL","IFNULL","IF","GREATEST","LEAST","NOW","CURRENT_DATE","CURRENT_TIMESTAMP","GETDATE","SYSDATE","FLOOR","CEIL","CEILING","POWER","MOD");
    public String validate(SqlDialect dialect,String catalog,String defaultNamespace,String sql,Set<Resource> allowed,boolean unrestricted) {
        DbType type=switch(dialect.type()){case MYSQL->DbType.mysql;case POSTGRESQL->DbType.postgresql;case SQLSERVER->DbType.sqlserver;case ORACLE->DbType.oracle;};
        List<SQLStatement> statements;
        try{statements=SQLUtils.parseStatements(sql,type);}catch(RuntimeException e){throw new IllegalArgumentException("UNRESOLVED_TARGET：无法可靠解析查询目标");}
        if(statements.size()!=1||!(statements.get(0) instanceof SQLSelectStatement))throw new IllegalArgumentException("只允许一条 SELECT 查询");
        var statement=statements.get(0);
        statement.accept(new SQLASTVisitorAdapter(){
            @Override public void preVisit(SQLObject node) {
                if(node instanceof SQLStatement&&!(node instanceof SQLSelectStatement))throw new IllegalArgumentException("拒绝带写入子语句的查询");
            }
            @Override public boolean visit(SQLSelectQueryBlock block) {
                if(block.getInto()!=null||block.isForUpdate())throw new IllegalArgumentException("只读分析不允许 INTO 或 FOR UPDATE");return true;
            }
            @Override public boolean visit(SQLMethodInvokeExpr method) {
                if(method.getOwner()!=null)throw new IllegalArgumentException("UNRESOLVED_FUNCTION：不允许调用范围外的自定义函数");
                if(!FUNCTIONS.contains(method.getMethodName().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("UNRESOLVED_FUNCTION：不支持或无法证明只读的函数 "+method.getMethodName());return true;
            }
            @Override public boolean visit(SQLAggregateExpr aggregate) {
                if(aggregate.getOwner()!=null||!Set.of("SUM","COUNT","MIN","MAX","AVG","STDDEV","STDDEV_POP","STDDEV_SAMP","VARIANCE","VAR_POP","VAR_SAMP","STRING_AGG","GROUP_CONCAT","BOOL_AND","BOOL_OR").contains(aggregate.getMethodName().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("UNRESOLVED_FUNCTION：不支持自定义聚合函数");return true;
            }
            @Override public boolean visit(SQLExprTableSource table) {
                String namespace,name;
                if(table.getExpr() instanceof SQLIdentifierExpr id) {
                    name=unquote(id.getName());if(cteReference(table,name))return true;
                    namespace=defaultNamespace;if(namespace==null||namespace.isBlank())throw new IllegalArgumentException("请使用 Schema/库名限定表，或提供默认命名空间");
                } else if(table.getExpr() instanceof SQLPropertyExpr property) {
                    name=unquote(property.getName());
                    if(property.getOwner() instanceof SQLIdentifierExpr owner)namespace=unquote(owner.getName());
                    else if(property.getOwner() instanceof SQLPropertyExpr owner&&owner.getOwner() instanceof SQLIdentifierExpr database&&Objects.equals(unquote(database.getName()),catalog))namespace=unquote(owner.getName());
                    else throw new IllegalArgumentException("UNRESOLVED_TARGET：不允许跨连接/链接服务器对象");
                } else throw new IllegalArgumentException("UNRESOLVED_TARGET：不支持函数或动态表源");
                if(unrestricted&&dialect.type()==com.meper.chatbi.spi.DatabaseType.MYSQL&&catalog!=null&&!catalog.isBlank()&&!catalog.equals(namespace))throw new IllegalArgumentException("目标库不属于所选数据库："+namespace);
                if(!unrestricted&&!allowed.contains(new Resource(namespace,name)))throw new IllegalArgumentException("目标表不属于当前数据集："+namespace+"."+name);
                // 限定后的 SQL 实际执行目标与验证目标一致，不能依赖连接默认 schema。
                table.setExpr(new SQLPropertyExpr(new SQLIdentifierExpr(dialect.quoteIdentifier(namespace)),dialect.quoteIdentifier(name)));
                return true;
            }
        });
        return SQLUtils.toSQLString(statement,type);
    }
    private static boolean cteReference(SQLObject node,String name) {
        for(SQLObject p=node.getParent();p!=null;p=p.getParent()) {
            SQLWithSubqueryClause with=p instanceof SQLSelect s?s.getWithSubQuery():p instanceof SQLSelectQueryBlock b?b.getWith():null;
            if(with==null)continue;
            for(var entry:with.getEntries())if(unquote(entry.getAlias()).equals(name)) {
                for(SQLObject ancestor=node.getParent();ancestor!=null&&ancestor!=p;ancestor=ancestor.getParent())if(ancestor==entry)return false;
                return true;
            }
        }
        return false;
    }
    private static String unquote(String s) {if(s==null)return "";if(s.length()>1&&((s.startsWith("`")&&s.endsWith("`"))||(s.startsWith("\"")&&s.endsWith("\""))||(s.startsWith("[")&&s.endsWith("]")))){String q=s.substring(0,1);return s.substring(1,s.length()-1).replace(q+q,q).replace("]]","]");}return s;}
}
