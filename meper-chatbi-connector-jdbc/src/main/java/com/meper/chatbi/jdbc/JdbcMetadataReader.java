package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.DatabaseType;
import com.meper.chatbi.spi.DialectRegistry;

import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.ColumnInfo;
import com.meper.chatbi.spi.model.IndexInfo;
import com.meper.chatbi.spi.model.QueryResultData;
import com.meper.chatbi.spi.model.TableDataPage;
import com.meper.chatbi.spi.model.TableDetail;
import com.meper.chatbi.spi.model.TableInfo;
import com.meper.chatbi.spi.model.ExecutionLimits;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JDBC 标准元数据读取（借鉴 Chat2DB DefaultSQLExecutor 的元数据代理层）：
 * 库/Schema、表清单、表结构（列/索引）、表数据分页。
 *
 * <p>跨库差异由两处吸收：
 * ① JDBC DatabaseMetaData 的 catalog/schema 语义（MySQL catalog=database），
 * ② 方言的 {@link SqlDialect#namespaceLayout()} 与系统库名过滤。
 */
public class JdbcMetadataReader {

    /** 顶层命名空间列表（MySQL=数据库；PG/MSSQL/Oracle=schema），已过滤系统库并排序。 */
    public List<String> listNamespaces(Connection conn, DatabaseType type) throws SQLException {
        SqlDialect dialect = DialectRegistry.get(type);
        List<String> system = dialect.systemNamespaceNames();
        DatabaseMetaData md = conn.getMetaData();
        List<String> names = new ArrayList<>();
        if (dialect.namespaceLayout() == SqlDialect.NamespaceLayout.CATALOG_IS_DATABASE) {
            try (ResultSet rs = md.getCatalogs()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (!system.contains(name)) {
                        names.add(name);
                    }
                }
            }
        } else {
            try (ResultSet rs = md.getSchemas()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_SCHEM");
                    if (!system.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return names.stream().sorted().toList();
    }

    /** 命名空间下的表与视图（表在前，组内按名称排序）。 */
    public List<TableInfo> listTables(Connection conn, DatabaseType type,
                                      String namespace, String tableNamePattern) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(catalogOf(type, namespace), schemaOf(type, namespace),
                tableNamePattern == null ? "%" : tableNamePattern, new String[]{"TABLE", "VIEW"})) {
            Map<String, List<TableInfo>> grouped = new TreeMap<>();
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");
                String remarks = rs.getString("REMARKS");
                String normalized = tableType != null && tableType.toLowerCase().contains("view")
                        ? TableInfo.TYPE_VIEW : TableInfo.TYPE_TABLE;
                grouped.computeIfAbsent(normalized, k -> new ArrayList<>())
                        .add(new TableInfo(name, normalized, remarks));
            }
            List<TableInfo> result = new ArrayList<>();
            result.addAll(grouped.getOrDefault(TableInfo.TYPE_TABLE, List.of()));
            result.addAll(grouped.getOrDefault(TableInfo.TYPE_VIEW, List.of()));
            return result;
        }
    }

    /** 表结构：列 + 索引 + 基本信息。 */
    public TableDetail describeTable(Connection conn, DatabaseType type,
                                     String namespace, String table) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        String catalog = catalogOf(type, namespace);
        String schema = schemaOf(type, namespace);

        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                columns.add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        !"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                        rs.getString("COLUMN_DEF"),
                        rs.getString("REMARKS"),
                        false,
                        "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))));
            }
        }

        Map<String, Boolean> primaryKeyColumns = new LinkedHashMap<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                primaryKeyColumns.put(rs.getString("COLUMN_NAME"), true);
            }
        }
        columns = columns.stream()
                .map(c -> new ColumnInfo(c.name(), c.typeName(), c.nullable(), c.defaultValue(),
                        c.remarks(), primaryKeyColumns.containsKey(c.name()), c.autoIncrement()))
                .toList();

        Map<String, IndexInfo> indexes = new LinkedHashMap<>();
        try (ResultSet rs = md.getIndexInfo(catalog, schema, table, false, true)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue; // 跳过统计信息行
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                IndexInfo existing = indexes.get(indexName);
                if (existing == null) {
                    indexes.put(indexName, new IndexInfo(indexName, unique, List.of(columnName)));
                } else if (!existing.columns().contains(columnName)) {
                    // getIndexInfo 已按 ORDINAL_POSITION 排序，追加即保序
                    List<String> cols = new ArrayList<>(existing.columns());
                    cols.add(columnName);
                    indexes.put(indexName, new IndexInfo(indexName, existing.unique(), cols));
                }
            }
        }

        String tableType = TableInfo.TYPE_TABLE;
        String remarks = null;
        try (ResultSet rs = md.getTables(catalog, schema, table, new String[]{"TABLE", "VIEW"})) {
            if (rs.next()) {
                String t = rs.getString("TABLE_TYPE");
                tableType = t != null && t.toLowerCase().contains("view") ? TableInfo.TYPE_VIEW : TableInfo.TYPE_TABLE;
                remarks = rs.getString("REMARKS");
            }
        }
        return new TableDetail(table, tableType, remarks, columns, List.copyOf(indexes.values()));
    }

    /** 表数据分页：SELECT 分页改写 + COUNT(*) 精确总数（单条只读查询，强制超时与单元格限额）。 */
    public TableDataPage readTableData(Connection conn, DatabaseType type,
                                       String namespace, String table,
                                       int page, int size, ExecutionLimits limits) throws SQLException {
        SqlDialect dialect = DialectRegistry.get(type);
        String qualified = dialect.quoteIdentifier(namespace) + "." + dialect.quoteIdentifier(table);

        long total;
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(limits.queryTimeoutSeconds());
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + qualified)) {
                rs.next();
                total = rs.getLong(1);
            }
        }

        String pagedSql = dialect.paginate("SELECT * FROM " + qualified, (page - 1) * size, size);
        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(limits.queryTimeoutSeconds());
            try (ResultSet rs = stmt.executeQuery(pagedSql)) {
                return new TableDataPage(readData(rs, limits), total, page, size);
            }
        }
    }

    private QueryResultData readData(ResultSet rs, ExecutionLimits limits) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        boolean columnsTruncated = columnCount > limits.maxColumns();
        int effective = Math.min(columnCount, limits.maxColumns());
        List<String> cols = new ArrayList<>(effective);
        for (int i = 1; i <= effective; i++) {
            cols.add(meta.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        boolean rowsTruncated = false;
        while (rs.next()) {
            if (rows.size() >= limits.maxRows()) {
                rowsTruncated = true; // 安全网：分页读取时行数受 size 约束，正常不会触发
                break;
            }
            List<String> row = new ArrayList<>(effective);
            for (int i = 1; i <= effective; i++) {
                String v = rs.getString(i);
                row.add(v != null && v.length() > limits.cellCharLimit()
                        ? v.substring(0, limits.cellCharLimit()) + "…(截断)" : v);
            }
            rows.add(row);
        }
        return new QueryResultData(cols, rows, columnsTruncated, rowsTruncated);
    }

    private String catalogOf(DatabaseType type, String namespace) {
        return DialectRegistry.get(type).namespaceLayout() == SqlDialect.NamespaceLayout.CATALOG_IS_DATABASE
                ? namespace : null;
    }

    private String schemaOf(DatabaseType type, String namespace) {
        return DialectRegistry.get(type).namespaceLayout() == SqlDialect.NamespaceLayout.SCHEMA_BASED
                ? namespace : null;
    }
}
