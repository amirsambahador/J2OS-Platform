package org.j2os.platform.resicord.bulkhead;

/**
 * Configuration for one named bulkhead: how much work may run concurrently, how much more may
 * wait in line, and how long a caller will wait for a slot before being rejected.
 *
 * @param maxConcurrentThreads the number of worker threads dedicated to this bulkhead, and the
 *                             maximum number of callers that may be actively running at once;
 *                             must be >= 1
 * @param maxQueueSize         the number of additional callers allowed to wait for a free worker
 *                             thread once all {@code maxConcurrentThreads} are busy; must be >= 0
 * @param maxWaitMillis        how long a caller will wait for a permit (a running slot or a queue
 *                             slot) before being rejected with a
 *                             {@link org.j2os.platform.resicord.exception.BulkheadRejectedExecutionException};
 *                             must be >= 0 (0 means fail immediately if no permit is free)
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public record BulkheadPolicy(int maxConcurrentThreads, int maxQueueSize, long maxWaitMillis) {

    /**
     * Validates the record's components.
     *
     * @throws IllegalArgumentException if any component is out of its allowed range
     */
    public BulkheadPolicy {
        if (maxConcurrentThreads < 1) {
            throw new IllegalArgumentException("maxConcurrentThreads must be >= 1");
        }
        if (maxQueueSize < 0) {
            throw new IllegalArgumentException("maxQueueSize must be >= 0");
        }
        if (maxWaitMillis < 0) {
            throw new IllegalArgumentException("maxWaitMillis must be >= 0");
        }
    }

    /**
     * The total number of callers this bulkhead admits at once — running plus queued.
     *
     * @return {@code maxConcurrentThreads + maxQueueSize}
     */
    public int totalCapacity() {
        return maxConcurrentThreads + maxQueueSize;
    }
}
