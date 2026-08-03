package org.j2os.examples.desktop.jshard;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;

/**
 * Demonstrates multi-tenant isolation: two completely independent {@link JShardDataSource}
 * instances (each with its own connection pool), so operations against one tenant's cluster
 * never contend with or affect the other's.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example4 {

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws Exception if any step fails unexpectedly
     */
    public static void main(String[] args) throws Exception {

        JShardConnectionConfig tenantAConfig = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/postgres", "postgres", "myjava123", 10);
        JShardConnectionConfig tenantBConfig = JShardConnectionConfig.of(
                "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/postgres", "postgres", "myjava123", 10);

        try (JShardDataSource tenantA = JShardDataSourceProvider.builder()
                .shard("ds0", tenantAConfig)
                .table("person", "person_id")
                .build();
             JShardDataSource tenantB = JShardDataSourceProvider.builder()
                     .shard("ds0", tenantBConfig)
                     .table("person", "person_id")
                     .build()) {

            try (var conn = tenantA.getConnection()) {
                conn.createStatement().execute("DELETE FROM person");
            }
            try (var conn = tenantB.getConnection()) {
                conn.createStatement().execute("DELETE FROM person");
            }
        }

        System.out.println("Both tenants closed independently, with no pool interference between them.");
    }
}