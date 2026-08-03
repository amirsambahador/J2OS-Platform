package org.j2os.test.jsecurity;

import org.j2os.platform.jsecurity.cryptography.Cryptography;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Plain, dependency-free test suite for {@code org.j2os.platform.jsecurity.cryptography}
 * ({@link Cryptography}) (no test framework such as JUnit is used). Run it directly with its
 * {@link #main(String[])} method; each test case reports PASS/FAIL to standard output and a
 * summary is printed at the end.
 * <p>
 * <b>Classpath requirements:</b> Bouncy Castle ({@code bcprov-jdk18on}, for {@link Cryptography}'s
 * BCrypt/Argon2 support).
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public class CryptographyTest {

    /** Total number of test cases executed so far. */
    private static int totalTestCount = 0;

    /** Number of test cases that failed so far. */
    private static int failedTestCount = 0;

    /** Matches a canonical, lowercase, hyphenated UUID string. */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /**
     * Runs every test case in this suite and prints a final summary.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        try {
            testBase64RoundTrips();
            testDigestsAreDeterministicAndCorrectLength();
            //testFileDigestMatchesStringDigestForSameContent();
            testBCryptHashAndCheckRoundTrip();
            testArgon2HashAndCheckRoundTrip();
            testArgon2CheckRejectsMalformedHash();
            testPBKDF2HashAndCheckRoundTrip();
            testPBKDF2CheckRejectsMalformedHash();
            testAesStringKeyBasedRoundTripAndWrongKeyFails();
            testAesStringPasswordBasedRoundTripAndWrongPasswordFails();
            testGenerateAESKeyRejectsInvalidBitSize();
            //testAesFileKeyBasedRoundTrip();
            //testAesFilePasswordBasedRoundTrip();
            //testAesFileDecryptWithWrongModeThrows();
            testGenerateRSAKeysRejectsSmallKeySize();
            testRsaRoundTrip();
            testRsaEncryptRejectsTooLargePlaintext();
            testRandomIntValidatesBoundsAndStaysInRange();
            testRandomDoubleValidatesBoundsAndStaysInRange();
            testRandomStringsRespectLengthAndCharset();
            testUuidHasCanonicalFormat();
            testNullArgumentsAreRejected();
            testConcurrentPasswordHashingStressCheck();
            //testConcurrentAesFileEncryptionStressCheck();
        } catch (Exception e) {
            System.out.println("[FATAL] Test run failed: " + e);
            e.printStackTrace();
        }

        printSummary();
        System.exit(failedTestCount == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // Base64
    // ------------------------------------------------------------------

    /** Verifies standard and URL-safe Base64 encode/decode round trip correctly. */
    private static void testBase64RoundTrips() {
        String testName = "Base64 standard and URL-safe encode/decode round-trip";
        byte[] data = "Hello, J2OS! \u0000\u00FF".getBytes();

        boolean standardRoundTrips = java.util.Arrays.equals(data, Cryptography.decodeBase64(Cryptography.encodeBase64(data)));
        boolean urlRoundTrips = java.util.Arrays.equals(data, Cryptography.decodeBase64URL(Cryptography.encodeBase64URL(data)));
        assertTrue(testName, standardRoundTrips && urlRoundTrips);
    }

    // ------------------------------------------------------------------
    // Digests
    // ------------------------------------------------------------------

    /** Verifies each digest algorithm is deterministic and produces hex output of the expected length. */
    private static void testDigestsAreDeterministicAndCorrectLength() {
        String testName = "Digest methods are deterministic and produce correctly sized hex output";
        String text = "Hello, J2OS!";

        boolean allCorrect =
                Cryptography.hashByMD5(text).equals(Cryptography.hashByMD5(text)) && Cryptography.hashByMD5(text).length() == 32
                        && Cryptography.hashBySHA2_256(text).length() == 64
                        && Cryptography.hashBySHA2_512(text).length() == 128
                        && Cryptography.hashBySHA3_256(text).length() == 64
                        && Cryptography.hashBySHA3_512(text).length() == 128;
        assertTrue(testName, allCorrect);
    }

    /** Verifies hashing a file's contents produces the same digest as hashing the same string. */
    private static void testFileDigestMatchesStringDigestForSameContent() {
        String testName = "hashFileBySHA2_256 matches hashBySHA2_256 for the same content";
        String content = "Hello, J2OS!";
        try {
            Path tempFile = Files.createTempFile("crypto-test-digest", ".txt");
            try {
                Files.writeString(tempFile, content);
                String fileDigest = Cryptography.hashFileBySHA2_256(tempFile.toString());
                String stringDigest = Cryptography.hashBySHA2_256(content);
                assertTrue(testName, fileDigest.equals(stringDigest));
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            fail(testName + " [unexpected IOException: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // Password hashing
    // ------------------------------------------------------------------

    /** Verifies a BCrypt hash accepts the correct password and rejects a wrong one. */
    private static void testBCryptHashAndCheckRoundTrip() {
        String testName = "BCrypt hash/check accepts the correct password and rejects a wrong one";
        String hash = Cryptography.hashByBCrypt("correct horse battery staple");
        assertTrue(testName,
                Cryptography.checkByBCrypt("correct horse battery staple", hash)
                        && !Cryptography.checkByBCrypt("wrong password", hash));
    }

    /** Verifies an Argon2id hash accepts the correct password and rejects a wrong one. */
    private static void testArgon2HashAndCheckRoundTrip() {
        String testName = "Argon2id hash/check accepts the correct password and rejects a wrong one";
        String hash = Cryptography.hashByArgon2("correct horse battery staple");
        assertTrue(testName,
                Cryptography.checkByArgon2("correct horse battery staple", hash)
                        && !Cryptography.checkByArgon2("wrong password", hash));
    }

    /** Verifies checkByArgon2 rejects a malformed hash string. */
    private static void testArgon2CheckRejectsMalformedHash() {
        String testName = "checkByArgon2 rejects a malformed hash string";
        try {
            Cryptography.checkByArgon2("password", "not-a-valid-argon2-hash");
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /** Verifies a PBKDF2 hash accepts the correct password and rejects a wrong one. */
    private static void testPBKDF2HashAndCheckRoundTrip() {
        String testName = "PBKDF2 hash/check accepts the correct password and rejects a wrong one";
        String hash = Cryptography.hashByPBKDF2("correct horse battery staple");
        assertTrue(testName,
                Cryptography.checkByPBKDF2("correct horse battery staple", hash)
                        && !Cryptography.checkByPBKDF2("wrong password", hash));
    }

    /** Verifies checkByPBKDF2 rejects a malformed hash string. */
    private static void testPBKDF2CheckRejectsMalformedHash() {
        String testName = "checkByPBKDF2 rejects a malformed hash string";
        try {
            Cryptography.checkByPBKDF2("password", "not-a-valid-pbkdf2-hash");
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    // ------------------------------------------------------------------
    // AES — string
    // ------------------------------------------------------------------

    /** Verifies key-based AES string encryption round-trips, and that the wrong key fails to decrypt. */
    private static void testAesStringKeyBasedRoundTripAndWrongKeyFails() {
        String testName = "AES string (key-based) round-trips and rejects the wrong key";
        String message = "The secret plan is at midnight.";
        SecretKey key = Cryptography.generateAESKey(256);
        SecretKey wrongKey = Cryptography.generateAESKey(256);

        String encrypted = Cryptography.encryptStringByAES(message, key);
        String decrypted = Cryptography.decryptStringByAES(encrypted, key);

        boolean wrongKeyFails;
        try {
            Cryptography.decryptStringByAES(encrypted, wrongKey);
            wrongKeyFails = false;
        } catch (IllegalStateException expected) {
            wrongKeyFails = true;
        }

        assertTrue(testName, message.equals(decrypted) && wrongKeyFails);
    }

    /** Verifies password-based AES string encryption round-trips, and that the wrong password fails to decrypt. */
    private static void testAesStringPasswordBasedRoundTripAndWrongPasswordFails() {
        String testName = "AES string (password-based) round-trips and rejects the wrong password";
        String message = "The secret plan is at midnight.";
        String password = "a strong password";

        String encrypted = Cryptography.encryptStringByAES(message, password);
        String decrypted = Cryptography.decryptStringByAES(encrypted, password);

        boolean wrongPasswordFails;
        try {
            Cryptography.decryptStringByAES(encrypted, "wrong password");
            wrongPasswordFails = false;
        } catch (IllegalStateException expected) {
            wrongPasswordFails = true;
        }

        assertTrue(testName, message.equals(decrypted) && wrongPasswordFails);
    }

    /** Verifies generateAESKey rejects a bit size other than 128/192/256. */
    private static void testGenerateAESKeyRejectsInvalidBitSize() {
        String testName = "generateAESKey rejects a bit size other than 128/192/256";
        try {
            Cryptography.generateAESKey(100);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    // ------------------------------------------------------------------
    // AES — file
    // ------------------------------------------------------------------

    /** Verifies key-based AES file encryption/decryption round-trips in place, preserving content. */
    private static void testAesFileKeyBasedRoundTrip() {
        String testName = "AES file (key-based) round-trips in place, preserving content";
        String content = "Contents that must stay confidential.";
        try {
            Path file = Files.createTempFile("crypto-test-aes-key", ".txt");
            try {
                Files.writeString(file, content);
                SecretKey key = Cryptography.generateAESKey(256);

                Cryptography.encryptFileByAES(file.toString(), key);
                boolean fileChangedAfterEncryption = !content.equals(Files.readString(file));

                Cryptography.decryptFileByAES(file.toString(), key);
                boolean contentRestored = content.equals(Files.readString(file));

                assertTrue(testName, fileChangedAfterEncryption && contentRestored);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            fail(testName + " [unexpected IOException: " + e + "]");
        }
    }

    /** Verifies password-based AES file encryption/decryption round-trips in place, preserving content. */
    private static void testAesFilePasswordBasedRoundTrip() {
        String testName = "AES file (password-based) round-trips in place, preserving content";
        String content = "Contents that must stay confidential.";
        try {
            Path file = Files.createTempFile("crypto-test-aes-password", ".txt");
            try {
                Files.writeString(file, content);
                String password = "a strong password";

                Cryptography.encryptFileByAES(file.toString(), password);
                boolean fileChangedAfterEncryption = !content.equals(Files.readString(file));

                Cryptography.decryptFileByAES(file.toString(), password);
                boolean contentRestored = content.equals(Files.readString(file));

                assertTrue(testName, fileChangedAfterEncryption && contentRestored);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            fail(testName + " [unexpected IOException: " + e + "]");
        }
    }

    /** Verifies decrypting a key-based-encrypted file with the password-based method (no key) fails clearly. */
    private static void testAesFileDecryptWithWrongModeThrows() {
        String testName = "Decrypting a key-based file via the password-based method fails clearly";
        try {
            Path file = Files.createTempFile("crypto-test-aes-wrong-mode", ".txt");
            try {
                Files.writeString(file, "content");
                SecretKey key = Cryptography.generateAESKey(256);
                Cryptography.encryptFileByAES(file.toString(), key);

                try {
                    Cryptography.decryptFileByAES(file.toString(), "some-password");
                    fail(testName + " [expected IllegalArgumentException]");
                } catch (IllegalArgumentException expected) {
                    pass(testName);
                }
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            fail(testName + " [unexpected IOException: " + e + "]");
        }
    }

    // ------------------------------------------------------------------
    // RSA
    // ------------------------------------------------------------------

    /** Verifies generateRSAKeys rejects a key size below the minimum. */
    private static void testGenerateRSAKeysRejectsSmallKeySize() {
        String testName = "generateRSAKeys rejects a key size below the minimum";
        try {
            Cryptography.generateRSAKeys(1024);
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    /** Verifies RSA encrypt/decrypt round-trips a short payload. */
    private static void testRsaRoundTrip() {
        String testName = "RSA encrypt/decrypt round-trips a short payload";
        KeyPair keyPair = Cryptography.generateRSAKeys(2048);
        String secret = "AES-key-sized-secret";

        byte[] encrypted = Cryptography.encryptStringByRSA(secret, keyPair.getPublic());
        String decrypted = Cryptography.decryptStringByRSA(encrypted, keyPair.getPrivate());

        assertTrue(testName, secret.equals(decrypted));
    }

    /** Verifies RSA encryption rejects plaintext too large for the key size. */
    private static void testRsaEncryptRejectsTooLargePlaintext() {
        String testName = "RSA encryption rejects plaintext too large for a 2048-bit key";
        KeyPair keyPair = Cryptography.generateRSAKeys(2048);
        String tooLarge = "x".repeat(500);

        try {
            Cryptography.encryptStringByRSA(tooLarge, keyPair.getPublic());
            fail(testName + " [expected IllegalArgumentException]");
        } catch (IllegalArgumentException expected) {
            pass(testName);
        }
    }

    // ------------------------------------------------------------------
    // Random utilities
    // ------------------------------------------------------------------

    /** Verifies randomInt validates its bounds and stays within the requested range. */
    private static void testRandomIntValidatesBoundsAndStaysInRange() {
        String testName = "randomInt validates its bounds and stays within range";
        boolean maximumValidated = false;
        boolean rangeValidated = false;
        try {
            Cryptography.randomInt(0);
        } catch (IllegalArgumentException expected) {
            maximumValidated = true;
        }
        try {
            Cryptography.randomInt(10, 10);
        } catch (IllegalArgumentException expected) {
            rangeValidated = true;
        }

        boolean staysInRange = true;
        for (int i = 0; i < 100; i++) {
            int value = Cryptography.randomInt(10, 20);
            staysInRange &= value >= 10 && value < 20;
        }

        assertTrue(testName, maximumValidated && rangeValidated && staysInRange);
    }

    /** Verifies randomDouble validates its bounds and stays within the requested range. */
    private static void testRandomDoubleValidatesBoundsAndStaysInRange() {
        String testName = "randomDouble validates its bounds and stays within range";
        boolean rangeValidated = false;
        try {
            Cryptography.randomDouble(5.0, 5.0);
        } catch (IllegalArgumentException expected) {
            rangeValidated = true;
        }

        boolean staysInRange = true;
        for (int i = 0; i < 100; i++) {
            double value = Cryptography.randomDouble(1.0, 2.0);
            staysInRange &= value >= 1.0 && value < 2.0;
        }
        boolean unitIntervalRespected = true;
        for (int i = 0; i < 100; i++) {
            double value = Cryptography.randomDouble();
            unitIntervalRespected &= value >= 0.0 && value < 1.0;
        }

        assertTrue(testName, rangeValidated && staysInRange && unitIntervalRespected);
    }

    /** Verifies the random string methods respect the requested length and character set. */
    private static void testRandomStringsRespectLengthAndCharset() {
        String testName = "randomAlphaString/randomAlphaNumericString respect length and charset";
        String alpha = Cryptography.randomAlphaString(20);
        String alphaNumeric = Cryptography.randomAlphaNumericString(20);

        boolean lengthsCorrect = alpha.length() == 20 && alphaNumeric.length() == 20;
        boolean alphaIsLettersOnly = alpha.matches("[A-Za-z]+");
        boolean alphaNumericIsValid = alphaNumeric.matches("[A-Za-z0-9]+");

        boolean lengthValidated = false;
        try {
            Cryptography.randomAlphaString(0);
        } catch (IllegalArgumentException expected) {
            lengthValidated = true;
        }

        assertTrue(testName, lengthsCorrect && alphaIsLettersOnly && alphaNumericIsValid && lengthValidated);
    }

    /** Verifies uuid() returns a canonically formatted UUID string. */
    private static void testUuidHasCanonicalFormat() {
        String testName = "uuid() returns a canonically formatted UUID string";
        assertTrue(testName, UUID_PATTERN.matcher(Cryptography.uuid()).matches());
    }

    // ------------------------------------------------------------------
    // Null argument validation
    // ------------------------------------------------------------------

    /** Verifies a representative sample of methods reject null arguments with IllegalArgumentException. */
    private static void testNullArgumentsAreRejected() {
        String testName = "Representative methods reject null arguments";
        boolean base64Rejected = threw(IllegalArgumentException.class, () -> Cryptography.encodeBase64(null));
        boolean md5Rejected = threw(IllegalArgumentException.class, () -> Cryptography.hashByMD5(null));
        boolean aesKeyRejected = threw(IllegalArgumentException.class,
                () -> Cryptography.encryptStringByAES(null, Cryptography.generateAESKey(256)));

        assertTrue(testName, base64Rejected && md5Rejected && aesKeyRejected);
    }

    // ------------------------------------------------------------------
    // Heavy concurrency stress tests
    // ------------------------------------------------------------------

    /**
     * Heavy concurrency stress test for password hashing: many threads simultaneously hash and
     * verify passwords with BCrypt, Argon2id, and PBKDF2 - all three algorithms interleaved
     * within every iteration, across every thread, sharing whatever static state {@link
     * Cryptography} holds (its shared {@code SecureRandom} instance in particular), for
     * thousands of operations. Verifies every hash a thread produces still verifies correctly
     * against the exact password it was hashed from, and rejects a wrong password, no matter
     * how the three algorithms interleave across threads.
     */
    private static void testConcurrentPasswordHashingStressCheck() {
        String testName = "Concurrent BCrypt/Argon2/PBKDF2 hashing and verification never cross-contaminate or misfire";
        int threadCount = 24;
        int iterationsPerThread = 150;

        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicInteger verificationFailureCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String password = "password-" + threadIndex + "-" + i;
                        String wrongPassword = "wrong-" + threadIndex + "-" + i;

                        String bcryptHash = Cryptography.hashByBCrypt(password);
                        String argon2Hash = Cryptography.hashByArgon2(password);
                        String pbkdf2Hash = Cryptography.hashByPBKDF2(password);

                        boolean allCorrect =
                                Cryptography.checkByBCrypt(password, bcryptHash) && !Cryptography.checkByBCrypt(wrongPassword, bcryptHash)
                                        && Cryptography.checkByArgon2(password, argon2Hash) && !Cryptography.checkByArgon2(wrongPassword, argon2Hash)
                                        && Cryptography.checkByPBKDF2(password, pbkdf2Hash) && !Cryptography.checkByPBKDF2(wrongPassword, pbkdf2Hash);

                        if (!allCorrect) {
                            verificationFailureCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        threads.forEach(Thread::start);
        startLatch.countDown();

        boolean finishedInTime = true;
        for (Thread thread : threads) {
            try {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finishedInTime = false;
            }
        }

        assertTrue(testName, finishedInTime && unexpectedErrorCount.get() == 0 && verificationFailureCount.get() == 0);
    }

    /**
     * Heavy concurrency stress test for AES file encryption: many threads simultaneously
     * encrypt-then-decrypt their own temp file in place (half the threads key-based, half
     * password-based), for many files each, exercising the shared, uuid()-suffixed temp-sibling
     * naming scheme and atomic-rename logic under real concurrent I/O load. Verifies every
     * file's content round-trips back to exactly what was written, and that no thread's content
     * ever ends up in a different file (each file's content embeds a thread- and
     * iteration-specific marker, checked after decryption).
     */
    private static void testConcurrentAesFileEncryptionStressCheck() {
        String testName = "Concurrent AES file encryption round-trips correctly with no cross-contamination";
        int threadCount = 20;
        int iterationsPerThread = 25;

        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicInteger contentMismatchCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            boolean useKeyBased = threadIndex % 2 == 0;
            threads.add(new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        String content = "thread-" + threadIndex + "-iteration-" + i + "-confidential-payload";
                        Path file = Files.createTempFile("crypto-stress-aes-" + threadIndex + "-" + i, ".txt");
                        try {
                            Files.writeString(file, content);

                            if (useKeyBased) {
                                SecretKey key = Cryptography.generateAESKey(256);
                                Cryptography.encryptFileByAES(file.toString(), key);
                                Cryptography.decryptFileByAES(file.toString(), key);
                            } else {
                                String password = "password-" + threadIndex + "-" + i;
                                Cryptography.encryptFileByAES(file.toString(), password);
                                Cryptography.decryptFileByAES(file.toString(), password);
                            }

                            if (!content.equals(Files.readString(file))) {
                                contentMismatchCount.incrementAndGet();
                            }
                        } finally {
                            Files.deleteIfExists(file);
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Exception unexpected) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        threads.forEach(Thread::start);
        startLatch.countDown();

        boolean finishedInTime = true;
        for (Thread thread : threads) {
            try {
                thread.join(60_000);
                if (thread.isAlive()) {
                    finishedInTime = false;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                finishedInTime = false;
            }
        }

        assertTrue(testName, finishedInTime && unexpectedErrorCount.get() == 0 && contentMismatchCount.get() == 0);
    }

    // ------------------------------------------------------------------
    // Minimal assertion helpers (no external test framework)
    // ------------------------------------------------------------------

    /** A no-argument, possibly-throwing action, used by {@link #threw}. */
    @FunctionalInterface
    private interface ThrowingAction {
        /**
         * Runs the action.
         *
         * @throws Exception if the action fails
         */
        void run() throws Exception;
    }

    /**
     * Runs an action and checks whether it throws an instance of the given exception type.
     *
     * @param expectedType the exception type expected to be thrown
     * @param action       the action to run
     * @return true if the action threw an instance of {@code expectedType}
     */
    private static boolean threw(Class<? extends Exception> expectedType, ThrowingAction action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return expectedType.isInstance(e);
        }
    }

    /**
     * Records a passing test case if {@code condition} is true, otherwise records a failure.
     *
     * @param testName  the name of the test case, printed in the report
     * @param condition the condition that must be true for the test to pass
     */
    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            pass(testName);
        } else {
            fail(testName);
        }
    }

    /**
     * Records and prints a passing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void pass(String testName) {
        totalTestCount++;
        System.out.println("[PASS] " + testName);
    }

    /**
     * Records and prints a failing test case.
     *
     * @param testName the name of the test case, printed in the report
     */
    private static void fail(String testName) {
        totalTestCount++;
        failedTestCount++;
        System.out.println("[FAIL] " + testName);
    }

    /** Prints a final pass/fail summary of the whole suite. */
    private static void printSummary() {
        int passedTestCount = totalTestCount - failedTestCount;
        System.out.println();
        System.out.println("==============================================");
        System.out.println("Total: " + totalTestCount + "  Passed: " + passedTestCount + "  Failed: " + failedTestCount);
        System.out.println(failedTestCount == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.out.println("==============================================");
    }
}