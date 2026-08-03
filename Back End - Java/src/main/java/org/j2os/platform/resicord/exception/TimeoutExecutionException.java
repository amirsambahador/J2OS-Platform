package org.j2os.platform.resicord.exception;

/**
 * Thrown when a call bound by {@link org.j2os.platform.resicord.Try#timeLimit} does not complete
 * within its configured time limit. The work itself is cancelled (best-effort, via
 * {@link java.util.concurrent.Future#cancel(boolean)}) but may continue running in the background
 * if it doesn't respond to interruption.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class TimeoutExecutionException extends RuntimeException {

    /**
     * Creates the exception wrapping the underlying {@link java.util.concurrent.TimeoutException}.
     *
     * @param cause the timeout that occurred
     */
    public TimeoutExecutionException(Throwable cause) {
        super(cause);
    }
}
