package org.j2os.examples.web.api;

import jakarta.annotation.Resource;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.datasource.JShardDataSource;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;
import org.j2os.platform.page2.PageDataSQL;
import org.j2os.platform.resicord.Try;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Demonstrates two different ways of getting a connection to run a page2 SQL query over:
 * <ul>
 *     <li>{@link #getHuman}: the app's own Spring-managed {@link #dataSource} (whatever that's
 *     configured to be), fetched/released through {@link DataSourceUtils} so it participates
 *     correctly in the {@code @Transactional} boundary.</li>
 *     <li>{@link #getHuman2} (via {@link #findAll}): a separate, manually-built
 *     {@link JShardDataSource} cluster held in {@link SharedDataSourceHolder}, wrapped in a
 *     {@link Try} retry for resilience.</li>
 * </ul>
 */
@RestController
public class PageSQLExample {

    @Resource
    private DataSource dataSource;//no shard

    /**
     * Runs a page2 query over the app's own Spring-managed {@link #dataSource}.
     *
     * @param map the page2 request parameters (paging/search/sort)
     * @return the page2 result
     * @throws Exception if the query fails
     */
    @GetMapping("/getHumanBySQL")
    @Transactional
    public Object getHuman(@RequestParam Map<String, Object> map) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            PageDataSQL pageDataSQL = new PageDataSQL(connection);
            pageDataSQL.searchAndSortOn("human_id", "name", "family");
            return pageDataSQL.getResult("SELECT NAME,FAMILY,HUMAN_ID FROM HUMAN", null, map);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * Runs the same query as {@link #getHuman}, but via {@link #findAll} (the manually-built
     * {@link SharedDataSourceHolder} cluster) with a retry/fallback wrapper.
     *
     * @param map the page2 request parameters (paging/search/sort)
     * @return the page2 result, or the fallback string if every retry attempt fails
     * @throws Exception if {@link Try#get()} itself fails outside the wrapped work
     */
    @GetMapping("/getHumanBySQL2")
    @Transactional
    public Object getHuman2(@RequestParam Map<String, Object> map) throws Exception {
        return new Try<>()
                .retry(3, 200)
                .doWork(() -> findAll(map))
                .onError(e -> "Fallback value (cause: " + e.getMessage() + ")")
                .get();
    }

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

    /**
     * Holds a single, manually-built {@link JShardDataSource} cluster, shared across every call
     * to {@link #findAll}. Deliberately not named {@code JShardDataSourceProvider} (like the
     * builder it uses internally) to avoid shadowing that class within this file.
     */
    public static class SharedDataSourceHolder {
        private static final DataSource DATA_SOURCE;

        static {
            try {
                DATA_SOURCE = JShardDataSourceProvider.builder()
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