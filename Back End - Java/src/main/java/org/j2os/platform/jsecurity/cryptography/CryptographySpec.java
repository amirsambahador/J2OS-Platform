package org.j2os.platform.jsecurity.cryptography;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * This file is purely a documentation/API contract — the method signatures {@link Cryptography}
 * is expected to provide, grouped and commented for reference. {@link Cryptography} is not
 * declared to {@code implements} this interface.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public interface CryptographySpec {

    // ===================== Base64 =====================

    /**
     * Encodes bytes as standard Base64.
     *
     * @param data the bytes to encode
     * @return the Base64-encoded string
     */
    String encodeBase64(byte[] data);

    /**
     * Decodes a standard Base64 string.
     *
     * @param base64 the Base64 string to decode
     * @return the decoded bytes
     */
    byte[] decodeBase64(String base64);

    /**
     * Encodes bytes as URL-safe Base64.
     *
     * @param data the bytes to encode
     * @return the URL-safe Base64-encoded string
     */
    String encodeBase64URL(byte[] data);

    /**
     * Decodes a URL-safe Base64 string.
     *
     * @param base64Url the URL-safe Base64 string to decode
     * @return the decoded bytes
     */
    byte[] decodeBase64URL(String base64Url);

    // ===================== Hashing =====================

    /**
     * Computes the MD5 digest of a string.
     *
     * @param plainText the text to hash
     * @return the hex-encoded MD5 digest
     */
    String hashByMD5(String plainText);           // legacy compatibility only — not secure

    /**
     * Computes the SHA-256 digest of a string.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA-256 digest
     */
    String hashBySHA2_256(String plainText);

    /**
     * Computes the SHA-512 digest of a string.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA-512 digest
     */
    String hashBySHA2_512(String plainText);

    /**
     * Computes the SHA3-256 digest of a string.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA3-256 digest
     */
    String hashBySHA3_256(String plainText);

    /**
     * Computes the SHA3-512 digest of a string.
     *
     * @param plainText the text to hash
     * @return the hex-encoded SHA3-512 digest
     */
    String hashBySHA3_512(String plainText);

    /**
     * Hashes a password with BCrypt.
     *
     * @param plainText the password to hash
     * @return the BCrypt hash string
     */
    String hashByBCrypt(String plainText);

    /**
     * Verifies a password against a BCrypt hash.
     *
     * @param plainText  the password to check
     * @param bcryptHash the hash to check it against
     * @return true if the password matches
     */
    boolean checkByBCrypt(String plainText, String bcryptHash);

    /**
     * Hashes a password with Argon2id.
     *
     * @param plainText the password to hash
     * @return the Argon2id hash string
     */
    String hashByArgon2(String plainText);

    /**
     * Verifies a password against an Argon2id hash.
     *
     * @param plainText  the password to check
     * @param argon2Hash the hash to check it against
     * @return true if the password matches
     */
    boolean checkByArgon2(String plainText, String argon2Hash);

    /**
     * Hashes a password with PBKDF2.
     *
     * @param plainText the password to hash
     * @return the PBKDF2 hash string
     */
    String hashByPBKDF2(String plainText);

    /**
     * Verifies a password against a PBKDF2 hash.
     *
     * @param plainText  the password to check
     * @param pbkdf2Hash the hash to check it against
     * @return true if the password matches
     */
    boolean checkByPBKDF2(String plainText, String pbkdf2Hash);

    /**
     * Computes the MD5 digest of a file's contents.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded MD5 digest
     */
    String hashFileByMD5(String filePath);         // legacy compatibility only — not secure

    /**
     * Computes the SHA-256 digest of a file's contents.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded SHA-256 digest
     */
    String hashFileBySHA2_256(String filePath);

    /**
     * Computes the SHA-512 digest of a file's contents.
     *
     * @param filePath the path of the file to hash
     * @return the hex-encoded SHA-512 digest
     */
    String hashFileBySHA2_512(String filePath);

    // ===================== AES (symmetric) =====================

    /**
     * Generates a fresh random AES key.
     *
     * @param bits the key size, in bits (128 / 192 / 256)
     * @return the new key
     */
    SecretKey generateAESKey(int bits); // 128 / 192 / 256

    // SecretKey version: faster — the key is already available

    /**
     * Encrypts a file in place with the given key.
     *
     * @param filePath the path of the file to encrypt
     * @param key      the key to encrypt with
     */
    void encryptFileByAES(String filePath, SecretKey key);   // overwrites the original file

    /**
     * Encrypts a file in place with the given key, optionally forcing the result to durable
     * storage (fsync) before and after the atomic rename.
     *
     * @param filePath the path of the file to encrypt
     * @param key      the key to encrypt with
     * @param durable  if true, fsync the temp file and its containing directory around the atomic rename
     */
    void encryptFileByAES(String filePath, SecretKey key, boolean durable);   // overwrites the original file

    /**
     * Decrypts a file in place with the given key.
     *
     * @param filePath the path of the file to decrypt
     * @param key      the key to decrypt with
     */
    void decryptFileByAES(String filePath, SecretKey key);   // overwrites the original file

    /**
     * Decrypts a file in place with the given key, optionally forcing the result to durable
     * storage (fsync) before and after the atomic rename.
     *
     * @param filePath the path of the file to decrypt
     * @param key      the key to decrypt with
     * @param durable  if true, fsync the temp file and its containing directory around the atomic rename
     */
    void decryptFileByAES(String filePath, SecretKey key, boolean durable);   // overwrites the original file

    /**
     * Encrypts a string with the given key.
     *
     * @param plainText the text to encrypt
     * @param key       the key to encrypt with
     * @return the encrypted, encoded ciphertext
     */
    String encryptStringByAES(String plainText, SecretKey key);

    /**
     * Decrypts a string with the given key.
     *
     * @param aesBase64Text the encoded ciphertext to decrypt
     * @param key           the key to decrypt with
     * @return the decrypted plaintext
     */
    String decryptStringByAES(String aesBase64Text, SecretKey key);

    // String password version: slower (PBKDF2 runs every time)

    /**
     * Encrypts a file in place with a key derived from a password.
     *
     * @param filePath the path of the file to encrypt
     * @param password the password to derive the key from
     */
    void encryptFileByAES(String filePath, String password); // overwrites the original file

    /**
     * Encrypts a file in place with a key derived from a password, optionally forcing the
     * result to durable storage (fsync) before and after the atomic rename.
     *
     * @param filePath the path of the file to encrypt
     * @param password the password to derive the key from
     * @param durable  if true, fsync the temp file and its containing directory around the atomic rename
     */
    void encryptFileByAES(String filePath, String password, boolean durable); // overwrites the original file

    /**
     * Decrypts a file in place with a key derived from a password.
     *
     * @param filePath the path of the file to decrypt
     * @param password the password to derive the key from
     */
    void decryptFileByAES(String filePath, String password); // overwrites the original file

    /**
     * Decrypts a file in place with a key derived from a password, optionally forcing the
     * result to durable storage (fsync) before and after the atomic rename.
     *
     * @param filePath the path of the file to decrypt
     * @param password the password to derive the key from
     * @param durable  if true, fsync the temp file and its containing directory around the atomic rename
     */
    void decryptFileByAES(String filePath, String password, boolean durable); // overwrites the original file

    /**
     * Encrypts a string with a key derived from a password.
     *
     * @param plainText the text to encrypt
     * @param password  the password to derive the key from
     * @return the encrypted, encoded ciphertext
     */
    String encryptStringByAES(String plainText, String password);

    /**
     * Decrypts a string with a key derived from a password.
     *
     * @param aesBase64Text the encoded ciphertext to decrypt
     * @param password      the password to derive the key from
     * @return the decrypted plaintext
     */
    String decryptStringByAES(String aesBase64Text, String password);

    // ===================== RSA (asymmetric) =====================

    /**
     * Generates a fresh random RSA key pair.
     *
     * @param keySize the key size, in bits (e.g. 2048 or 4096)
     * @return the new key pair
     */
    KeyPair generateRSAKeys(int keySize); // e.g. 2048 or 4096

    /**
     * Encrypts a short string with a public key.
     *
     * @param plainText the text to encrypt
     * @param publicKey the key to encrypt with
     * @return the encrypted bytes
     */
    byte[] encryptStringByRSA(String plainText, PublicKey publicKey);

    /**
     * Decrypts bytes with the matching private key.
     *
     * @param cipherBytes the encrypted bytes to decrypt
     * @param privateKey  the key to decrypt with
     * @return the decrypted plaintext
     */
    String decryptStringByRSA(byte[] cipherBytes, PrivateKey privateKey);

    /**
     * Encodes a public key as a Base64 string (X.509 encoding), for storing or transmitting it as text.
     *
     * @param publicKey the key to encode
     * @return the Base64-encoded key
     */
    String encodeRSAPublicKey(PublicKey publicKey);

    /**
     * Encodes a private key as a Base64 string (PKCS#8 encoding). The result is unencrypted key
     * material and must be handled with the same care as the key itself.
     *
     * @param privateKey the key to encode
     * @return the Base64-encoded key
     */
    String encodeRSAPrivateKey(PrivateKey privateKey); // unencrypted key material — handle with care

    /**
     * Decodes a public key previously encoded by {@link #encodeRSAPublicKey}.
     *
     * @param base64PublicKey the Base64-encoded key to decode
     * @return the decoded public key
     */
    PublicKey decodeRSAPublicKey(String base64PublicKey);

    /**
     * Decodes a private key previously encoded by {@link #encodeRSAPrivateKey}.
     *
     * @param base64PrivateKey the Base64-encoded key to decode
     * @return the decoded private key
     */
    PrivateKey decodeRSAPrivateKey(String base64PrivateKey);

    // ===================== Utility =====================

    /**
     * Generates a random UUID.
     *
     * @return the new UUID string
     */
    String uuid();

    /**
     * Generates a random integer in {@code [0, maximum)}.
     *
     * @param maximum the exclusive upper bound
     * @return the random integer
     */
    int randomInt(int maximum);

    /**
     * Generates a random integer in {@code [minimum, maximum)}.
     *
     * @param minimum the inclusive lower bound
     * @param maximum the exclusive upper bound
     * @return the random integer
     */
    int randomInt(int minimum, int maximum);

    /**
     * Generates a random double in {@code [0.0, 1.0)}.
     *
     * @return the random double
     */
    double randomDouble();

    /**
     * Generates a random double in {@code [minimum, maximum)}.
     *
     * @param minimum the inclusive lower bound
     * @param maximum the exclusive upper bound
     * @return the random double
     */
    double randomDouble(double minimum, double maximum);

    /**
     * Generates a random boolean.
     *
     * @return the random boolean
     */
    boolean randomBoolean();

    /**
     * Generates a random string of letters only.
     *
     * @param length the desired string length
     * @return the random string
     */
    String randomAlphaString(int length);          // letters only

    /**
     * Generates a random string of letters and digits.
     *
     * @param length the desired string length
     * @return the random string
     */
    String randomAlphaNumericString(int length);   // letters and digits
}