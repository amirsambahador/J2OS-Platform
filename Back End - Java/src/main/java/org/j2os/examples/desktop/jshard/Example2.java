package org.j2os.examples.desktop.jshard;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Demonstrates a cluster where each shard has a primary plus one or more read replicas
 * ({@code .shard(name, primary, replicas...)}), against a real PostgreSQL instance: {@code ds0}
 * has a primary and two replicas, {@code ds1} has a primary and one replica.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example2 {

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws Exception if any step fails unexpectedly
     */
    public static void main(String[] args) throws Exception {

        JShardConnectionConfig ds0Primary = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 3);
        JShardConnectionConfig ds0Replica1 = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 4);
        JShardConnectionConfig ds0Replica2 = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p2", "postgres", "myjava123", 4);

        JShardConnectionConfig ds1Primary = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p3", "postgres", "myjava123", 5);
        JShardConnectionConfig ds1Replica = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p4", "postgres", "myjava123", 10);

        try (JShardDataSource ds = JShardDataSourceProvider.builder()
                .shard("ds0", ds0Primary, ds0Replica1, ds0Replica2) // one primary + two replicas
                .shard("ds1", ds1Primary, ds1Replica)                // one primary + one replica
                .table("person", "person_id")
                .showSql(true)
                .build()) {

            try (Connection conn = ds.getConnection()) {
                conn.createStatement().execute("DELETE FROM person");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < 30; i++) {
                        String personId = UUID.randomUUID().toString();
                        ps.setString(1, personId);
                        ps.setString(2, "NAME" + 1000 + i);
                        ps.setString(3, "FAMILY" + (999L * (i + 1)));
                        ps.addBatch();
                        System.out.println("INSERT → " + personId.substring(0, 8)
                                + " | INTO → " + ds.getRouter().getShardKey(personId));
                    }
                    ps.executeBatch();
                }

                try (ResultSet rs = conn.createStatement().executeQuery(
                        "SELECT * FROM person ORDER BY person_id")) {
                    System.out.println("\n--- Results (may come from a replica) ---");
                    while (rs.next()) {
                        System.out.println(rs.getString(1) + " | name=" + rs.getString(2)
                                + " | family=" + rs.getString(3));
                    }
                }
            }
        }

        System.out.println("Run complete (primary + replica pools closed).");
    }
}