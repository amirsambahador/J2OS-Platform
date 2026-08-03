package org.j2os.platform.jsecurity.cryptography;

import lombok.experimental.UtilityClass;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.bouncycastle.crypto.params.Argon2Parameters;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * General-purpose cryptography utility class: Base64 encoding, simple and file digests,
 * password hashing (BCrypt, Argon2id, PBKDF2), AES-GCM encryption (of strings and files, either
 * key-based or password-based), RSA-OAEP encryption, and a handful of {@link SecureRandom}-backed
 * random-value helpers.
 * <p>
 * The public method set matches the contract documented in {@link CryptographySpec}, though this
 * class does not formally {@code implements} it.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>{@link #hashByMD5(String)} and {@link #hashFileByMD5(String)} are provided only for
 *       legacy compatibility (e.g. matching existing MD5 checksums) — MD5 is not
 *       collision-resistant and must not be used for anything security-sensitive, including
 *       password storage.</li>
 *   <li>Password hashing ({@link #hashByBCrypt}, {@link #hashByArgon2}, {@link #hashByPBKDF2})
 *       and password-based key derivation ({@link #encryptStringByAES(String, String)} and its
 *       file/decrypt counterparts) are separate concerns with separate tuning: BCrypt/Argon2/PBKDF2
 *       are for verifying a password against a stored hash, while the password-based AES
 *       overloads derive a symmetric key from a password via PBKDF2 for encryption — they are
 *       not interchangeable, and a hash produced by one cannot be verified by another.</li>
 *   <li>All fixed cryptographic parameters (AES key length, PBKDF2 iteration count, BCrypt cost,
 *       Argon2 memory/iterations/parallelism, RSA minimum key size) are hardcoded class
 *       constants, not configurable per call.</li>
 *   <li>{@link #encryptFileByAES(String, SecretKey)} and its overloads encrypt/decrypt a file
 *       <b>in place</b> (the original file is overwritten with an atomic rename from a temp
 *       file) — there is no non-destructive, "encrypt to a new file" option.</li>
 *   <li>RSA (see {@link #encryptStringByRSA}) is only suitable for small payloads (its own
 *       key size sets a hard ceiling on the plaintext it can encrypt in one call) — it is meant
 *       for wrapping a symmetric key or other short secret, not for encrypting arbitrary-sized
 *       data; use AES for that instead.</li>
 *   <li>{@link #hashByBCrypt} and {@link #checkByBCrypt} pre-hash the password with SHA-256
 *       before handing it to BCrypt, to avoid BCrypt's silent 72-byte input truncation. This
 *       means hashes produced by this class are <b>not</b> directly interoperable with a raw
 *       BCrypt implementation elsewhere (e.g. another language's bcrypt library) unless that
 *       implementation applies the same pre-hash.</li>
 *   <li>No digital signature support (signing/verifying) is provided by this class.</li>
 *   <li>{@link #checkByArgon2} and {@link #checkByPBKDF2} are meant to verify hashes produced by
 *       this same class ({@link #hashByArgon2}/{@link #hashByPBKDF2}); their parsers expect the
 *       fixed parameter order this class itself writes, and will reject a hash string whose
 *       embedded cost parameters (memory/iterations/parallelism, as applicable) or hash length
 *       exceed a hardcoded safety ceiling — a defense against a maliciously crafted hash string
 *       driving excessive CPU/memory use, not a general-purpose parser hardened against every
 *       possible malformed or adversarial input.</li>
 * </ul>
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
@UtilityClass
public class Cryptography {

    /**
     * Length, in bits, of AES keys derived from a password.
     */
    private static final int AES_KEY_LENGTH_BITS = 256;

    /**
     * Length, in bytes, of the random GCM initialization vector used for every AES-GCM operation.
     */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /**
     * Length, in bits, of the GCM authentication tag appended to AES-GCM ciphertext.
     */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /**
     * Length, in bytes, of the random salt used for password hashing and password-based key derivation.
     */
    private static final int SALT_LENGTH_BYTES = 16;

    /**
     * Number of PBKDF2 iterations used both for {@link #hashByPBKDF2} and for password-based AES key derivation.
     */
    private static final int PBKDF2_ITERATIONS = 210_000;

    /**
     * JCE algorithm name used for PBKDF2.
     */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * BCrypt work factor (cost) used by {@link #hashByBCrypt}.
     */
    private static final int BCRYPT_COST = 12;

    /**
     * Number of passes used by {@link #hashByArgon2}.
     */
    private static final int ARGON2_ITERATIONS = 3;

    /**
     * Memory cost, in KB, used by {@link #hashByArgon2}.
     */
    private static final int ARGON2_MEMORY_KB = 65536; // 64 MB

    /**
     * Degree of parallelism used by {@link #hashByArgon2}.
     */
    private static final int ARGON2_PARALLELISM = 1;

    /**
     * Length, in bytes, of the raw hash produced by {@link #hashByArgon2}.
     */
    private static final int ARGON2_HASH_LENGTH_BYTES = 32;

    /**
     * Upper bound, in KB, on the memory cost {@link #checkByArgon2} will honor when re-deriving
     * a hash from parameters embedded in an externally supplied Argon2 hash string. Guards
     * against a maliciously crafted hash string driving this process to exhaust memory or CPU.
     */
    private static final int ARGON2_VERIFY_MAX_MEMORY_KB = 262_144; // 256 MB

    /**
     * Upper bound on the iteration count {@link #checkByArgon2} will honor when re-deriving a
     * hash from parameters embedded in an externally supplied Argon2 hash string.
     */
    private static final int ARGON2_VERIFY_MAX_ITERATIONS = 10;

    /**
     * Upper bound on the parallelism {@link #checkByArgon2} will honor when re-deriving a hash
     * from parameters embedded in an externally supplied Argon2 hash string.
     */
    private static final int ARGON2_VERIFY_MAX_PARALLELISM = 8;

    /**
     * Upper bound, in bytes, on the hash length {@link #checkByArgon2} will honor, derived from
     * the length of the (attacker-controlled) expected-hash segment of an externally supplied
     * Argon2 hash string. Guards against an oversized hash segment driving excessive memory use
     * before the memory/iterations/parallelism checks even run.
     */
    private static final int ARGON2_VERIFY_MAX_HASH_LENGTH_BYTES = 128;

    /**
     * Upper bound on the iteration count {@link #checkByPBKDF2} will honor when re-deriving a
     * hash from parameters embedded in an externally supplied PBKDF2 hash string. Guards against
     * a maliciously crafted hash string driving this process to spend excessive CPU time.
     */
    private static final int PBKDF2_VERIFY_MAX_ITERATIONS = 1_000_000;

    /**
     * Upper bound, in bytes, on the hash length {@link #checkByPBKDF2} will honor, derived from
     * the length of the (attacker-controlled) expected-hash segment of an externally supplied
     * PBKDF2 hash string. Guards against an oversized hash segment driving excessive memory use.
     */
    private static final int PBKDF2_VERIFY_MAX_HASH_LENGTH_BYTES = 64; // matches SHA-512 output size

    /**
     * JCE transformation string used for RSA encryption/decryption.
     */
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * Minimum RSA key size, in bits, accepted by {@link #generateRSAKeys(int)}.
     */
    private static final int RSA_MIN_KEY_SIZE_BITS = 2048;

    /**
     * Leading byte written to an AES-encrypted file when it was encrypted with a directly supplied {@link SecretKey}.
     */
    private static final byte AES_FLAG_KEY_BASED = 0;

    /**
     * Leading byte written to an AES-encrypted file when it was encrypted with a password (and thus has a stored salt).
     */
    private static final byte AES_FLAG_PASSWORD_BASED = 1;

    /**
     * Alphabet used by {@link #randomAlphaString(int)}.
     */
    private static final char[] ALPHA_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /**
     * Alphabet used by {@link #randomAlphaNumericString(int)}.
     */
    private static final char[] ALPHANUMERIC_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /**
     * Shared {@link SecureRandom} instance backing every random-value method in this class.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================================================================
    // Base64
    // ==================================================================

    /**
     * Encodes bytes as standard Base64.
     *
     * @param data the bytes to encode
     * @return the Base64-encoded string
     * @throws IllegalArgumentException if {@code data} is null
     */
    public static String encodeBase64(byte[] data) {
        requireNonNull(data, "data");
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a standard Base64 string.
     *
     * @param base64 the Base64 string to decode
     * @return the decoded bytes
     * @throws IllegalArgumentException if {@code base64} is null, or is not valid Base64
     */
    public static byte[] decodeBase64(String base64) {
        requireNonNull(base64, "base64");
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Encodes bytes as URL-safe Base64.
     *
     * @param data the bytes to encode
     * @return the URL-safe Base64-encoded string
     * @throws IllegalArgumentException if {@code data} is null
     */
    public static String encodeBase64URL(byte[] data) {
        requireNonNull(data, "data");
        return Base64.getUrlEncoder().encodeToString(data);
    }

    /**
     * Decodes a URL-safe Base64 string.
     *
     * @param base64Url the URL-safe Base64 string to decode
     * @return the decoded bytes
     * @throws IllegalArgumentException if {@code base64Url} is null, or is not valid URL-safe Base64
     */
    public static byte[] decodeBase64URL(String base64Url) {
        requireNonNull(base64Url, "base64Url");
        return Base64.getUrlDecoder().decode(base64Url);
    }

    // ==================================================================
    // Simple hashes (digest) — MD5 kept only for legacy compatibility
    // ==================================================================

    /**
     * Computes the MD5 digest of a string, as lowercase hex.
     * <p>
     * Provided only for legacy compatibility; MD5 is not secure and must not be used for
     * anything security-sensitive.
     *
     * @param plainText the text to hash
     * @return the hex-encoded MD5 digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashByMD5(String plainText) {
        return digestToHex(plainText, "MD5");
    }

    /**
     * Computes the SHA-256 digest of a string, as lowercase hex.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA-256 digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashBySHA2_256(String plainText) {
        return digestToHex(plainText, "SHA-256");
    }

    /**
     * Computes the SHA-512 digest of a string, as lowercase hex.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA-512 digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashBySHA2_512(String plainText) {
        return digestToHex(plainText, "SHA-512");
    }

    /**
     * Computes the SHA3-256 digest of a string, as lowercase hex.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA3-256 digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashBySHA3_256(String plainText) {
        return digestToHex(plainText, "SHA3-256");
    }

    /**
     * Computes the SHA3-512 digest of a string, as lowercase hex.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA3-512 digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashBySHA3_512(String plainText) {
        return digestToHex(plainText, "SHA3-512");
    }

    /**
     * Computes a message digest of a string's UTF-8 bytes, as lowercase hex.
     *
     * @param plainText the text to hash
     * @param algorithm the {@link MessageDigest} algorithm name to use
     * @return the hex-encoded digest
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    private static String digestToHex(String plainText, String algorithm) {
        requireNonNull(plainText, "plainText");
        MessageDigest digest = newDigest(algorithm);
        byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    // ==================================================================
    // File hashing — streaming, without loading the whole file into memory
    // ==================================================================

    /**
     * Computes the MD5 digest of a file's contents, as lowercase hex, streaming the file rather
     * than loading it entirely into memory.
     * <p>
     * Provided only for legacy compatibility; MD5 is not secure.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded MD5 digest
     * @throws IllegalArgumentException if {@code filePath} is null
     * @throws UncheckedIOException     if reading the file fails
     */
    public static String hashFileByMD5(String filePath) {
        return digestFileToHex(filePath, "MD5");
    }

    /**
     * Computes the SHA-256 digest of a file's contents, as lowercase hex, streaming the file
     * rather than loading it entirely into memory.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded SHA-256 digest
     * @throws IllegalArgumentException if {@code filePath} is null
     * @throws UncheckedIOException     if reading the file fails
     */
    public static String hashFileBySHA2_256(String filePath) {
        return digestFileToHex(filePath, "SHA-256");
    }

    /**
     * Computes the SHA-512 digest of a file's contents, as lowercase hex, streaming the file
     * rather than loading it entirely into memory.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded SHA-512 digest
     * @throws IllegalArgumentException if {@code filePath} is null
     * @throws UncheckedIOException     if reading the file fails
     */
    public static String hashFileBySHA2_512(String filePath) {
        return digestFileToHex(filePath, "SHA-512");
    }

    /**
     * Streams a file through a {@link MessageDigest} and returns its hex-encoded digest.
     *
     * @param filePath  the path of the file to hash
     * @param algorithm the {@link MessageDigest} algorithm name to use
     * @return the hex-encoded digest
     * @throws IllegalArgumentException if {@code filePath} is null
     * @throws UncheckedIOException     if reading the file fails
     */
    private static String digestFileToHex(String filePath, String algorithm) {
        requireNonNull(filePath, "filePath");
        MessageDigest digest = newDigest(algorithm);
        try (InputStream in = Files.newInputStream(Paths.get(filePath));
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading file: " + filePath, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Resolves a {@link MessageDigest} instance for the given algorithm.
     *
     * @param algorithm the algorithm name to resolve
     * @return a new digest instance
     * @throws IllegalStateException if the algorithm is not available in this JVM
     */
    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm not available: " + algorithm, e);
        }
    }

    // ==================================================================
    // BCrypt
    // ==================================================================

    /**
     * Hashes a password with BCrypt, using a freshly generated random salt.
     * <p>
     * The password is first pre-hashed with SHA-256 (see {@link #bcryptPrehash(String)}) before
     * being passed to BCrypt, because the underlying Blowfish-based algorithm only consumes the
     * first 72 bytes of its input and silently ignores anything beyond that — without this
     * pre-hash, two different passwords sharing the same first 72 bytes would produce the same
     * hash.
     *
     * @param plainText the password to hash
     * @return the BCrypt hash string, in OpenBSD/{@code $2$}-style format (embeds the salt and cost)
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashByBCrypt(String plainText) {
        requireNonNull(plainText, "plainText");
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        char[] password = bcryptPrehash(plainText);
        try {
            return OpenBSDBCrypt.generate(password, salt, BCRYPT_COST);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies a password against a previously computed BCrypt hash.
     * <p>
     * Applies the same SHA-256 pre-hash as {@link #hashByBCrypt(String)} before delegating to
     * BCrypt, so it correctly verifies hashes produced by that method.
     *
     * @param plainText  the password to check
     * @param bcryptHash the BCrypt hash to check it against
     * @return true if the password matches the hash
     * @throws IllegalArgumentException if either argument is null, or if {@code bcryptHash} is not a valid BCrypt hash
     */
    public static boolean checkByBCrypt(String plainText, String bcryptHash) {
        requireNonNull(plainText, "plainText");
        requireNonNull(bcryptHash, "bcryptHash");
        char[] password = bcryptPrehash(plainText);
        try {
            return OpenBSDBCrypt.checkPassword(bcryptHash, password);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid BCrypt hash format", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Pre-hashes a password with SHA-256 and Base64-encodes the digest (without padding) into a
     * fixed-length, 43-character string, so that the value actually handed to BCrypt is always
     * well under its 72-byte input limit — preserving the full entropy of arbitrarily long
     * passwords instead of silently truncating them.
     *
     * @param plainText the password to pre-hash
     * @return the Base64-encoded SHA-256 digest, as a char array
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    private static char[] bcryptPrehash(String plainText) {
        requireNonNull(plainText, "plainText");
        byte[] hash = newDigest("SHA-256").digest(plainText.getBytes(StandardCharsets.UTF_8));
        try {
            return Base64.getEncoder().withoutPadding().encodeToString(hash).toCharArray();
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    // ==================================================================
    // Argon2id — output string format follows the PHC string format standard
    // ==================================================================

    /**
     * Hashes a password with Argon2id, using a freshly generated random salt, and returns it
     * formatted as a self-describing PHC-format string (embeds the algorithm, version, cost
     * parameters, salt, and hash).
     *
     * @param plainText the password to hash
     * @return the Argon2id hash, as a {@code $argon2id$...} PHC-format string
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashByArgon2(String plainText) {
        requireNonNull(plainText, "plainText");
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        byte[] hash = argon2Raw(plainText, salt, ARGON2_ITERATIONS, ARGON2_MEMORY_KB,
                ARGON2_PARALLELISM, ARGON2_HASH_LENGTH_BYTES);
        try {
            Base64.Encoder enc = Base64.getEncoder().withoutPadding();
            return "$argon2id$v=19$m=" + ARGON2_MEMORY_KB + ",t=" + ARGON2_ITERATIONS + ",p=" + ARGON2_PARALLELISM
                    + "$" + enc.encodeToString(salt)
                    + "$" + enc.encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    /**
     * Verifies a password against a previously computed Argon2id PHC-format hash string,
     * re-deriving the hash with the parameters embedded in the string itself.
     *
     * @param plainText  the password to check
     * @param argon2Hash the Argon2id PHC-format hash string to check it against
     * @return true if the password matches the hash
     * @throws IllegalArgumentException if either argument is null, if {@code argon2Hash} is not a
     *                                   valid Argon2id PHC-format string, or if its embedded cost
     *                                   parameters or hash length exceed this method's safety
     *                                   ceilings (guards against a maliciously crafted hash string
     *                                   causing excessive memory/CPU use)
     */
    public static boolean checkByArgon2(String plainText, String argon2Hash) {
        requireNonNull(plainText, "plainText");
        requireNonNull(argon2Hash, "argon2Hash");

        String[] parts = argon2Hash.split("\\$");
        if (parts.length != 6 || !"argon2id".equals(parts[1])) {
            throw new IllegalArgumentException("Invalid Argon2 hash format");
        }

        int memory;
        int iterations;
        int parallelism;
        byte[] salt;
        byte[] expectedHash;
        try {
            String[] params = parts[3].split(",");
            memory = Integer.parseInt(params[0].substring(2));
            iterations = Integer.parseInt(params[1].substring(2));
            parallelism = Integer.parseInt(params[2].substring(2));
            salt = Base64.getDecoder().decode(parts[4]);
            expectedHash = Base64.getDecoder().decode(parts[5]);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Argon2 hash format", e);
        }

        if (memory <= 0 || memory > ARGON2_VERIFY_MAX_MEMORY_KB
                || iterations <= 0 || iterations > ARGON2_VERIFY_MAX_ITERATIONS
                || parallelism <= 0 || parallelism > ARGON2_VERIFY_MAX_PARALLELISM
                || expectedHash.length <= 0 || expectedHash.length > ARGON2_VERIFY_MAX_HASH_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "Argon2 hash cost parameters exceed the allowed safety ceilings "
                            + "(memory<=" + ARGON2_VERIFY_MAX_MEMORY_KB + "KB, iterations<=" + ARGON2_VERIFY_MAX_ITERATIONS
                            + ", parallelism<=" + ARGON2_VERIFY_MAX_PARALLELISM
                            + ", hash length<=" + ARGON2_VERIFY_MAX_HASH_LENGTH_BYTES + " bytes)");
        }

        byte[] actualHash = argon2Raw(plainText, salt, iterations, memory, parallelism, expectedHash.length);
        try {
            return MessageDigest.isEqual(actualHash, expectedHash);
        } finally {
            Arrays.fill(actualHash, (byte) 0);
            Arrays.fill(expectedHash, (byte) 0);
        }
    }

    /**
     * Computes a raw Argon2id hash with the given parameters.
     *
     * @param plainText   the password to hash
     * @param salt        the salt to use
     * @param iterations  the number of passes
     * @param memoryKb    the memory cost, in KB
     * @param parallelism the degree of parallelism
     * @param hashLength  the desired raw hash length, in bytes
     * @return the raw hash bytes
     */
    private static byte[] argon2Raw(String plainText, byte[] salt, int iterations, int memoryKb,
                                    int parallelism, int hashLength) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        char[] password = plainText.toCharArray();
        byte[] hash = new byte[hashLength];
        try {
            generator.generateBytes(password, hash);
            return hash;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    // ==================================================================
    // PBKDF2 — both as a standalone hash function and for deriving an AES key
    // ==================================================================

    /**
     * Hashes a password with PBKDF2 (HMAC-SHA256), using a freshly generated random salt, and
     * returns it formatted as a self-describing string (embeds the iteration count, salt, and hash).
     *
     * @param plainText the password to hash
     * @return the PBKDF2 hash, as a {@code $pbkdf2-sha256$...} format string
     * @throws IllegalArgumentException if {@code plainText} is null
     */
    public static String hashByPBKDF2(String plainText) {
        requireNonNull(plainText, "plainText");
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        byte[] hash = pbkdf2Raw(plainText.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        try {
            Base64.Encoder enc = Base64.getEncoder().withoutPadding();
            return "$pbkdf2-sha256$i=" + PBKDF2_ITERATIONS
                    + "$" + enc.encodeToString(salt)
                    + "$" + enc.encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    /**
     * Verifies a password against a previously computed PBKDF2 format hash string, re-deriving
     * the hash with the parameters embedded in the string itself.
     *
     * @param plainText  the password to check
     * @param pbkdf2Hash the PBKDF2 format hash string to check it against
     * @return true if the password matches the hash
     * @throws IllegalArgumentException if either argument is null, if {@code pbkdf2Hash} is not a
     *                                   valid PBKDF2 format string, or if its embedded iteration
     *                                   count or hash length exceed this method's safety
     *                                   ceilings (guards against a maliciously crafted hash
     *                                   string causing excessive CPU/memory use)
     */
    public static boolean checkByPBKDF2(String plainText, String pbkdf2Hash) {
        requireNonNull(plainText, "plainText");
        requireNonNull(pbkdf2Hash, "pbkdf2Hash");

        String[] parts = pbkdf2Hash.split("\\$");
        if (parts.length != 5 || !"pbkdf2-sha256".equals(parts[1])) {
            throw new IllegalArgumentException("Invalid PBKDF2 hash format");
        }

        int iterations;
        byte[] salt;
        byte[] expectedHash;
        try {
            iterations = Integer.parseInt(parts[2].substring(2));
            salt = Base64.getDecoder().decode(parts[3]);
            expectedHash = Base64.getDecoder().decode(parts[4]);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PBKDF2 hash format", e);
        }

        if (iterations <= 0 || iterations > PBKDF2_VERIFY_MAX_ITERATIONS
                || expectedHash.length <= 0 || expectedHash.length > PBKDF2_VERIFY_MAX_HASH_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "PBKDF2 hash parameters exceed the allowed safety ceilings "
                            + "(iterations<=" + PBKDF2_VERIFY_MAX_ITERATIONS
                            + ", hash length<=" + PBKDF2_VERIFY_MAX_HASH_LENGTH_BYTES + " bytes)");
        }

        byte[] actualHash = pbkdf2Raw(plainText.toCharArray(), salt, iterations, expectedHash.length * 8);
        try {
            return MessageDigest.isEqual(actualHash, expectedHash);
        } finally {
            Arrays.fill(actualHash, (byte) 0);
            Arrays.fill(expectedHash, (byte) 0);
        }
    }

    /**
     * Computes raw PBKDF2 (HMAC-SHA256) output for the given parameters.
     *
     * @param password      the password to derive from; cleared to zero before returning
     * @param salt          the salt to use
     * @param iterations    the number of PBKDF2 iterations
     * @param keyLengthBits the desired output length, in bits
     * @return the derived bytes
     * @throws IllegalStateException if PBKDF2 is not available in this JVM
     */
    private static byte[] pbkdf2Raw(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
                return factory.generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Derives an AES key from a password and salt via PBKDF2.
     *
     * @param password the password to derive the key from
     * @param salt     the salt to use
     * @return the derived AES key
     * @throws IllegalArgumentException if {@code password} is null
     */
    private static SecretKey deriveAESKeyFromPassword(String password, byte[] salt) {
        requireNonNull(password, "password");
        byte[] keyBytes = pbkdf2Raw(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_LENGTH_BITS);
        try {
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Generates a fresh random AES key of the given size.
     *
     * @param bits the key size, in bits; must be 128, 192, or 256
     * @return a new random AES key
     * @throws IllegalArgumentException if {@code bits} is not 128, 192, or 256
     */
    public static SecretKey generateAESKey(int bits) {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new IllegalArgumentException("AES key size must be 128, 192, or 256 bits");
        }
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(bits, SECURE_RANDOM);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES key generation not available", e);
        }
    }

    /**
     * Encrypts a string with AES-GCM using the given key, returning the IV and ciphertext
     * concatenated and Base64-encoded.
     *
     * @param plainText the text to encrypt
     * @param key       the AES key to encrypt with
     * @return the Base64-encoded {@code iv || ciphertext}
     * @throws IllegalArgumentException if either argument is null
     */
    public static String encryptStringByAES(String plainText, SecretKey key) {
        requireNonNull(plainText, "plainText");
        requireNonNull(key, "key");
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        byte[] cipherText = aesGcm(Cipher.ENCRYPT_MODE, key, iv, plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(concat(iv, cipherText));
    }

    /**
     * Decrypts a string previously encrypted by {@link #encryptStringByAES(String, SecretKey)}.
     *
     * @param aesBase64Text the Base64-encoded {@code iv || ciphertext} to decrypt
     * @param key           the AES key it was encrypted with
     * @return the decrypted plaintext
     * @throws IllegalArgumentException if either argument is null, or the payload is malformed
     * @throws IllegalStateException    if decryption fails (wrong key or corrupted data)
     */
    public static String decryptStringByAES(String aesBase64Text, SecretKey key) {
        requireNonNull(aesBase64Text, "aesBase64Text");
        requireNonNull(key, "key");
        byte[] all = Base64.getDecoder().decode(aesBase64Text);
        requireMinLength(all, GCM_IV_LENGTH_BYTES, "AES payload");

        byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherText = Arrays.copyOfRange(all, GCM_IV_LENGTH_BYTES, all.length);
        byte[] plain = aesGcm(Cipher.DECRYPT_MODE, key, iv, cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * Encrypts a string with AES-GCM using a key derived from a password (PBKDF2, with a fresh
     * random salt), returning the salt, IV, and ciphertext concatenated and Base64-encoded.
     *
     * @param plainText the text to encrypt
     * @param password  the password to derive the encryption key from
     * @return the Base64-encoded {@code salt || iv || ciphertext}
     * @throws IllegalArgumentException if either argument is null
     */
    public static String encryptStringByAES(String plainText, String password) {
        requireNonNull(plainText, "plainText");
        requireNonNull(password, "password");
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        SecretKey key = deriveAESKeyFromPassword(password, salt);
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        byte[] cipherText = aesGcm(Cipher.ENCRYPT_MODE, key, iv, plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(concat(salt, iv, cipherText));
    }

    /**
     * Decrypts a string previously encrypted by {@link #encryptStringByAES(String, String)},
     * re-deriving the key from the same password and the salt embedded in the payload.
     *
     * @param aesBase64Text the Base64-encoded {@code salt || iv || ciphertext} to decrypt
     * @param password      the password it was encrypted with
     * @return the decrypted plaintext
     * @throws IllegalArgumentException if either argument is null, or the payload is malformed
     * @throws IllegalStateException    if decryption fails (wrong password or corrupted data)
     */
    public static String decryptStringByAES(String aesBase64Text, String password) {
        requireNonNull(aesBase64Text, "aesBase64Text");
        requireNonNull(password, "password");
        byte[] all = Base64.getDecoder().decode(aesBase64Text);
        requireMinLength(all, SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES, "AES payload");

        byte[] salt = Arrays.copyOfRange(all, 0, SALT_LENGTH_BYTES);
        byte[] iv = Arrays.copyOfRange(all, SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES);
        byte[] cipherText = Arrays.copyOfRange(all, SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES, all.length);

        SecretKey key = deriveAESKeyFromPassword(password, salt);
        byte[] plain = aesGcm(Cipher.DECRYPT_MODE, key, iv, cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * Runs AES/GCM/NoPadding in the given mode.
     *
     * @param mode  {@link Cipher#ENCRYPT_MODE} or {@link Cipher#DECRYPT_MODE}
     * @param key   the AES key to use
     * @param iv    the initialization vector to use
     * @param input the bytes to encrypt or decrypt
     * @return the resulting bytes
     * @throws IllegalStateException if the operation fails (e.g. wrong key/corrupted data on decrypt)
     */
    private static byte[] aesGcm(int mode, SecretKey key, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    mode == Cipher.ENCRYPT_MODE
                            ? "AES encryption failed"
                            : "AES decryption failed - wrong key/password or corrupted data",
                    e);
        }
    }

    // ==================================================================
    // AES — file (in-place encryption with atomic write) — two overloads
    // ==================================================================

    /**
     * Encrypts a file in place with AES-GCM using the given key: writes to a sibling temp file,
     * then atomically replaces the original. Equivalent to {@code encryptFileByAES(filePath, key, false)}.
     *
     * @param filePath the path of the file to encrypt
     * @param key      the AES key to encrypt with
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if encryption fails
     */
    public static void encryptFileByAES(String filePath, SecretKey key) {
        encryptFileByAES(filePath, key, false);
    }

    /**
     * Encrypts a file in place with AES-GCM using the given key: writes to a sibling temp file,
     * then atomically replaces the original.
     *
     * @param filePath the path of the file to encrypt
     * @param key      the AES key to encrypt with
     * @param durable  if true, forces the temp file's contents to disk (see {@link #fsync(Path)})
     *                 before the atomic rename, and best-effort fsyncs the containing directory
     *                 afterward, so the encrypted result survives a crash immediately following
     *                 this call — at the cost of extra I/O; if false, behaves exactly like
     *                 {@link #encryptFileByAES(String, SecretKey)}
     * @throws IllegalArgumentException if {@code filePath} or {@code key} is null
     * @throws IllegalStateException    if encryption fails
     */
    public static void encryptFileByAES(String filePath, SecretKey key, boolean durable) {
        requireNonNull(filePath, "filePath");
        requireNonNull(key, "key");
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        streamEncryptFile(filePath, key, iv, AES_FLAG_KEY_BASED, null, durable);
    }

    /**
     * Decrypts a file in place, previously encrypted by {@link #encryptFileByAES(String, SecretKey)}:
     * writes to a sibling temp file, then atomically replaces the original. Equivalent to
     * {@code decryptFileByAES(filePath, key, false)}.
     *
     * @param filePath the path of the file to decrypt
     * @param key      the AES key it was encrypted with
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if decryption fails (wrong key or corrupted file)
     */
    public static void decryptFileByAES(String filePath, SecretKey key) {
        decryptFileByAES(filePath, key, false);
    }

    /**
     * Decrypts a file in place, previously encrypted by {@link #encryptFileByAES(String, SecretKey)}:
     * writes to a sibling temp file, then atomically replaces the original.
     *
     * @param filePath the path of the file to decrypt
     * @param key      the AES key it was encrypted with
     * @param durable  if true, forces the temp file's contents to disk before the atomic rename
     *                 and best-effort fsyncs the containing directory afterward; if false,
     *                 behaves exactly like {@link #decryptFileByAES(String, SecretKey)}
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if decryption fails (wrong key or corrupted file)
     */
    public static void decryptFileByAES(String filePath, SecretKey key, boolean durable) {
        requireNonNull(filePath, "filePath");
        requireNonNull(key, "key");
        streamDecryptFile(filePath, key, null, durable);
    }

    /**
     * Encrypts a file in place with AES-GCM using a key derived from a password: writes to a
     * sibling temp file, then atomically replaces the original. Equivalent to
     * {@code encryptFileByAES(filePath, password, false)}.
     *
     * @param filePath the path of the file to encrypt
     * @param password the password to derive the encryption key from
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if encryption fails
     */
    public static void encryptFileByAES(String filePath, String password) {
        encryptFileByAES(filePath, password, false);
    }

    /**
     * Encrypts a file in place with AES-GCM using a key derived from a password: writes to a
     * sibling temp file, then atomically replaces the original.
     *
     * @param filePath the path of the file to encrypt
     * @param password the password to derive the encryption key from
     * @param durable  if true, forces the temp file's contents to disk before the atomic rename
     *                 and best-effort fsyncs the containing directory afterward; if false,
     *                 behaves exactly like {@link #encryptFileByAES(String, String)}
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if encryption fails
     */
    public static void encryptFileByAES(String filePath, String password, boolean durable) {
        requireNonNull(filePath, "filePath");
        requireNonNull(password, "password");
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        SecretKey key = deriveAESKeyFromPassword(password, salt);
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        streamEncryptFile(filePath, key, iv, AES_FLAG_PASSWORD_BASED, salt, durable);
    }

    /**
     * Decrypts a file in place, previously encrypted by {@link #encryptFileByAES(String, String)}:
     * writes to a sibling temp file, then atomically replaces the original. Equivalent to
     * {@code decryptFileByAES(filePath, password, false)}.
     *
     * @param filePath the path of the file to decrypt
     * @param password the password it was encrypted with
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if decryption fails (wrong password or corrupted file)
     */
    public static void decryptFileByAES(String filePath, String password) {
        decryptFileByAES(filePath, password, false);
    }

    /**
     * Decrypts a file in place, previously encrypted by {@link #encryptFileByAES(String, String)}:
     * writes to a sibling temp file, then atomically replaces the original.
     *
     * @param filePath the path of the file to decrypt
     * @param password the password it was encrypted with
     * @param durable  if true, forces the temp file's contents to disk before the atomic rename
     *                 and best-effort fsyncs the containing directory afterward; if false,
     *                 behaves exactly like {@link #decryptFileByAES(String, String)}
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if decryption fails (wrong password or corrupted file)
     */
    public static void decryptFileByAES(String filePath, String password, boolean durable) {
        requireNonNull(filePath, "filePath");
        requireNonNull(password, "password");
        streamDecryptFile(filePath, null, password, durable);
    }

    /**
     * Streams a file through AES-GCM encryption into a sibling temp file (prefixed with a
     * key-based/password-based flag byte, an optional salt, and the IV), then atomically
     * replaces the original file with it.
     *
     * @param filePath the path of the file to encrypt
     * @param key      the AES key to encrypt with
     * @param iv       the initialization vector to use
     * @param flag     {@link #AES_FLAG_KEY_BASED} or {@link #AES_FLAG_PASSWORD_BASED}
     * @param salt     the salt to embed if {@code flag} is {@link #AES_FLAG_PASSWORD_BASED}, otherwise unused
     * @param durable  if true, fsync the temp file before the atomic rename and the containing
     *                 directory afterward (see {@link #fsync(Path)})
     * @throws IllegalStateException if reading, encrypting, or writing fails; the temp file is deleted first
     */
    private static void streamEncryptFile(String filePath, SecretKey key, byte[] iv, byte flag, byte[] salt,
                                          boolean durable) {
        Path source = Paths.get(filePath);
        Path temp = tempSibling(source);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            try (InputStream in = Files.newInputStream(source);
                 OutputStream rawOut = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {

                rawOut.write(flag);
                if (flag == AES_FLAG_PASSWORD_BASED) {
                    rawOut.write(salt);
                }
                rawOut.write(iv);

                try (CipherOutputStream cipherOut = new CipherOutputStream(rawOut, cipher)) {
                    in.transferTo(cipherOut);
                }
            }
            if (durable) {
                fsync(temp);
            }
            atomicReplace(temp, source);
            if (durable) {
                fsyncParentDirectoryQuietly(source);
            }
        } catch (IOException | GeneralSecurityException e) {
            deleteQuietly(temp);
            throw new IllegalStateException("AES file encryption failed: " + filePath, e);
        }
    }

    /**
     * Streams a file (previously written by {@link #streamEncryptFile}) through AES-GCM
     * decryption into a sibling temp file, then atomically replaces the original file with it.
     * <p>
     * Deliberately does not use {@link CipherInputStream} here: with AEAD transformations such as
     * AES/GCM, some JDK versions can swallow the {@code AEADBadTagException} raised by the final
     * {@code doFinal()} call inside {@code CipherInputStream.read()} and surface a plain, silent
     * EOF instead - meaning a tampered or wrong-key file could decrypt "successfully" into a
     * truncated but unflagged plaintext. Driving {@link Cipher#update(byte[], int, int)}/
     * {@link Cipher#doFinal()} manually guarantees an authentication failure always propagates as
     * an exception.
     *
     * @param filePath    the path of the file to decrypt
     * @param providedKey the key to use if the file was key-based encrypted, otherwise ignored
     * @param password    the password to use if the file was password-based encrypted, otherwise ignored
     * @param durable     if true, fsync the temp file before the atomic rename and the containing
     *                    directory afterward (see {@link #fsync(Path)})
     * @throws IllegalArgumentException if the file is empty, the leading format flag byte is
     *                                  neither {@link #AES_FLAG_KEY_BASED} nor
     *                                  {@link #AES_FLAG_PASSWORD_BASED}, the salt or IV is
     *                                  truncated (indicating a corrupted or unrecognized file),
     *                                  or the required key/password for its encryption mode is
     *                                  missing
     * @throws IllegalStateException    if reading, decrypting, or writing fails; the temp file is deleted first
     */
    private static void streamDecryptFile(String filePath, SecretKey providedKey, String password, boolean durable) {
        Path source = Paths.get(filePath);
        Path temp = tempSibling(source);
        try (InputStream rawIn = Files.newInputStream(source)) {
            int flag = rawIn.read();
            if (flag == -1) {
                throw new IllegalArgumentException("File is empty or corrupted: " + filePath);
            }
            if (flag != AES_FLAG_KEY_BASED && flag != AES_FLAG_PASSWORD_BASED) {
                throw new IllegalArgumentException(
                        "Unrecognized AES file format (unknown leading flag byte: " + flag
                                + "); file is corrupted or was not produced by encryptFileByAES: " + filePath);
            }

            SecretKey key;
            if (flag == AES_FLAG_PASSWORD_BASED) {
                if (password == null) {
                    throw new IllegalArgumentException(
                            "File was encrypted with a password; a password is required to decrypt it.");
                }
                byte[] salt = rawIn.readNBytes(SALT_LENGTH_BYTES);
                if (salt.length != SALT_LENGTH_BYTES) {
                    throw new IllegalArgumentException("Invalid AES file: truncated salt: " + filePath);
                }
                key = deriveAESKeyFromPassword(password, salt);
            } else {
                if (providedKey == null) {
                    throw new IllegalArgumentException(
                            "File was encrypted with a SecretKey; that same key is required to decrypt it.");
                }
                key = providedKey;
            }

            byte[] iv = rawIn.readNBytes(GCM_IV_LENGTH_BYTES);
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Invalid AES file: truncated IV: " + filePath);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = rawIn.read(buffer)) != -1) {
                    byte[] chunk = cipher.update(buffer, 0, bytesRead);
                    if (chunk != null) {
                        out.write(chunk);
                    }
                }
                byte[] finalChunk = cipher.doFinal();
                if (finalChunk != null) {
                    out.write(finalChunk);
                }
            }
            if (durable) {
                fsync(temp);
            }
            atomicReplace(temp, source);
            if (durable) {
                fsyncParentDirectoryQuietly(source);
            }
        } catch (IOException | GeneralSecurityException e) {
            deleteQuietly(temp);
            throw new IllegalStateException(
                    "AES file decryption failed (wrong key/password or corrupted file): " + filePath, e);
        }
    }

    /**
     * Builds a sibling temp file path for a source file, with a unique, hidden-style name.
     *
     * @param source the source file the temp file will sit next to
     * @return the temp file's path
     */
    private static Path tempSibling(Path source) {
        Path fileName = source.getFileName();
        String tempName = "." + (fileName != null ? fileName : Paths.get("file")) + ".tmp-" + uuid();
        Path parent = source.toAbsolutePath().getParent();
        return parent != null ? parent.resolve(tempName) : Paths.get(tempName);
    }

    /**
     * Atomically replaces a destination file with a temp file.
     * <p>
     * Deliberately does <b>not</b> fall back to a non-atomic {@link Files#move} when the
     * filesystem doesn't support {@link StandardCopyOption#ATOMIC_MOVE}: a non-atomic replace can
     * be interrupted mid-operation (e.g. by a crash or power loss) in a way that loses the
     * original file entirely, which is an unacceptable risk for encryption/decryption of
     * potentially sensitive data. If the filesystem doesn't support atomic moves,
     * {@link AtomicMoveNotSupportedException} propagates to the caller instead.
     *
     * @param temp        the temp file to move into place
     * @param destination the file to replace
     * @throws IOException                     if the move fails
     * @throws AtomicMoveNotSupportedException if the filesystem does not support atomic moves
     */
    private static void atomicReplace(Path temp, Path destination) throws IOException {
        Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Forces a file's contents to durable storage (equivalent to {@code fsync(2)}), so that a
     * crash or power loss immediately afterward cannot lose data that was written but not yet
     * flushed by the OS page cache.
     *
     * @param path the file to fsync
     * @throws IOException if the file cannot be opened or the sync fails
     */
    private static void fsync(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    /**
     * Best-effort fsync of a file's parent directory, so the directory entry created by the
     * preceding atomic rename is itself durable. Not all platforms/filesystems support opening
     * and syncing a directory (notably Windows), so failures here are deliberately swallowed
     * rather than propagated — this is a best-effort durability improvement, not a correctness
     * requirement of the encryption/decryption itself.
     *
     * @param file the file whose parent directory should be fsynced
     */
    private static void fsyncParentDirectoryQuietly(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try (FileChannel dirChannel = FileChannel.open(parent, StandardOpenOption.READ)) {
            dirChannel.force(true);
        } catch (IOException ignored) {
            // best-effort only; not supported on all platforms/filesystems (e.g. Windows)
        }
    }

    /**
     * Deletes a file if it exists, silently ignoring any failure.
     *
     * @param path the file to delete
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    // ==================================================================
    // RSA
    // ==================================================================

    /**
     * Generates a fresh random RSA key pair.
     *
     * @param keySize the key size, in bits; must be at least {@link #RSA_MIN_KEY_SIZE_BITS}
     * @return a new random RSA key pair
     * @throws IllegalArgumentException if {@code keySize} is below the minimum
     */
    public static KeyPair generateRSAKeys(int keySize) {
        if (keySize < RSA_MIN_KEY_SIZE_BITS) {
            throw new IllegalArgumentException(
                    "RSA key size must be at least " + RSA_MIN_KEY_SIZE_BITS + " bits for adequate security.");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize, SECURE_RANDOM);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    /**
     * Encrypts a (short) string with RSA-OAEP using a public key.
     *
     * @param plainText the text to encrypt; must be small enough to fit under this key's OAEP limit
     * @param publicKey the RSA public key to encrypt with
     * @return the raw ciphertext bytes
     * @throws IllegalArgumentException if either argument is null, or {@code plainText} is too
     *                                  large for this key size (RSA is only suitable for short payloads)
     * @throws IllegalStateException    if encryption fails for another reason
     */
    public static byte[] encryptStringByRSA(String plainText, PublicKey publicKey) {
        requireNonNull(plainText, "plainText");
        requireNonNull(publicKey, "publicKey");
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams(), SECURE_RANDOM);
            return cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalBlockSizeException e) {
            throw new IllegalArgumentException(
                    "Plain text is too large for RSA encryption with this key size. "
                            + "RSA is only suitable for small payloads (e.g. an AES key) — use AES for larger data.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA encryption failed", e);
        }
    }

    /**
     * Decrypts bytes previously encrypted by {@link #encryptStringByRSA}, using the matching private key.
     *
     * @param cipherBytes the ciphertext to decrypt
     * @param privateKey  the RSA private key to decrypt with
     * @return the decrypted plaintext
     * @throws IllegalArgumentException if either argument is null
     * @throws IllegalStateException    if decryption fails (wrong key or corrupted data)
     */
    public static String decryptStringByRSA(byte[] cipherBytes, PrivateKey privateKey) {
        requireNonNull(cipherBytes, "cipherBytes");
        requireNonNull(privateKey, "privateKey");
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams());
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA decryption failed - wrong key or corrupted data", e);
        }
    }

    /**
     * Encodes an RSA public key as a Base64 string, using its standard X.509
     * SubjectPublicKeyInfo encoding — the same format returned by {@link PublicKey#getEncoded()}.
     * Useful for storing or transmitting a public key as plain text (e.g. in a config file or
     * over the network); decode it back with {@link #decodeRSAPublicKey(String)}.
     *
     * @param publicKey the RSA public key to encode
     * @return the Base64-encoded key
     * @throws IllegalArgumentException if {@code publicKey} is null
     */
    public static String encodeRSAPublicKey(PublicKey publicKey) {
        requireNonNull(publicKey, "publicKey");
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Encodes an RSA private key as a Base64 string, using its standard PKCS#8 encoding — the
     * same format returned by {@link PrivateKey#getEncoded()}.
     * <p>
     * The resulting string is unencrypted key material: treat it with the same care as the key
     * itself (do not log it, and store it only somewhere access-controlled). Decode it back with
     * {@link #decodeRSAPrivateKey(String)}.
     *
     * @param privateKey the RSA private key to encode
     * @return the Base64-encoded key
     * @throws IllegalArgumentException if {@code privateKey} is null
     */
    public static String encodeRSAPrivateKey(PrivateKey privateKey) {
        requireNonNull(privateKey, "privateKey");
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Decodes an RSA public key previously encoded by {@link #encodeRSAPublicKey(PublicKey)}
     * (standard X.509 SubjectPublicKeyInfo encoding, Base64-encoded).
     *
     * @param base64PublicKey the Base64-encoded public key to decode
     * @return the decoded RSA public key
     * @throws IllegalArgumentException if {@code base64PublicKey} is null, is not valid Base64,
     *                                  or is not a validly encoded RSA public key
     */
    public static PublicKey decodeRSAPublicKey(String base64PublicKey) {
        requireNonNull(base64PublicKey, "base64PublicKey");
        byte[] encoded = Base64.getDecoder().decode(base64PublicKey);
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
            return rsaKeyFactory().generatePublic(spec);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid RSA public key encoding", e);
        }
    }

    /**
     * Decodes an RSA private key previously encoded by {@link #encodeRSAPrivateKey(PrivateKey)}
     * (standard PKCS#8 encoding, Base64-encoded).
     *
     * @param base64PrivateKey the Base64-encoded private key to decode
     * @return the decoded RSA private key
     * @throws IllegalArgumentException if {@code base64PrivateKey} is null, is not valid Base64,
     *                                  or is not a validly encoded RSA private key
     */
    public static PrivateKey decodeRSAPrivateKey(String base64PrivateKey) {
        requireNonNull(base64PrivateKey, "base64PrivateKey");
        byte[] encoded = Base64.getDecoder().decode(base64PrivateKey);
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded);
            return rsaKeyFactory().generatePrivate(spec);
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid RSA private key encoding", e);
        }
    }

    /**
     * Resolves a {@link KeyFactory} instance for RSA.
     *
     * @return a new RSA key factory instance
     * @throws IllegalStateException if RSA is not available in this JVM
     */
    private static KeyFactory rsaKeyFactory() {
        try {
            return KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    /**
     * Builds the OAEP parameters used by every RSA operation in this class (SHA-256 digest and
     * MGF1 mask generation function).
     *
     * @return the OAEP parameter spec
     */
    private static OAEPParameterSpec oaepParams() {
        return new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    // ==================================================================
    // Utility
    // ==================================================================

    /**
     * Generates a random UUID (version 4).
     *
     * @return a new random UUID string
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates a random, uniformly distributed integer in the range {@code [0, maximum)}.
     *
     * @param maximum the exclusive upper bound; must be positive
     * @return the random integer
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public static int randomInt(int maximum) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("maximum must be positive");
        }
        return SECURE_RANDOM.nextInt(maximum);
    }

    /**
     * Generates a random, uniformly distributed integer in the range {@code [minimum, maximum)}.
     *
     * @param minimum the inclusive lower bound
     * @param maximum the exclusive upper bound; must be greater than {@code minimum}
     * @return the random integer
     * @throws IllegalArgumentException if {@code minimum} is not less than {@code maximum}
     */
    public static int randomInt(int minimum, int maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("minimum must be less than maximum");
        }
        return SECURE_RANDOM.nextInt(minimum, maximum);
    }

    /**
     * Generates a random double in the range {@code [0.0, 1.0)}.
     *
     * @return the random double
     */
    public static double randomDouble() {
        return SECURE_RANDOM.nextDouble();
    }

    /**
     * Generates a random double in the range {@code [minimum, maximum)}.
     *
     * @param minimum the inclusive lower bound
     * @param maximum the exclusive upper bound; must be greater than {@code minimum}
     * @return the random double
     * @throws IllegalArgumentException if {@code minimum} is not less than {@code maximum}
     */
    public static double randomDouble(double minimum, double maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("minimum must be less than maximum");
        }
        return minimum + SECURE_RANDOM.nextDouble() * (maximum - minimum);
    }

    /**
     * Generates a random boolean.
     *
     * @return the random boolean
     */
    public static boolean randomBoolean() {
        return SECURE_RANDOM.nextBoolean();
    }

    /**
     * Generates a random string of the given length, drawn from upper- and lower-case letters only.
     *
     * @param length the desired string length; must be positive
     * @return the random string
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    public static String randomAlphaString(int length) {
        return randomStringFrom(ALPHA_CHARS, length);
    }

    /**
     * Generates a random string of the given length, drawn from upper- and lower-case letters and digits.
     *
     * @param length the desired string length; must be positive
     * @return the random string
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    public static String randomAlphaNumericString(int length) {
        return randomStringFrom(ALPHANUMERIC_CHARS, length);
    }

    /**
     * Generates a random string of the given length, drawing each character uniformly from the
     * given alphabet.
     *
     * @param alphabet the characters to draw from
     * @param length   the desired string length; must be positive
     * @return the random string
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    private static String randomStringFrom(char[] alphabet, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet[SECURE_RANDOM.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    /**
     * Generates cryptographically random bytes.
     *
     * @param length the number of bytes to generate
     * @return the random bytes
     */
    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    // ==================================================================
    // General helpers
    // ==================================================================

    /**
     * Validates that a value is not null.
     *
     * @param value the value to check
     * @param name  the parameter name to report if the check fails
     * @throws IllegalArgumentException if {@code value} is null
     */
    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    /**
     * Validates that a byte array is at least a given length.
     *
     * @param data        the data to check
     * @param minLength   the minimum required length
     * @param description a description of the data, used in the error message if the check fails
     * @throws IllegalArgumentException if {@code data} is shorter than {@code minLength}
     */
    private static void requireMinLength(byte[] data, int minLength, String description) {
        if (data.length < minLength) {
            throw new IllegalArgumentException("Invalid " + description + ": too short to be valid");
        }
    }

    /**
     * Concatenates several byte arrays into one.
     *
     * @param arrays the arrays to concatenate, in order
     * @return the concatenated bytes
     */
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) {
            total += a.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(total);
        for (byte[] a : arrays) {
            buffer.put(a);
        }
        return buffer.array();
    }
}