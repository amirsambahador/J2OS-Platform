package org.j2os.platform.resicord.exception;

/**
 * Thrown when a call routed through a bulkhead (see
 * {@link org.j2os.platform.resicord.bulkhead.BulkheadPolicies}) cannot be admitted — either
 * because no permit became free within the configured wait time, or because the bulkhead's
 * underlying executor has been shut down.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class BulkheadRejectedExecutionException extends RuntimeException {

    /**
     * Creates the exception with a message and no cause.
     *
     * @param message a description of why the call was rejected
     */
    public BulkheadRejectedExecutionException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and an underlying cause.
     *
     * @param message a description of why the call was rejected
     * @param cause   the underlying cause
     */
    public BulkheadRejectedExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
