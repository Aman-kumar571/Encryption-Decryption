package com.filecrypt;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Automated test class for FileCrypt (no JUnit required).
 *
 * Tests verify:
 *   1-4. Round-trip encrypt/decrypt for files of size 0, 1, 1000, and 5 MB
 *   5.   Wrong password produces the expected error message
 *   6.   A flipped byte in the ciphertext is detected
 *   7.   A truncated file is detected
 *   8.   Invalid magic bytes are detected
 *
 * Run: java com.filecrypt.SelfTest
 * Exit code 0 means all tests passed, 1 means at least one failed.
 */
public class SelfTest {

    private static final String CORRECT_PASSWORD = "TestPassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword99!";
    private static final String EXPECTED_ERROR = "Wrong password, or the file is corrupted.";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("FileCrypt SelfTest");
        System.out.println("==================");

        testRoundTrip(0, "round-trip 0 bytes");
        testRoundTrip(1, "round-trip 1 byte");
        testRoundTrip(1000, "round-trip 1000 bytes");
        testRoundTrip(5 * 1024 * 1024, "round-trip 5 MB");
        testWrongPassword();
        testFlippedByte();
        testTruncatedFile();
        testBadMagic();

        System.out.println();
        System.out.println("==================");
        System.out.println("Passed: " + passed + ", Failed: " + failed);

        System.exit(failed > 0 ? 1 : 0);
    }

    /**
     * Tests that encrypting and then decrypting a file of the given size
     * produces output identical to the original.
     */
    private static void testRoundTrip(int size, String testName) {
        try {
            // Create a temp file with random data
            byte[] original = new byte[size];
            new Random(42 + size).nextBytes(original);

            Path plainFile = Files.createTempFile("fct_test_plain_", ".dat");
            Path encFile = Files.createTempFile("fct_test_enc_", ".fct");
            Path decFile = Files.createTempFile("fct_test_dec_", ".dat");

            try {
                Files.write(plainFile, original);

                CryptoService service = new CryptoService();
                char[] password = CORRECT_PASSWORD.toCharArray();

                // Encrypt
                service.encrypt(plainFile, encFile, password, null);
                Arrays.fill(password, '\0');

                // Decrypt
                password = CORRECT_PASSWORD.toCharArray();
                service.decrypt(encFile, decFile, password, null);
                Arrays.fill(password, '\0');

                // Compare
                byte[] decrypted = Files.readAllBytes(decFile);
                if (Arrays.equals(original, decrypted)) {
                    pass(testName);
                } else {
                    fail(testName, "Decrypted data does not match original");
                }
            } finally {
                Files.deleteIfExists(plainFile);
                Files.deleteIfExists(encFile);
                Files.deleteIfExists(decFile);
            }
        } catch (Exception e) {
            fail(testName, e.getMessage());
        }
    }

    /** Tests that decryption with a wrong password fails with the correct message. */
    private static void testWrongPassword() {
        String testName = "wrong password";
        try {
            byte[] data = new byte[100];
            new Random(99).nextBytes(data);

            Path plainFile = Files.createTempFile("fct_test_plain_", ".dat");
            Path encFile = Files.createTempFile("fct_test_enc_", ".fct");
            Path decFile = Files.createTempFile("fct_test_dec_", ".dat");

            try {
                Files.write(plainFile, data);

                CryptoService service = new CryptoService();
                char[] password = CORRECT_PASSWORD.toCharArray();
                service.encrypt(plainFile, encFile, password, null);
                Arrays.fill(password, '\0');

                // Try to decrypt with wrong password
                char[] wrongPwd = WRONG_PASSWORD.toCharArray();
                try {
                    service.decrypt(encFile, decFile, wrongPwd, null);
                    fail(testName, "Expected IOException but decryption succeeded");
                } catch (IOException e) {
                    if (EXPECTED_ERROR.equals(e.getMessage())) {
                        pass(testName);
                    } else {
                        fail(testName, "Wrong error message: " + e.getMessage());
                    }
                } finally {
                    Arrays.fill(wrongPwd, '\0');
                }
            } finally {
                Files.deleteIfExists(plainFile);
                Files.deleteIfExists(encFile);
                Files.deleteIfExists(decFile);
            }
        } catch (Exception e) {
            fail(testName, e.getMessage());
        }
    }

    /** Tests that flipping a byte in the ciphertext is detected by GCM. */
    private static void testFlippedByte() {
        String testName = "flipped ciphertext byte";
        try {
            byte[] data = new byte[200];
            new Random(77).nextBytes(data);

            Path plainFile = Files.createTempFile("fct_test_plain_", ".dat");
            Path encFile = Files.createTempFile("fct_test_enc_", ".fct");
            Path decFile = Files.createTempFile("fct_test_dec_", ".dat");

            try {
                Files.write(plainFile, data);

                CryptoService service = new CryptoService();
                char[] password = CORRECT_PASSWORD.toCharArray();
                service.encrypt(plainFile, encFile, password, null);
                Arrays.fill(password, '\0');

                // Flip a byte in the ciphertext (after the 32-byte header)
                try (RandomAccessFile raf = new RandomAccessFile(encFile.toFile(), "rw")) {
                    long pos = FileHeader.HEADER_SIZE + 10;
                    raf.seek(pos);
                    byte b = raf.readByte();
                    raf.seek(pos);
                    raf.writeByte(b ^ 0xFF);
                }

                // Decrypt should fail
                password = CORRECT_PASSWORD.toCharArray();
                try {
                    service.decrypt(encFile, decFile, password, null);
                    fail(testName, "Expected IOException but decryption succeeded");
                } catch (IOException e) {
                    if (EXPECTED_ERROR.equals(e.getMessage())) {
                        pass(testName);
                    } else {
                        fail(testName, "Wrong error message: " + e.getMessage());
                    }
                } finally {
                    Arrays.fill(password, '\0');
                }
            } finally {
                Files.deleteIfExists(plainFile);
                Files.deleteIfExists(encFile);
                Files.deleteIfExists(decFile);
            }
        } catch (Exception e) {
            fail(testName, e.getMessage());
        }
    }

    /** Tests that a truncated encrypted file is detected. */
    private static void testTruncatedFile() {
        String testName = "truncated file";
        try {
            byte[] data = new byte[200];
            new Random(55).nextBytes(data);

            Path plainFile = Files.createTempFile("fct_test_plain_", ".dat");
            Path encFile = Files.createTempFile("fct_test_enc_", ".fct");
            Path decFile = Files.createTempFile("fct_test_dec_", ".dat");

            try {
                Files.write(plainFile, data);

                CryptoService service = new CryptoService();
                char[] password = CORRECT_PASSWORD.toCharArray();
                service.encrypt(plainFile, encFile, password, null);
                Arrays.fill(password, '\0');

                // Truncate the file by removing last 10 bytes
                byte[] encBytes = Files.readAllBytes(encFile);
                byte[] truncated = Arrays.copyOf(encBytes, encBytes.length - 10);
                Files.write(encFile, truncated);

                // Decrypt should fail
                password = CORRECT_PASSWORD.toCharArray();
                try {
                    service.decrypt(encFile, decFile, password, null);
                    fail(testName, "Expected IOException but decryption succeeded");
                } catch (IOException e) {
                    if (EXPECTED_ERROR.equals(e.getMessage())) {
                        pass(testName);
                    } else {
                        fail(testName, "Wrong error message: " + e.getMessage());
                    }
                } finally {
                    Arrays.fill(password, '\0');
                }
            } finally {
                Files.deleteIfExists(plainFile);
                Files.deleteIfExists(encFile);
                Files.deleteIfExists(decFile);
            }
        } catch (Exception e) {
            fail(testName, e.getMessage());
        }
    }

    /** Tests that a file with invalid magic bytes is rejected. */
    private static void testBadMagic() {
        String testName = "bad magic bytes";
        try {
            byte[] data = new byte[100];
            new Random(33).nextBytes(data);

            Path plainFile = Files.createTempFile("fct_test_plain_", ".dat");
            Path encFile = Files.createTempFile("fct_test_enc_", ".fct");
            Path decFile = Files.createTempFile("fct_test_dec_", ".dat");

            try {
                Files.write(plainFile, data);

                CryptoService service = new CryptoService();
                char[] password = CORRECT_PASSWORD.toCharArray();
                service.encrypt(plainFile, encFile, password, null);
                Arrays.fill(password, '\0');

                // Overwrite magic bytes with "XXXX"
                try (RandomAccessFile raf = new RandomAccessFile(encFile.toFile(), "rw")) {
                    raf.seek(0);
                    raf.write("XXXX".getBytes());
                }

                // Decrypt should fail
                password = CORRECT_PASSWORD.toCharArray();
                try {
                    service.decrypt(encFile, decFile, password, null);
                    fail(testName, "Expected IOException but decryption succeeded");
                } catch (IOException e) {
                    if (EXPECTED_ERROR.equals(e.getMessage())) {
                        pass(testName);
                    } else {
                        fail(testName, "Wrong error message: " + e.getMessage());
                    }
                } finally {
                    Arrays.fill(password, '\0');
                }
            } finally {
                Files.deleteIfExists(plainFile);
                Files.deleteIfExists(encFile);
                Files.deleteIfExists(decFile);
            }
        } catch (Exception e) {
            fail(testName, e.getMessage());
        }
    }

    private static void pass(String testName) {
        System.out.println("  PASS: " + testName);
        passed++;
    }

    private static void fail(String testName, String reason) {
        System.out.println("  FAIL: " + testName + " - " + reason);
        failed++;
    }
}