package org.j2os.test.jshard;

import com.zaxxer.hikari.HikariDataSource;
import org.j2os.platform.jshard.algorithm.JShardHashAlgorithm;
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.config.JShardTableConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;
import org.j2os.platform.jshard.datasource.JShardDataSourceRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single comprehensive, dependency-free test suite for {@code org.j2os.platform.jshard}
 * (no test framework such as JUnit is used) - the consolidation of what used to be ten
 * separate {@code Example*Checks} classes plus a {@code RunAllMains} runner. Run it directly
 * with its {@link #main(String[])} method; each check group reports PASS/FAIL to standard
 * output (with a full stack trace on failure) and a summary is printed at the end.
 * <p>
 * Every check group here is self-contained and uses only an in-memory H2 database - no
 * external database is required to run this suite.
 * <p>
 * The minimal assertion helpers ({@code isTrue}, {@code equals}, {@code throwsException}, etc.)
 * live in this same class, at the bottom, rather than in a separate utility class.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class JShardTest {

    /**
     * Runs every check group and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        Map<String, RunnableThatThrows> checkGroups = new LinkedHashMap<>();
        checkGroups.put("Hash algorithm checks", JShardTest::runHashAlgorithmChecks);
        checkGroups.put("Connection config checks", JShardTest::runConnectionConfigChecks);
        checkGroups.put("Table config checks", JShardTest::runTableConfigChecks);
        checkGroups.put("Cluster validation checks", JShardTest::runClusterValidationChecks);
        checkGroups.put("Cluster H2 integration checks", JShardTest::runClusterH2IntegrationChecks);
        checkGroups.put("DataSource pool checks", JShardTest::runDataSourcePoolChecks);
        checkGroups.put("Concurrency stress checks", JShardTest::runConcurrencyStressChecks);
        checkGroups.put("Chaos checks", JShardTest::runChaosChecks);
        checkGroups.put("Pool exhaustion checks", JShardTest::runPoolExhaustionChecks);
        checkGroups.put("Hash algorithm concurrency stress checks", JShardTest::runHashAlgorithmConcurrencyStressChecks);

        int passed = 0;
        int failed = 0;

        for (Map.Entry<String, RunnableThatThrows> entry : checkGroups.entrySet()) {
            String name = entry.getKey();
            System.out.println();
            System.out.println("========== " + name + " ==========");
            try {
                entry.getValue().run();
                System.out.println("---- " + name + ": PASS ----");
                passed++;
            } catch (Throwable t) {
                System.out.println("---- " + name + ": FAIL ----");
                t.printStackTrace(System.out);
                failed++;
            }
        }

        System.out.println();
        System.out.println("======================================");
        System.out.println("Total: " + passed + " PASS, " + failed + " FAIL (of " + checkGroups.size() + " check groups)");
        System.out.println("======================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    // ==================================================================
    // Hash algorithm checks
    // ==================================================================

    private static void runHashAlgorithmChecks() {
        sameValueAlwaysRoutesToSameShard();
        blankOrNullValueIsRejected();
        emptyShardListIsRejected();
        mismatchedShardingCountIsRejected();
        tooManyVirtualNodesIsRejected();
        distributionIsRoughlyBalancedAcrossShards();
        addingAShardOnlyRemapsASmallFractionOfKeys();
        differentHashSeedsProduceDifferentMappings();
    }

    private static JShardHashAlgorithm newAlgorithm(int shardCount, int vNodes, int seed) {
        JShardHashAlgorithm algo = new JShardHashAlgorithm();
        Properties props = new Properties();
        props.setProperty("sharding-count", String.valueOf(shardCount));
        props.setProperty("virtual-nodes-per-shard", String.valueOf(vNodes));
        props.setProperty("hash-seed", String.valueOf(seed));
        algo.init(props);
        return algo;
    }

    private static void sameValueAlwaysRoutesToSameShard() {
        JShardHashAlgorithm algo = newAlgorithm(3, 100, 0);
        List<String> shards = List.of("ds0", "ds1", "ds2");
        String value = UUID.randomUUID().toString();

        String first = algo.predictShard(shards, value);
        for (int i = 0; i < 50; i++) {
            equals(first, algo.predictShard(shards, value), "Routing must be stable");
        }
    }

    private static void blankOrNullValueIsRejected() {
        JShardHashAlgorithm algo = newAlgorithm(2, 50, 0);
        List<String> shards = List.of("ds0", "ds1");
        throwsException(IllegalArgumentException.class, () -> algo.predictShard(shards, null),
                "A null value must be rejected");
        throwsException(IllegalArgumentException.class, () -> algo.predictShard(shards, "  "),
                "A blank value must be rejected");
    }

    private static void emptyShardListIsRejected() {
        JShardHashAlgorithm algo = newAlgorithm(1, 50, 0);
        throwsException(IllegalStateException.class, () -> algo.predictShard(List.of(), "some-value"),
                "An empty shard list must be rejected");
    }

    private static void mismatchedShardingCountIsRejected() {
        JShardHashAlgorithm algo = newAlgorithm(3, 50, 0);
        throwsException(IllegalStateException.class,
                () -> algo.predictShard(List.of("ds0", "ds1"), "value"),
                "A sharding-count mismatch must be rejected");
    }

    private static void tooManyVirtualNodesIsRejected() {
        Properties props = new Properties();
        props.setProperty("virtual-nodes-per-shard",
                String.valueOf(JShardHashAlgorithm.MAX_VIRTUAL_NODES_PER_SHARD + 1));
        JShardHashAlgorithm algo = new JShardHashAlgorithm();
        throwsException(IllegalArgumentException.class, () -> algo.init(props),
                "virtual-nodes-per-shard above the ceiling must be rejected");
    }

    private static void distributionIsRoughlyBalancedAcrossShards() {
        JShardHashAlgorithm algo = newAlgorithm(4, 200, 0);
        List<String> shards = List.of("ds0", "ds1", "ds2", "ds3");
        int[] counts = new int[4];
        int total = 4000;

        for (int i = 0; i < total; i++) {
            String shard = algo.predictShard(shards, "key-" + i);
            counts[Integer.parseInt(shard.substring(2))]++;
        }

        for (int count : counts) {
            double share = count / (double) total;
            isTrue(share > 0.15 && share < 0.35, "Unbalanced distribution: " + share);
        }
    }

    private static void addingAShardOnlyRemapsASmallFractionOfKeys() {
        JShardHashAlgorithm before = newAlgorithm(3, 200, 0);
        JShardHashAlgorithm after = newAlgorithm(4, 200, 0);

        List<String> shardsBefore = List.of("ds0", "ds1", "ds2");
        List<String> shardsAfter = List.of("ds0", "ds1", "ds2", "ds3");

        int total = 3000;
        int remapped = 0;
        for (int i = 0; i < total; i++) {
            String key = "key-" + i;
            String beforeShard = before.predictShard(shardsBefore, key);
            String afterShard = after.predictShard(shardsAfter, key);
            if (!beforeShard.equals(afterShard)) {
                remapped++;
            }
        }

        double remappedShare = remapped / (double) total;
        isTrue(remappedShare < 0.5, "More remapping than expected: " + remappedShare);
    }

    private static void differentHashSeedsProduceDifferentMappings() {
        JShardHashAlgorithm algoSeed0 = newAlgorithm(3, 100, 0);
        JShardHashAlgorithm algoSeed1 = newAlgorithm(3, 100, 42);
        List<String> shards = List.of("ds0", "ds1", "ds2");

        int differences = 0;
        for (int i = 0; i < 200; i++) {
            String key = "key-" + i;
            if (!algoSeed0.predictShard(shards, key).equals(algoSeed1.predictShard(shards, key))) {
                differences++;
            }
        }
        isTrue(differences > 0, "Expected at least some keys to differ with a different seed");
    }

    // ==================================================================
    // Connection config checks
    // ==================================================================

    private static void runConnectionConfigChecks() {
        requiresNonBlankFields();
        requiresPositivePoolSize();
        toStringNeverLeaksPassword();
        equalsAndHashCodeHonorAllFields();
    }

    private static void requiresNonBlankFields() {
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of(null, "jdbc:h2:mem:x", "sa", "sa", 1), "driver=null must be rejected");
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of("org.h2.Driver", " ", "sa", "sa", 1), "A blank jdbcUrl must be rejected");
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "", "sa", 1), "A blank username must be rejected");
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", null, 1), "password=null must be rejected");
    }

    private static void requiresPositivePoolSize() {
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", "sa", 0), "poolSize=0 must be rejected");
        throwsException(IllegalArgumentException.class,
                () -> JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", "sa", -1), "A negative poolSize must be rejected");
    }

    private static void toStringNeverLeaksPassword() {
        JShardConnectionConfig config = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db?x=1", "postgres", "super-secret", 5);
        String text = config.toString();
        isFalse(text.contains("super-secret"), "The password must not leak into toString");
        isFalse(text.contains("?x=1"), "The query string must be stripped from the masked host");
        isTrue(text.contains("p***s"), "The username must be masked");
    }

    private static void equalsAndHashCodeHonorAllFields() {
        JShardConnectionConfig a = JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", "sa", 5);
        JShardConnectionConfig b = JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", "sa", 5);
        JShardConnectionConfig differentPool = JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:x", "sa", "sa", 6);

        equals(a, b, "Two identical configs must be equal");
        equals(a.hashCode(), b.hashCode(), "hashCode must match for equal configs");
        notEquals(a, differentPool, "A different poolSize must not be equal");
    }

    // ==================================================================
    // Table config checks
    // ==================================================================

    private static void runTableConfigChecks() {
        rejectsBlankOrNull();
        rejectsLeadingOrTrailingWhitespace();
        rejectsDotsCommasAndQuotes();
        rejectsInternalWhitespaceAndControlChars();
        acceptsValidIdentifiers();
        equalsAndHashCode();
    }

    private static void rejectsBlankOrNull() {
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig(null, "id"), "name=null must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person", " "), "A blank shardingColumn must be rejected");
    }

    private static void rejectsLeadingOrTrailingWhitespace() {
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig(" person", "id"), "Leading whitespace must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person ", "id"), "Trailing whitespace must be rejected");
    }

    private static void rejectsDotsCommasAndQuotes() {
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person.name", "id"), "A dot must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person,x", "id"), "A comma must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person'", "id"), "A single quote must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("person\"", "id"), "A double quote must be rejected");
    }

    private static void rejectsInternalWhitespaceAndControlChars() {
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("per son", "id"), "Internal whitespace must be rejected");
        throwsException(IllegalArgumentException.class, () -> new JShardTableConfig("per\tson", "id"), "A tab character must be rejected");
    }

    private static void acceptsValidIdentifiers() {
        JShardTableConfig config = new JShardTableConfig("person", "person_id");
        equals("person", config.name(), "name must be set correctly");
        equals("person_id", config.shardingColumn(), "shardingColumn must be set correctly");
    }

    private static void equalsAndHashCode() {
        equals(new JShardTableConfig("person", "id"), new JShardTableConfig("person", "id"), "Two identical configs must be equal");
        notEquals(new JShardTableConfig("person", "id"), new JShardTableConfig("car", "id"), "Different configs must not be equal");
    }

    // ==================================================================
    // Cluster validation checks
    // ==================================================================

    private static void runClusterValidationChecks() {
        rejectsBuildWithNoShards();
        rejectsBuildWithNoTables();
        rejectsDuplicateShardName();
        rejectsNullPrimaryConfig();
        rejectsNonPositiveVirtualNodes();
        rejectsVirtualNodesAboveMax();
        rejectsInvalidShardNames();
        rejectsDuplicateTableNames();
        assertAllReachableFailsFastForAnUnreachableShard();
    }

    private static JShardConnectionConfig dummyConfig() {
        return JShardConnectionConfig.of("org.h2.Driver", "jdbc:h2:mem:unused", "sa", "sa", 1);
    }

    private static void rejectsBuildWithNoShards() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().table("person", "id").build(),
                "Building with no shards at all must be rejected");
    }

    private static void rejectsBuildWithNoTables() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", dummyConfig()).build(),
                "Building with no tables at all must be rejected");
    }

    private static void rejectsDuplicateShardName() {
        throwsException(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", dummyConfig()).shard("ds0", dummyConfig()),
                "A duplicate shard name must be rejected");
    }

    private static void rejectsNullPrimaryConfig() {
        throwsException(NullPointerException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", null),
                "primary=null must be rejected");
    }

    private static void rejectsNonPositiveVirtualNodes() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", dummyConfig()).table("person", "id")
                        .virtualNodesPerShard(0).build(),
                "virtualNodesPerShard=0 must be rejected");
    }

    private static void rejectsVirtualNodesAboveMax() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", dummyConfig()).table("person", "id")
                        .virtualNodesPerShard(JShardHashAlgorithm.MAX_VIRTUAL_NODES_PER_SHARD + 1).build(),
                "virtualNodesPerShard above the ceiling must be rejected");
    }

    private static void rejectsInvalidShardNames() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds.0", dummyConfig()).table("person", "id").build(),
                "A shard name with a dot must be rejected");
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard(" ds0", dummyConfig()).table("person", "id").build(),
                "A shard name with a space must be rejected");
    }

    private static void rejectsDuplicateTableNames() {
        throwsExceptionChecked(IllegalArgumentException.class,
                () -> JShardDataSourceProvider.builder().shard("ds0", dummyConfig())
                        .table("person", "id").table("person", "other").build(),
                "A duplicate table name must be rejected");
    }

    private static void assertAllReachableFailsFastForAnUnreachableShard() {
        JShardConnectionConfig unreachable = JShardConnectionConfig.of(
                "org.h2.Driver", "jdbc:h2:tcp://127.0.0.1:1/mem:nope", "sa", "sa", 1);

        try {
            JShardDataSourceProvider.assertAllReachable(Map.of("ds0", List.of(unreachable)));
            throw new AssertionError("Expected an IllegalStateException");
        } catch (IllegalStateException expected) {
            isTrue(expected.getMessage().contains("ds0"), "The error message must name the shard");
        }
    }

    // ==================================================================
    // Cluster H2 integration checks
    // ==================================================================

    private static void runClusterH2IntegrationChecks() throws Exception {
        insertedRowsLandOnTheShardTheRouterPredicted();
        singleConfigShardWorksWithoutReadWriteSplitting();
        closeReleasesThePoolsAndBlocksFurtherUse();
    }

    private static void insertedRowsLandOnTheShardTheRouterPredicted() throws Exception {
        createPersonTable("jshard_ds0");
        createPersonTable("jshard_ds1");

        try (JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", h2Config("jshard_ds0", 3))
                .shard("ds1", h2Config("jshard_ds1", 3))
                .table("person", "person_id")
                .build()) {

            List<String> ids = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                for (int i = 0; i < 20; i++) {
                    String id = UUID.randomUUID().toString();
                    ids.add(id);
                    ps.setString(1, id);
                    ps.setString(2, "name" + i);
                    ps.setString(3, "family" + i);
                    ps.executeUpdate();
                }
            }

            isFalse(ids.isEmpty(), "The id list must not be empty");
            for (String id : ids) {
                String predictedShard = dataSource.getRouter().getShardKey(id);
                isTrue(rowExistsInDb("jshard_" + predictedShard, id),
                        "id " + id + " must be exactly on the predicted shard " + predictedShard);
            }
        }
    }

    private static void singleConfigShardWorksWithoutReadWriteSplitting() throws Exception {
        createPersonTable("jshard_no_replica");

        try (JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", h2Config("jshard_no_replica", 3))
                .table("person", "person_id")
                .build();
             Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
            ps.setString(1, "id-1");
            ps.setString(2, "n");
            ps.setString(3, "f");
            equals(1, ps.executeUpdate(), "Insert on a shard without a replica must succeed");
        }
    }

    private static void closeReleasesThePoolsAndBlocksFurtherUse() throws Exception {
        createPersonTable("jshard_close_test");

        JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", h2Config("jshard_close_test", 3))
                .table("person", "person_id")
                .build();

        try (Connection ignored = dataSource.getConnection()) {
            notNull(ignored, "A connection must be obtainable before close");
        }

        dataSource.close();
        throwsExceptionChecked(SQLException.class, dataSource::getConnection,
                "An SQLException must be thrown after close");
        doesNotThrow(dataSource::close, "Closing again must not throw (idempotent)");
    }

    // ==================================================================
    // DataSource pool checks
    // ==================================================================

    private static void runDataSourcePoolChecks() throws Exception {
        createReturnsAWorkingPooledDataSource();
        closeAllClosesEveryTrackedPool();
        isReachableReturnsFalseForAClosedPort();
        isReachableReturnsTrueForAWorkingDatabase();
    }

    private static void createReturnsAWorkingPooledDataSource() throws Exception {
        JShardDataSourceRegistry pool = new JShardDataSourceRegistry();
        DataSource ds = pool.create("ds0", h2Config("pool_test_1", 2));

        isTrue(ds instanceof HikariDataSource, "create must return a HikariDataSource");
        try (Connection conn = ds.getConnection()) {
            isTrue(conn.isValid(2), "The connection must be valid");
        } finally {
            pool.closeAll();
        }
    }

    private static void closeAllClosesEveryTrackedPool() {
        JShardDataSourceRegistry pool = new JShardDataSourceRegistry();
        HikariDataSource ds1 = (HikariDataSource) pool.create("ds0", h2Config("pool_test_2", 2));
        HikariDataSource ds2 = (HikariDataSource) pool.create("ds1", h2Config("pool_test_3", 2));

        pool.closeAll();

        isTrue(ds1.isClosed(), "The first pool must be closed");
        isTrue(ds2.isClosed(), "The second pool must be closed");
    }

    private static void isReachableReturnsFalseForAClosedPort() {
        JShardConnectionConfig unreachable = JShardConnectionConfig.of(
                "org.h2.Driver", "jdbc:h2:tcp://127.0.0.1:1/mem:nope", "sa", "sa", 1);
        isFalse(JShardDataSourceRegistry.isReachable(unreachable, 1), "A closed port must be unreachable");
    }

    private static void isReachableReturnsTrueForAWorkingDatabase() {
        JShardConnectionConfig reachable = h2Config("pool_test_reachable", 2);
        isTrue(JShardDataSourceRegistry.isReachable(reachable, 2), "A healthy database must be reachable");
    }

    // ==================================================================
    // Concurrency stress checks
    // ==================================================================

    private static final int STRESS_SHARD_COUNT = 3;
    private static final int STRESS_THREAD_COUNT = 20;
    private static final int STRESS_ROWS_PER_THREAD = 15;
    private static final int STRESS_TOTAL_ROWS = STRESS_THREAD_COUNT * STRESS_ROWS_PER_THREAD;

    private static void runConcurrencyStressChecks() throws Exception {
        concurrentInsertsAcrossShardsLoseNothingAndCorruptNothing();
        everyInsertedRowLandsExactlyOnThePredictedShardUnderConcurrency();
    }

    private static void concurrentInsertsAcrossShardsLoseNothingAndCorruptNothing() throws Exception {
        String[] dbNames = {"stress_ds0", "stress_ds1", "stress_ds2"};
        for (String db : dbNames) {
            createPersonTable(db);
        }

        JShardDataSourceProvider.Builder builder = JShardDataSourceProvider.builder();
        for (int i = 0; i < STRESS_SHARD_COUNT; i++) {
            builder.shard("ds" + i, h2ConfigWithLongTimeout(dbNames[i], 25));
        }

        try (JShardDataSource dataSource = builder.table("person", "person_id").build()) {
            ExecutorService pool = Executors.newFixedThreadPool(STRESS_THREAD_COUNT);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(STRESS_THREAD_COUNT);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < STRESS_THREAD_COUNT; t++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        try (Connection conn = dataSource.getConnection();
                             PreparedStatement ps = conn.prepareStatement(
                                     "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                            for (int i = 0; i < STRESS_ROWS_PER_THREAD; i++) {
                                ps.setString(1, UUID.randomUUID().toString());
                                ps.setString(2, "n" + i);
                                ps.setString(3, "f" + i);
                                ps.executeUpdate();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            isTrue(doneLatch.await(90, TimeUnit.SECONDS), "Threads did not finish in time");
            pool.shutdown();

            equals(0, errors.get(), "No thread should have thrown an exception");

            long totalAcrossShards = 0;
            for (String db : dbNames) {
                totalAcrossShards += countRows(db);
            }
            equals((long) STRESS_TOTAL_ROWS, totalAcrossShards, "No record should be lost or duplicated");
        }
    }

    private static void everyInsertedRowLandsExactlyOnThePredictedShardUnderConcurrency() throws Exception {
        String[] dbNames = {"stress2_ds0", "stress2_ds1", "stress2_ds2"};
        for (String db : dbNames) {
            createPersonTable(db);
        }

        JShardDataSourceProvider.Builder builder = JShardDataSourceProvider.builder();
        for (int i = 0; i < STRESS_SHARD_COUNT; i++) {
            builder.shard("ds" + i, h2ConfigWithLongTimeout(dbNames[i], 25));
        }

        try (JShardDataSource dataSource = builder.table("person", "person_id").build()) {
            int idsPerThread = 15;
            int threadCount = 20;
            @SuppressWarnings("unchecked")
            List<String>[] idsByThread = new List[threadCount];
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                idsByThread[t] = new ArrayList<>();
                pool.submit(() -> {
                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement ps = conn.prepareStatement(
                                 "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                        for (int i = 0; i < idsPerThread; i++) {
                            String id = UUID.randomUUID().toString();
                            ps.setString(1, id);
                            ps.setString(2, "n");
                            ps.setString(3, "f");
                            ps.executeUpdate();
                            idsByThread[threadIdx].add(id);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            isTrue(doneLatch.await(90, TimeUnit.SECONDS), "Threads did not finish in time");
            pool.shutdown();
            equals(0, errors.get(), "No thread should have thrown an exception");

            int checked = 0;
            for (List<String> ids : idsByThread) {
                for (String id : ids) {
                    String predictedShard = dataSource.getRouter().getShardKey(id);
                    String dbName = "stress2_" + predictedShard;
                    isTrue(rowExistsInDb(dbName, id),
                            "id " + id + " must be exactly on shard " + predictedShard);
                    checked++;
                }
            }
            equals(threadCount * idsPerThread, checked, "The number of checked records must be complete");
        }
    }

    private static long countRows(String dbName) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "sa");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM person")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ==================================================================
    // Chaos checks
    // ==================================================================

    private static void runChaosChecks() throws Exception {
        oneShardGoingDownDoesNotAffectTheHealthyShard();
    }

    private static void oneShardGoingDownDoesNotAffectTheHealthyShard() throws Exception {
        createPersonTable("chaos_ds0");
        createPersonTable("chaos_ds1");

        JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", h2Config("chaos_ds0", 3))
                .shard("ds1", h2Config("chaos_ds1", 3))
                .table("person", "person_id")
                .build();

        String idRoutedToDs1 = findKeyRoutedTo(dataSource, "ds1");
        String idRoutedToDs0 = findKeyRoutedTo(dataSource, "ds0");

        try (Connection raw = DriverManager.getConnection(
                "jdbc:h2:mem:chaos_ds1;DB_CLOSE_DELAY=-1", "sa", "sa");
             Statement stmt = raw.createStatement()) {
            stmt.execute("SHUTDOWN");
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
            ps.setString(1, idRoutedToDs0);
            ps.setString(2, "ok");
            ps.setString(3, "ok");
            equals(1, ps.executeUpdate(), "The healthy shard must keep working fully");
        }

        throwsExceptionChecked(SQLException.class, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                ps.setString(1, idRoutedToDs1);
                ps.setString(2, "fail");
                ps.setString(3, "fail");
                ps.executeUpdate();
            }
        }, "The down shard must give a clear SQLException");

        doesNotThrow(dataSource::close, "Final close must not hang, even with a broken pool");
    }

    private static String findKeyRoutedTo(JShardDataSource dataSource, String shardName) {
        for (int i = 0; i < 10_000; i++) {
            String candidate = UUID.randomUUID().toString();
            if (shardName.equals(dataSource.getRouter().getShardKey(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a key routing to shard " + shardName);
    }

    // ==================================================================
    // Pool exhaustion checks
    // ==================================================================

    private static void runPoolExhaustionChecks() throws Exception {
        exhaustedPoolFailsFastWithClearTimeoutInsteadOfHanging();
    }

    private static void exhaustedPoolFailsFastWithClearTimeoutInsteadOfHanging() throws Exception {
        createPersonTable("exhaustion_ds0");

        JShardConnectionConfig config = JShardConnectionConfig.builder()
                .driverClassName("org.h2.Driver")
                .jdbcUrl("jdbc:h2:mem:exhaustion_ds0;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("sa")
                .poolSize(2)
                .connectionTimeoutMs(1500)
                .build();

        try (JShardDataSource dataSource = JShardDataSourceProvider.builder()
                .shard("ds0", config)
                .table("person", "person_id")
                .build()) {

            Connection held1 = dataSource.getConnection();
            Connection held2 = dataSource.getConnection();
            held1.createStatement().execute("SELECT 1");
            held2.createStatement().execute("SELECT 1");

            try {
                long start = System.currentTimeMillis();
                throwsExceptionChecked(SQLException.class, () -> {
                    try (Connection conn = dataSource.getConnection();
                         Statement stmt = conn.createStatement()) {
                        stmt.execute("SELECT 1");
                    }
                }, "When the pool is full, the third request must fail after the timeout");
                long elapsed = System.currentTimeMillis() - start;

                isTrue(elapsed >= 1000,
                        "Expected to wait close to connectionTimeout, but it took " + elapsed + "ms");
                isTrue(elapsed < 10_000,
                        "Should not take practically forever, but it took " + elapsed + "ms");
            } finally {
                held1.close();
                held2.close();
            }

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                doesNotThrow(() -> stmt.execute("SELECT 1"),
                        "Once connections are released, the pool must recover");
            }
        }
    }

    // ==================================================================
    // Hash algorithm concurrency stress checks
    // ==================================================================

    private static void runHashAlgorithmConcurrencyStressChecks() throws Exception {
        manyThreadsBuildingAndReadingSharedRingCachesConcurrently();
    }

    private static void manyThreadsBuildingAndReadingSharedRingCachesConcurrently() throws Exception {
        int threadCount = 32;
        int lookupsPerThread = 3000;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger();
        ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>> referenceMappingByThread = new ConcurrentHashMap<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                try {
                    startGate.await();
                    int shardCount = 2 + (threadIdx % 4);
                    int vNodes = 50 + (threadIdx % 5) * 30;
                    List<String> shards = new ArrayList<>();
                    for (int s = 0; s < shardCount; s++) {
                        shards.add("shard-" + s);
                    }

                    JShardHashAlgorithm algo = new JShardHashAlgorithm();
                    Properties props = new Properties();
                    props.setProperty("sharding-count", String.valueOf(shardCount));
                    props.setProperty("virtual-nodes-per-shard", String.valueOf(vNodes));
                    props.setProperty("hash-seed", "0");
                    algo.init(props);

                    ConcurrentHashMap<String, String> seen = new ConcurrentHashMap<>();
                    for (int i = 0; i < lookupsPerThread; i++) {
                        String key = "key-" + threadIdx + "-" + i;
                        String shard = algo.predictShard(shards, key);
                        String previous = seen.putIfAbsent(key, shard);
                        if (previous != null && !previous.equals(shard)) {
                            throw new IllegalStateException(
                                    "Mapping inconsistency for " + key + ": " + previous + " vs " + shard);
                        }
                    }
                    referenceMappingByThread.put(threadIdx, seen);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        isTrue(doneLatch.await(50, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();

        equals(0, errors.get(), "No thread should have thrown an exception or a mapping inconsistency");
        equals(threadCount, referenceMappingByThread.size(), "Every thread must have recorded a result");
    }

    // ==================================================================
    // Shared helpers
    // ==================================================================

    /**
     * Builds an H2 connection config via {@link JShardConnectionConfig#of}, for check groups
     * that don't need a custom connection timeout.
     *
     * @param dbName   the H2 in-memory database name
     * @param poolSize the connection pool size
     * @return the connection config
     */
    private static JShardConnectionConfig h2Config(String dbName, int poolSize) {
        return JShardConnectionConfig.of(
                "org.h2.Driver", "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "sa", poolSize);
    }

    /**
     * Builds an H2 connection config with a generous 60s connection timeout, for the
     * concurrency stress checks - which hold many connections at once under heavy load and
     * would otherwise risk spurious timeouts unrelated to what's actually being tested.
     *
     * @param dbName   the H2 in-memory database name
     * @param poolSize the connection pool size
     * @return the connection config
     */
    private static JShardConnectionConfig h2ConfigWithLongTimeout(String dbName, int poolSize) {
        return JShardConnectionConfig.builder()
                .driverClassName("org.h2.Driver")
                .jdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("sa")
                .poolSize(poolSize)
                .connectionTimeoutMs(60_000)
                .build();
    }

    /**
     * Creates (or reuses) an H2 in-memory database with an empty {@code person} table.
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

    /**
     * Checks whether a person row with the given id exists in the given H2 in-memory database.
     *
     * @param dbName the H2 in-memory database name
     * @param id     the person_id to look up
     * @return true if a matching row exists
     * @throws Exception if the query fails
     */
    private static boolean rowExistsInDb(String dbName, String id) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "sa");
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM person WHERE person_id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ==================================================================
    // Minimal assertion helpers (no external test framework)
    // ==================================================================

    private static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void isFalse(boolean condition, String message) {
        isTrue(!condition, message);
    }

    private static void equals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " — expected: " + expected + ", actual: " + actual);
        }
    }

    private static void notEquals(Object a, Object b, String message) {
        if (Objects.equals(a, b)) {
            throw new AssertionError(message + " — expected these to differ: " + a);
        }
    }

    private static void notNull(Object value, String message) {
        isTrue(value != null, message);
    }

    private static void throwsException(Class<? extends Throwable> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return;
            }
            throw new AssertionError(message + " — wrong exception type: " + t.getClass().getName(), t);
        }
        throw new AssertionError(message + " — expected " + expectedType.getSimpleName() + " to be thrown, but nothing was thrown");
    }

    private static void throwsExceptionChecked(Class<? extends Throwable> expectedType, RunnableThatThrows action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) {
                return;
            }
            throw new AssertionError(message + " — wrong exception type: " + t.getClass().getName(), t);
        }
        throw new AssertionError(message + " — expected " + expectedType.getSimpleName() + " to be thrown, but nothing was thrown");
    }

    private static void doesNotThrow(RunnableThatThrows action, String message) {
        try {
            action.run();
        } catch (Throwable t) {
            throw new AssertionError(message + " — should not have thrown: " + t, t);
        }
    }

    /** A no-argument, possibly-throwing action, used both to run each check group and by the assertion helpers above. */
    private interface RunnableThatThrows {
        /**
         * Runs the action.
         *
         * @throws Exception if the action fails
         */
        void run() throws Exception;
    }
}