package org.jshard.algorithm;

import org.apache.shardingsphere.infra.datanode.DataNodeInfo;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.*;

/**
 * A ShardingSphere {@link StandardShardingAlgorithm} routing values to
 * shards via {@link Algorithm}, a stateless implementation of BinomialHash
 * (Coluzzi, Brocco, Antonucci &amp; Leidi, 2024 - arXiv:2406.19836): a
 * constant-time, minimal-memory consistent hashing algorithm.
 * <p>
 * Unlike a ring-based consistent hashing scheme, no hash ring or per-shard
 * slot table is ever built or cached - every routing decision is computed
 * independently, from the sharding value alone, with no process-wide state
 * shared across calls. The only state an instance of this class holds is a
 * small {@code slotId -> shardName} {@link #shardMap}, rebuilt whenever the
 * available shard count changes.
 * <p>
 * An instance of this class is normally instantiated and configured by
 * ShardingSphere itself (via {@link #init(Properties)}), one instance per
 * configured sharding algorithm entry.
 *
 * @author taha rouhi
 */
public class AlgorithmConfig implements StandardShardingAlgorithm<String> {


    private volatile Map<Integer,String> shardMap;

    private volatile Integer shardCount;

    private final Algorithm algorithm = new Algorithm();




    /**
     * Configures this algorithm instance from the properties supplied by
     * ShardingSphere. Recognizes one optional property:
     * <ul>
     *   <li>{@code sharding-count} - the expected number of available shards;
     *       if set, every routing call validates that the actual shard count matches</li>
     * </ul>
     *
     * @param props the algorithm properties supplied by ShardingSphere; may be {@code null}
     * @throws IllegalArgumentException if {@code sharding-count} is present and not positive
     */
    @Override
    public void init(Properties props){
        String countProp = props == null ? null : props.getProperty("sharding-count");
        if (countProp != null) {
            shardCount = Integer.parseInt(countProp.trim());
            if (shardCount <= 0) {
                throw new IllegalArgumentException("sharding-count must be positive");
            }
        }
    }

    /**
     * ShardingSphere entry point for an exact-match (precise) sharding
     * condition: routes the given sharding value to a single target shard
     * via {@link Algorithm#getShardId(byte[], int)}.
     *
     * @param availableTargetNames the currently available shard names
     * @param shardingValue        the exact sharding column value to route
     * @return the name of the shard the value routes to
     * @throws IllegalStateException    if {@code availableTargetNames} is {@code null}/empty, or
     *                                   its size doesn't match a configured {@code sharding-count}
     * @throws IllegalArgumentException if the sharding column value is {@code null} or blank
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<String> shardingValue) {

        String logicTableName = shardingValue.getLogicTableName();

        if (availableTargetNames == null || availableTargetNames.isEmpty()) {
            throw new IllegalStateException(
                    "No shards are available for table '" + logicTableName + "'");
        }
        if (shardCount != null && availableTargetNames.size() != shardCount) {
            throw new IllegalStateException(String.format(
                    "The number of available shards (%d) does not match the configured sharding-count (%d)",
                    availableTargetNames.size(), shardCount));
        }

        String rawValue = shardingValue.getValue();
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(
                    "The sharding column value for table '" + logicTableName + "' must not be null/empty");
        }
        String key = rawValue.trim();

        if (this.shardMap == null || this.shardMap.size() !=  availableTargetNames.size()) {
            this.shardMap = buildShardMapFromNames(availableTargetNames);
        }

        return algorithm.getShardKey(shardMap, key);

    }


    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<String> shardingValue) {
        return availableTargetNames;
    }


    @Override
    public String getType() {
        return "JSHARD-BINOMIAL-ALGORITHM";
    }


    public Map<Integer,String> buildShardMapFromNames(Collection<String> shardNames) {
        List<String> sorted =  new ArrayList<>(shardNames);
        Collections.sort(sorted);
        Map<Integer, String> shardMap = new LinkedHashMap<>();
        int id = 0;
        for (String name : sorted) {
            shardMap.put(id, name);
            id++;
        }
        return shardMap;
    }


    public String predictShard(List<String> shardNames, String key) {
        PreciseShardingValue<String> shardingValue = new PreciseShardingValue<>("temp","temp", new DataNodeInfo("s",1, 'D') ,key);
        return doSharding(shardNames,shardingValue);
    }


    public int getSlotId(byte[] key){
        return algorithm.slotIdAssigner(key);
    }







}