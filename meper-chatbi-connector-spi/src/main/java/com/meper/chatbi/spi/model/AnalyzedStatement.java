package com.meper.chatbi.spi.model;

import com.meper.chatbi.spi.SqlCategory;

/**
 * 分类后的单条语句（分类器输出、执行器输入）。
 *
 * @param seq      在脚本中的序号（0 起）
 * @param sql      语句原文（不含结尾分号）
 * @param category 类别
 */
public record AnalyzedStatement(int seq, String sql, SqlCategory category) {
}
