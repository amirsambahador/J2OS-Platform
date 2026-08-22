package org.jshard.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link Algorithm}'s BinomialHash routing.
 * <p>
 * These are the tests that would have caught the {@code hash_i}/{@code hash_0}
 * bug in {@code getShardIndex}'s {@code c < M} branch (it must use the
 * original {@code hash_0}, per the paper's Algorithm 1, not the current
 * iteration's {@code hash_i}) - {@link #monotonicity_growingShardCount_onlyMovesToNewShard()}
 * and {@link #monotonicity_atPowerOfTwoBoundaries()} both fail against the
 * unpatched implementation (~36% of moves land on the wrong shard) and pass
 * once that fix is applied.
 */
class AlgorithmTest {

    private final Algorithm algorithm = new Algorithm();

    // ---------------------------------------------------------------
    // Basic correctness / contract
    // ---------------------------------------------------------------

    @Test
    void sameKeyAndShardCount_alwaysSameResult() {
        byte[] key = "user:12345".getBytes(StandardCharsets.UTF_8);
        int expected = algorithm.getShardId(key, 7);
        for (int i = 0; i < 1000; i++) {
            assertEquals(expected, algorithm.getShardId(key, 7));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 7, 10, 17, 100, 257, 1000})
    void shardId_alwaysInValidRange(int shardCount) {
        Random random = new Random(shardCount);
        for (int i = 0; i < 5000; i++) {
            byte[] key = ("k-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
            int shard = algorithm.getShardId(key, shardCount);
            assertTrue(shard >= 0 && shard < shardCount,
                    "shard=" + shard + " out of range for shardCount=" + shardCount);
        }
    }

    @Test
    void singleShard_alwaysReturnsZero() {
        Random random = new Random(9);
        for (int i = 0; i < 1000; i++) {
            byte[] key = ("k-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
            assertEquals(0, algorithm.getShardId(key, 1));
        }
    }

    @Test
    void slotId_isIndependentOfShardCountAndInRange() {
        byte[] key = "some-key".getBytes(StandardCharsets.UTF_8);
        int slotId = algorithm.getSlotId(key);
        assertTrue(slotId >= 0 && slotId < ConstantValues.SLOT_COUNT);
        assertEquals(slotId, algorithm.getSlotId(key));
    }

    @Test
    void getShardKey_matchesGetShardId_throughTheSameShardMap() {
        Map<Integer, String> shardMap = new HashMap<>();
        shardMap.put(0, "shard-a");
        shardMap.put(1, "shard-b");
        shardMap.put(2, "shard-c");

        String key = "user:777";
        int shardId = algorithm.getShardId(key.getBytes(StandardCharsets.UTF_8), shardMap.size());
        assertEquals(shardMap.get(shardId), algorithm.getShardKey(shardMap, key));
    }

    // ---------------------------------------------------------------
    // Redis-style hash tags ({...})
    // ---------------------------------------------------------------

    @Test
    void keysWithSameHashTag_alwaysColocate() {
        int shardCount = 13;
        String[] keys = {
                "user:{42}:profile",
                "user:{42}:orders",
                "order:{42}:items",
                "{42}"
        };
        int expected = algorithm.getShardId(keys[0].getBytes(StandardCharsets.UTF_8), shardCount);
        for (String key : keys) {
            assertEquals(expected, algorithm.getShardId(key.getBytes(StandardCharsets.UTF_8), shardCount),
                    "Key '" + key + "' did not colocate with tag {42}");
        }
    }

    @Test
    void hashTag_onlyBracesContentMatters_notWholeKey() {
        byte[] k1 = "aaaaaa{same}zzzzzz".getBytes(StandardCharsets.UTF_8);
        byte[] k2 = "bb{same}yy".getBytes(StandardCharsets.UTF_8);
        int shardCount = 11;
        assertEquals(algorithm.getShardId(k1, shardCount), algorithm.getShardId(k2, shardCount));
    }

    @Test
    void emptyHashTag_isIgnored_wholeKeyIsHashed() {
        // Per Redis convention, "{}" (nothing between the braces) is not a
        // valid tag and the whole key should be hashed instead.
        int shardCount = 8;
        byte[] withEmptyTag = "abc{}def".getBytes(StandardCharsets.UTF_8);
        int expectedSlot = MurmurHash3.murmurHash332(withEmptyTag, 0, withEmptyTag.length, 0) & ConstantValues.SLOT_MASK;
        assertEquals(expectedSlot, algorithm.getSlotId(withEmptyTag));
    }

    @Test
    void onlyFirstTagIsUsed_whenMultipleBracesArePresent() {
        // Redis rule: use the substring between the FIRST "{" and the
        // first "}" that follows it, ignoring any further braces.
        byte[] key = "a{first}b{second}c".getBytes(StandardCharsets.UTF_8);
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        int shardCount = 20;
        assertEquals(algorithm.getShardId(first, shardCount), algorithm.getShardId(key, shardCount));
    }

    @Test
    void unclosedBrace_hashesWholeKey() {
        // "{" with no matching "}" should not be treated as a tag.
        byte[] key = "abc{def".getBytes(StandardCharsets.UTF_8);
        int expectedSlot = MurmurHash3.murmurHash332(key, 0, key.length, 0) & ConstantValues.SLOT_MASK;
        assertEquals(expectedSlot, algorithm.getSlotId(key));
    }

    // ---------------------------------------------------------------
    // Monotonicity (growing shard count) - the core BinomialHash promise
    // ---------------------------------------------------------------

    @Test
    void monotonicity_growingShardCount_onlyMovesToNewShard() {
        Random random = new Random(1);
        int numKeys = 20_000;
        byte[][] keys = new byte[numKeys][];
        for (int i = 0; i < numKeys; i++) {
            keys[i] = ("key-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
        }

        int totalMoves = 0;
        int badMoves = 0;

        for (int n = 1; n <= 300; n++) {
            for (byte[] key : keys) {
                int oldShard = algorithm.getShardId(key, n);
                int newShard = algorithm.getShardId(key, n + 1);
                if (oldShard != newShard) {
                    totalMoves++;
                    // The only legal destination for a moved key is the
                    // newly added shard, at index n.
                    if (newShard != n) {
                        badMoves++;
                    }
                }
            }
        }

        assertEquals(0, badMoves,
                "Monotonicity violated: " + badMoves + " of " + totalMoves +
                        " moves went to a pre-existing shard instead of the new one (index n)");
    }

    @Test
    void monotonicity_atPowerOfTwoBoundaries() {
        // The hash_i/hash_0 bug's effect was concentrated almost entirely
        // at these transitions (E doubling), so this is tested separately
        // and explicitly, not just folded into the general sweep above.
        int[] boundaries = {2, 4, 8, 16, 32, 64, 128, 256};
        Random random = new Random(77);
        int numKeys = 5000;
        for (int n : boundaries) {
            int bad = 0;
            for (int i = 0; i < numKeys; i++) {
                byte[] key = ("bk-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
                int oldShard = algorithm.getShardId(key, n);
                int newShard = algorithm.getShardId(key, n + 1);
                if (oldShard != newShard && newShard != n) {
                    bad++;
                }
            }
            assertEquals(0, bad, "Violations right at power-of-two boundary n=" + n);
        }
    }

    @Test
    void minimalDisruption_shrinkingShardCount_onlyKeysFromRemovedShardMove() {
        Random random = new Random(2);
        int numKeys = 20_000;
        byte[][] keys = new byte[numKeys][];
        for (int i = 0; i < numKeys; i++) {
            keys[i] = ("key-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
        }

        for (int n = 2; n <= 300; n++) {
            for (byte[] key : keys) {
                int shardWithN = algorithm.getShardId(key, n);
                int shardWithNMinus1 = algorithm.getShardId(key, n - 1);
                // A key that wasn't on the removed shard (index n-1) must
                // stay exactly where it was.
                if (shardWithN != n - 1) {
                    assertEquals(shardWithN, shardWithNMinus1,
                            "Key not on removed shard " + (n - 1) + " moved unexpectedly at n=" + n);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Balance / uniformity
    //
    // NOTE ON THE TOLERANCE USED BELOW: Algorithm first folds every key
    // into one of only ConstantValues.SLOT_COUNT (2^14 = 16384) fixed
    // slots (see Algorithm's class Javadoc), and only then routes the
    // slot to a shard. This means the effective "population" being
    // distributed across shards is capped at SLOT_COUNT regardless of how
    // many distinct keys actually exist - adding more distinct keys does
    // NOT reduce this noise floor, because keys sharing a slot always land
    // on the same shard together. Ordinary "balls into bins" statistics
    // (SLOT_COUNT balls into shardCount bins) therefore predict a real,
    // inherent relative deviation of roughly sqrt(shardCount / SLOT_COUNT)
    // per shard - about 8% at shardCount=100, for example - well before
    // any bug is involved. The tolerance below is calibrated to that
    // floor (with a safety margin) rather than to a flat percentage, and
    // this is deliberate: a flat 10-15% threshold is not statistically
    // sound at these shardCount/SLOT_COUNT ratios and will fail on a
    // perfectly correct implementation. See ConstantValues.SLOT_BITS.
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 16, 32, 100})
    void distribution_isWithinTheStatisticallyExpectedNoiseFloor(int shardCount) {
        Random random = new Random(shardCount * 31L);
        int numKeys = 200_000;
        int[] counts = new int[shardCount];
        for (int i = 0; i < numKeys; i++) {
            byte[] key = ("k-" + random.nextInt()).getBytes(StandardCharsets.UTF_8);
            counts[algorithm.getShardId(key, shardCount)]++;
        }

        double expected = numKeys / (double) shardCount;
        double relativeNoiseFloor = Math.sqrt((double) shardCount / ConstantValues.SLOT_COUNT);
        double tolerance = 5.0 * relativeNoiseFloor; // safety margin above the theoretical floor

        for (int count : counts) {
            double deviation = Math.abs(count - expected) / expected;
            assertTrue(deviation < tolerance,
                    "shardCount=" + shardCount + " deviation=" + deviation + " tolerance=" + tolerance);
        }
    }

    // ---------------------------------------------------------------
    // Concurrency
    // ---------------------------------------------------------------

    @Test
    void concurrentCalls_produceConsistentResults() throws InterruptedException {
        int shardCount = 23;
        byte[] key = "concurrent-key".getBytes(StandardCharsets.UTF_8);
        int expected = algorithm.getShardId(key, shardCount);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<Integer> results = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < 10_000; j++) {
                        results.add(algorithm.getShardId(key, shardCount));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();

        assertEquals(Set.of(expected), results);
    }
}
