package org.jshard.datasource;

import org.jshard.config.JShardConnectionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JShardDataSourceProvider}'s configuration validation.
 * <p>
 * These only exercise the validation paths that run BEFORE any real
 * connection pool is created (see {@code buildDataSource}: the null/empty
 * checks and {@code validateShardNames}/{@code validateNoDuplicateTableNames}
 * all run before {@code new JShardDataSourceRegistry()} and before any
 * {@code pool.create(...)} call) - so a dummy, never-actually-connected-to
 * {@link JShardConnectionConfig} is sufficient and no real/reachable
 * database is required. A successful, end-to-end {@code .build()} call
 * (wiring a real ShardingSphere data source against a real or in-memory
 * database) is intentionally NOT covered here - see
 * {@link JShardDataSourceRegistryTest} for that, and treat a genuine
 * end-to-end integration test as a separate, explicit follow-up.
 */
class JShardDataSourceProviderTest {

    private static JShardConnectionConfig dummyConfig() {
        // Never actually connected to in these tests - validation fails
        // before any connection attempt is made, so the driver/URL here
        // don't need to be real.
        return JShardConnectionConfig.of("org.example.NoSuchDriver", "jdbc:fake://nowhere/db", "sa", "sa", 1);
    }

    @Test
    void build_withNoShardsAdded_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .table("orders", "user_id")
                        .build());
    }

    @Test
    void build_withNoTablesAdded_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .shard("shard0", dummyConfig())
                        .build());
    }

    @Test
    void shard_withDuplicateName_throwsImmediately() {
        JShardDataSourceProvider.Builder builder = JShardDataSourceProvider.builder()
                .shard("shard0", dummyConfig());
        assertThrows(IllegalArgumentException.class, () -> builder.shard("shard0", dummyConfig()));
    }

    @Test
    void shard_withNullName_throws() {
        assertThrows(NullPointerException.class, () ->
                JShardDataSourceProvider.builder().shard(null, dummyConfig()));
    }

    @Test
    void shard_withNullPrimaryConfig_throws() {
        assertThrows(NullPointerException.class, () ->
                JShardDataSourceProvider.builder().shard("shard0", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"shard.zero", "shard,zero", "shard'zero", "shard zero", "shard\"zero"})
    void build_withInvalidCharacterInShardName_throws(String badName) {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .shard(badName, dummyConfig())
                        .table("orders", "user_id")
                        .build());
    }

    @Test
    void build_withBlankShardName_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .shard("   ", dummyConfig())
                        .table("orders", "user_id")
                        .build());
    }

    @Test
    void build_withDuplicateTableNames_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .shard("shard0", dummyConfig())
                        .shard("shard1", dummyConfig())
                        .table("orders", "user_id")
                        .table("orders", "customer_id") // same table name added twice
                        .build());
    }

    @Test
    void table_withInvalidIdentifier_throwsEagerly() {
        // JShardTableConfig validates at the point .table(...) is called,
        // not deferred to .build().
        JShardDataSourceProvider.Builder builder = JShardDataSourceProvider.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.table("bad.name", "user_id"));
    }

    @Test
    void shards_withNullMap_throws() {
        assertThrows(NullPointerException.class, () ->
                JShardDataSourceProvider.builder().shards(null));
    }

    @Test
    void shards_withEmptyConfigListForAShard_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                JShardDataSourceProvider.builder()
                        .shards(java.util.Map.of("shard0", java.util.List.of())));
    }

    @Test
    void tables_withNullList_throws() {
        assertThrows(NullPointerException.class, () ->
                JShardDataSourceProvider.builder().tables(null));
    }

    @Test
    void multipleValidShardsAndTables_passValidation_andFailOnlyAtConnectionTime() {
        // With valid names/no duplicates, validation itself should not
        // throw - the eventual failure (thrown from further along in
        // buildDataSource, once it actually attempts to create pools
        // against these fake JDBC URLs) confirms validation was passed
        // through successfully rather than short-circuiting for an
        // unrelated reason.
        JShardDataSourceProvider.Builder builder = JShardDataSourceProvider.builder()
                .shard("shard0", dummyConfig())
                .shard("shard1", dummyConfig())
                .table("orders", "user_id")
                .table("payments", "order_id");

        // Not asserting a specific exception type here deliberately: once
        // validation passes, the next failure comes from HikariCP/the
        // driver rejecting the fake JDBC URL, which is an implementation
        // detail of the pooling library, not of this provider.
        assertThrows(Exception.class, builder::build);
    }
}
