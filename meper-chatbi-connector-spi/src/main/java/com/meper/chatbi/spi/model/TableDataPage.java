package com.meper.chatbi.spi.model;

/**
 * 表数据分页（服务端分页；total 为精确 COUNT(*)，small-table 语义）。
 *
 * @param data   当前页数据
 * @param total  表总行数（精确值）
 * @param page   当前页（1 起）
 * @param size   每页行数
 */
public record TableDataPage(QueryResultData data, long total, int page, int size) {
}
