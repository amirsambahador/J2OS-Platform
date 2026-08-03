package org.j2os.examples.desktop.jshard;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates two builder features against a real PostgreSQL instance: bulk-registering shards
 * from a {@code Map} via {@code .shards(...)} instead of chaining {@code .shard(...)} calls one
 * at a time, and registering more than one sharded table ({@code person}, {@code car}) on the
 * same cluster. Also shows {@code assertAllReachable} as an explicit pre-flight health check.
 * <p>
 * Unlike {@link Example2}, the shards here have a single connection each (no replicas) - this
 * example isn't about primary/replica routing, so there's no need to duplicate that setup.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example3 {

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws Exception if any step fails unexpectedly
     */
    public static void main(String[] args) throws Exception {

        JShardConnectionConfig ds0 = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 5);
        JShardConnectionConfig ds1 = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p3", "postgres", "myjava123", 5);

        Map<String, List<JShardConnectionConfig>> shardMap = Map.of(
                "ds0", List.of(ds0),
                "ds1", List.of(ds1)
        );

        // Before building the cluster, explicitly check that every shard is reachable. If one
        // is down, this fails fast with a clear message - not mid-build or mid-query later.
        JShardDataSourceProvider.assertAllReachable(shardMap);

        try (JShardDataSource ds = JShardDataSourceProvider.builder()
                .shards(shardMap)
                .table("person", "id")
                .table("car", "person_id")
                .build();
             Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM person");
        }

        System.out.println("Run complete (all shards were healthy, pool closed).");
    }
}