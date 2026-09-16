package com.meper.chatbi.spi;

import com.meper.chatbi.spi.model.CapabilityDescriptor;
import com.meper.chatbi.spi.model.ConnectionSpec;

/**
 * 数据库方言契约。URL 拼装、分页改写、标识符引用等方言行为全部收敛在此
 * （借鉴 Chat2DB「URL 外置」的边界：连接执行层只使用方言产出的 URL）。
 *
 * <p>实现通过 {@link java.util.ServiceLoader} 注册（META-INF/services），
 * 由 {@link DialectRegistry} 聚合；方言模块不得被 connector-jdbc/domain 直接依赖。
 */
public interface SqlDialect {

    DatabaseType type();

    /** 按连接参数拼装 JDBC URL（含 SSL 参数映射；不携带用户名密码）。 */
    String buildJdbcUrl(ConnectionSpec spec);

    /** 连通探活 SQL。 */
    String probeSql();

    /** 能力声明。 */
    CapabilityDescriptor capabilities();

    /** 标识符引用（含内部转义）。 */
    String quoteIdentifier(String identifier);

    /**
     * 物理分页改写（尽力而为；无法安全改写时返回 null，执行层以 maxRows 截断兜底）。
     *
     * @param sql    原始单条 SELECT（不带分页子句）
     * @param offset 起始行（0 起）
     * @param limit  行数
     */
    String paginate(String sql, int offset, int limit);

    /** 去除语句结尾分号与空白（分页改写前的公共预处理）。 */
    static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
    }

    /**
     * 元数据命名空间布局（借鉴 Chat2DB DBConfig.supportDatabase/supportSchema 的差异声明）：
     * MySQL 的 catalog 即 database；PostgreSQL/SQL Server/Oracle 以 schema 组织。
     */
    default NamespaceLayout namespaceLayout() {
        return NamespaceLayout.SCHEMA_BASED;
    }

    /** 需要在元数据树中隐藏的系统库名（按 namespaceLayout 对应层级过滤）。 */
    default java.util.List<String> systemNamespaceNames() {
        return java.util.List.of();
    }

    enum NamespaceLayout { CATALOG_IS_DATABASE, SCHEMA_BASED }
}
