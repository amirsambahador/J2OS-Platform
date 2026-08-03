package org.j2os.examples.desktop.jshard;

import jakarta.annotation.Resource;
import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.page2.PageDataSQL;
import org.j2os.platform.resicord.Try;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Demonstrates combining {@code jshard}, {@code page2}, and {@code resicord} in a Spring
 * controller, and contrasts two different ways of getting a connection to run a page2 query
 * over:
 * <ul>
 *     <li>{@link #getHuman}: the app's own Spring-managed {@link #dataSource} (whatever that's
 *     configured to be), fetched/released through {@link DataSourceUtils} so it participates
 *     correctly in Spring transactions.</li>
 *     <li>{@link #getHuman2} (via {@link #findAll}): a separate, manually-built
 *     {@link JShardDataSource} cluster held in {@link SharedDataSourceHolder}, wrapped in a
 *     {@link Try} retry for resilience.</li>
 * </ul>
 * Both endpoints are commented out ({@code @GetMapping}/{@code @Transactional}) since this class
 * is meant to be read as a reference, not deployed as-is.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@RestController
public class JShardResiCordPage2Example {

    @Resource
    private DataSource dataSource;

    /**
     * Runs a page2 query over the manually-built {@link SharedDataSourceHolder} cluster.
     *
     * @param map the page2 request parameters (paging/search/sort)
     * @return the page2 result
     * @throws Exception if the query fails
     */
    public static Object findAll(Map<String, Object> map) throws Exception {
        try (Connection connection = SharedDataSourceHolder.getConnection()) {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            pageDataSQL.searchAndSortOn("human_id", "name", "family");
            return pageDataSQL.getResult("SELECT NAME,FAMILY,HUMAN_ID FROM HUMAN", null, map);
        }
    }

    //@GetMapping("/getHumanBySQL")
    //@Transactional
    public Object getHuman(@RequestParam Map<String, Object> map) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        PageDataSQL pageDataSQL = new PageDataSQL(connection);
        pageDataSQL.searchAndSortOn("human_id", "name", "family");
        var output = pageDataSQL.getResult("SELECT NAME,FAMILY,HUMAN_ID FROM HUMAN", null, map);
        DataSourceUtils.releaseConnection(connection, dataSource);
        return output;
    }

    //@GetMapping("/getHumanBySQL2")
    //@Transactional
    public Object getHuman2(@RequestParam Map<String, Object> map) throws Exception {
        return new Try<>()
                .retry(3, 200)
                .doWork(() -> findAll(map))
                .onError(e -> "Fallback value (cause: " + e.getMessage() + ")")
                .get();
    }

    /**
     * Holds a single, manually-built {@link JShardDataSource} cluster, shared across every call
     * to {@link #findAll}. Deliberately not named {@code JShardDataSourceProvider} (like the
     * builder it uses internally) to avoid shadowing that class within this file.
     */
    public static class SharedDataSourceHolder {
        private static final DataSource DATA_SOURCE;

        static {
            try {
                DATA_SOURCE = org.j2os.platform.jshard.datasource.JShardDataSourceProvider.builder()
                        .shard("ds0",
                                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 3),
                                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "myjava123", 4),
                                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p2", "postgres", "myjava123", 4)
                        )
                        .shard("ds1",
                                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p3", "postgres", "myjava123", 5), // write
                                JShardConnectionConfig.of("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p4", "postgres", "myjava123", 10))
                        .table("person", "person_id")
                        //.virtualNodesPerShard(200)  // optional
                        .build();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static Connection getConnection() throws SQLException {
            return DATA_SOURCE.getConnection();
        }
    }
}