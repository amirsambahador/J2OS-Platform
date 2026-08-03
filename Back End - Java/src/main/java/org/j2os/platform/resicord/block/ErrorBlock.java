package org.j2os.platform.resicord.block;

/**
 * A fallback invoked by {@link org.j2os.platform.resicord.Try#get()} with the final failure from
 * a {@link Block}, producing a substitute result instead of letting the failure propagate.
 *
 * @param <R> the type of fallback value produced
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@FunctionalInterface
public interface ErrorBlock<R> {

    /**
     * Handles the failure and returns a substitute result.
     *
     * @param throwable the failure that occurred
     * @return the fallback result
     */
    R body(Throwable throwable);
}
