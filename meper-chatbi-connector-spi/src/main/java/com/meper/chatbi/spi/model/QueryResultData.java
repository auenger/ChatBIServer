package com.meper.chatbi.spi.model;

import java.util.List;

/**
 * 查询结果集。单元格一律转 String（渲染安全；强类型读取属后续能力）。
 *
 * @param columns      列名（按 ResultSetMetaData label）
 * @param rows         行数据（null 表示 SQL NULL）
 * @param columnsTruncated 列数超限被截断
 * @param rowsTruncated    行数超限被截断
 */
public record QueryResultData(List<String> columns, List<List<String>> rows,
                              boolean columnsTruncated, boolean rowsTruncated) {

    public QueryResultData {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
