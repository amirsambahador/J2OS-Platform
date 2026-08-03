package org.j2os.test.page2;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/**
 * Builds a JPA {@link EntityManagerFactory} entirely in Java, with no {@code persistence.xml}
 * involved at all — using Hibernate's native bootstrap API ({@link StandardServiceRegistryBuilder}
 * + {@link MetadataSources}) instead of {@code jakarta.persistence.Persistence}.
 * <p>
 * Hibernate's {@link SessionFactory} implements {@link EntityManagerFactory} directly (and its
 * {@code Session} implements {@code EntityManager}), so the object this returns is a fully
 * standard JPA entity manager factory as far as any caller is concerned.
 * <p>
 * Used only by {@code Page2Test}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class HibernateBootstrap {

    private HibernateBootstrap() {
    }

    /**
     * Builds an entity manager factory backed by an in-memory H2 database, mapping the given
     * annotated entity classes.
     *
     * @param jdbcUrl       the JDBC URL to connect to, e.g. {@code "jdbc:h2:mem:page2-test;DB_CLOSE_DELAY=-1"}
     * @param entityClasses the JPA-annotated entity classes to map
     * @return a ready-to-use entity manager factory
     */
    public static EntityManagerFactory createEntityManagerFactory(String jdbcUrl, Class<?>... entityClasses) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("jakarta.persistence.jdbc.url", jdbcUrl)
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.user", "sa")
                .applySetting("jakarta.persistence.jdbc.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.show_sql", "false")
                .build();

        try {
            MetadataSources metadataSources = new MetadataSources(registry);
            for (Class<?> entityClass : entityClasses) {
                metadataSources.addAnnotatedClass(entityClass);
            }
            Metadata metadata = metadataSources.buildMetadata();
            return metadata.buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }
}