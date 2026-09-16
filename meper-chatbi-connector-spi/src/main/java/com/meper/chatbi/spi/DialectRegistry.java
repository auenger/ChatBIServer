package com.meper.chatbi.spi;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 方言注册表：通过 ServiceLoader 聚合 classpath 上所有方言模块的实现。
 * 上层（connector-jdbc / domain）只经此处获取方言，不直接依赖方言模块。
 */
public final class DialectRegistry {

    private static final Map<DatabaseType, SqlDialect> DIALECTS = new EnumMap<>(DatabaseType.class);

    static {
        for (SqlDialect dialect : ServiceLoader.load(SqlDialect.class, DialectRegistry.class.getClassLoader())) {
            SqlDialect existing = DIALECTS.put(dialect.type(), dialect);
            if (existing != null) {
                throw new IllegalStateException("重复注册的方言实现: " + dialect.type());
            }
        }
    }

    private DialectRegistry() {
    }

    /** 获取方言；未注册时抛出明确异常（能力矩阵中记为 UNSUPPORTED）。 */
    public static SqlDialect get(DatabaseType type) {
        SqlDialect dialect = DIALECTS.get(type);
        if (dialect == null) {
            throw new UnsupportedOperationException("数据库类型未注册方言实现: " + type);
        }
        return dialect;
    }
}
