package org.j2os.platform.jshard.algorithm;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * A ShardingSphere {@link StandardShardingAlgorithm} implementing consistent
 * hashing (a "hash ring") over the set of available shard names.
 * <p>
 * Each shard is given {@link #virtualNodesPerShard} virtual nodes placed at
 * deterministic positions on a 64-bit ring (via MurmurHash3-128, truncated
 * to a {@code long}); a value is routed to the shard owning the first
 * virtual node at or after the value's own hash position (wrapping around
 * to the first node if none is found). This keeps the amount of data that
 * moves when the shard set changes proportional to {@code 1/N} rather than
 * requiring a full reshuffle, which is the main advantage of consistent
 * hashing over a plain {@code hash % shardCount} scheme.
 * <p>
 * Built hash rings and per-shard virtual-node slot positions are cached in
 * process-wide, size- and time-bounded caches ({@link #RING_CACHE} and
 * {@link #SHARD_SLOTS_CACHE}) keyed purely by shard names, virtual-node
 * count, and hash seed - not by any physical connection details - so
 * multiple independently-constructed algorithm/router instances configured
 * with the same parameters always compute (and reuse) the exact same ring,
 * guaranteeing identical routing decisions across them.
 * <p>
 * An instance of this class is normally instantiated and configured by
 * ShardingSphere itself (via {@link #init(Properties)}), one instance per
 * configured sharding algorithm entry.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JShardHashAlgorithm implements StandardShardingAlgorithm<String> {

    /** Default number of virtual nodes placed on the ring per shard, used when not overridden via {@link #init(Properties)}. */
    public static final int DEFAULT_VIRTUAL_NODES_PER_SHARD = 160;

    /** Default hash seed, used when not overridden via {@link #init(Properties)}. */
    public static final int DEFAULT_HASH_SEED = 0;

    /** Upper bound accepted for {@code virtual-nodes-per-shard} in {@link #init(Properties)}, to guard against pathological configuration. */
    public static final int MAX_VIRTUAL_NODES_PER_SHARD = 100_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(JShardHashAlgorithm.class);

    /** Maximum number of distinct hash rings kept in {@link #RING_CACHE}. */
    private static final int RING_CACHE_MAX_SIZE = 1_000;

    /** Maximum number of distinct per-shard virtual-node slot arrays kept in {@link #SHARD_SLOTS_CACHE}. */
    private static final int SHARD_SLOTS_CACHE_MAX_SIZE = 50_000;

    /** How long an unused cache entry is kept before eviction, in both {@link #RING_CACHE} and {@link #SHARD_SLOTS_CACHE}. */
    private static final long CACHE_EXPIRE_AFTER_ACCESS_HOURS = 6;

    /**
     * Process-wide cache of built hash rings, keyed by the exact set of
     * shard names plus the virtual-node count and hash seed used to build
     * them. Shared across every {@link JShardHashAlgorithm} instance (and
     * every {@link org.j2os.platform.jshard.router.JShardRouter}) in the
     * JVM, so identically-configured rings are computed once and reused.
     */
    private static final Cache<RingCacheKey, NavigableMap<Long, String>> RING_CACHE = CacheBuilder.newBuilder()
            .maximumSize(RING_CACHE_MAX_SIZE)
            .expireAfterAccess(Duration.ofHours(CACHE_EXPIRE_AFTER_ACCESS_HOURS))
            .build();

    /**
     * Process-wide cache of computed virtual-node hash positions for a
     * single shard, keyed by shard name plus virtual-node count and hash
     * seed. Reused across multiple ring builds (e.g. by different tables
     * sharing the same shard set).
     */
    private static final Cache<ShardSlotsKey, long[]> SHARD_SLOTS_CACHE = CacheBuilder.newBuilder()
            .maximumSize(SHARD_SLOTS_CACHE_MAX_SIZE)
            .expireAfterAccess(Duration.ofHours(CACHE_EXPIRE_AFTER_ACCESS_HOURS))
            .build();

    /** Expected number of available shards, set via {@code sharding-count} in {@link #init(Properties)}; {@code null} if not configured. */
    private volatile Integer expectedShardingCount;

    /** Number of virtual nodes per shard on the ring, set via {@code virtual-nodes-per-shard} in {@link #init(Properties)}. */
    private volatile int virtualNodesPerShard = DEFAULT_VIRTUAL_NODES_PER_SHARD;

    /** Hash seed used for both virtual-node placement and value hashing, set via {@code hash-seed} in {@link #init(Properties)}. */
    private volatile int hashSeed = DEFAULT_HASH_SEED;

    /**
     * Routes a value to a shard by finding the first ring entry at or after
     * the value's hash position, wrapping around to the first entry of the
     * ring if the value's hash falls after every entry.
     *
     * @param ring  the hash ring to route against
     * @param value the value to route
     * @param seed  the hash seed to use when hashing {@code value}
     * @return the name of the shard that owns the ring position the value maps to
     */
    private static String route(NavigableMap<Long, String> ring, String value, int seed) {
        long hash = hashValue(value, seed);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * Returns the hash ring for the given shard names, virtual-node count
     * and seed, building (and caching) it if not already cached.
     *
     * @param availableTargetNames the shard names to build the ring from
     * @param virtualNodesPerShard the number of virtual nodes per shard
     * @param seed                 the hash seed used for placement
     * @return the (possibly cached) hash ring
     */
    private static NavigableMap<Long, String> getOrBuildRing(Collection<String> availableTargetNames,
                                                             int virtualNodesPerShard, int seed) {
        Set<String> snapshot = Set.copyOf(availableTargetNames);
        RingCacheKey key = new RingCacheKey(snapshot, virtualNodesPerShard, seed);
        return getFromCache(RING_CACHE, key, () -> buildRing(snapshot, virtualNodesPerShard, seed));
    }

    /**
     * Builds a fresh hash ring by placing every shard's virtual nodes onto
     * it, resolving any hash collisions along the way. Shard names are
     * processed in a fixed, deterministic (alphabetical) order so that, if
     * a collision ever occurs between two shards' virtual nodes, which
     * shard wins that slot is reproducible across JVM restarts - {@code
     * Set} iteration order (e.g. from {@link Set#copyOf}) is not
     * guaranteed to be stable across runs, so it must never be allowed to
     * influence the outcome of a real collision.
     *
     * @param shardNames            the shard names to place on the ring
     * @param virtualNodesPerShard  the number of virtual nodes per shard
     * @param seed                  the hash seed used for placement
     * @return the newly built hash ring
     */
    private static NavigableMap<Long, String> buildRing(Set<String> shardNames, int virtualNodesPerShard, int seed) {
        List<String> orderedShardNames = new ArrayList<>(shardNames);
        Collections.sort(orderedShardNames);

        TreeMap<Long, String> newRing = new TreeMap<>();
        for (String shardName : orderedShardNames) {
            long[] slots = getOrComputeShardSlots(shardName, virtualNodesPerShard, seed);
            for (long slot : slots) {
                putResolvingCollision(newRing, slot, shardName, seed);
            }
        }
        return newRing;
    }

    /**
     * Places a single virtual-node hash position onto the ring, resolving a
     * collision (the same position already claimed by a different shard)
     * by repeatedly rehashing the position until a free slot is found.
     *
     * @param ring      the ring being built
     * @param hash      the virtual node's initial hash position
     * @param shardName the shard this virtual node belongs to
     * @param seed      the hash seed used for collision rehashing
     * @throws IllegalStateException if no free slot is found after 10,000 rehash attempts
     */
    private static void putResolvingCollision(TreeMap<Long, String> ring, long hash, String shardName, int seed) {
        long h = hash;
        int attempt = 0;
        while (true) {
            String existing = ring.putIfAbsent(h, shardName);
            if (existing == null || existing.equals(shardName)) {
                return;
            }
            attempt++;
            if (attempt > 10_000) {
                throw new IllegalStateException(
                        "Could not resolve a hash ring collision after " + attempt + " attempts");
            }
            LOGGER.debug("Hash collision between virtual node of shard '{}' and '{}' at slot {} — retry #{}",
                    shardName, existing, h, attempt);
            h = hashValue(h + "#collision#" + attempt, seed);
        }
    }

    /**
     * Returns the virtual-node hash positions for a single shard, computing
     * (and caching) them if not already cached.
     *
     * @param shardName            the shard to compute positions for
     * @param virtualNodesPerShard the number of virtual nodes to compute
     * @param seed                 the hash seed used for placement
     * @return the (possibly cached) array of virtual-node hash positions
     */
    private static long[] getOrComputeShardSlots(String shardName, int virtualNodesPerShard, int seed) {
        ShardSlotsKey key = new ShardSlotsKey(shardName, virtualNodesPerShard, seed);
        return getFromCache(SHARD_SLOTS_CACHE, key, () -> computeShardSlots(shardName, virtualNodesPerShard, seed));
    }

    /**
     * Computes the initial (pre-collision-resolution) hash position of
     * every virtual node for a single shard, by hashing
     * {@code shardName + '#' + virtualNodeIndex} for each virtual node.
     *
     * @param shardName            the shard to compute positions for
     * @param virtualNodesPerShard the number of virtual nodes to compute
     * @param seed                 the hash seed used for placement
     * @return the array of virtual-node hash positions, indexed by virtual node number
     */
    private static long[] computeShardSlots(String shardName, int virtualNodesPerShard, int seed) {
        long[] slots = new long[virtualNodesPerShard];
        HashFunction hashFunction = Hashing.murmur3_128(seed);
        for (int v = 0; v < virtualNodesPerShard; v++) {
            Hasher hasher = hashFunction.newHasher();
            hasher.putUnencodedChars(shardName);
            hasher.putChar('#');
            hasher.putInt(v);
            slots[v] = hasher.hash().asLong();
        }
        return slots;
    }

    /**
     * Hashes an arbitrary string to a 64-bit position on the ring, using
     * MurmurHash3-128 with the given seed (truncated to a {@code long}).
     *
     * @param value the string to hash
     * @param seed  the hash seed to use
     * @return the resulting 64-bit hash value
     */
    private static long hashValue(String value, int seed) {
        return Hashing.murmur3_128(seed).hashString(value, StandardCharsets.UTF_8).asLong();
    }

    /**
     * Fetches {@code key} from {@code cache}, computing and storing it via
     * {@code loader} on a miss.
     *
     * @param cache  the cache to read/write
     * @param key    the cache key
     * @param loader supplies the value to cache on a miss
     * @param <K>    the cache key type
     * @param <V>    the cached value type
     * @return the cached (or freshly computed) value
     * @throws IllegalStateException if {@code loader} throws while computing the value
     */
    private static <K, V> V getFromCache(Cache<K, V> cache, K key, Callable<V> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Error computing/retrieving a value from the cache", e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * Configures this algorithm instance from the properties supplied by
     * ShardingSphere. Recognizes three optional properties:
     * <ul>
     *   <li>{@code sharding-count} - the expected number of available shards;
     *       if set, every routing call validates that the actual shard count matches</li>
     *   <li>{@code virtual-nodes-per-shard} - overrides {@link #DEFAULT_VIRTUAL_NODES_PER_SHARD}</li>
     *   <li>{@code hash-seed} - overrides {@link #DEFAULT_HASH_SEED}</li>
     * </ul>
     *
     * @param props the algorithm properties supplied by ShardingSphere; may be {@code null}
     * @throws IllegalArgumentException if {@code sharding-count} is present and not positive,
     *                                   or {@code virtual-nodes-per-shard} is present and not
     *                                   positive or exceeds {@link #MAX_VIRTUAL_NODES_PER_SHARD}
     */
    @Override
    public void init(Properties props) {
        String countProp = props == null ? null : props.getProperty("sharding-count");
        if (countProp != null) {
            expectedShardingCount = Integer.parseInt(countProp.trim());
            if (expectedShardingCount <= 0) {
                throw new IllegalArgumentException("sharding-count must be positive");
            }
        }

        String vNodesProp = props == null ? null : props.getProperty("virtual-nodes-per-shard");
        if (vNodesProp != null) {
            int parsed = Integer.parseInt(vNodesProp.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("virtual-nodes-per-shard must be positive");
            }
            if (parsed > MAX_VIRTUAL_NODES_PER_SHARD) {
                throw new IllegalArgumentException(
                        "virtual-nodes-per-shard (" + parsed + ") exceeds the allowed maximum (" + MAX_VIRTUAL_NODES_PER_SHARD
                                + ")");
            }
            virtualNodesPerShard = parsed;
        }

        String seedProp = props == null ? null : props.getProperty("hash-seed");
        if (seedProp != null) {
            hashSeed = Integer.parseInt(seedProp.trim());
        }
    }

    /**
     * ShardingSphere entry point for an exact-match (precise) sharding
     * condition: routes the given sharding value to a single target shard.
     *
     * @param availableTargetNames the currently available shard names
     * @param shardingValue        the exact sharding column value to route
     * @return the name of the shard the value routes to
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<String> shardingValue) {
        return routeWithValidation(availableTargetNames, shardingValue.getValue(), shardingValue.getLogicTableName());
    }

    /**
     * ShardingSphere entry point for a range sharding condition. Since a
     * hash-based algorithm cannot determine which shards a range of values
     * maps to without hashing every individual value, this conservatively
     * returns every available shard so no matching rows are missed.
     *
     * @param availableTargetNames the currently available shard names
     * @param shardingValue        the sharding column range being queried
     * @return {@code availableTargetNames}, unchanged
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> shardingValue) {
        return availableTargetNames;
    }

    /**
     * Returns the algorithm type name this class registers itself under in
     * ShardingSphere configuration.
     *
     * @return the algorithm type name
     */
    @Override
    public String getType() {
        return "JSHARD_CONSISTENT_HASH";
    }

    /**
     * Predicts which shard a value would route to, without going through
     * ShardingSphere's {@link #doSharding} entry points. Useful for
     * standalone routing predictions (see
     * {@link org.j2os.platform.jshard.router.JShardRouter}) or for testing.
     *
     * @param availableTargetNames the shard names to route against
     * @param value                the value to route
     * @return the name of the shard the value routes to
     */
    public String predictShard(Collection<String> availableTargetNames, String value) {
        return routeWithValidation(availableTargetNames, value, "n/a");
    }

    /**
     * Validates the inputs to a routing call, then builds/reuses the hash
     * ring and routes {@code value} against it.
     *
     * @param availableTargetNames the shard names to route against
     * @param value                the value to route
     * @param logicTableName       the logical table name, used only for error messages
     * @return the name of the shard {@code value} routes to
     * @throws IllegalArgumentException if {@code value} is {@code null} or blank
     * @throws IllegalStateException    if {@code availableTargetNames} is {@code null}/empty, or
     *                                   its size doesn't match a configured {@code sharding-count}
     */
    private String routeWithValidation(Collection<String> availableTargetNames, String value, String logicTableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "The sharding column value for table '" + logicTableName + "' must not be null/empty");
        }
        if (availableTargetNames == null || availableTargetNames.isEmpty()) {
            throw new IllegalStateException(
                    "No shards are available for table '" + logicTableName + "'");
        }
        if (expectedShardingCount != null && availableTargetNames.size() != expectedShardingCount) {
            throw new IllegalStateException(String.format(
                    "The number of available shards (%d) does not match the configured sharding-count (%d)",
                    availableTargetNames.size(), expectedShardingCount));
        }
        NavigableMap<Long, String> ring = getOrBuildRing(availableTargetNames, virtualNodesPerShard, hashSeed);
        return route(ring, value, hashSeed);
    }

    /** Cache key for {@link #RING_CACHE}: a hash ring is fully determined by its shard names, virtual-node count, and seed. */
    private record RingCacheKey(Set<String> shardNames, int virtualNodesPerShard, int seed) {
    }

    /** Cache key for {@link #SHARD_SLOTS_CACHE}: a shard's virtual-node positions are fully determined by its name, virtual-node count, and seed. */
    private record ShardSlotsKey(String shardName, int virtualNodesPerShard, int seed) {
    }
}