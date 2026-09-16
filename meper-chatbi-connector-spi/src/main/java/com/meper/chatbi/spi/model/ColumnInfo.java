package com.meper.chatbi.spi.model;

/** 列信息。 */
public record ColumnInfo(
        String name,
        String typeName,
        boolean nullable,
        String defaultValue,
        String remarks,
        boolean primaryKey,
        boolean autoIncrement) {
}
