package org.j2os.platform.jshard.router;

import org.j2os.platform.jshard.algorithm.JShardHashAlgorithm;

import java.util.Properties;
import java.util.Set;

/**
 * Standalone, database-free way to predict which shard a given sharding
 * column value would route to, using the exact same consistent-hashing
 * algorithm ({@link JShardHashAlgorithm}) that ShardingSphere uses
 * internally for real queries.
 * <p>
 * A {@code JShardRouter} takes an immutable snapshot of the shard name set
 * at construction time; it does not track later changes to the actual
 * cluster topology.
 * <p>
 * Because {@link JShardHashAlgorithm}'s hash rings are cached process-wide,
 * keyed only by shard names, virtual-node count, and hash seed (see
 * {@link JShardHashAlgorithm}), a router constructed with the
 * same parameters as the ones used to build a real
 * {@link org.j2os.platform.jshard.datasource.JShardDataSource} will always
 * produce identical routing decisions to that data source's real queries.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class JShardRouter {

    /**
     * The algorithm instance backing this router's predictions.
     */
    private final JShardHashAlgorithm algorithm;

    /**
     * The immutable snapshot of shard names this router routes against.
     */
    private final Set<String> shardNames;

    /**
     * Creates a new router for the given shard names.
     *
     * @param shardNames           the shard names to route against; copied into an immutable snapshot
     * @param virtualNodesPerShard the number of virtual nodes per shard on the hash ring
     * @param hashSeed             the hash seed used for virtual-node placement and value hashing
     * @throws IllegalArgumentException if {@code shardNames} is {@code null} or empty
     */
    public JShardRouter(Set<String> shardNames, int virtualNodesPerShard, int hashSeed) {
        if (shardNames == null || shardNames.isEmpty()) {
            throw new IllegalArgumentException("shardNames must not be null/empty");
        }
        this.shardNames = Set.copyOf(shardNames);

        Properties props = new Properties();
        props.setProperty("sharding-count", String.valueOf(shardNames.size()));
        props.setProperty("virtual-nodes-per-shard", String.valueOf(virtualNodesPerShard));
        props.setProperty("hash-seed", String.valueOf(hashSeed));

        this.algorithm = new JShardHashAlgorithm();
        this.algorithm.init(props);
    }

    /**
     * Predicts which shard the given sharding column value would route to.
     *
     * @param shardingColumnValue the sharding column value to route
     * @return the name of the shard this value would route to
     * @throws IllegalArgumentException if {@code shardingColumnValue} is {@code null} or blank
     */
    public String getShardKey(String shardingColumnValue) {
        return algorithm.predictShard(shardNames, shardingColumnValue);
    }
}
