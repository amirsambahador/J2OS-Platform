package org.j2os.platform.jshard.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the HikariCP connection pools backing a single
 * {@link JShardDataSource}: one pool per physical data source key (a
 * logical shard name, or a derived {@code "<shard>_primary"}/
 * {@code "<shard>_replicaN"} name for shards with replicas - see
 * {@link JShardDataSourceProvider}).
 * <p>
 * Each {@link JShardDataSourceProvider.Builder#build()} call creates its
 * own private {@code JShardDataSourceRegistry} instance, so pools from
 * independently built {@link JShardDataSource}s never interfere with each
 * other (see {@link JShardDataSourceProvider}'s multi-tenant usage).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JShardDataSourceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(JShardDataSourceRegistry.class);

    /** Every pool currently registered, keyed by the physical data source key it was created under. */
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    /**
     * Builds a fully configured {@link HikariDataSource} from a
     * {@link JShardConnectionConfig}: the five required fields are always
     * applied, and each optional pool-tuning parameter is applied only if
     * it was set on the config.
     *
     * @param config the connection configuration to build a pool from
     * @return a new, already-initializing {@link HikariDataSource}
     */
    private static HikariDataSource createHikariDataSource(JShardConnectionConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());

        if (config.getConnectionTimeoutMs() != null) {
            hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        }
        if (config.getIdleTimeoutMs() != null) {
            hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        }
        if (config.getMaxLifetimeMs() != null) {
            hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        }
        if (config.getMinimumIdle() != null) {
            hikariConfig.setMinimumIdle(config.getMinimumIdle());
        }
        if (config.getValidationTimeoutMs() != null) {
            hikariConfig.setValidationTimeout(config.getValidationTimeoutMs());
        }
        if (config.getKeepaliveTimeMs() != null) {
            hikariConfig.setKeepaliveTime(config.getKeepaliveTimeMs());
        }

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Checks whether a database is reachable using the given connection
     * configuration, without registering any long-lived pool. Creates a
     * short-lived, single-connection probe pool bounded by
     * {@code timeoutSeconds} for both connection acquisition and pool
     * initialization, and always closes it before returning.
     *
     * @param config         the connection configuration to probe
     * @param timeoutSeconds how long to wait for the probe connection, in seconds (minimum 1)
     * @return {@code true} if a valid connection could be established within the timeout
     */
    public static boolean isReachable(JShardConnectionConfig config, int timeoutSeconds) {
        HikariConfig probeConfig = new HikariConfig();
        probeConfig.setJdbcUrl(config.getJdbcUrl());
        probeConfig.setUsername(config.getUsername());
        probeConfig.setPassword(config.getPassword());
        probeConfig.setDriverClassName(config.getDriverClassName());
        probeConfig.setMaximumPoolSize(1);
        probeConfig.setMinimumIdle(0);
        long timeoutMs = Math.max(1, timeoutSeconds) * 1000L;
        probeConfig.setConnectionTimeout(timeoutMs);
        probeConfig.setInitializationFailTimeout(timeoutMs);

        try (HikariDataSource probeDs = new HikariDataSource(probeConfig);
             Connection conn = probeDs.getConnection()) {
            return conn.isValid(timeoutSeconds);
        } catch (Exception e) {
            LOGGER.debug("Shard is not reachable: {}", config, e);
            return false;
        }
    }

    /**
     * Closes a {@link HikariDataSource}, logging (rather than propagating)
     * any failure.
     *
     * @param ds the pool to close
     */
    private static void closeQuietly(HikariDataSource ds) {
        try {
            ds.close();
        } catch (Exception e) {
            LOGGER.warn("Error closing a HikariDataSource", e);
        }
    }

    /**
     * Creates and registers a new connection pool under the given key.
     *
     * @param key    the physical data source key to register the pool under
     * @param config the connection configuration to build the pool from
     * @return the newly created pool
     * @throws IllegalStateException if a pool is already registered under {@code key}
     *                                (the newly created pool is closed before this is thrown)
     */
    public DataSource create(String key, JShardConnectionConfig config) {
        HikariDataSource ds = createHikariDataSource(config);
        HikariDataSource previous = pools.putIfAbsent(key, ds);
        if (previous != null) {
            closeQuietly(ds);
            throw new IllegalStateException("A DataSource is already registered under key '" + key + "' in this registry");
        }
        return ds;
    }

    /**
     * Closes every pool currently registered in this registry, and clears
     * the registry. Safe to call even if some pools are already closed.
     */
    public void closeAll() {
        pools.values().forEach(JShardDataSourceRegistry::closeQuietly);
        pools.clear();
    }
}
