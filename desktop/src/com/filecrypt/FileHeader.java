package com.filecrypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Reads, writes, and validates the 32-byte header of an encrypted .fct file.
 *
 * Header layout (32 bytes total):
 *   Bytes  0-3  : ASCII magic "FCT1" (identifies the file format)
 *   Bytes  4-19 : 16-byte random salt (used for key derivation)
 *   Bytes 20-31 : 12-byte random IV  (used for AES-GCM encryption)
 */
public class FileHeader {

    /** Total size of the header in bytes. */
    public static final int HEADER_SIZE = 32;

    /** Magic bytes that identify a FileCrypt file. */
    public static final byte[] MAGIC = "FCT1".getBytes(StandardCharsets.US_ASCII);

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    private final byte[] salt;
    private final byte[] iv;

    /**
     * Creates a header with the given salt and IV.
     *
     * @param salt 16-byte salt
     * @param iv   12-byte initialization vector
     */
    private FileHeader(byte[] salt, byte[] iv) {
        this.salt = salt;
        this.iv = iv;
    }

    /**
     * Generates a new header with cryptographically random salt and IV.
     *
     * @return a fresh FileHeader ready to be written to a file
     */
    public static FileHeader generate() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(salt);
        random.nextBytes(iv);
        return new FileHeader(salt, iv);
    }

    /**
     * Reads and validates a header from the given input stream.
     * Throws an IOException if the magic bytes do not match or if the
     * stream does not contain enough data.
     *
     * @param in the input stream positioned at the start of the file
     * @return a parsed FileHeader
     * @throws IOException if the header is invalid or the stream is too short
     */
    public static FileHeader readFrom(InputStream in) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int totalRead = 0;
        while (totalRead < HEADER_SIZE) {
            int bytesRead = in.read(header, totalRead, HEADER_SIZE - totalRead);
            if (bytesRead == -1) {
                throw new IOException("Wrong password, or the file is corrupted.");
            }
            totalRead += bytesRead;
        }

        // Validate magic bytes
        byte[] magic = Arrays.copyOfRange(header, 0, MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Wrong password, or the file is corrupted.");
        }

        byte[] salt = Arrays.copyOfRange(header, 4, 4 + SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(header, 4 + SALT_LENGTH, HEADER_SIZE);
        return new FileHeader(salt, iv);
    }

    /**
     * Writes this header (magic + salt + IV) to the given output stream.
     *
     * @param out the output stream to write to
     * @throws IOException if a write error occurs
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(MAGIC);
        out.write(salt);
        out.write(iv);
    }

    /** Returns a copy of the 16-byte salt. */
    public byte[] getSalt() {
        return Arrays.copyOf(salt, salt.length);
    }

    /** Returns a copy of the 12-byte IV. */
    public byte[] getIv() {
        return Arrays.copyOf(iv, iv.length);
    }
}