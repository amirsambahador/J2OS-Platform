package org.j2os.examples.desktop.jbalancer;

import org.j2os.platform.jbalancer.JRoundRobinBalancer;
import org.j2os.platform.jbalancer.exception.ResourceNotFoundException;

import java.util.List;

/**
 * Example demonstrating how to use {@link JRoundRobinBalancer} to distribute
 * requests across multiple URLs for a given resource using a round-robin
 * strategy.
 * <p>
 * {@link JRoundRobinBalancer} is a singleton. Each resource is registered
 * once with a list of URLs, and every subsequent call to
 * {@link JRoundRobinBalancer#getResourceUrl(String)} returns the next URL in
 * the list, wrapping back to the first URL after the last one is reached.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class Example {

    /**
     * Runs the round-robin balancer demonstration.
     *
     * @param args not used
     * @throws ResourceNotFoundException if a resource is requested before it
     *                                    has been configured
     */
    public static void main(String[] args) throws ResourceNotFoundException {
        demonstrateRoundRobinBalancing();
    }

    /**
     * Registers two resources ({@code save-person} and {@code delete-person}),
     * each backed by two URLs, then requests each resource's URL four times
     * to show the round-robin rotation and wrap-around behavior.
     * <p>
     * Expected output pattern for each resource (two URLs, four requests):
     * {@code url1, url2, url1, url2}.
     *
     * @throws ResourceNotFoundException if a resource is requested before it
     *                                    has been configured
     */
    private static void demonstrateRoundRobinBalancing() throws ResourceNotFoundException {
        System.out.println("== Round-robin URL balancing ==");

        JRoundRobinBalancer balancer = JRoundRobinBalancer.getInstance();

        // Register each resource once with the URLs it should rotate through.
        balancer.configurationResource(
                "save-person",
                List.of("http://1.1.1.1/savePerson", "http://2.2.2.2/savePerson")
        );
        balancer.configurationResource(
                "delete-person",
                List.of("http://3.3.3.3/deletePerson", "http://4.4.4.4/deletePerson")
        );

        // Each call advances to the next URL for that resource, wrapping
        // around once the end of the list is reached.
        int rounds = 4;
        for (int i = 1; i <= rounds; i++) {
            System.out.printf("Round %d -> save-person:   %s%n", i, balancer.getResourceUrl("save-person"));
            System.out.printf("Round %d -> delete-person: %s%n", i, balancer.getResourceUrl("delete-person"));
        }
    }
}