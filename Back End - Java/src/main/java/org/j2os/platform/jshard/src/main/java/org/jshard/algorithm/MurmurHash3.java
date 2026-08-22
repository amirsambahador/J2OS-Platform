package org.jshard.algorithm;

/**
 * Standalone implementation of the 32-bit MurmurHash3 algorithm
 * (Appleby, 2011), used by {@link Algorithm} both for hashing raw key
 * bytes and for the repeated integer re-hashing steps BinomialHash needs
 * (see {@link Algorithm#getSlotId(byte[])} and the private routing logic
 * in {@link Algorithm}). Not an original algorithm - reimplemented here,
 * dependency-free, to avoid pulling in a hashing library for this single
 * primitive.
 * <p>
 * Stateless: every method is a pure function of its arguments, with no
 * shared state across calls.
 *
 * @author taha rouhi
 */
public class MurmurHash3 {


    private MurmurHash3() {
    }
    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;


    /**
     * Computes the 32-bit MurmurHash3 of {@code len} bytes of {@code data}
     * starting at {@code offset}.
     *
     * @param data   the bytes to hash
     * @param offset the offset into {@code data} to start hashing from
     * @param len    the number of bytes to hash, starting at {@code offset}
     * @param seed   the hash seed
     * @return the 32-bit hash
     */
    public static int murmurHash332(byte[] data, int offset, int len, int seed) {
        int h1 = seed;
        int roundedEnd = offset + (len & ~0x3);


        for (int i = offset; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);

            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }


        int k1 = 0;
        int tailLen = len & 0x3;
        int tailOffset = roundedEnd;

        if (tailLen == 3) {
            k1 ^= (data[tailOffset + 2] & 0xff) << 16;
        }
        if (tailLen >= 2) {
            k1 ^= (data[tailOffset + 1] & 0xff) << 8;
        }
        if (tailLen >= 1) {
            k1 ^= (data[tailOffset] & 0xff);
            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;
            h1 ^= k1;
        }

        h1 ^= len;


        return fmix(h1);
    }


    /**
     * Computes the 32-bit MurmurHash3 of a single {@code int} value,
     * treated as 4 input bytes. Used by {@link Algorithm} to re-hash a
     * slot id or intermediate bucket value against a new seed/salt.
     *
     * @param input the value to hash
     * @param seed  the hash seed
     * @return the 32-bit hash
     */
    public static int murmurHash332(int input, int seed) {
        int h1 = seed;

        int k1 = input;
        k1 *= C1;
        k1 = Integer.rotateLeft(k1, 15);
        k1 *= C2;

        h1 ^= k1;
        h1 = Integer.rotateLeft(h1, 13);
        h1 = h1 * 5 + 0xe6546b64;


        h1 ^= 4;


        return fmix(h1);
    }



    /**
     * MurmurHash3's final mixing step, applied to the accumulated hash
     * state to improve avalanche behavior (ensuring small input changes
     * produce widely different output bits).
     *
     * @param h1 the accumulated hash state
     * @return the finalized 32-bit hash
     */
    private static int fmix(int h1) {
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);
        return h1;
    }


    /**
     * Computes the 32-bit MurmurHash3 of the entirety of {@code data}.
     * Equivalent to {@code murmurHash332(data, 0, data.length, seed)}.
     *
     * @param data the bytes to hash
     * @param seed the hash seed
     * @return the 32-bit hash
     */
    public static int murmurHash332(byte[] data, int seed) {
        return murmurHash332(data, 0, data.length, seed);
    }
}