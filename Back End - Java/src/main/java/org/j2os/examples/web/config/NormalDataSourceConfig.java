package org.j2os.examples.web.config;


import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "org.j2os.examples.web.repository",// TODO: CHANGE!
        entityManagerFactoryRef = "normalEntityManagerFactory",
        transactionManagerRef = "normalTransactionManager"
)
public class NormalDataSourceConfig {
    @Primary//Default
    @Bean
    public LocalContainerEntityManagerFactoryBean normalEntityManagerFactory(
            @Qualifier("normalDataSource") DataSource dataSource,
            @Qualifier("normalEntityManagerFactoryBuilder") EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(dataSource)
                .packages("org.j2os.examples.web.entity")// TODO: CHANGE!
                .persistenceUnit("normalPersistenceUnit")
                .build();
    }

    @Bean
    public EntityManagerFactoryBuilder normalEntityManagerFactoryBuilder() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "create-drop");// TODO: CHANGE!
        properties.put("hibernate.show_sql", true);// TODO: CHANGE!
        properties.put("hibernate.format_sql", true);// TODO: CHANGE!
        properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), (dataSource) -> properties, null);//What is null: Spring provided Persistence
    }

    @Bean
    public DataSourceProperties normalDataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/postgres");// TODO: CHANGE!
        properties.setUsername("amirsam");// TODO: CHANGE!
        properties.setPassword("myjava123");// TODO: CHANGE!
        properties.setDriverClassName("org.postgresql.Driver");// TODO: CHANGE!
        return properties;
    }

    @Primary//Default
    @Bean
    public PlatformTransactionManager normalTransactionManager(
            @Qualifier("normalEntityManagerFactory") LocalContainerEntityManagerFactoryBean normalEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(normalEntityManagerFactory.getObject()));
    }

    @Primary//Default
    @Bean
    public DataSource normalDataSource() {
        HikariDataSource dataSource = normalDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setMaximumPoolSize(10);// TODO: CHANGE!
        return dataSource;
    }
}
