package org.j2os.examples.desktop.jshard;

import org.j2os.platform.jshard.algorithm.JShardHashAlgorithm;
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.config.JShardTableConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;
import org.j2os.platform.jshard.datasource.JShardDataSourceRegistry;
import org.j2os.platform.jshard.router.JShardRouter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Full guided tour of the {@code jshard} module: every public method of
 * every public class is called at least once, using two in-memory H2
 * databases so the example runs standalone with no external database
 * required.
 * <p>
 * The other examples in this package (Example1-Example4) demonstrate specific real-world
 * scenarios (basic clustering, primary/replica routing, bulk config + health checks,
 * multi-tenant isolation) against a real PostgreSQL instance — this one is the reference for
 * "what does every method do", not a usage scenario in its own right.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class Example0 {

    /**
     * Runs the guided tour.
     *
     * @param args not used
     * @throws Exception if any step fails unexpectedly
     */
    public static void main(String[] args) throws Exception {

        // ==================================================================
        // 1. JShardConnectionConfig — connection details for one physical database
        // ==================================================================

        // Quick shortcut: when you only need the 5 basic values (no fine-grained
        // Hikari settings), this is the fastest way.
        JShardConnectionConfig quickConfig = JShardConnectionConfig.of(
                "org.h2.Driver", "jdbc:h2:mem:tour_ds0;DB_CLOSE_DELAY=-1", "sa", "sa", 5);

        // The same thing, but via the Builder (when you also want to fine-tune
        // the connection pool).
        JShardConnectionConfig fullConfig = JShardConnectionConfig.builder()
                .driverClassName("org.h2.Driver")     // JDBC driver class name
                .jdbcUrl("jdbc:h2:mem:tour_ds1;DB_CLOSE_DELAY=-1") // database address
                .username("sa")                        // username
                .password("sa")                         // password
                .poolSize(5)                            // maximum number of concurrent connections
                .connectionTimeoutMs(30_000)             // how long to wait before failing if the pool is exhausted
                .idleTimeoutMs(600_000)                  // close an idle connection after this many milliseconds
                .maxLifetimeMs(1_800_000)                // maximum lifetime of a connection before automatic renewal
                .minimumIdle(1)                          // minimum number of ready connections, even when idle
                .validationTimeoutMs(5_000)              // how long to wait while checking a connection is healthy
                .keepaliveTimeMs(300_000)                // how often to ping to keep the connection alive
                .build();

        // The values we set can be read back (e.g. for logging or debugging):
        System.out.println("jdbcUrl: " + fullConfig.getJdbcUrl());               // database address
        System.out.println("username: " + fullConfig.getUsername());            // username
        System.out.println("password: " + fullConfig.getPassword());            // raw password (internal only, never shown in toString)
        System.out.println("poolSize: " + fullConfig.getPoolSize());             // pool size
        System.out.println("driverClassName: " + fullConfig.getDriverClassName()); // driver name
        System.out.println("connectionTimeoutMs: " + fullConfig.getConnectionTimeoutMs()); // connection-acquisition timeout
        System.out.println("idleTimeoutMs: " + fullConfig.getIdleTimeoutMs());   // idle timeout
        System.out.println("maxLifetimeMs: " + fullConfig.getMaxLifetimeMs());   // maximum connection lifetime
        System.out.println("minimumIdle: " + fullConfig.getMinimumIdle());       // minimum ready connections
        System.out.println("validationTimeoutMs: " + fullConfig.getValidationTimeoutMs()); // validation timeout
        System.out.println("keepaliveTimeMs: " + fullConfig.getKeepaliveTimeMs()); // keepalive interval

        // toString() never reveals the password (safe for logs):
        System.out.println("toString: " + fullConfig);                          // log-safe printable version
        System.out.println("equals: " + quickConfig.equals(fullConfig));        // comparing two configs
        System.out.println("hashCode: " + quickConfig.hashCode());              // usable as a Map/Set key

        // ==================================================================
        // 2. JShardTableConfig — table name + the column sharding is based on
        // ==================================================================
        JShardTableConfig personTable = new JShardTableConfig("person", "person_id");
        System.out.println("table name: " + personTable.name());                 // table name (public field)
        System.out.println("sharding column: " + personTable.shardingColumn());   // the column routing is based on
        System.out.println("table toString: " + personTable);                  // printable form
        System.out.println("table equals: " + personTable.equals(new JShardTableConfig("person", "person_id"))); // comparison
        System.out.println("table hashCode: " + personTable.hashCode());        // hash, usable in Map/Set

        // ==================================================================
        // 3. Spin up two temporary H2 databases for this tour (demo only)
        // ==================================================================
        createPersonTable("tour_ds0");
        createPersonTable("tour_ds1");

        // ==================================================================
        // 4. JShardDataSourceProvider.assertAllReachable — health check before building the cluster
        // ==================================================================
        Map<String, List<JShardConnectionConfig>> shardMap = Map.of(
                "ds0", List.of(quickConfig),
                "ds1", List.of(fullConfig)
        );
        // Checks that every shard is actually reachable; if one is down, this
        // fails immediately with a clear message, instead of mid-operation later.
        JShardDataSourceProvider.assertAllReachable(shardMap);


        // ==================================================================
        // 5. JShardDataSourceProvider.builder() — building the cluster itself
        // ==================================================================
        JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", quickConfig)               // adds a shard with no replicas
                .tables(List.of(personTable))            // same as .table(...), but when you already have a List<JShardTableConfig> ready
                .virtualNodesPerShard(200)                // how many "virtual nodes" to give each shard on the hash ring (more evenness)
                .hashSeed(0)                               // hash salt — changing it reshuffles the entire data-to-shard mapping
                .showSql(true)                             // log the logical/actual SQL for every query
                .build();                                  // the actual build step; this is where real connections to the databases happen

        // ==================================================================
        // 6. JShardDataSource — the standard DataSource you hand to the rest of your app
        // ==================================================================
        JShardRouter router = dataSource.getRouter();     // access the router to predict where a key will route

        try (Connection conn = dataSource.getConnection(); // get a fresh connection from the cluster
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO person (person_id, name, family) VALUES ('u1', 'Ali', 'Rezaei')");
        }

        try {
            // This overload exists in the standard JDBC API to supply a
            // separate user/password; many connection pools (including
            // Hikari) don't support it and deliberately throw.
            dataSource.getConnection("sa", "sa");
        } catch (Exception e) {
            System.out.println("getConnection(user, pass): unsupported as expected — " + e.getMessage());
        }

        System.out.println("isWrapperFor(DataSource.class): " + dataSource.isWrapperFor(DataSource.class)); // can this be viewed as another type?
        try {
            dataSource.unwrap(DataSource.class);           // if supported, returns the same object as that other type
        } catch (Exception e) {
            System.out.println("unwrap: not supported — " + e.getMessage());
        }

        System.out.println("getLogWriter: " + dataSource.getLogWriter());        // where JDBC logs are written (defaults to null)
        dataSource.setLogWriter(null);                                            // change it (here we just set it to null, i.e. off)

        System.out.println("getLoginTimeout: " + dataSource.getLoginTimeout());  // how many seconds to wait during login before failing
        dataSource.setLoginTimeout(10);                                          // set it to 10 seconds

        try {
            dataSource.getParentLogger();                  // the java.util.logging parent logger (most implementations don't support this)
        } catch (Exception e) {
            System.out.println("getParentLogger: not supported — " + e.getClass().getSimpleName());
        }

        // ==================================================================
        // 7. JShardRouter — predicting the route without touching a real database
        // ==================================================================
        String predictedShard = router.getShardKey("u1"); // given just a value, tells you which shard it targets
        System.out.println("router.getShardKey(\"u1\"): " + predictedShard);

        // You can also build a fully independent router (without any
        // JShardDataSource) — e.g. to know in advance where a key will go.
        JShardRouter standaloneRouter = new JShardRouter(Set.of("ds0", "ds1"), 200, 0);
        System.out.println("standalone router: " + standaloneRouter.getShardKey("u2"));

        // ==================================================================
        // 8. JShardDataSourceRegistry — the underlying pool-management layer (you usually don't call this directly)
        // ==================================================================
        JShardDataSourceRegistry manualPool = new JShardDataSourceRegistry();
        DataSource manualDs = manualPool.create("manual", quickConfig); // creates and registers a new HikariDataSource
        System.out.println("manual pool created a DataSource: " + (manualDs != null));

        System.out.println("isReachable: " +
                JShardDataSourceRegistry.isReachable(quickConfig, 3)); // checks whether a config actually connects (no pool needed)

        manualPool.closeAll(); // closes every pool registered in this registry

        // ==================================================================
        // 9. JShardHashAlgorithm — the hashing algorithm itself (very low level)
        // ==================================================================
        // This class isn't normally called directly — ShardingSphere invokes
        // it internally via reflection. But since it's public, it can also be
        // used directly (e.g. for testing or debugging).
        JShardHashAlgorithm algo = new JShardHashAlgorithm();
        Properties algoProps = new Properties();
        algoProps.setProperty("virtual-nodes-per-shard", "200"); // init() reads and applies this
        algoProps.setProperty("hash-seed", "0");
        algo.init(algoProps);                                     // initial algorithm configuration
        System.out.println("algorithm type: " + algo.getType()); // the algorithm type name (for registering with ShardingSphere)
        System.out.println("routeForTesting: " +
                algo.predictShard(List.of("ds0", "ds1"), "u3")); // direct routing without going through JDBC

        // ==================================================================
        // 10. Final cleanup
        // ==================================================================
        dataSource.close(); // closes every pool in the main cluster (idempotent — calling it again won't error)

        System.out.println("Tour complete — every public method of the library was called once.");
    }

    /**
     * Creates (or reuses) an in-memory H2 database with an empty {@code person} table, so this
     * tour has something real to read from and write to without needing an external database.
     *
     * @param dbName the H2 in-memory database name
     * @throws Exception if the connection or table creation fails
     */
    private static void createPersonTable(String dbName) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "sa");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS person (" +
                    "person_id VARCHAR(64) PRIMARY KEY, name VARCHAR(64), family VARCHAR(64))");
        }
    }
}