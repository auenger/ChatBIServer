package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.DatabaseType;

/**
 * 内置驱动注册表：校验目标库驱动已在 classpath（驱动随方言模块内置，2026-09-15 决策：
 * 不做自定义驱动 JAR 上传，规避不可信代码加载风险）。
 */
public final class BuiltinDriverRegistry {

    private BuiltinDriverRegistry() {
    }

    /** 驱动缺失时抛出明确异常，不在运行期做任何下载/加载。 */
    public static void assertAvailable(DatabaseType type) {
        try {
            Class.forName(type.driverClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("内置 JDBC 驱动缺失: " + type.driverClassName(), e);
        }
    }
}
