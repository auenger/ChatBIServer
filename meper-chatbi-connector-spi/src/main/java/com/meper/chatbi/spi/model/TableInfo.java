package com.meper.chatbi.spi.model;

/** 库/Schema 中的表或视图条目。 */
public record TableInfo(String name, String type, String remarks) {

    public static final String TYPE_TABLE = "TABLE";
    public static final String TYPE_VIEW = "VIEW";
}
