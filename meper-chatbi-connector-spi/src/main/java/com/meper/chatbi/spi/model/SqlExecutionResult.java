package com.meper.chatbi.spi.model;

import java.util.List;

/**
 * 一次工作台执行的完整结果（逐语句独立，阶段 1 为 autocommit 语义）。
 *
 * @param statements 逐语句结果，顺序与请求一致
 */
public record SqlExecutionResult(List<StatementResult> statements) {

    public SqlExecutionResult {
        statements = List.copyOf(statements);
    }

    public boolean allSucceeded() {
        return statements.stream().allMatch(s -> s.error() == null);
    }
}
