package org.j2os.examples.web;

import jakarta.servlet.Filter;
import org.j2os.examples.web.entity.human.Human;
import org.j2os.examples.web.entity.human.Information;
import org.j2os.examples.web.entity.tree.Tree;
import org.j2os.examples.web.entity.wiki.Wiki;

import org.j2os.examples.web.repository.HumanRepository;
import org.j2os.examples.web.repository.InformationRepository;
import org.j2os.examples.web.repository.TreeRepository;
import org.j2os.examples.web.repository.WikiRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Application entry point. Also seeds sample data (humans, tree categories, wiki pages) into an
 * empty database on startup, and configures a permissive CORS filter for local development.
 */
@SpringBootApplication
public class Main {

    /**
     * Seeds sample data on startup, but only if the database is empty (checked via
     * {@code treeRepository.count() == 0}, so this never runs against a database that already
     * has data).
     * <p>
     * <b>Note:</b> {@code @Transactional} here has no effect - it's a {@code jakarta.transaction}
     * annotation placed on the {@code @Bean} factory method itself, which Spring calls once
     * (outside the proxy that makes {@code @Transactional} work) just to construct the returned
     * {@link CommandLineRunner}. The actual database writes happen later, when Spring Boot
     * invokes that runner's {@code run()} method directly - not through a transactional proxy -
     * so if any {@code save()} call below fails partway through, the earlier saves in this same
     * run are NOT rolled back. If atomicity across all these saves is actually wanted, this
     * needs restructuring - e.g. moving the seeding logic into a {@code @Service} method
     * annotated with Spring's {@code org.springframework.transaction.annotation.Transactional}
     * and calling that from here.
     *
     * @param treeRepository        repository for tree/category nodes
     * @param wikiRepository        repository for wiki pages
     * @param humanRepository       repository for humans
     * @param informationRepository repository for per-human information records
     * @return the startup runner
     */
    @Bean
    @Transactional
    CommandLineRunner startup(TreeRepository treeRepository, WikiRepository wikiRepository, HumanRepository humanRepository, InformationRepository informationRepository) {
        return args -> {

            if (treeRepository.count() == 0) {
                Human human1 = new Human().setName("Amirsam").setFamily("Bahador");
                Human human2 = new Human().setName("Mohammad").setFamily("Ghaderi");
                humanRepository.save(human1);
                humanRepository.save(human2);
                Information information1 = new Information().setContent("AmirsamInfo1").setHuman(human1);
                Information information2 = new Information().setContent("AmirsamInfo2").setHuman(human1);
                Information information3 = new Information().setContent("AmirsamInfo3").setHuman(human1);
                informationRepository.save(information1);
                informationRepository.save(information2);
                informationRepository.save(information3);
                Information information4 = new Information().setContent("MohammadInfo1").setHuman(human2);
                Information information5 = new Information().setContent("MohammadInfo2").setHuman(human2);
                Information information6 = new Information().setContent("MohammadInfo3").setHuman(human2);
                informationRepository.save(information4);
                informationRepository.save(information5);
                informationRepository.save(information6);

                // Seed a small tree of categories.
                var rootCategory = new Tree().setTreeName("ریشه");
                rootCategory = treeRepository.save(rootCategory);

                var productCategory = new Tree().setTreeName("محصولات").setParentTree(rootCategory);
                treeRepository.save(productCategory);

                var serviceCategory = new Tree().setTreeName("خدمات").setParentTree(rootCategory);
                treeRepository.save(serviceCategory);

                // Seed 900 wiki pages, enough to exercise pagination in the page2 examples.
                for (int x = 0; x < 900; x++) {
                    Wiki wiki = new Wiki()
                            .setContent("محتوا " + x)
                            .setTitle("تیتر " + x)
                            .setRowTextColor("#B3F5D1")
                            .setRowBackgroundColor("#055555")
                            .setUserPublisher("امیرسام بهادر" + x);
                    wikiRepository.save(wiki);
                }
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class);
    }

    /**
     * Permissive CORS filter (allows any origin) for local development. See the commented-out
     * alternative below for a production-style config restricting to specific origins.
     */
    @Bean
    FilterRegistrationBean<Filter> crossOrigin() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", new CorsConfiguration().applyPermitDefaultValues());
        return new FilterRegistrationBean<>(new CorsFilter(source));
    }

    // Production-style alternative to the crossOrigin() bean above, restricting CORS to a
    // specific allowlist of origins instead of permitting all of them. Kept as a reference, not
    // active - since a bean method of the same name can't coexist with the one above, swap this
    // in by replacing crossOrigin() with this body rather than uncommenting it as a second bean.
    /*
    @Bean
    FilterRegistrationBean<Filter> crossOrigin() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
        config.setAllowedOrigins(List.of(
                "http://192.168.1.100",
                "http://10.0.0.50"
        ));
        source.registerCorsConfiguration("/**", config);
        return new FilterRegistrationBean<>(new CorsFilter(source));
    }
    */
}