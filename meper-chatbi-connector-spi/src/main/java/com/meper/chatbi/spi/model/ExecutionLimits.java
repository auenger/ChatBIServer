package com.meper.chatbi.spi.model;

/**
 * 执行限额（结果治理：架构方案 §4「字段裁剪/脱敏/大值治理」的阶段 1 实现）。
 *
 * @param maxRows             单语句返回行数上限（服务端硬上限 {@link #HARD_MAX_ROWS}）
 * @param queryTimeoutSeconds 单语句超时秒数
 * @param cellCharLimit       单元格字符截断长度
 * @param maxColumns          单结果集列数上限
 */
public record ExecutionLimits(int maxRows, int queryTimeoutSeconds, int cellCharLimit, int maxColumns) {

    public static final int HARD_MAX_ROWS = 10_000;
    public static final int DEFAULT_MAX_ROWS = 1_000;
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_CELL_CHAR_LIMIT = 4_000;
    public static final int DEFAULT_MAX_COLUMNS = 100;

    public static final ExecutionLimits DEFAULTS =
            new ExecutionLimits(DEFAULT_MAX_ROWS, DEFAULT_TIMEOUT_SECONDS, DEFAULT_CELL_CHAR_LIMIT, DEFAULT_MAX_COLUMNS);

    public ExecutionLimits {
        if (maxRows < 1 || maxRows > HARD_MAX_ROWS) {
            throw new IllegalArgumentException("maxRows 必须在 1.." + HARD_MAX_ROWS + " 之间: " + maxRows);
        }
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 600) {
            throw new IllegalArgumentException("queryTimeoutSeconds 必须在 1..600 之间: " + queryTimeoutSeconds);
        }
        if (maxColumns < 1) {
            throw new IllegalArgumentException("maxColumns 必须为正数: " + maxColumns);
        }
    }
}
