package org.j2os.platform.jbalancer.exception;

/**
 * Thrown when a lookup for a resource by its identifier fails because no
 * resource has been registered under that identifier.
 * <p>
 * Callers should first register a resource under the given identifier via
 * {@link org.j2os.platform.jbalancer.JRoundRobinBalancer#configurationResource(String, java.util.List)}
 * before requesting a URL for it.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class ResourceNotFoundException extends Exception {

    /**
     * Creates a new exception describing which resource identifier could not be found.
     *
     * @param resourceId the identifier that was looked up and not found
     */
    public ResourceNotFoundException(String resourceId) {
        super("Resource " + resourceId + " not found, creating a new one using configurationResource(String resourceId, List<String> urls)");
    }
}