package com.meper.chatbi.jdbc;

import com.meper.chatbi.spi.DialectRegistry;
import com.meper.chatbi.spi.SqlDialect;
import com.meper.chatbi.spi.model.ConnectionSpec;
import com.meper.chatbi.spi.model.DataSourceProfile;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态业务库连接池注册表。
 *
 * <p>设计（替代 Chat2DB 自研池的改进点）：
 * <ul>
 *   <li>池 key 为结构化 (datasourceId, credentialVersion)，不含任何控制台/会话语义；</li>
 *   <li>凭据轮换 = 生成新版本 key 的池 + {@link #evict(long)} 逐出旧池（generation 失效思想）；</li>
 *   <li>池实现用 HikariCP：显式连接超时/生命周期上限，规避自研池「无并发上限、无超时」缺口；</li>
 *   <li>minimumIdle=0：不为登记过的数据源维持常驻空闲连接。</li>
 * </ul>
 */
public class ConnectionPoolRegistry implements AutoCloseable {

    /** 池 key：数据源 + 凭据版本。凭据轮换必然产生新 key。 */
    public record PoolKey(long datasourceId, long credentialVersion) {
    }

    private final ConcurrentHashMap<PoolKey, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final List<HikariDataSource> evicted = new CopyOnWriteArrayList<>();
    private final int maxPoolSize;

    public ConnectionPoolRegistry(int maxPoolSize) {
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize 必须为正数");
        }
        this.maxPoolSize = maxPoolSize;
    }

    /** 获取（或懒创建）该数据源当前凭据版本对应的池。 */
    public DataSource poolFor(DataSourceProfile profile, char[] password) {
        PoolKey key = new PoolKey(profile.id(), profile.credentialVersion());
        return pools.computeIfAbsent(key, k -> create(profile, password));
    }

    private HikariDataSource create(DataSourceProfile profile, char[] password) {
        SqlDialect dialect = DialectRegistry.get(profile.type());
        ConnectionSpec spec = ConnectionSpec.of(profile, password);
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dialect.buildJdbcUrl(spec));
        cfg.setUsername(profile.username());
        cfg.setPassword(new String(password));
        cfg.setMaximumPoolSize(maxPoolSize);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(5_000);
        cfg.setValidationTimeout(3_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setPoolName("meper-ds-" + profile.id() + "-v" + profile.credentialVersion());
        return new HikariDataSource(cfg);
    }

    /** 逐出该数据源的全部池（凭据轮换/删除时调用）。 */
    public void evict(long datasourceId) {
        pools.keySet().removeIf(key -> {
            if (key.datasourceId() == datasourceId) {
                HikariDataSource ds = pools.remove(key);
                if (ds != null) {
                    evicted.add(ds);
                    ds.close();
                }
                return true;
            }
            return false;
        });
    }

    public int size() {
        return pools.size();
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
