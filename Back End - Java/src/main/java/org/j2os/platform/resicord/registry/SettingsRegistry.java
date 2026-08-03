package org.j2os.platform.resicord.registry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, name-keyed store of configuration values, shared by the
 * {@link org.j2os.platform.resicord.retry.RetryPolicies} and
 * {@link org.j2os.platform.resicord.timelimit.TimeLimitPolicies} static facades. Unlike
 * {@link org.j2os.platform.resicord.bulkhead.BulkheadPolicies} (which owns live executors), the
 * values stored here are plain immutable configuration, so "removing" or "replacing" an entry
 * has no lifecycle to manage beyond the map itself.
 *
 * @param <T> the type of value stored
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class SettingsRegistry<T> {

    private final ConcurrentHashMap<String, T> store = new ConcurrentHashMap<>();

    /**
     * Stores a value under a name, replacing any existing value with that name.
     *
     * @param name  the name to store under
     * @param value the value to store
     */
    public void put(String name, T value) {
        store.put(name, value);
    }

    /**
     * Looks up a value by name.
     *
     * @param name the name to look up
     * @return the value, or {@link Optional#empty()} if no value is stored under that name
     */
    public Optional<T> get(String name) {
        return Optional.ofNullable(store.get(name));
    }

    /**
     * Returns a point-in-time, immutable snapshot of every stored name/value pair.
     *
     * @return the snapshot map
     */
    public Map<String, T> snapshotAll() {
        return Map.copyOf(store);
    }

    /**
     * Removes a stored value. A no-op if the name isn't present.
     *
     * @param name the name to remove
     */
    public void remove(String name) {
        store.remove(name);
    }
}
