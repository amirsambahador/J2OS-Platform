package org.j2os.platform.resicord.timelimit;

import org.j2os.platform.resicord.exception.NotFoundException;
import org.j2os.platform.resicord.registry.SettingsRegistry;

import java.util.Map;

/**
 * Static registry of named time-limit policies, referenced from call sites via
 * {@link org.j2os.platform.resicord.Try#timeLimit(String)} so time-limit tuning can be changed
 * centrally without touching every call site.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class TimeLimitPolicies {

    private static final SettingsRegistry<TimeLimitPolicy> registry = new SettingsRegistry<>();

    private TimeLimitPolicies() {
    }

    /**
     * Defines (or replaces) a named time-limit policy.
     *
     * @param name   the policy's name
     * @param policy the policy to store
     */
    public static void define(String name, TimeLimitPolicy policy) {
        registry.put(name, policy);
    }

    /**
     * Looks up a named time-limit policy.
     *
     * @param name the policy's name
     * @return the policy
     * @throws NotFoundException if no policy is defined under this name
     */
    public static TimeLimitPolicy get(String name) {
        return registry.get(name).orElseThrow(() -> new NotFoundException("time limit", name));
    }

    /**
     * Returns a point-in-time snapshot of every defined time-limit policy, keyed by name.
     *
     * @return the snapshot map
     */
    public static Map<String, TimeLimitPolicy> listAll() {
        return registry.snapshotAll();
    }

    /**
     * Removes a named time-limit policy. A no-op if the name isn't defined.
     *
     * @param name the policy's name
     */
    public static void remove(String name) {
        registry.remove(name);
    }
}
