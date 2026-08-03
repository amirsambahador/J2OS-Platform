package org.j2os.platform.resicord.timelimit;

/**
 * Configuration for bounding how long a call is allowed to run.
 *
 * @param millis the time limit, in milliseconds; must be >= 0. A value of 0 means the limit is
 *               disabled (see {@link #isEnabled()}) rather than "time out immediately".
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public record TimeLimitPolicy(long millis) {

    /**
     * The disabled time-limit policy (no limit applied). Used by
     * {@link org.j2os.platform.resicord.Try#get()} when no time-limit policy has been configured.
     */
    public static final TimeLimitPolicy NONE = new TimeLimitPolicy(0);

    public TimeLimitPolicy {
        if (millis < 0) {
            throw new IllegalArgumentException("millis must be >= 0");
        }
    }

    /**
     * Whether this policy actually imposes a time limit.
     *
     * @return true if {@code millis > 0}
     */
    public boolean isEnabled() {
        return millis > 0;
    }
}
