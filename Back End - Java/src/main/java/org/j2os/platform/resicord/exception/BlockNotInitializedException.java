package org.j2os.platform.resicord.exception;

/**
 * Thrown by {@link org.j2os.platform.resicord.Try#get()} when called before a work block has
 * been set via the constructor or {@link org.j2os.platform.resicord.Try#doWork}.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class BlockNotInitializedException extends RuntimeException {

    /**
     * Creates the exception with a fixed, descriptive message.
     */
    public BlockNotInitializedException() {
        super("No work block has been set. Call doWork(Block) before get().");
    }
}
