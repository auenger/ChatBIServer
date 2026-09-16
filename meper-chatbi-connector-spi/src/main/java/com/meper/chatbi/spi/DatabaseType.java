package com.meper.chatbi.spi;

/**
 * 支持的数据库类型。首批四库（2026-09-15 决策），驱动全部内置。
 *
 * <p>版本矩阵：MySQL 5.7/8.4.x、SQL Server 2019/2022、PostgreSQL 16、Oracle 19c/23ai。
 */
public enum DatabaseType {

    MYSQL("mysql", 3306, "com.mysql.cj.jdbc.Driver"),

    SQLSERVER("sqlserver", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver"),

    POSTGRESQL("postgresql", 5432, "org.postgresql.Driver"),

    ORACLE("oracle", 1521, "oracle.jdbc.OracleDriver");

    private final String code;
    private final int defaultPort;
    private final String driverClassName;

    DatabaseType(String code, int defaultPort, String driverClassName) {
        this.code = code;
        this.defaultPort = defaultPort;
        this.driverClassName = driverClassName;
    }

    /** 类型码，用于 API 与存储层。 */
    public String code() {
        return code;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** 内置 JDBC 驱动主类名。 */
    public String driverClassName() {
        return driverClassName;
    }
}
