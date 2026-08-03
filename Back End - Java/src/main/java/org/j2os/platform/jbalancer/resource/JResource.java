package org.j2os.platform.jbalancer.resource;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds an immutable list of URLs for a single resource and hands them out
 * one at a time in round-robin order.
 * <p>
 * Thread-safe: the current position is tracked with an {@link AtomicInteger},
 * so concurrent calls to {@link #getNextUrl()} will not return a stale index
 * or corrupt the counter.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class JResource {

    /**
     * The URLs available for this resource, in round-robin order. A defensive, immutable copy; never null or empty.
     */
    private final List<String> urls;

    /**
     * Tracks the index of the next URL to hand out.
     */
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Creates a new round-robin resource backed by the given URLs.
     *
     * @param urls the URLs to rotate through; must not be null or empty. The list is
     *             defensively copied, so later mutation of the caller's list has no
     *             effect on this resource.
     * @throws IllegalArgumentException if {@code urls} is null or empty
     */
    public JResource(List<String> urls) {
        if (Objects.isNull(urls) || urls.isEmpty()) {
            throw new IllegalArgumentException("urls must not be null or empty");
        }
        this.urls = List.copyOf(urls);
    }

    /**
     * Returns the next URL in round-robin order, wrapping back to the first
     * URL after the last one has been returned.
     *
     * @return the next URL for this resource
     */
    public String getNextUrl() {
        // Atomically advance the counter and wrap it around the list size so
        // that concurrent callers each observe a distinct, valid index.
        int index = counter.getAndUpdate(i -> (i + 1) % urls.size());
        return urls.get(index);
    }
}