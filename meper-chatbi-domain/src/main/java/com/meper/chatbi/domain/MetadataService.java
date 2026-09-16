package com.meper.chatbi.domain;

import com.meper.chatbi.jdbc.JdbcConnectionTester;
import com.meper.chatbi.jdbc.JdbcMetadataReader;
import com.meper.chatbi.spi.model.TableDataPage;
import com.meper.chatbi.spi.model.TableDetail;
import com.meper.chatbi.spi.model.TableInfo;
import com.meper.chatbi.spi.model.ExecutionLimits;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 元数据领域服务：库/Schema、表清单、表结构、表数据分页（Chat2DB「库表树」所需的后端能力）。
 *
 * <p>元数据为只读访问；阶段 1 不对元数据读取写审计（高频低敏），P2 统一接入操作审计策略。
 */
@Service
public class MetadataService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;

    private final DataSourceService dataSources;
    private final JdbcMetadataReader reader = new JdbcMetadataReader();

    public MetadataService(DataSourceService dataSources) {
        this.dataSources = dataSources;
    }

    /** 顶层命名空间（MySQL=数据库；其余=schema），已过滤系统库。 */
    public List<String> namespaces(long datasourceId) {
        var profile = dataSources.get(datasourceId);
        return read(profile.id(), profile.type(), conn -> reader.listNamespaces(conn, profile.type()));
    }

    public List<TableInfo> tables(long datasourceId, String namespace, String namePattern) {
        var profile = dataSources.get(datasourceId);
        return read(profile.id(), profile.type(),
                conn -> reader.listTables(conn, profile.type(), namespace, namePattern));
    }

    public TableDetail tableDetail(long datasourceId, String namespace, String table) {
        var profile = dataSources.get(datasourceId);
        return read(profile.id(), profile.type(),
                conn -> reader.describeTable(conn, profile.type(), namespace, table));
    }

    public TableDataPage tableData(long datasourceId, String namespace, String table, int page, int size) {
        var profile = dataSources.get(datasourceId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return read(profile.id(), profile.type(), conn -> reader.readTableData(conn, profile.type(),
                namespace, table, safePage, safeSize, ExecutionLimits.DEFAULTS));
    }

    private <T> T read(long datasourceId, com.meper.chatbi.spi.DatabaseType type, MetadataRead<T> action) {
        try (Connection conn = dataSources.poolFor(dataSources.get(datasourceId)).getConnection()) {
            return action.read(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("元数据读取失败: " + JdbcConnectionTester.summarize(e));
        }
    }

    @FunctionalInterface
    private interface MetadataRead<T> {
        T read(Connection conn) throws SQLException;
    }
}
