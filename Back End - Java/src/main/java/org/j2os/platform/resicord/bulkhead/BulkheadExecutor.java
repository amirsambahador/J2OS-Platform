package org.j2os.platform.resicord.bulkhead;

import org.j2os.platform.resicord.exception.BulkheadRejectedExecutionException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The running state behind one named bulkhead: a dedicated {@link ThreadPoolExecutor} sized to
 * {@link BulkheadPolicy#maxConcurrentThreads()}, plus a {@link ResizableSemaphore} bounding the
 * total number of callers admitted (running or queued) to
 * {@link BulkheadPolicy#totalCapacity()}. A caller that can't acquire a permit within
 * {@link BulkheadPolicy#maxWaitMillis()} is rejected rather than left waiting indefinitely.
 * <p>
 * Package-private: callers interact with bulkheads only through the static
 * {@link BulkheadPolicies} facade, which owns the registry of named instances of this class.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
final class BulkheadExecutor {

    private final ThreadPoolExecutor executor;
    private final ResizableSemaphore semaphore;
    private volatile BulkheadPolicy config;

    /**
     * Creates a bulkhead executor for a named pool, sized according to the given policy.
     *
     * @param name   the pool's name, used only to label its worker threads for diagnostics
     * @param config the initial policy
     */
    BulkheadExecutor(String name, BulkheadPolicy config) {
        this.executor = new ThreadPoolExecutor(
                config.maxConcurrentThreads(),
                config.maxConcurrentThreads(),
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                daemonThreadFactory("resicord-bulkhead-" + name));
        this.semaphore = new ResizableSemaphore(config.totalCapacity());
        this.config = config;
    }

    /**
     * Builds a {@link ThreadFactory} that names its threads {@code prefix-N} (N incrementing from
     * 1) and marks them as daemon threads, so they never prevent JVM shutdown.
     *
     * @param prefix the thread name prefix
     * @return the thread factory
     */
    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Applies a new policy to this already-running bulkhead: resizes the underlying thread pool
     * and semaphore in place, without losing in-flight work or permits already held by callers
     * currently executing.
     *
     * @param newConfig the policy to apply
     */
    synchronized void reconfigure(BulkheadPolicy newConfig) {
        int newCore = newConfig.maxConcurrentThreads();
        if (newCore > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(newCore);
            executor.setCorePoolSize(newCore);
        } else {
            executor.setCorePoolSize(newCore);
            executor.setMaximumPoolSize(newCore);
        }
        semaphore.resizeTo(newConfig.totalCapacity());
        this.config = newConfig;
    }

    /**
     * Returns the policy currently in effect for this bulkhead.
     *
     * @return the current policy
     */
    BulkheadPolicy currentConfig() {
        return config;
    }

    /**
     * Runs a task through this bulkhead: acquires a permit (waiting up to the configured
     * {@link BulkheadPolicy#maxWaitMillis()}), submits the task to the underlying executor, waits
     * for its result, and always releases the permit afterward.
     *
     * @param task the task to run
     * @param <T>  the task's result type
     * @return the task's result
     * @throws BulkheadRejectedExecutionException if no permit becomes available within the
     *                                             configured wait time, or if the underlying
     *                                             executor has been shut down
     * @throws Exception                          whatever the task itself throws
     * @throws InterruptedException                if the calling thread is interrupted while
     *                                             waiting for a permit or for the task's result
     */
    <T> T submit(Callable<T> task) throws Exception {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(config.maxWaitMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (!acquired) {
            throw new BulkheadRejectedExecutionException("Timed out waiting for a bulkhead permit");
        }
        try {
            Future<T> future;
            try {
                future = executor.submit(task);
            } catch (RejectedExecutionException e) {
                throw new BulkheadRejectedExecutionException("Bulkhead was shut down", e);
            }
            try {
                return future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Error error) {
                    throw error;
                }
                throw (cause instanceof Exception ex) ? ex : e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                throw e;
            }
        } finally {
            semaphore.release();
        }
    }

    /**
     * Shuts down the underlying executor: stops accepting new work, while letting any task
     * already submitted run to completion.
     */
    void shutdown() {
        executor.shutdown();
    }
}