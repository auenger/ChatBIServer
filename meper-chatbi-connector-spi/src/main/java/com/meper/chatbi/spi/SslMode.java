package com.meper.chatbi.spi;

/**
 * TLS 使用策略。各 JDBC 驱动的具体参数映射由方言实现。
 *
 * <p>约束（实施方案 §4）：禁止「连接失败后自动降级 SSL」；
 * sslMode 由数据源配置显式决定，连接失败保留原始错误。
 */
public enum SslMode {

    /** 尽可能使用 TLS（驱动默认语义）。 */
    PREFERRED,

    /** 强制 TLS。 */
    REQUIRED,

    /** 关闭 TLS。 */
    DISABLED
}
