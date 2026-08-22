package org.jshard.algorithm;

/**
 * Fixed tuning constants for {@link Algorithm}'s BinomialHash
 * implementation.
 * <p>
 * These are compile-time constants, not runtime-configurable via
 * {@link AlgorithmConfig#init(java.util.Properties)}: the slot space and
 * retry budget are chosen once and shared by every {@link Algorithm}
 * instance in the JVM.
 *
 * @author taha rouhi
 */
public class ConstantValues {

    /** Number of bits used for the slot id space; {@link #SLOT_COUNT} is {@code 2^SLOT_BITS}. */
    public static final int SLOT_BITS = 14;

    /** Total number of fixed slots keys are assigned to, independent of shard count (see {@link Algorithm#getSlotId(byte[])}). */
    public static final int SLOT_COUNT = 1 << SLOT_BITS;;

    /** Bitmask used to fold a hash into {@code [0, SLOT_COUNT)}; equal to {@code SLOT_COUNT - 1}. */
    public static final int SLOT_MASK = SLOT_COUNT - 1;

    /** Maximum number of extra hash attempts {@link Algorithm} makes before falling back to a deterministic remap, as recommended by the BinomialHash paper. */
    public static final int OMEGA = 6;

}