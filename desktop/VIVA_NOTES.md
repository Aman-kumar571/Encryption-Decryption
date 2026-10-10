# FileCrypt - Viva Examination Notes

## 1. Project Overview
FileCrypt is a cross-platform file encryption and decryption application. It delivers a Java 17 Swing desktop application and an interoperable browser-based client (using the standard Web Crypto API). Both platforms share the exact same cryptographic design and binary file layout (`.fct`).

---

## 2. Java Class Architecture

- **Main (`com.filecrypt.Main`)**:
  Application entry point. When launched without parameters, it sets up and displays `MainFrame` on the Swing Event Dispatch Thread (EDT). When passed arguments (`encrypt <file> <password>` or `decrypt <file> <password>`), it runs in headless CLI mode for testing or automation.
- **CryptoService (`com.filecrypt.CryptoService`)**:
  Contains the core cryptographic operations. It handles key derivation via PBKDF2WithHmacSHA256 (600,000 iterations), sets up AES/GCM/NoPadding ciphers, streams plaintext and ciphertext using 8192-byte buffers, and safely zeroes sensitive password and key arrays from memory.
- **FileHeader (`com.filecrypt.FileHeader`)**:
  Parses, generates, and serializes the fixed 32-byte header:
  - 4 bytes magic (`FCT1`)
  - 16 bytes random salt
  - 12 bytes random IV
  Validates header authenticity before starting decryption.
- **MainFrame (`com.filecrypt.MainFrame`)**:
  Swing user interface providing native look-and-feel. Features drag-and-drop file imports via `TransferHandler`, password verification, interactive status updates, and a progress bar. Uses `SwingWorker` to run long-running crypto work asynchronously without freezing the UI.
- **ProgressListener (`com.filecrypt.ProgressListener`)**:
  A single-method functional interface (`onProgress(long bytesProcessed, long totalBytes)`) used to stream progress updates back to UI or CLI consoles.
- **SelfTest (`com.filecrypt.SelfTest`)**:
  A standalone test suite requiring zero third-party testing frameworks. Tests round-trip encryption across varied file sizes (0B, 1B, 1KB, 5MB), wrong password handling, flipped ciphertext bytes, file truncation, and corrupted magic headers.

---

## 3. Why AES-GCM?
- **Authenticated Encryption**: Unlike legacy modes such as CBC or ECB which only provide confidentiality, Galois/Counter Mode (GCM) is an AEAD (Authenticated Encryption with Associated Data) mode.
- **Tamper Detection**: GCM computes a 128-bit authentication tag over the ciphertext. If an attacker modifies even a single bit of the encrypted file, decryption fails immediately during tag verification.
- **No Separate HMAC**: GCM eliminates the need to manually implement an Encrypt-then-MAC scheme (like combining AES-CBC with HMAC-SHA256), reducing structural vulnerabilities like padding oracle attacks.

---

## 4. Why PBKDF2?
- **Entropy Stretching**: Human passwords possess relatively low entropy. PBKDF2 (Password-Based Key Derivation Function 2) stretches a password into a uniform 256-bit cryptographic key.
- **Slows Brute-Force**: By configuring 600,000 iterations of HMAC-SHA256 (matching OWASP recommendations), key derivation requires non-trivial computation, drastically slowing down offline dictionary attacks and specialized GPU cracking hardware.

---

## 5. What is a Salt?
- A salt is a 16-byte cryptographically secure random value generated for every file.
- It is mixed into the PBKDF2 calculation alongside the password.
- **Purpose**: It ensures that two identical files encrypted with the exact same password produce completely distinct encryption keys and ciphertexts. This renders precomputed lookup tables (rainbow tables) completely ineffective.
- **Storage**: The salt does not need to be secret; it is safely stored in bytes 4-19 of the file header.

---

## 6. What is an IV (Initialization Vector)?
- A 12-byte random nonce passed to AES-GCM.
- **Purpose**: Ensures that even if the same key encrypts multiple blocks of data, identical plaintext blocks produce distinct ciphertext blocks.
- **Rule**: In GCM mode, an IV must never be repeated with the same key. Using a random 12-byte IV for every file guarantees uniqueness.
- **Storage**: The IV is public and stored in bytes 20-31 of the header.

---

## 7. What Happens on Wrong Password or File Corruption?
- When the wrong password is supplied, PBKDF2 produces an incorrect 256-bit key.
- When AES-GCM attempts to decrypt using this wrong key, the computed authentication tag will not match the 16-byte tag recorded at the end of the ciphertext.
- Java throws an `AEADBadTagException` (WebCrypto rejects the promise).
- The application catches this and emits the exact generic error message:
  `"Wrong password, or the file is corrupted."`
- The system never distinguishes between a wrong password and modified data, preventing side-channel information leaks.

---

## 8. Memory and Security Best Practices
- **Password Hygiene**: Passwords are handled as `char[]` arrays rather than `String` objects. In Java, strings are immutable and reside in the string pool until garbage collection. With `char[]`, the memory can be explicitly overwritten with zeros (`Arrays.fill(password, '\0')`) immediately after key derivation.
- **Cryptographic Randomness**: Salts and IVs are generated using `java.security.SecureRandom` instead of pseudo-random generators (`java.util.Random`).

---

## 9. Top 10 Viva Questions & Concise Answers

1. **What encryption algorithm does FileCrypt use?**
   AES-256 in Galois/Counter Mode (AES-256-GCM) with a 128-bit authentication tag.

2. **Why is GCM preferred over CBC?**
   GCM provides both confidentiality and built-in cryptographic integrity, preventing chosen-ciphertext and bit-flipping attacks without needing a separate MAC.

3. **What is PBKDF2, and why are 600,000 iterations used?**
   PBKDF2 is a key stretching function that derives a 256-bit key from a password. 600,000 iterations follow OWASP guidelines to thwart brute-force and hardware-accelerated cracking.

4. **Why is a salt necessary if the password is long?**
   The salt prevents rainbow table attacks and guarantees that the same password generates distinct keys across different files.

5. **Why must the IV never be reused in AES-GCM?**
   Reusing an IV with the same key destroys the authenticity guarantees of GCM and can allow attackers to recover plaintext and forge authentication tags.

6. **What is the structure of the `.fct` file header?**
   A 32-byte header: 4 bytes magic (`FCT1`), 16 bytes salt, and 12 bytes IV, followed by the ciphertext and the 16-byte tag.

7. **How does the system distinguish between a bad password and corrupted data?**
   It deliberately does not. Both scenarios trigger a GCM tag authentication failure and yield the identical message: `"Wrong password, or the file is corrupted."`

8. **Why use `char[]` instead of `java.lang.String` for password inputs?**
   `String` is immutable and cannot be purged from RAM on demand. `char[]` can be immediately cleared with zeros (`Arrays.fill`) after use, minimizing exposure in memory dumps.

9. **How does the desktop app avoid freezing the user interface during large file operations?**
   File processing is executed on a background worker thread via `SwingWorker`, while UI state and progress bar updates are posted back to the Event Dispatch Thread (EDT).

10. **Can files encrypted on the website be decrypted by the desktop application?**
    Yes. Both implementations adhere to the exact same cryptographic parameters (PBKDF2-HMAC-SHA256 with 600k iterations and AES-256-GCM with a 32-byte header).