package com.meper.chatbi.spi.model;

import java.util.List;

/** 表结构详情：基本信息 + 列 + 索引。 */
public record TableDetail(String table, String type, String remarks,
                          List<ColumnInfo> columns, List<IndexInfo> indexes) {

    public TableDetail {
        columns = List.copyOf(columns);
        indexes = List.copyOf(indexes);
    }
}
