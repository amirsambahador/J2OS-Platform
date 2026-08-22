package org.jshard.router;


import org.jshard.algorithm.Algorithm;
import org.jshard.algorithm.AlgorithmConfig;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Predicts, without touching the database or going through ShardingSphere's
 * {@code doSharding} entry points, which shard a given sharding column
 * value would route to via {@link AlgorithmConfig}.
 * <p>
 * Immutable and safe to share: the shard name list is fixed at construction
 * time via {@link #JShardRouter(List)}, and {@link #getShardKey(String)}
 * predictions are consistent across calls for the same input.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @author taha rouhi
 */
public final class JShardRouter {

    private final List<String> shardNames;
    private final AlgorithmConfig algorithm;




    /**
     * Builds a router for a fixed set of shard names.
     *
     * @param shardNames the shard names to route against; must not be {@code null} or empty
     * @throws IllegalArgumentException if {@code shardNames} is {@code null} or empty
     */
    public JShardRouter(List<String> shardNames) {
        if  (shardNames == null || shardNames.isEmpty()){
            throw new IllegalArgumentException("shardNames can't be null or empty");
        }
        List<String> sortedShardNames = new ArrayList<>(shardNames);
        Collections.sort(sortedShardNames);
        this.shardNames = sortedShardNames;
        int shardCount = this.shardNames.size();


        Properties props =  new Properties();
        props.setProperty("sharding-count", String.valueOf(shardCount));



        this.algorithm = new AlgorithmConfig();
        this.algorithm.init(props);



    }




    /**
     * Predicts which shard {@code key} would route to.
     *
     * @param key the sharding column value to route
     * @return the name of the shard {@code key} routes to
     * @throws IllegalArgumentException if {@code key} is {@code null} or blank
     */
    public String getShardKey(String key){
        return algorithm.predictShard(shardNames, key);

    }


    /**
     * Returns the slot id {@code key} would be assigned to, independent of
     * the current shard count (see {@link AlgorithmConfig#getSlotId(byte[])}).
     * <p>
     * Intended for callers that want to persist a row's slot id to support
     * future slot-based resharding/migration without a full-table scan -
     * see {@link Algorithm}'s Javadoc for details. Not
     * currently used for migration anywhere in this project.
     *
     * @param key the value to compute a slot id for
     * @return the slot id, in {@code [0, ConstantValues.SLOT_COUNT)}
     */
    public int getSlotId(String key){
        byte[] byteKey = key.getBytes(StandardCharsets.UTF_8);
        return algorithm.getSlotId(byteKey);
    }








}