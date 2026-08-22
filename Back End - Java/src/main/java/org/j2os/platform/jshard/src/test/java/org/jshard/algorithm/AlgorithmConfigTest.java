package org.jshard.algorithm;

import org.apache.shardingsphere.infra.datanode.DataNodeInfo;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AlgorithmConfig}, the ShardingSphere {@code StandardShardingAlgorithm}
 * entry point that wraps {@link Algorithm}.
 * <p>
 * {@link PreciseShardingValue} instances are constructed the same way
 * {@link AlgorithmConfig#predictShard(List, String)} already does internally
 * ({@code new PreciseShardingValue<>(table, column, new DataNodeInfo(...), value)}),
 * so these tests don't depend on any ShardingSphere wiring beyond what the
 * production code already exercises.
 */
class AlgorithmConfigTest {

    private static PreciseShardingValue<String> preciseValue(String value) {
        return new PreciseShardingValue<>("orders", "user_id", new DataNodeInfo("orders", 1, 'D'), value);
    }

    // ---------------------------------------------------------------
    // init()
    // ---------------------------------------------------------------

    @Test
    void init_withNullProperties_doesNotThrow() {
        AlgorithmConfig config = new AlgorithmConfig();
        assertDoesNotThrow(() -> config.init(null));
    }

    @Test
    void init_withoutShardingCountProperty_doesNotEnforceCount() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(new Properties());
        // No sharding-count configured - any non-empty availableTargetNames size should be accepted.
        assertDoesNotThrow(() -> config.doSharding(List.of("s0", "s1", "s2"), preciseValue("k1")));
        assertDoesNotThrow(() -> config.doSharding(List.of("s0", "s1", "s2", "s3", "s4"), preciseValue("k1")));
    }

    @Test
    void init_withValidShardingCount_isAccepted() {
        AlgorithmConfig config = new AlgorithmConfig();
        Properties props = new Properties();
        props.setProperty("sharding-count", "4");
        assertDoesNotThrow(() -> config.init(props));
    }

    @Test
    void init_withShardingCountHavingWhitespace_isTrimmed() {
        AlgorithmConfig config = new AlgorithmConfig();
        Properties props = new Properties();
        props.setProperty("sharding-count", "  4  ");
        assertDoesNotThrow(() -> config.init(props));
    }

    @Test
    void init_withZeroOrNegativeShardingCount_throws() {
        AlgorithmConfig zero = new AlgorithmConfig();
        Properties zeroProps = new Properties();
        zeroProps.setProperty("sharding-count", "0");
        assertThrows(IllegalArgumentException.class, () -> zero.init(zeroProps));

        AlgorithmConfig negative = new AlgorithmConfig();
        Properties negativeProps = new Properties();
        negativeProps.setProperty("sharding-count", "-3");
        assertThrows(IllegalArgumentException.class, () -> negative.init(negativeProps));
    }

    @Test
    void init_withNonNumericShardingCount_throws() {
        AlgorithmConfig config = new AlgorithmConfig();
        Properties props = new Properties();
        props.setProperty("sharding-count", "not-a-number");
        assertThrows(NumberFormatException.class, () -> config.init(props));
    }

    // ---------------------------------------------------------------
    // doSharding(PreciseShardingValue)
    // ---------------------------------------------------------------

    @Test
    void doSharding_withNullAvailableTargetNames_throws() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        assertThrows(IllegalStateException.class, () -> config.doSharding(null, preciseValue("k1")));
    }

    @Test
    void doSharding_withEmptyAvailableTargetNames_throws() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        assertThrows(IllegalStateException.class, () -> config.doSharding(List.of(), preciseValue("k1")));
    }

    @Test
    void doSharding_whenAvailableCountDoesNotMatchConfiguredShardingCount_throws() {
        AlgorithmConfig config = new AlgorithmConfig();
        Properties props = new Properties();
        props.setProperty("sharding-count", "5");
        config.init(props);

        assertThrows(IllegalStateException.class,
                () -> config.doSharding(List.of("s0", "s1", "s2"), preciseValue("k1")));
    }

    @Test
    void doSharding_withNullOrBlankValue_throws() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2");

        assertThrows(IllegalArgumentException.class, () -> config.doSharding(shards, preciseValue(null)));
        assertThrows(IllegalArgumentException.class, () -> config.doSharding(shards, preciseValue("")));
        assertThrows(IllegalArgumentException.class, () -> config.doSharding(shards, preciseValue("   ")));
    }

    @Test
    void doSharding_returnsOneOfTheGivenTargetNames() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2", "s3", "s4");
        for (int i = 0; i < 500; i++) {
            String result = config.doSharding(shards, preciseValue("user-" + i));
            assertTrue(shards.contains(result), "doSharding returned a name not in availableTargetNames: " + result);
        }
    }

    @Test
    void doSharding_valueIsTrimmedBeforeRouting() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2", "s3");
        assertEquals(
                config.doSharding(shards, preciseValue("abc")),
                config.doSharding(shards, preciseValue("  abc  ")));
    }

    @Test
    void doSharding_sameKeyAndTargetNames_isDeterministic() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("db0", "db1", "db2", "db3", "db4", "db5");
        String first = config.doSharding(shards, preciseValue("stable-key"));
        for (int i = 0; i < 200; i++) {
            assertEquals(first, config.doSharding(shards, preciseValue("stable-key")));
        }
    }

    @Test
    void doSharding_rebuildsInternalShardMap_whenAvailableTargetNamesSizeChanges() {
        // Regression guard for the shardMap-invalidation logic: growing (or
        // shrinking) the available shard set between calls must not throw,
        // and results must stay within the newly given set.
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);

        List<String> threeShards = List.of("s0", "s1", "s2");
        List<String> fiveShards = List.of("s0", "s1", "s2", "s3", "s4");

        for (int i = 0; i < 100; i++) {
            String key = "user-" + i;
            String withThree = config.doSharding(threeShards, preciseValue(key));
            assertTrue(threeShards.contains(withThree));
            String withFive = config.doSharding(fiveShards, preciseValue(key));
            assertTrue(fiveShards.contains(withFive));
        }
    }

    // ---------------------------------------------------------------
    // doSharding(RangeShardingValue) - the value is never dereferenced by
    // the implementation (it unconditionally returns availableTargetNames),
    // so null is a valid, faithful stand-in here.
    // ---------------------------------------------------------------

    @Test
    void doSharding_forRangeQueries_broadcastsToEveryAvailableTarget() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2", "s3");
        assertEquals(shards, config.doSharding(shards, (org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue<String>) null));
    }

    // ---------------------------------------------------------------
    // getType()
    // ---------------------------------------------------------------

    @Test
    void getType_returnsJSHARD() {
        assertEquals("JSHARD-BINOMIAL-ALGORITHM", new AlgorithmConfig().getType());
    }

    // ---------------------------------------------------------------
    // predictShard() / getSlotId() - used by JShardRouter
    // ---------------------------------------------------------------

    @Test
    void predictShard_matchesDoSharding_forTheSameOrderedNameList() {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2", "s3", "s4", "s5");
        String key = "prediction-check";
        assertEquals(
                config.doSharding(shards, preciseValue(key)),
                config.predictShard(shards, key));
    }

    @Test
    void getSlotId_delegatesToAlgorithm_andIsStable() {
        AlgorithmConfig config = new AlgorithmConfig();
        byte[] key = "slot-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int slot = config.getSlotId(key);
        assertTrue(slot >= 0 && slot < ConstantValues.SLOT_COUNT);
        assertEquals(slot, config.getSlotId(key));
    }

    // ---------------------------------------------------------------
    // Thread-safety
    //
    // Verifies that concurrent access to AlgorithmConfig remains stable
    // and thread-safe when shardMap is lazily rebuilt across multiple threads.
    // ---------------------------------------------------------------
    @Test
    void concurrentDoSharding_withFixedTargetNames_doesNotThrowOrCorrupt() throws InterruptedException {
        AlgorithmConfig config = new AlgorithmConfig();
        config.init(null);
        List<String> shards = List.of("s0", "s1", "s2", "s3", "s4", "s5", "s6");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                try {
                    for (int i = 0; i < 5000; i++) {
                        String result = config.doSharding(shards, preciseValue("k-" + i));
                        assertTrue(shards.contains(result));
                    }
                } finally {
                    latch.countDown();
                }
            }));
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();
        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get());
        }
    }
}
