package org.j2os.platform.resicord.block;

/**
 * A unit of work run by {@link org.j2os.platform.resicord.Try}, producing a result of type
 * {@code T} or throwing on failure.
 *
 * @param <T> the type of value produced
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@FunctionalInterface
public interface Block<T> {

    /**
     * Runs the work and returns its result.
     *
     * @return the result
     * @throws Exception if the work fails
     */
    T body() throws Exception;
}
