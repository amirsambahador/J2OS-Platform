package org.j2os.platform.resicord.retry;

import org.j2os.platform.resicord.exception.NotFoundException;
import org.j2os.platform.resicord.registry.SettingsRegistry;

import java.util.Map;

/**
 * Static registry of named retry policies, referenced from call sites via
 * {@link org.j2os.platform.resicord.Try#retry(String)} so retry tuning can be changed centrally
 * without touching every call site.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class RetryPolicies {

    private static final SettingsRegistry<RetryPolicy> registry = new SettingsRegistry<>();

    private RetryPolicies() {
    }

    /**
     * Defines (or replaces) a named retry policy.
     *
     * @param name   the policy's name
     * @param policy the policy to store
     */
    public static void define(String name, RetryPolicy policy) {
        registry.put(name, policy);
    }

    /**
     * Looks up a named retry policy.
     *
     * @param name the policy's name
     * @return the policy
     * @throws NotFoundException if no policy is defined under this name
     */
    public static RetryPolicy get(String name) {
        return registry.get(name).orElseThrow(() -> new NotFoundException("retry policy", name));
    }

    /**
     * Returns a point-in-time snapshot of every defined retry policy, keyed by name.
     *
     * @return the snapshot map
     */
    public static Map<String, RetryPolicy> listAll() {
        return registry.snapshotAll();
    }

    /**
     * Removes a named retry policy. A no-op if the name isn't defined.
     *
     * @param name the policy's name
     */
    public static void remove(String name) {
        registry.remove(name);
    }
}
