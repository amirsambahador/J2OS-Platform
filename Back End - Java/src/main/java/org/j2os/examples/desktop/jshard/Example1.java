package org.j2os.examples.desktop.jshard;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Demonstrates the most basic real cluster: three shards, each with a single connection (no
 * replicas), against a real PostgreSQL instance. Inserts a batch of rows, printing which shard
 * each one routed to, then reads them back through the cluster.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example1 {

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws Exception if any step fails unexpectedly
     */
    public static void main(String[] args) throws Exception {

        JShardConnectionConfig shardA = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 3);
        JShardConnectionConfig shardB = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p2", "postgres", "myjava123", 2);
        JShardConnectionConfig shardC = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p3", "postgres", "myjava123", 15);

        try (JShardDataSource ds = JShardDataSourceProvider.builder()
                .shard("shard-a", shardA)
                .shard("shard-b", shardB)
                .shard("shard-c", shardC)
                .table("person", "person_id")
                .showSql(true)
                .build()) {

            try (Connection conn = ds.getConnection()) {

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM person");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < 100; i++) {
                        String uuid = UUID.randomUUID().toString();
                        ps.setString(1, uuid);
                        ps.setString(2, "mohammad" + i);
                        ps.setString(3, "ghaderi" + i);
                        ps.addBatch();

                        // To see where each record will end up, we use the router that comes
                        // straight from this JShardDataSource - no separate static method needed.
                        System.out.println("INSERT → " + uuid.substring(0, 8)
                                + " | INTO → " + ds.getRouter().getShardKey(uuid));
                    }
                    ps.executeBatch();
                }

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT name, family FROM person ORDER BY person_id")) {
                    System.out.println("\n--- From the cluster ---");
                    int count = 1;
                    while (rs.next()) {
                        System.out.println(count++ + ") " + rs.getString(1) + " " + rs.getString(2));
                    }
                }
            }
        }

        System.out.println("Run complete (pools closed).");
    }
}