package com.meper.chatbi.spi.model;

import java.util.List;

/** 索引信息。 */
public record IndexInfo(String name, boolean unique, List<String> columns) {

    public IndexInfo {
        columns = List.copyOf(columns);
    }
}
