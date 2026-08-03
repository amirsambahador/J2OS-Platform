package org.j2os.platform.jbalancer;

import org.j2os.platform.jbalancer.exception.ResourceNotFoundException;
import org.j2os.platform.jbalancer.resource.JResource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry of round-robin resources, keyed by a case-insensitive resource identifier.
 * <p>
 * Each resource identifier is associated with a {@link JResource}, which rotates through a
 * configured list of URLs. Use {@link #configurationResource(String, List)} to register or
 * replace a resource, and {@link #getResourceUrl(String)} to retrieve the next URL for it.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JRoundRobinBalancer {

    /**
     * The single shared instance of this balancer.
     */
    private static final JRoundRobinBalancer RESOURCE_DISCOVERY_BALANCER = new JRoundRobinBalancer();

    /**
     * Maps normalized resource identifiers to their round-robin resource.
     */
    private final ConcurrentHashMap<String, JResource> resourceProvidersMap = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce the singleton pattern; use {@link #getInstance()}.
     */
    private JRoundRobinBalancer() {
    }

    /**
     * Returns the single shared instance of this balancer.
     *
     * @return the singleton {@code JRoundRobinBalancer} instance
     */
    public static JRoundRobinBalancer getInstance() {
        return RESOURCE_DISCOVERY_BALANCER;
    }

    /**
     * Normalizes a resource identifier for case-insensitive lookup.
     * <p>
     * Uses {@link Locale#ROOT} rather than the JVM's default locale, so the
     * uppercasing result (and therefore which identifiers are considered
     * equal) does not vary depending on the server's locale settings (e.g.
     * a Turkish locale would otherwise uppercase {@code 'i'} to {@code 'İ'}
     * instead of {@code 'I'}, silently breaking lookups).
     *
     * @param resourceId the identifier to normalize; must not be null
     * @return the uppercase form of {@code resourceId}
     */
    private static String normalize(String resourceId) {
        return Objects.requireNonNull(resourceId, "resourceId must not be null").toUpperCase(Locale.ROOT);
    }

    /**
     * Registers a resource under the given identifier, or replaces the existing
     * resource (and resets its rotation) if one is already registered under that identifier.
     *
     * @param resourceId the identifier for the resource; matching is case-insensitive
     * @param urls       the URLs to rotate through for this resource; must not be null or empty
     * @return this balancer, to allow chained configuration calls
     */
    public JRoundRobinBalancer configurationResource(String resourceId, List<String> urls) {
        resourceProvidersMap.put(normalize(resourceId), new JResource(urls));
        return this;
    }

    /**
     * Removes the resource registered under the given identifier, if any.
     *
     * @param resourceId the identifier of the resource to remove; matching is case-insensitive
     * @return this balancer, to allow chained configuration calls
     */
    public JRoundRobinBalancer removeResource(String resourceId) {
        resourceProvidersMap.remove(normalize(resourceId));
        return this;
    }

    /**
     * Returns the next URL, in round-robin order, for the resource registered
     * under the given identifier.
     *
     * @param resourceId the identifier of the resource to look up; matching is case-insensitive
     * @return the next URL for the resource
     * @throws ResourceNotFoundException if no resource is registered under {@code resourceId}
     */
    public String getResourceUrl(String resourceId) throws ResourceNotFoundException {
        String id = normalize(resourceId);
        JResource resource = resourceProvidersMap.get(id);
        if (Objects.isNull(resource)) {
            throw new ResourceNotFoundException(id);
        }
        return resource.getNextUrl();
    }
}