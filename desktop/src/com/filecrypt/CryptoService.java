package com.filecrypt;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Provides file encryption and decryption using AES-256-GCM.
 *
 * Key derivation uses PBKDF2 with HMAC-SHA256, 600,000 iterations, and a
 * random 16-byte salt. The 12-byte IV and salt are stored in the file header
 * so that decryption can reconstruct the same key and cipher state.
 *
 * GCM mode provides both confidentiality (the data is encrypted) and
 * integrity (any tampering with the ciphertext is detected via the
 * 128-bit authentication tag appended to the ciphertext).
 */
public class CryptoService {

    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int BUFFER_SIZE = 8192;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * Encrypts a file with the given password.
     *
     * Steps:
     * 1. Generate a random salt and IV (stored in the header).
     * 2. Derive a 256-bit AES key from the password and salt using PBKDF2.
     * 3. Write the 32-byte header (magic, salt, IV) to the output.
     * 4. Stream the plaintext through AES-GCM, writing ciphertext to output.
     * 5. GCM appends a 16-byte authentication tag at the end automatically.
     *
     * @param inputFile  path to the plaintext file
     * @param outputFile path for the encrypted output
     * @param password   the encryption password (will NOT be zeroed here;
     *                   the caller is responsible for zeroing it)
     * @param listener   optional progress callback (may be null)
     * @throws IOException if an I/O or cryptographic error occurs
     */
    public void encrypt(Path inputFile, Path outputFile, char[] password,
                        ProgressListener listener) throws IOException {

        FileHeader header = FileHeader.generate();
        byte[] keyBytes = null;
        try {
            SecretKey key = deriveKey(password, header.getSalt());
            keyBytes = key.getEncoded();

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, header.getIv());
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            long totalBytes = Files.size(inputFile);

            try (InputStream in = new BufferedInputStream(Files.newInputStream(inputFile));
                 OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(outputFile));
                 CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher)) {

                header.writeTo(fileOut);

                byte[] buffer = new byte[BUFFER_SIZE];
                long processed = 0;
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    cipherOut.write(buffer, 0, bytesRead);
                    processed += bytesRead;
                    if (listener != null) {
                        listener.onProgress(processed, totalBytes);
                    }
                }
                // CipherOutputStream.close() finalizes the GCM tag
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    /**
     * Decrypts a file with the given password.
     *
     * Steps:
     * 1. Read the 32-byte header to get the salt and IV.
     * 2. Derive the AES key from the password and salt.
     * 3. Stream the ciphertext through AES-GCM decryption.
     * 4. If the password is wrong or the file was tampered with, the GCM
     *    tag check fails and an IOException is thrown.
     *
     * @param inputFile  path to the encrypted .fct file
     * @param outputFile path for the decrypted output
     * @param password   the decryption password
     * @param listener   optional progress callback (may be null)
     * @throws IOException if decryption fails (wrong password, corruption, or I/O error)
     */
    public void decrypt(Path inputFile, Path outputFile, char[] password,
                        ProgressListener listener) throws IOException {

        byte[] keyBytes = null;
        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(inputFile))) {

            FileHeader header = FileHeader.readFrom(fileIn);

            SecretKey key = deriveKey(password, header.getSalt());
            keyBytes = key.getEncoded();

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, header.getIv());
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            // Total ciphertext size is file size minus the header
            long totalCipherBytes = Files.size(inputFile) - FileHeader.HEADER_SIZE;

            try (CipherInputStream cipherIn = new CipherInputStream(fileIn, cipher);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long processed = 0;
                int bytesRead;
                while ((bytesRead = cipherIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    processed += bytesRead;
                    if (listener != null) {
                        listener.onProgress(processed, totalCipherBytes);
                    }
                }
            }
        } catch (IOException e) {
            // CipherInputStream wraps AEADBadTagException in IOException.
            // Check the cause to detect wrong password or tampered data.
            if (isCausedByTagMismatch(e)) {
                throw new IOException("Wrong password, or the file is corrupted.");
            }
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IOException("Wrong password, or the file is corrupted.");
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    /**
     * Checks whether an exception was caused by an AES-GCM tag mismatch,
     * which indicates a wrong password or tampered ciphertext.
     * CipherInputStream wraps crypto exceptions in IOException, so we
     * need to walk the cause chain.
     */
    private boolean isCausedByTagMismatch(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof AEADBadTagException) {
                return true;
            }
            // Also check the message for "Tag mismatch" in case the
            // exception type is a generic one wrapping the real cause
            String msg = current.getMessage();
            if (msg != null && msg.contains("Tag mismatch")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Derives a 256-bit AES key from a password and salt using PBKDF2.
     *
     * PBKDF2 applies HMAC-SHA256 repeatedly (600,000 times) to make
     * brute-force password guessing very slow.
     *
     * @param password the user password
     * @param salt     the random salt from the file header
     * @return a SecretKey suitable for AES encryption
     * @throws GeneralSecurityException if key derivation fails
     */
    private SecretKey deriveKey(char[] password, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }
}