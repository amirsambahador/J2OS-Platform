package org.j2os.platform.resicord.exception;

/**
 * Thrown when a named policy (retry, time-limit, or bulkhead) is referenced — by
 * {@link org.j2os.platform.resicord.Try#retry(String)},
 * {@link org.j2os.platform.resicord.Try#timeLimit(String)}, or
 * {@link org.j2os.platform.resicord.Try#bulkhead(String)} — but was never registered under that
 * name. Signals a configuration mistake rather than a transient failure, so
 * {@link org.j2os.platform.resicord.Try#get()} never retries it or routes it through an error handler.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates the exception with a message naming the kind of policy and the missing name.
     *
     * @param kind a short label for what wasn't found (e.g. {@code "retry policy"}, {@code "time limit"}, {@code "bulkhead"})
     * @param name the name that was looked up
     */
    public NotFoundException(String kind, String name) {
        super("No " + kind + " named '" + name + "' has been defined");
    }
}
