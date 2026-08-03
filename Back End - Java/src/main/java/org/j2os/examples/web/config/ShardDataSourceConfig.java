package org.j2os.examples.web.config;

import org.j2os.platform.jshard.config.JShardConnectionConfig;
import org.j2os.platform.jshard.config.JShardTableConfig;
import org.j2os.platform.jshard.datasource.JShardDataSourceProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.*;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "org.j2os.examples.web.shard.repository",// TODO: CHANGE!
        entityManagerFactoryRef = "shardEntityManagerFactory",
        transactionManagerRef = "shardTransactionManager"
)
public class ShardDataSourceConfig {

    @Bean
    public DataSource shardDataSource() {
        // TODO: CHANGE!
        Map<String, List<JShardConnectionConfig>> shards = new LinkedHashMap<>();
        shards.put("shard-a", List.of(JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl("jdbc:postgresql://localhost:5432/p1")
                .username("postgres")
                .password("myjava123")
                .poolSize(3)
                .build()));
        shards.put("shard-b", List.of(JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl("jdbc:postgresql://localhost:5432/p2")
                .username("postgres")
                .password("myjava123")
                .poolSize(2)
                .build()));
        shards.put("shard-c", List.of(JShardConnectionConfig.builder()
                .driverClassName("org.postgresql.Driver")
                .jdbcUrl("jdbc:postgresql://localhost:5432/p3")
                .username("postgres")
                .password("myjava123")
                .poolSize(15)
                .build()));

        List<JShardTableConfig> tables = List.of(
                new JShardTableConfig("order_tbl", "order_id")// TODO: CHANGE!
        );

        try {
            JShardDataSourceProvider.assertAllReachable(shards);

            return JShardDataSourceProvider.builder()
                    .shards(shards)
                    .tables(tables)
                    .showSql(true)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sharded datasource", e);
        }
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean shardEntityManagerFactory(
            @Qualifier("shardDataSource") DataSource dataSource,
            @Qualifier("shardEntityManagerFactoryBuilder") EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(dataSource)
                .packages("org.j2os.examples.web.shard.entity")// TODO: CHANGE!
                .persistenceUnit("shardPersistenceUnit")
                .build();
    }

    @Bean
    public EntityManagerFactoryBuilder shardEntityManagerFactoryBuilder() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");// Force: update. Other type not supported
        properties.put("hibernate.show_sql", true);// TODO: CHANGE!
        properties.put("hibernate.format_sql", true);// TODO: CHANGE!
        properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");

        return new EntityManagerFactoryBuilder(
                new HibernateJpaVendorAdapter(),
                (datasource) -> properties,
                null
        );
    }

    @Bean
    public PlatformTransactionManager shardTransactionManager(
            @Qualifier("shardEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(Objects.requireNonNull(emf.getObject()));
    }
}