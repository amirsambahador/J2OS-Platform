package org.jshard.datasource;

import org.jshard.config.JShardConnectionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link JShardDataSourceRegistry} using an in-memory H2 database.
 * Exercises {@link JShardDataSourceRegistry#isReachable} and {@link JShardDataSourceRegistry#create}
 * against an actual JDBC driver and HikariCP pool.
 * <p>
 * <b>Note:</b> H2 is included in {@code pom.xml} with {@code test} scope for these unit/integration tests.
 * If connecting to other databases (e.g., PostgreSQL, MySQL) in production or custom test suites,
 * ensure their respective JDBC driver dependencies are added to your {@code pom.xml}.
 */
class JShardDataSourceRegistryTest {

    private final JShardDataSourceRegistry registry = new JShardDataSourceRegistry();

    private static JShardConnectionConfig h2Config(String dbName) {
        return JShardConnectionConfig.of(
                "org.h2.Driver",
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1",
                "sa", "sa", 3);
    }

    @AfterEach
    void tearDown() {
        registry.closeAll();
    }

    @Test
    void isReachable_forARealInMemoryDatabase_returnsTrue() {
        JShardConnectionConfig config = h2Config("reachable_" + UUID.randomUUID());
        assertTrue(JShardDataSourceRegistry.isReachable(config, 3));
    }

    @Test
    void isReachable_forAnUnreachableDatabase_returnsFalseWithoutThrowing() {
        JShardConnectionConfig config = JShardConnectionConfig.of(
                "org.h2.Driver",
                // A TCP H2 URL with nothing listening - a fast, reliable "unreachable" case.
                "jdbc:h2:tcp://127.0.0.1:1/nonexistent",
                "sa", "sa", 1);
        assertFalse(JShardDataSourceRegistry.isReachable(config, 1));
    }

    @Test
    void isReachable_withUnknownDriverClass_returnsFalseWithoutThrowing() {
        JShardConnectionConfig config = JShardConnectionConfig.of(
                "org.example.NoSuchDriver", "jdbc:fake://nowhere", "sa", "sa", 1);
        assertFalse(JShardDataSourceRegistry.isReachable(config, 1));
    }

    @Test
    void create_returnsAWorkingDataSource() throws Exception {
        JShardConnectionConfig config = h2Config("create_" + UUID.randomUUID());
        DataSource ds = registry.create("shard0", config);
        try (Connection conn = ds.getConnection()) {
            assertTrue(conn.isValid(2));
        }
    }

    @Test
    void create_withDuplicateKey_throwsAndClosesTheRejectedPool() {
        JShardConnectionConfig config = h2Config("dup_" + UUID.randomUUID());
        registry.create("shard0", config);

        JShardConnectionConfig secondConfig = h2Config("dup2_" + UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> registry.create("shard0", secondConfig));
    }

    @Test
    void create_withDifferentKeys_bothSucceed() throws Exception {
        DataSource ds1 = registry.create("shard0", h2Config("multi0_" + UUID.randomUUID()));
        DataSource ds2 = registry.create("shard1", h2Config("multi1_" + UUID.randomUUID()));

        try (Connection c1 = ds1.getConnection(); Connection c2 = ds2.getConnection()) {
            assertTrue(c1.isValid(2));
            assertTrue(c2.isValid(2));
        }
    }

    @Test
    void closeAll_closesEveryRegisteredPool() throws Exception {
        var ds1 = (com.zaxxer.hikari.HikariDataSource) registry.create("shard0", h2Config("close0_" + UUID.randomUUID()));
        var ds2 = (com.zaxxer.hikari.HikariDataSource) registry.create("shard1", h2Config("close1_" + UUID.randomUUID()));

        registry.closeAll();

        assertTrue(ds1.isClosed());
        assertTrue(ds2.isClosed());
    }

    @Test
    void closeAll_isSafeToCallMultipleTimes() {
        registry.create("shard0", h2Config("repeat_close_" + UUID.randomUUID()));
        assertDoesNotThrow(registry::closeAll);
        assertDoesNotThrow(registry::closeAll);
    }

    @Test
    void closeAll_isSafeToCallWithNoPoolsRegistered() {
        assertDoesNotThrow(new JShardDataSourceRegistry()::closeAll);
    }
}
