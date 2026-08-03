package org.j2os.examples.desktop.jsecurity;

import org.j2os.platform.jsecurity.cryptography.Cryptography;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Demonstrates every capability of {@link Cryptography}: Base64, digests (string and file),
 * password hashing (BCrypt/Argon2id/PBKDF2), AES-GCM (string and file, key-based and
 * password-based), RSA-OAEP, and the random-value utility methods.
 *
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
public final class CryptographyExample {

    private CryptographyExample() {
    }

    /**
     * Runs the example.
     *
     * @param args not used
     * @throws Exception if any demo fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        demoBase64();
        demoDigests();
        demoPasswordHashing();
        demoAESString();
        //demoAESFile();
        demoRSA();
        demoRandomUtilities();
    }

    /** Demonstrates standard and URL-safe Base64 round trips. */
    private static void demoBase64() {
        System.out.println("=== Base64 ===");
        byte[] data = "Hello, J2OS!".getBytes();

        String base64 = Cryptography.encodeBase64(data);
        System.out.println("encodeBase64:    " + base64);
        System.out.println("decodeBase64:    " + new String(Cryptography.decodeBase64(base64)));

        String base64Url = Cryptography.encodeBase64URL(data);
        System.out.println("encodeBase64URL: " + base64Url);
        System.out.println("decodeBase64URL: " + new String(Cryptography.decodeBase64URL(base64Url)));
    }

    /** Demonstrates string and file digests. */
    private static void demoDigests() throws IOException {
        System.out.println("\n=== Digests ===");
        String text = "Hello, J2OS!";
        System.out.println("MD5:      " + Cryptography.hashByMD5(text));
        System.out.println("SHA2-256: " + Cryptography.hashBySHA2_256(text));
        System.out.println("SHA2-512: " + Cryptography.hashBySHA2_512(text));
        System.out.println("SHA3-256: " + Cryptography.hashBySHA3_256(text));
        System.out.println("SHA3-512: " + Cryptography.hashBySHA3_512(text));

        Path tempFile = Files.createTempFile("crypto-example-digest", ".txt");
        try {
            Files.writeString(tempFile, text);
            System.out.println("File MD5:      " + Cryptography.hashFileByMD5(tempFile.toString()));
            System.out.println("File SHA2-256: " + Cryptography.hashFileBySHA2_256(tempFile.toString()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /** Demonstrates password hashing and verification with BCrypt, Argon2id, and PBKDF2. */
    private static void demoPasswordHashing() {
        System.out.println("\n=== Password hashing ===");
        String password = "correct horse battery staple";

        String bcryptHash = Cryptography.hashByBCrypt(password);
        System.out.println("BCrypt hash:  " + bcryptHash);
        System.out.println("BCrypt check (correct password): " + Cryptography.checkByBCrypt(password, bcryptHash));
        System.out.println("BCrypt check (wrong password):    " + Cryptography.checkByBCrypt("wrong password", bcryptHash));

        String argon2Hash = Cryptography.hashByArgon2(password);
        System.out.println("Argon2id hash: " + argon2Hash);
        System.out.println("Argon2id check (correct password): " + Cryptography.checkByArgon2(password, argon2Hash));
        System.out.println("Argon2id check (wrong password):    " + Cryptography.checkByArgon2("wrong password", argon2Hash));

        String pbkdf2Hash = Cryptography.hashByPBKDF2(password);
        System.out.println("PBKDF2 hash: " + pbkdf2Hash);
        System.out.println("PBKDF2 check (correct password): " + Cryptography.checkByPBKDF2(password, pbkdf2Hash));
        System.out.println("PBKDF2 check (wrong password):    " + Cryptography.checkByPBKDF2("wrong password", pbkdf2Hash));
    }

    /** Demonstrates AES-GCM string encryption, both key-based and password-based. */
    private static void demoAESString() {
        System.out.println("\n=== AES (string) ===");
        String message = "The secret plan is at midnight.";

        SecretKey key = Cryptography.generateAESKey(256);
        String encryptedByKey = Cryptography.encryptStringByAES(message, key);
        System.out.println("Encrypted (key-based):      " + encryptedByKey);
        System.out.println("Decrypted (key-based):      " + Cryptography.decryptStringByAES(encryptedByKey, key));

        String password = "a strong password";
        String encryptedByPassword = Cryptography.encryptStringByAES(message, password);
        System.out.println("Encrypted (password-based): " + encryptedByPassword);
        System.out.println("Decrypted (password-based): " + Cryptography.decryptStringByAES(encryptedByPassword, password));
    }

    /** Demonstrates AES-GCM file encryption in place, both key-based and password-based. */
    private static void demoAESFile() throws IOException {
        System.out.println("\n=== AES (file) ===");
        String originalContent = "Contents of a file that needs to stay confidential.";

        Path keyBasedFile = Files.createTempFile("crypto-example-aes-key", ".txt");
        try {
            Files.writeString(keyBasedFile, originalContent);
            SecretKey key = Cryptography.generateAESKey(256);

            Cryptography.encryptFileByAES(keyBasedFile.toString(), key);
            System.out.println("File encrypted in place (key-based): " + keyBasedFile);

            Cryptography.decryptFileByAES(keyBasedFile.toString(), key);
            System.out.println("File decrypted content:              " + Files.readString(keyBasedFile));
        } finally {
            Files.deleteIfExists(keyBasedFile);
        }

        Path passwordBasedFile = Files.createTempFile("crypto-example-aes-password", ".txt");
        try {
            Files.writeString(passwordBasedFile, originalContent);
            String password = "another strong password";

            Cryptography.encryptFileByAES(passwordBasedFile.toString(), password);
            System.out.println("File encrypted in place (password-based): " + passwordBasedFile);

            Cryptography.decryptFileByAES(passwordBasedFile.toString(), password);
            System.out.println("File decrypted content:                   " + Files.readString(passwordBasedFile));
        } finally {
            Files.deleteIfExists(passwordBasedFile);
        }
    }

    /** Demonstrates RSA-OAEP encryption of a short payload (e.g. suitable for wrapping an AES key). */
    private static void demoRSA() throws NoSuchAlgorithmException, InvalidKeySpecException {
        System.out.println("\n=== RSA ===");
        KeyPair keyPair = Cryptography.generateRSAKeys(2048);

        String shortSecret = "AES-key-sized-secret";
        byte[] encrypted = Cryptography.encryptStringByRSA(shortSecret, keyPair.getPublic());
        String decrypted = Cryptography.decryptStringByRSA(encrypted, keyPair.getPrivate());

        System.out.println("Encrypted (Base64): " + Cryptography.encodeBase64(encrypted));
        System.out.println("Decrypted:          " + decrypted);
    }

    /** Demonstrates the random-value and UUID utility methods. */
    private static void demoRandomUtilities() {
        System.out.println("\n=== Random utilities ===");
        System.out.println("uuid:                          " + Cryptography.uuid());
        System.out.println("randomInt(100):                " + Cryptography.randomInt(100));
        System.out.println("randomInt(10, 20):             " + Cryptography.randomInt(10, 20));
        System.out.println("randomDouble():                " + Cryptography.randomDouble());
        System.out.println("randomDouble(1.0, 2.0):        " + Cryptography.randomDouble(1.0, 2.0));
        System.out.println("randomBoolean():               " + Cryptography.randomBoolean());
        System.out.println("randomAlphaString(12):         " + Cryptography.randomAlphaString(12));
        System.out.println("randomAlphaNumericString(12):  " + Cryptography.randomAlphaNumericString(12));
    }
}