package org.jshard.datasource;

import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.algorithm.core.config.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.ReadwriteSplittingRuleConfiguration;
import org.apache.shardingsphere.readwritesplitting.config.rule.ReadwriteSplittingDataSourceGroupRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.jshard.algorithm.Algorithm;

import org.jshard.algorithm.AlgorithmConfig;
import org.jshard.config.JShardConnectionConfig;
import org.jshard.config.JShardTableConfig;
import org.jshard.router.JShardRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles a ready-to-use {@link JShardDataSource} from a set of shards
 * (each with an optional primary/replica list) and tables, wiring up
 * ShardingSphere's {@link ShardingRuleConfiguration} to route every
 * table's queries via {@link AlgorithmConfig} - a stateless BinomialHash
 * consistent hashing sharding strategy (see {@link Algorithm}) - and, for
 * any shard with replicas, a {@link ReadwriteSplittingRuleConfiguration}
 * routing writes to the primary and reads across the replicas.
 * <p>
 * Entry point is {@link #builder()}; see {@link Builder} for the full
 * configuration surface.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @author taha rouhi
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
            boolean showSql
    ) throws Exception {

        if (primaryReplicaMap == null || primaryReplicaMap.isEmpty()) {
            throw new IllegalArgumentException("At least one shard must be added via .shard(...)");
        }
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("At least one table must be added via .table(...)");
        }

        validateShardNames(primaryReplicaMap.keySet());
        validateNoDuplicateTableNames(tables);


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
                algoProps.setProperty("algorithmClassName", AlgorithmConfig.class.getName());
                algoProps.setProperty("sharding-count", String.valueOf(logicalNames.size()));
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


            JShardRouter router = new JShardRouter(logicalNames);
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
     * {@link #build()}.
     */
    public static final class Builder {
        private final Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
        private final List<JShardTableConfig> tables = new ArrayList<>();
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
            return JShardDataSourceProvider.buildDataSource(shards, tables, showSql);
        }



    }
}