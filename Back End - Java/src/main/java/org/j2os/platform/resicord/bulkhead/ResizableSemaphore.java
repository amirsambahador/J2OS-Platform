package org.j2os.platform.resicord.bulkhead;

import java.util.concurrent.Semaphore;

/**
 * A {@link Semaphore} whose total permit count can be changed after construction, so a
 * {@link BulkheadExecutor} can be reconfigured to a new capacity without discarding and
 * recreating it (which would drop permits currently held by in-flight callers).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
final class ResizableSemaphore extends Semaphore {

    private volatile int totalPermits;

    /**
     * Creates a fair semaphore with the given initial total permit count.
     *
     * @param initialPermits the initial total number of permits
     */
    ResizableSemaphore(int initialPermits) {
        super(initialPermits, true);
        this.totalPermits = initialPermits;
    }

    /**
     * Changes the total permit count to {@code newTotal}, releasing additional permits if it
     * grew or reducing available permits if it shrank (via {@link #reducePermits(int)}, which
     * only affects permits not currently held — an in-flight caller's already-acquired permit is
     * unaffected).
     *
     * @param newTotal the new total permit count
     */
    synchronized void resizeTo(int newTotal) {
        int delta = newTotal - totalPermits;
        if (delta > 0) {
            release(delta);
        } else if (delta < 0) {
            reducePermits(-delta);
        }
        totalPermits = newTotal;
    }
}
