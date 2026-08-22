package org.jshard.router;

import org.jshard.algorithm.AlgorithmConfig;
import org.jshard.algorithm.ConstantValues;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JShardRouter}.
 */
class JShardRouterTest {

    private static final List<String> SHARDS = List.of("shard-a", "shard-b", "shard-c", "shard-d", "shard-e");

    // ---------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------

    @Test
    void nullShardNames_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardRouter(null));
    }

    @Test
    void emptyShardNames_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JShardRouter(List.of()));
    }

    @Test
    void validShardNames_isAccepted() {
        assertDoesNotThrow(() -> new JShardRouter(SHARDS));
    }

    // ---------------------------------------------------------------
    // getShardKey()
    // ---------------------------------------------------------------

    @Test
    void getShardKey_returnsOneOfTheConfiguredShardNames() {
        JShardRouter router = new JShardRouter(SHARDS);
        for (int i = 0; i < 500; i++) {
            String result = router.getShardKey("user-" + i);
            assertTrue(SHARDS.contains(result), "Predicted a shard not in the configured list: " + result);
        }
    }

    @Test
    void getShardKey_isDeterministic() {
        JShardRouter router = new JShardRouter(SHARDS);
        String first = router.getShardKey("stable-user");
        for (int i = 0; i < 200; i++) {
            assertEquals(first, router.getShardKey("stable-user"));
        }
    }

    @Test
    void getShardKey_matchesAlgorithmConfigPredictionForTheSameOrderedList() {
        // JShardRouter is a thin, fixed-list wrapper around
        // AlgorithmConfig.predictShard - this pins that relationship down
        // directly rather than just trusting it.
        JShardRouter router = new JShardRouter(SHARDS);
        AlgorithmConfig algorithmConfig = new AlgorithmConfig();
        java.util.Properties props = new java.util.Properties();
        props.setProperty("sharding-count", String.valueOf(SHARDS.size()));
        algorithmConfig.init(props);

        for (int i = 0; i < 100; i++) {
            String key = "check-" + i;
            assertEquals(algorithmConfig.predictShard(SHARDS, key), router.getShardKey(key));
        }
    }

    @Test
    void differentRouterInstances_withTheSameShardList_agree() {
        JShardRouter router1 = new JShardRouter(SHARDS);
        JShardRouter router2 = new JShardRouter(new ArrayList<>(SHARDS));
        for (int i = 0; i < 200; i++) {
            String key = "cross-instance-" + i;
            assertEquals(router1.getShardKey(key), router2.getShardKey(key));
        }
    }

    /**
     * Predicts shard routing for keys in a deterministic, construction-order-independent manner.
     * <p>
     * {@link JShardRouter} internally sorts the provided shard names alphabetically upon
     * construction. This guarantees consistent routing decisions regardless of the original
     * collection order and ensures alignment with {@code JShardDataSourceProvider}, which
     * enforces identical alphabetical sorting before delegating to ShardingSphere.
     */
    @Test
    void shardKeyPrediction_isNotSensitiveToConstructionOrder() {
        List<String> ascending = new ArrayList<>(SHARDS);
        List<String> reversed = new ArrayList<>(SHARDS);
        Collections.reverse(reversed);

        JShardRouter routerAscending = new JShardRouter(ascending);
        JShardRouter routerReversed = new JShardRouter(reversed);

        int differing = 0;
        int sampleSize = 500;
        for (int i = 0; i < sampleSize; i++) {
            String key = "order-sensitivity-" + i;
            assertEquals(routerAscending.getShardKey(key), routerReversed.getShardKey(key));
        }
    }

    // ---------------------------------------------------------------
    // getSlotId()
    // ---------------------------------------------------------------

    @Test
    void getSlotId_isIndependentOfShardListAndStable() {
        JShardRouter router = new JShardRouter(SHARDS);
        int slot = router.getSlotId("some-key");
        assertTrue(slot >= 0 && slot < ConstantValues.SLOT_COUNT);
        assertEquals(slot, router.getSlotId("some-key"));
    }

    @Test
    void getSlotId_isTheSameAcrossDifferentRoutersRegardlessOfShardList() {
        JShardRouter fiveShards = new JShardRouter(SHARDS);
        JShardRouter twoShards = new JShardRouter(List.of("only-a", "only-b"));
        assertEquals(fiveShards.getSlotId("same-key"), twoShards.getSlotId("same-key"));
    }
}
