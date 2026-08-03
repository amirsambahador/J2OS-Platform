package org.j2os.platform.jshard.config;

import java.util.Objects;

/**
 * Immutable JDBC connection configuration for a single physical database
 * (one shard, or one primary/replica within a shard), built via
 * {@link #of(String, String, String, String, int)} or {@link #builder()}.
 * <p>
 * Beyond the five required fields (driver, URL, username, password, pool
 * size), several optional HikariCP-style pool tuning parameters can be set
 * through the {@link Builder} (connection timeout, idle timeout, max
 * lifetime, minimum idle, validation timeout, keepalive time); any left
 * unset are {@code null} and fall back to the connection pool's own
 * defaults wherever this config is consumed
 * (see {@link org.j2os.platform.jshard.datasource.JShardDataSourceRegistry}).
 * <p>
 * {@link #toString()} deliberately omits the password and strips any query
 * string from the JDBC URL, so this object is safe to include directly in
 * logs.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */

public final class JShardConnectionConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;
    private final String driverClassName;

    private final Long connectionTimeoutMs;
    private final Long idleTimeoutMs;
    private final Long maxLifetimeMs;
    private final Integer minimumIdle;
    private final Long validationTimeoutMs;
    private final Long keepaliveTimeMs;

    private JShardConnectionConfig(Builder b) {
        this.jdbcUrl = requireNonBlank(b.jdbcUrl, "jdbcUrl");
        this.username = requireNonBlank(b.username, "username");
        this.password = requireNonBlank(b.password, "password");
        this.driverClassName = requireNonBlank(b.driverClassName, "driverClassName");
        if (b.poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be greater than zero");
        }
        this.poolSize = b.poolSize;
        this.connectionTimeoutMs = b.connectionTimeoutMs;
        this.idleTimeoutMs = b.idleTimeoutMs;
        this.maxLifetimeMs = b.maxLifetimeMs;
        this.minimumIdle = b.minimumIdle;
        this.validationTimeoutMs = b.validationTimeoutMs;
        this.keepaliveTimeMs = b.keepaliveTimeMs;
    }

    /**
     * Convenience factory for the common case of only needing the five
     * required fields, with no pool-tuning parameters. Equivalent to
     * {@code builder().driverClassName(driver).jdbcUrl(jdbcUrl).username(username).password(password).poolSize(poolSize).build()}.
     *
     * @param driver   the fully-qualified JDBC driver class name
     * @param jdbcUrl  the JDBC connection URL
     * @param username the database username
     * @param password the database password
     * @param poolSize the maximum connection pool size; must be positive
     * @return a new, fully validated {@link JShardConnectionConfig}
     * @throws IllegalArgumentException if any required field is null/blank, or {@code poolSize} is not positive
     */
    public static JShardConnectionConfig of(String driver, String jdbcUrl, String username, String password, int poolSize) {
        return builder().driverClassName(driver).jdbcUrl(jdbcUrl).username(username).password(password).poolSize(poolSize).build();
    }

    /**
     * Starts a new {@link Builder} for constructing a
     * {@link JShardConnectionConfig} with optional pool-tuning parameters.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates that a required string field is neither {@code null} nor
     * blank.
     *
     * @param value     the value to validate
     * @param fieldName the field's name, used in the exception message
     * @return {@code value}, unchanged
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     */
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ShardConfig." + fieldName + " must not be null/blank");
        }
        return value;
    }

    /**
     * Masks a username for safe inclusion in logs: usernames of 2 or fewer
     * characters are fully replaced with {@code *}; longer usernames keep
     * only their first and last character (e.g. {@code "admin"} -&gt;
     * {@code "a***n"}).
     *
     * @param username the username to mask
     * @return the masked username
     */
    private static String maskUsername(String username) {
        if (username.length() <= 2) {
            return "*".repeat(username.length());
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    /**
     * Strips any query string from a JDBC URL, so credentials or other
     * sensitive parameters occasionally embedded there don't end up in logs.
     *
     * @param jdbcUrl the JDBC URL to sanitize
     * @return {@code jdbcUrl} with everything from (and including) the first {@code ?} removed
     */
    private static String maskHost(String jdbcUrl) {
        int qIdx = jdbcUrl.indexOf('?');
        String withoutQuery = qIdx >= 0 ? jdbcUrl.substring(0, qIdx) : jdbcUrl;
        return withoutQuery;
    }

    /**
     * Returns the JDBC connection URL.
     *
     * @return the JDBC URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Returns the database username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the database password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the maximum connection pool size.
     *
     * @return the pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the fully-qualified JDBC driver class name.
     *
     * @return the driver class name
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Returns the configured connection-acquisition timeout, in milliseconds.
     *
     * @return the connection timeout in ms, or {@code null} if not set (pool default applies)
     */
    public Long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Returns the configured idle-connection timeout, in milliseconds.
     *
     * @return the idle timeout in ms, or {@code null} if not set (pool default applies)
     */
    public Long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    /**
     * Returns the configured maximum connection lifetime, in milliseconds.
     *
     * @return the max lifetime in ms, or {@code null} if not set (pool default applies)
     */
    public Long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    /**
     * Returns the configured minimum number of idle connections kept ready.
     *
     * @return the minimum idle count, or {@code null} if not set (pool default applies)
     */
    public Integer getMinimumIdle() {
        return minimumIdle;
    }

    /**
     * Returns the configured connection-validation timeout, in milliseconds.
     *
     * @return the validation timeout in ms, or {@code null} if not set (pool default applies)
     */
    public Long getValidationTimeoutMs() {
        return validationTimeoutMs;
    }

    /**
     * Returns the configured keepalive interval, in milliseconds.
     *
     * @return the keepalive time in ms, or {@code null} if not set (pool default applies)
     */
    public Long getKeepaliveTimeMs() {
        return keepaliveTimeMs;
    }

    /**
     * Compares this configuration to another for equality across every
     * field, including the password.
     *
     * @param o the object to compare against
     * @return {@code true} if {@code o} is a {@link JShardConnectionConfig} with identical field values
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JShardConnectionConfig that)) return false;
        return poolSize == that.poolSize && jdbcUrl.equals(that.jdbcUrl) && username.equals(that.username)
                && password.equals(that.password) && driverClassName.equals(that.driverClassName)
                && Objects.equals(connectionTimeoutMs, that.connectionTimeoutMs) && Objects.equals(idleTimeoutMs, that.idleTimeoutMs)
                && Objects.equals(maxLifetimeMs, that.maxLifetimeMs) && Objects.equals(minimumIdle, that.minimumIdle)
                && Objects.equals(validationTimeoutMs, that.validationTimeoutMs) && Objects.equals(keepaliveTimeMs, that.keepaliveTimeMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jdbcUrl, username, password, poolSize, driverClassName, connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs, minimumIdle, validationTimeoutMs, keepaliveTimeMs);
    }

    /**
     * Returns a log-safe summary of this configuration: driver, host portion
     * of the JDBC URL (query string stripped), masked username, and pool
     * size. The password is never included.
     *
     * @return a log-safe string representation
     */
    @Override
    public String toString() {
        return "ShardConfig{driver='" + driverClassName + "', host=" + maskHost(jdbcUrl) + ", user=" + maskUsername(username) + ", poolSize=" + poolSize + "}";
    }

    /**
     * Builder for {@link JShardConnectionConfig}. All setters return
     * {@code this} for chaining; call {@link #build()} to validate and
     * construct the final immutable configuration.
     */
    public static final class Builder {
        private String jdbcUrl;
        private String username;
        private String password;
        private int poolSize;
        private String driverClassName;
        private Long connectionTimeoutMs;
        private Long idleTimeoutMs;
        private Long maxLifetimeMs;
        private Integer minimumIdle;
        private Long validationTimeoutMs;
        private Long keepaliveTimeMs;

        /**
         * Sets the fully-qualified JDBC driver class name.
         *
         * @param driverClassName the driver class name
         * @return this builder
         */
        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        /**
         * Sets the JDBC connection URL.
         *
         * @param jdbcUrl the JDBC URL
         * @return this builder
         */
        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        /**
         * Sets the database username.
         *
         * @param username the username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the database password.
         *
         * @param password the password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the maximum connection pool size.
         *
         * @param poolSize the pool size; must be positive
         * @return this builder
         */
        public Builder poolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        /**
         * Sets the connection-acquisition timeout.
         *
         * @param connectionTimeoutMs the timeout, in milliseconds
         * @return this builder
         */
        public Builder connectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Sets the idle-connection timeout.
         *
         * @param idleTimeoutMs the timeout, in milliseconds
         * @return this builder
         */
        public Builder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
            return this;
        }

        /**
         * Sets the maximum connection lifetime.
         *
         * @param maxLifetimeMs the lifetime, in milliseconds
         * @return this builder
         */
        public Builder maxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
            return this;
        }

        /**
         * Sets the minimum number of idle connections kept ready.
         *
         * @param minimumIdle the minimum idle count
         * @return this builder
         */
        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        /**
         * Sets the connection-validation timeout.
         *
         * @param validationTimeoutMs the timeout, in milliseconds
         * @return this builder
         */
        public Builder validationTimeoutMs(long validationTimeoutMs) {
            this.validationTimeoutMs = validationTimeoutMs;
            return this;
        }

        /**
         * Sets the keepalive interval.
         *
         * @param keepaliveTimeMs the interval, in milliseconds
         * @return this builder
         */
        public Builder keepaliveTimeMs(long keepaliveTimeMs) {
            this.keepaliveTimeMs = keepaliveTimeMs;
            return this;
        }

        /**
         * Validates every field and constructs the immutable configuration.
         *
         * @return a new, fully validated {@link JShardConnectionConfig}
         * @throws IllegalArgumentException if any required field is null/blank, or {@code poolSize} is not positive
         */
        public JShardConnectionConfig build() {
            return new JShardConnectionConfig(this);
        }
    }
}
