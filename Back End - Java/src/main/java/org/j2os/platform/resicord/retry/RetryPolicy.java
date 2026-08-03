package org.j2os.platform.resicord.retry;

/**
 * Configuration for retrying a failed call: how many times to attempt it, and how long to wait
 * between attempts.
 *
 * @param maxAttempts the maximum number of attempts, including the first (non-retry) attempt;
 *                    must be >= 1
 * @param delayMillis the delay between attempts, in milliseconds; must be >= 0
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public record RetryPolicy(int maxAttempts, long delayMillis) {

    /**
     * The no-retry policy: a single attempt, with no delay. Used by
     * {@link org.j2os.platform.resicord.Try#get()} when no retry policy has been configured.
     */
    public static final RetryPolicy NONE = new RetryPolicy(1, 0);

    /**
     * Validates the record's components.
     *
     * @throws IllegalArgumentException if either component is out of its allowed range
     */
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must be >= 0");
        }
    }
}
