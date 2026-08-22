package org.jshard.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correctness tests for {@link MurmurHash3}.
 * <p>
 * MurmurHash3-32 has well-known, published test vectors, and its output for
 * a given (input, seed) pair is a fixed, publicly documented value - so
 * unlike the routing algorithm above it, its correctness is a simple,
 * closed question we can pin down exactly rather than just test properties
 * of.
 */
class MurmurHash3Test {

    // Reference vectors verified against the canonical Austin Appleby
    // MurmurHash3 (x86_32) reference implementation and cross-checked
    // against the Python `mmh3` package output.
    @Test
    void knownVectors_matchReferenceImplementation() {
        assertEquals(0, MurmurHash3.murmurHash332(new byte[0], 0));
        assertEquals(0x514E28B7, MurmurHash3.murmurHash332(new byte[0], 1));

        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertEquals(0xBA6BD213, MurmurHash3.murmurHash332(data, 0));

        byte[] hello = "Hello, world!".getBytes(StandardCharsets.UTF_8);
        assertEquals(0xC0363E43, MurmurHash3.murmurHash332(hello, 0));
        assertEquals(0xAA5DC85B, MurmurHash3.murmurHash332(hello, 1));

        // 1..256 (wrapping at 256) as bytes, seed 0
        byte[] seq = new byte[256];
        for (int i = 0; i < 256; i++) {
            seq[i] = (byte) (i + 1);
        }
        assertEquals(0xF6403B6B, MurmurHash3.murmurHash332(seq, 0, 256, 0));
    }

    @Test
    void isDeterministic_sameInputAndSeed_alwaysSameHash() {
        byte[] data = "some-repeatable-key".getBytes(StandardCharsets.UTF_8);
        int first = MurmurHash3.murmurHash332(data, 42);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, MurmurHash3.murmurHash332(data, 42));
        }
    }

    @Test
    void differentSeeds_producesDifferentHashesForSameInput() {
        // Not a hard guarantee for every seed pair, but with a well-mixed
        // hash it should hold for a large majority of seeds - this guards
        // against a broken implementation that ignores the seed entirely.
        byte[] data = "seed-sensitivity-check".getBytes(StandardCharsets.UTF_8);
        int baseline = MurmurHash3.murmurHash332(data, 0);
        int distinctCount = 0;
        int totalSeeds = 200;
        for (int seed = 1; seed <= totalSeeds; seed++) {
            if (MurmurHash3.murmurHash332(data, seed) != baseline) {
                distinctCount++;
            }
        }
        assertTrue(distinctCount > totalSeeds * 0.99,
                "Too many seeds produced the same hash as seed=0 - seed may be ignored");
    }

    @Test
    void byteArrayOverload_matchesExplicitOffsetLengthOverload() {
        byte[] data = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        assertEquals(
                MurmurHash3.murmurHash332(data, 0, data.length, 7),
                MurmurHash3.murmurHash332(data, 7));
    }

    @Test
    void offsetAndLength_areHonored_hashesOnlyTheSlice() {
        // "XXX{payload}YYY" hashing bytes 3..10 should equal hashing "payload" directly
        byte[] wrapped = "XXX{payload}YYY".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "payload".getBytes(StandardCharsets.UTF_8);
        int fromSlice = MurmurHash3.murmurHash332(wrapped, 4, 7, 0);
        int fromPlain = MurmurHash3.murmurHash332(plain, 0);
        assertEquals(fromPlain, fromSlice);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 8, 15, 16, 17, 63, 64, 65, 1000})
    void handlesAllTailLengths_withoutError(int length) {
        // Exercises every tailLen case (0,1,2,3) across many total lengths,
        // and every branch of the 4-byte main loop, without throwing.
        byte[] data = new byte[length];
        new Random(length).nextBytes(data);
        assertDoesNotThrow(() -> MurmurHash3.murmurHash332(data, 0, data.length, 0));
    }

    @Test
    void intOverload_isConsistentAndSeedSensitive() {
        int first = MurmurHash3.murmurHash332(12345, 0);
        assertEquals(first, MurmurHash3.murmurHash332(12345, 0));
        assertNotEquals(first, MurmurHash3.murmurHash332(12345, 1));
        assertNotEquals(first, MurmurHash3.murmurHash332(12346, 0));
    }

    @Test
    void avalancheEffect_singleBitFlip_changesRoughlyHalfTheOutputBits() {
        // A well-mixed 32-bit hash should flip close to 16 of its 32 output
        // bits when a single input bit flips. This is a coarse sanity check
        // on fmix()/the mixing constants, not a strict cryptographic bound.
        byte[] base = "avalanche-test-input".getBytes(StandardCharsets.UTF_8);
        int baseHash = MurmurHash3.murmurHash332(base, 0);

        for (int bitToFlip = 0; bitToFlip < 8; bitToFlip++) {
            byte[] flipped = base.clone();
            flipped[0] ^= (1 << bitToFlip);
            int flippedHash = MurmurHash3.murmurHash332(flipped, 0);
            int differingBits = Integer.bitCount(baseHash ^ flippedHash);
            assertTrue(differingBits >= 8 && differingBits <= 24,
                    "Flipping input bit " + bitToFlip + " changed only " + differingBits + " output bits");
        }
    }

    @Test
    void distinctInputs_rarelyCollide_over100kSamples() {
        // Coarse uniformity/collision sanity check, not a formal proof.
        int sampleSize = 100_000;
        Set<Integer> hashes = new HashSet<>(sampleSize * 2);
        Random random = new Random(99);
        for (int i = 0; i < sampleSize; i++) {
            byte[] key = ("collision-check-" + random.nextLong()).getBytes(StandardCharsets.UTF_8);
            hashes.add(MurmurHash3.murmurHash332(key, 0));
        }
        // Birthday-paradox collisions on a 32-bit space at this sample size
        // are expected to be rare but not impossible; a healthy hash should
        // still land well above 99% unique.
        assertTrue(hashes.size() > sampleSize * 0.99,
                "Too many collisions: " + hashes.size() + " unique out of " + sampleSize);
    }
}
