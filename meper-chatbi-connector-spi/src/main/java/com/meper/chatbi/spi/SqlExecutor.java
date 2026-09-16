package com.meper.chatbi.spi;

import com.meper.chatbi.spi.model.AnalyzedStatement;
import com.meper.chatbi.spi.model.ExecutionContext;
import com.meper.chatbi.spi.model.ExecutionLimits;
import com.meper.chatbi.spi.model.SqlExecutionResult;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Consumer;

/**
 * SQL 执行契约。实现必须满足（实施方案 §4）：
 * 逐语句 queryTimeout、maxRows 硬截断、单元格/列数限额、错误摘要脱敏；
 * 阶段 1 为逐语句 autocommit、语句级失败即停止（剩余语句不执行），事务语义属 P3。
 */
public interface SqlExecutor {

    /**
     * @param context    执行上下文（服务端生成）
     * @param dataSource 连接来源（池或一次性）
     * @param statements 已分类语句
     * @param limits     执行限额
     * @param cancelHook 接收当前 Statement 供外部取消（可为 null；异步化属 P3）
     */
    SqlExecutionResult execute(ExecutionContext context,
                               DataSource dataSource,
                               List<AnalyzedStatement> statements,
                               ExecutionLimits limits,
                               Consumer<java.sql.Statement> cancelHook);
}
