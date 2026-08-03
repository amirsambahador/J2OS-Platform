package org.j2os.platform.jshard.datasource;

import org.j2os.platform.jshard.router.JShardRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * A standard {@link DataSource} that internally routes queries across
 * multiple physical shards via ShardingSphere, produced by
 * {@link JShardDataSourceProvider#builder()}.
 * <p>
 * This is a thin, closeable wrapper around the raw ShardingSphere-backed
 * {@link DataSource} ({@link #delegate}) plus the {@link JShardDataSourceRegistry}
 * that owns its underlying connection pools and the {@link JShardRouter}
 * that can predict (without touching the database) which shard a given
 * value would route to via {@link #getRouter()}.
 * <p>
 * {@link #close()} is idempotent: closing an already-closed instance is a
 * no-op. Once closed, {@link #getConnection()} and
 * {@link #getConnection(String, String)} start throwing; every connection
 * pool created for this data source's shards (via its
 * {@link JShardDataSourceRegistry}) is also closed.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class JShardDataSource implements DataSource, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JShardDataSource.class);

    /** The raw ShardingSphere-backed {@link DataSource} that actual JDBC operations are delegated to. */
    private final DataSource delegate;

    /** The registry owning every physical connection pool backing this data source's shards. */
    private final JShardDataSourceRegistry pool;

    /** Predicts, without touching the database, which shard a given sharding value would route to. */
    private final JShardRouter router;

    /** Whether {@link #close()} has already been called. */
    private volatile boolean closed = false;

    JShardDataSource(DataSource delegate, JShardDataSourceRegistry pool, JShardRouter router) {
        this.delegate = delegate;
        this.pool = pool;
        this.router = router;
    }

    /**
     * Returns the router that can predict which shard a given sharding
     * column value would route to, without needing a database connection.
     *
     * @return this data source's router
     */
    public JShardRouter getRouter() {
        return router;
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkNotClosed();
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        checkNotClosed();
        return delegate.getConnection(username, password);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If {@code iface} is an interface or superclass this object itself
     * implements (e.g. {@link JShardDataSource} or {@link DataSource}),
     * returns {@code this} directly; otherwise delegates to the wrapped
     * ShardingSphere data source.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code true} immediately if {@code iface} is an interface or
     * superclass this object itself implements; otherwise defers to the
     * wrapped ShardingSphere data source.
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    /**
     * Closes this data source: closes the underlying ShardingSphere data
     * source (if it is {@link AutoCloseable}) and every connection pool
     * registered in this data source's {@link JShardDataSourceRegistry}.
     * Safe to call more than once; calls after the first are a no-op.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (delegate instanceof AutoCloseable) {
                ((AutoCloseable) delegate).close();
            }
        } catch (Exception e) {
            LOGGER.warn("Error closing the ShardingSphere DataSource", e);
        } finally {
            pool.closeAll();
        }
    }

    /**
     * Guards a method against being called after {@link #close()}.
     *
     * @throws SQLException if this data source has already been closed
     */
    private void checkNotClosed() throws SQLException {
        if (closed) {
            throw new SQLException("This JShardDataSource has already been closed");
        }
    }
}
