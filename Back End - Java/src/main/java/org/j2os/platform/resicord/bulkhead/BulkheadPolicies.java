package org.j2os.platform.resicord.bulkhead;

import org.j2os.platform.resicord.exception.NotFoundException;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Static registry of named bulkhead pools: a bulkhead limits how much concurrent work can run
 * under a given name (via a dedicated thread pool + semaphore), so a slow or overloaded
 * dependency can't exhaust threads or resources shared by unrelated call sites.
 * <p>
 * Unlike {@link org.j2os.platform.resicord.retry.RetryPolicies} and
 * {@link org.j2os.platform.resicord.timelimit.TimeLimitPolicies}, a defined name here isn't just
 * a stored configuration value — it owns a live {@link BulkheadExecutor} (thread pool +
 * semaphore) that keeps running until explicitly {@link #remove(String) removed}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class BulkheadPolicies {

    private static final ConcurrentHashMap<String, BulkheadExecutor> executors = new ConcurrentHashMap<>();

    private BulkheadPolicies() {
    }

    /**
     * Defines a named bulkhead. If the name isn't already defined, a new pool is created with
     * this policy. If it is already defined, the existing pool is reconfigured in place (see
     * {@link BulkheadExecutor#reconfigure(BulkheadPolicy)}) rather than replaced, so in-flight
     * work and held permits aren't disrupted.
     *
     * @param name   the pool's name
     * @param config the policy to apply
     */
    public static void define(String name, BulkheadPolicy config) {
        executors.compute(name, (n, existing) -> {
            if (existing == null) {
                return new BulkheadExecutor(n, config);
            }
            existing.reconfigure(config);
            return existing;
        });
    }

    /**
     * Reconfigures an already-defined bulkhead in place.
     *
     * @param name   the pool's name
     * @param config the policy to apply
     * @throws NotFoundException if no bulkhead is defined under this name
     */
    public static void reconfigure(String name, BulkheadPolicy config) {
        executorOrThrow(name).reconfigure(config);
    }

    /**
     * Returns the policy currently in effect for a named bulkhead.
     *
     * @param name the pool's name
     * @return the current policy
     * @throws NotFoundException if no bulkhead is defined under this name
     */
    public static BulkheadPolicy currentConfig(String name) {
        return executorOrThrow(name).currentConfig();
    }

    /**
     * Returns a point-in-time snapshot of every defined bulkhead's current policy, keyed by name.
     *
     * @return an unmodifiable snapshot map
     */
    public static Map<String, BulkheadPolicy> listAll() {
        return executors.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().currentConfig()));
    }

    /**
     * Removes a named bulkhead and shuts down its underlying thread pool. A no-op if the name
     * isn't defined.
     *
     * @param name the pool's name
     */
    public static void remove(String name) {
        BulkheadExecutor executor = executors.remove(name);
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Runs a task through a named bulkhead.
     *
     * @param name the pool's name
     * @param task the task to run
     * @param <T>  the task's result type
     * @return the task's result
     * @throws NotFoundException if no bulkhead is defined under this name
     * @throws Exception         whatever the task itself throws, or a
     *                           {@link org.j2os.platform.resicord.exception.BulkheadRejectedExecutionException}
     *                           if the bulkhead has no free capacity
     */
    public static <T> T execute(String name, Callable<T> task) throws Exception {
        return executorOrThrow(name).submit(task);
    }

    /**
     * Looks up the executor for a named bulkhead.
     *
     * @param name the pool's name
     * @return the executor
     * @throws NotFoundException if no bulkhead is defined under this name
     */
    private static BulkheadExecutor executorOrThrow(String name) {
        BulkheadExecutor executor = executors.get(name);
        if (executor == null) {
            throw new NotFoundException("bulkhead", name);
        }
        return executor;
    }
}
