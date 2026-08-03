package org.j2os.platform.jshard.datasource;

import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.rule.ReadwriteSplittingDataSourceGroupRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.j2os.platform.jshard.algorithm.JShardHashAlgorithm;
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.config.JShardTableConfig;
import org.j2os.platform.jshard.router.JShardRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles a ready-to-use {@link JShardDataSource} from a set of shards
 * (each with an optional primary/replica list) and tables, wiring up
 * ShardingSphere's {@link ShardingRuleConfiguration} (using
 * {@link JShardHashAlgorithm} as the sharding algorithm for every table)
 * and, for any shard with replicas, a
 * {@link ReadwriteSplittingRuleConfiguration} routing writes to the primary
 * and reads across the replicas.
 * <p>
 * Entry point is {@link #builder()}; see {@link Builder} for the full
 * configuration surface.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class JShardDataSourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JShardDataSourceProvider.class);

    private JShardDataSourceProvider() {
    }

    /**
     * Starts a new {@link Builder} for assembling a {@link JShardDataSource}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks that every primary and replica connection configuration in
     * {@code shardMap} is currently reachable, failing fast with a clear
     * message identifying the offending shard/role rather than surfacing an
     * opaque failure later, mid-query or mid-build.
     *
     * @param shardMap the shards to check, keyed by logical shard name; each
     *                  value's first element is treated as the primary and
     *                  any remaining elements as replicas
     * @throws IllegalArgumentException if any shard has no configurations
     * @throws IllegalStateException    if any primary or replica is not reachable
     */
    public static void assertAllReachable(Map<String, List<JShardConnectionConfig>> shardMap) {
        for (Map.Entry<String, List<JShardConnectionConfig>> entry : shardMap.entrySet()) {
            String logicalDsName = entry.getKey();
            List<JShardConnectionConfig> configs = entry.getValue();
            if (configs == null || configs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Shard '" + logicalDsName + "' requires at least one ShardConfig (primary)");
            }
            for (int i = 0; i < configs.size(); i++) {
                String role = i == 0 ? "primary" : "replica" + i;
                if (!JShardDataSourceRegistry.isReachable(configs.get(i), 3)) {
                    throw new IllegalStateException(
                            "Shard '" + logicalDsName + "' (" + role + ") is not reachable.");
                }
                LOGGER.info("Shard healthy: {} ({})", logicalDsName, role);
            }
        }
    }

    /**
     * Validates the full configuration, creates every physical connection
     * pool, assembles the ShardingSphere rule set, and builds the final
     * {@link JShardDataSource}. If anything fails after pools have started
     * being created, every already-created pool in {@code pool} is closed
     * before the failure is rethrown.
     *
     * @param primaryReplicaMap    the shards to build, keyed by logical shard name
     * @param tables                the tables to configure sharding for
     * @param virtualNodesPerShard  the number of virtual nodes per shard on the hash ring, or {@code null} to use {@link JShardHashAlgorithm#DEFAULT_VIRTUAL_NODES_PER_SHARD}
     * @param hashSeed              the hash seed used for shard placement and routing
     * @param showSql               whether ShardingSphere should log the logical/actual SQL for every query
     * @return the fully assembled, ready-to-use {@link JShardDataSource}
     * @throws IllegalArgumentException if {@code primaryReplicaMap} or {@code tables} is null/empty,
     *                                   {@code virtualNodesPerShard} is out of range, a shard name is
     *                                   invalid, or a duplicate table name is present
     * @throws Exception                 if the underlying ShardingSphere data source fails to build
     */
    private static JShardDataSource buildDataSource(
            Map<String, List<JShardConnectionConfig>> primaryReplicaMap,
            List<JShardTableConfig> tables,
            Integer virtualNodesPerShard,
            int hashSeed,
            boolean showSql
    ) throws Exception {

        if (primaryReplicaMap == null || primaryReplicaMap.isEmpty()) {
            throw new IllegalArgumentException("At least one shard must be added via .shard(...)");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("At least one table must be added via .table(...)");
        }
        if (virtualNodesPerShard != null) {
            if (virtualNodesPerShard <= 0) {
                throw new IllegalArgumentException("virtualNodesPerShard must be greater than zero");
            }
            if (virtualNodesPerShard > JShardHashAlgorithm.MAX_VIRTUAL_NODES_PER_SHARD) {
                throw new IllegalArgumentException("virtualNodesPerShard (" + virtualNodesPerShard
                        + ") exceeds the allowed maximum (" + JShardHashAlgorithm.MAX_VIRTUAL_NODES_PER_SHARD + ")");
            }
        }
        validateShardNames(primaryReplicaMap.keySet());
        validateNoDuplicateTableNames(tables);

        int effectiveVNodes = virtualNodesPerShard != null
                ? virtualNodesPerShard
                : JShardHashAlgorithm.DEFAULT_VIRTUAL_NODES_PER_SHARD;

        JShardDataSourceRegistry pool = new JShardDataSourceRegistry();
        try {
            List<String> logicalNames = new ArrayList<>(primaryReplicaMap.keySet());
            Collections.sort(logicalNames);

            Map<String, DataSource> dsMap = new LinkedHashMap<>();
            List<ReadwriteSplittingDataSourceGroupRuleConfiguration> rwGroups = new ArrayList<>();

            for (String logicalDsName : logicalNames) {
                List<JShardConnectionConfig> configs = primaryReplicaMap.get(logicalDsName);
                JShardConnectionConfig primaryConfig = configs.get(0);

                if (configs.size() == 1) {
                    dsMap.put(logicalDsName, pool.create(logicalDsName, primaryConfig));
                    continue;
                }

                String primaryName = logicalDsName + "_primary";
                dsMap.put(primaryName, pool.create(primaryName, primaryConfig));

                List<String> replicaNames = new ArrayList<>();
                for (int j = 1; j < configs.size(); j++) {
                    JShardConnectionConfig replicaConfig = configs.get(j);
                    String replicaName = logicalDsName + "_replica" + j;
                    dsMap.put(replicaName, pool.create(replicaName, replicaConfig));
                    replicaNames.add(replicaName);
                }

                rwGroups.add(new ReadwriteSplittingDataSourceGroupRuleConfiguration(
                        logicalDsName, primaryName, replicaNames, null));
            }

            ShardingRuleConfiguration shardingConfig = new ShardingRuleConfiguration();
            for (JShardTableConfig table : tables) {
                String actualNodes = logicalNames.stream()
                        .map(name -> name + "." + table.name())
                        .collect(Collectors.joining(","));

                ShardingTableRuleConfiguration tableRule =
                        new ShardingTableRuleConfiguration(table.name(), actualNodes);

                Properties algoProps = new Properties();
                algoProps.setProperty("algorithmClassName", JShardHashAlgorithm.class.getName());
                algoProps.setProperty("sharding-count", String.valueOf(logicalNames.size()));
                algoProps.setProperty("virtual-nodes-per-shard", String.valueOf(effectiveVNodes));
                algoProps.setProperty("hash-seed", String.valueOf(hashSeed));
                algoProps.setProperty("strategy", "standard");

                String algoName = table.name() + "_hash_algo";
                shardingConfig.getShardingAlgorithms().put(algoName, new AlgorithmConfiguration("CLASS_BASED", algoProps));
                tableRule.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration(table.shardingColumn(), algoName));
                shardingConfig.getTables().add(tableRule);
            }

            Properties globalProps = new Properties();
            globalProps.setProperty("sql-show", showSql ? "true" : "false");

            List<RuleConfiguration> rules = new ArrayList<>();
            rules.add(shardingConfig);
            if (!rwGroups.isEmpty()) {
                rules.add(new ReadwriteSplittingRuleConfiguration(rwGroups, Collections.emptyMap()));
            }

            DataSource rawDataSource = ShardingSphereDataSourceFactory.createDataSource(dsMap, rules, globalProps);

            JShardRouter router = new JShardRouter(primaryReplicaMap.keySet(), effectiveVNodes, hashSeed);
            return new JShardDataSource(rawDataSource, pool, router);
        } catch (Exception e) {
            pool.closeAll();
            throw e;
        }
    }

    // ---- Core ----

    /**
     * Validates that no two tables share the same name.
     *
     * @param tables the tables to check
     * @throws IllegalArgumentException if a duplicate table name is found
     */
    private static void validateNoDuplicateTableNames(List<JShardTableConfig> tables) {
        Set<String> seen = new java.util.HashSet<>();
        for (JShardTableConfig table : tables) {
            if (!seen.add(table.name())) {
                throw new IllegalArgumentException("Duplicate table name in the tables list: '" + table.name() + "'");
            }
        }
    }

    /**
     * Validates that every shard name is safe to use as a ShardingSphere
     * logical data source name: non-blank, no leading/trailing whitespace,
     * and free of characters ({@code .}, {@code ,}, quotes, whitespace,
     * control characters) that would interfere with ShardingSphere's
     * configuration syntax.
     *
     * @param shardNames the shard names to validate
     * @throws IllegalArgumentException if any name fails one of the checks above
     */
    private static void validateShardNames(Set<String> shardNames) {
        for (String name : shardNames) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A shard name must not be null/blank");
            }
            if (!name.equals(name.strip())) {
                throw new IllegalArgumentException("A shard name must not have leading/trailing whitespace: '" + name + "'");
            }
            if (name.contains(".") || name.contains(",") || name.contains("'") || name.contains("\"")) {
                throw new IllegalArgumentException(
                        "A shard name must not contain '.', ',', or a quote character (ShardingSphere syntax restriction): '" + name + "'");
            }
            for (char c : name.toCharArray()) {
                if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                    throw new IllegalArgumentException(
                            "A shard name must not contain whitespace or control characters: '" + name + "'");
                }
            }
        }
    }

    /**
     * Fluent builder for {@link JShardDataSource}. Configure one or more
     * shards (each with an optional primary/replica list) via
     * {@link #shard} or {@link #shards}, one or more tables via
     * {@link #table} or {@link #tables}, optionally tune the hash ring via
     * {@link #virtualNodesPerShard}/{@link #hashSeed}, and finally call
     * {@link #build()}.
     */
    public static final class Builder {
        private final Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
        private final List<JShardTableConfig> tables = new ArrayList<>();
        private Integer virtualNodesPerShard;
        private int hashSeed = JShardHashAlgorithm.DEFAULT_HASH_SEED;
        private boolean showSql = false;

        private Builder() {
        }

        /**
         * Adds a shard, with an optional primary/replica list. If more
         * than one config is supplied, the first is treated as the primary
         * (writes) and the rest as replicas (reads), and a read-write
         * splitting group is configured automatically for this shard.
         *
         * @param name     the logical shard name; must be unique across this builder
         * @param primary  the primary connection configuration
         * @param replicas zero or more replica connection configurations
         * @return this builder
         * @throws NullPointerException     if {@code name} or {@code primary} is {@code null}
         * @throws IllegalArgumentException if a shard is already registered under {@code name}
         */
        public Builder shard(String name, JShardConnectionConfig primary, JShardConnectionConfig... replicas) {
            Objects.requireNonNull(name, "The shard name must not be null");
            Objects.requireNonNull(primary, "The primary config for shard '" + name + "' must not be null");
            if (shards.containsKey(name)) {
                throw new IllegalArgumentException("Shard '" + name + "' has already been added");
            }
            List<JShardConnectionConfig> configs = new ArrayList<>();
            configs.add(primary);
            Collections.addAll(configs, replicas);
            shards.put(name, configs);
            return this;
        }


        /**
         * Adds a table to be configured for sharding.
         *
         * @param name           the table name
         * @param shardingColumn the column whose value determines the target shard
         * @return this builder
         * @throws IllegalArgumentException if {@code name} or {@code shardingColumn} is not a valid identifier (see {@link JShardTableConfig})
         */
        public Builder table(String name, String shardingColumn) {
            tables.add(new JShardTableConfig(name, shardingColumn));
            return this;
        }

        /**
         * Adds every shard from a pre-built map in one call, equivalent to
         * calling {@link #shard} once per entry (using the first
         * configuration in each list as the primary, and any remaining
         * ones as replicas).
         *
         * @param shardConfigs the shards to add, keyed by logical shard name
         * @return this builder
         * @throws NullPointerException     if {@code shardConfigs} is {@code null}
         * @throws IllegalArgumentException if any entry's config list is null/empty,
         *                                   or a shard name collides with one already added
         */
        public Builder shards(Map<String, List<JShardConnectionConfig>> shardConfigs) {
            Objects.requireNonNull(shardConfigs, "The shardConfigs map must not be null");
            for (Map.Entry<String, List<JShardConnectionConfig>> entry : shardConfigs.entrySet()) {
                List<JShardConnectionConfig> configs = entry.getValue();
                if (configs == null || configs.isEmpty()) {
                    throw new IllegalArgumentException("Shard '" + entry.getKey() + "' requires at least one config (primary)");
                }
                JShardConnectionConfig primary = configs.get(0);       // the first element is always the primary
                JShardConnectionConfig[] replicas = configs.subList(1, configs.size())
                        .toArray(new JShardConnectionConfig[0]);         // the rest are replicas (an empty array if there are none)
                shard(entry.getKey(), primary, replicas);
            }
            return this;
        }

        /**
         * Adds every table from a pre-built list in one call, equivalent to
         * calling {@link #table} once per entry.
         *
         * @param tableConfigs the tables to add
         * @return this builder
         * @throws NullPointerException if {@code tableConfigs} is {@code null}
         */
        public Builder tables(List<JShardTableConfig> tableConfigs) {
            Objects.requireNonNull(tableConfigs, "The tableConfigs list must not be null");
            tables.addAll(tableConfigs);
            return this;
        }

        /**
         * Sets the number of virtual nodes placed on the hash ring per
         * shard. Higher values spread load more evenly across shards at the
         * cost of a larger ring; if not called,
         * {@link JShardHashAlgorithm#DEFAULT_VIRTUAL_NODES_PER_SHARD} is used.
         *
         * @param virtualNodesPerShard the virtual node count; must be positive and at most {@link JShardHashAlgorithm#MAX_VIRTUAL_NODES_PER_SHARD}
         * @return this builder
         */
        public Builder virtualNodesPerShard(int virtualNodesPerShard) {
            this.virtualNodesPerShard = virtualNodesPerShard;
            return this;
        }

        /**
         * Sets the hash seed used for virtual-node placement and value
         * routing. Changing this reshuffles the entire data-to-shard
         * mapping; if not called, {@link JShardHashAlgorithm#DEFAULT_HASH_SEED} is used.
         *
         * @param hashSeed the hash seed
         * @return this builder
         */
        public Builder hashSeed(int hashSeed) {
            this.hashSeed = hashSeed;
            return this;
        }

        /**
         * Enables or disables ShardingSphere's logical/actual SQL logging
         * for every query executed through the built data source.
         *
         * @param showSql whether to log SQL
         * @return this builder
         */
        public Builder showSql(boolean showSql) {
            this.showSql = showSql;
            return this;
        }

        /**
         * Validates the full configuration and builds the
         * {@link JShardDataSource}, actually connecting to every configured
         * shard.
         *
         * @return the fully assembled, ready-to-use {@link JShardDataSource}
         * @throws Exception if configuration is invalid or the underlying
         *                    ShardingSphere data source fails to build
         */
        public JShardDataSource build() throws Exception {
            return JShardDataSourceProvider.buildDataSource(shards, tables, virtualNodesPerShard, hashSeed, showSql);
        }
    }
}
