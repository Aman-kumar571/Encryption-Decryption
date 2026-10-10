# FileCrypt - Secure File Encryption & Decryption

FileCrypt is a cross-platform file encryption and decryption suite providing authenticated encryption using standard **AES-256-GCM** and key derivation via **PBKDF2-HMAC-SHA256**. It includes:

1. **Desktop Client (Java 17 Swing)**: Fast, native desktop GUI with drag-and-drop file processing and asynchronous operations.
2. **Web Client (Web Crypto API)**: Client-side in-browser encryption/decryption tool running 100% locally on device with zero server uploads.

Both desktop and web implementations share the exact same binary format (`.fct`), ensuring 100% cross-platform interoperability.

---

## 🔒 Cryptographic Specification

- **Algorithm**: AES-256 in Galois/Counter Mode (`AES/GCM/NoPadding`)
- **Key Derivation**: PBKDF2 with HMAC-SHA-256 (600,000 iterations conforming to OWASP guidelines)
- **Salt**: 16 cryptographically secure random bytes
- **Initialization Vector (IV)**: 12 cryptographically secure random bytes (unique per file)
- **Integrity & Authenticity**: 128-bit authentication tag appended to the ciphertext

### File Format (`.fct`)
```
+----------------+----------------+----------------+------------------------+
| Magic (4 bytes)| Salt (16 bytes)|  IV (12 bytes) | Ciphertext + Auth Tag  |
|    "FCT1"      | Cryptographic  |  AES-GCM IV    |   Encrypted Payload    |
+----------------+----------------+----------------+------------------------+
```

---

## 🚀 Getting Started

### Prerequisites
- **Desktop**: Java 17 or higher
- **Web**: Modern web browser (Chrome, Firefox, Safari, Edge)

### Desktop App (Java Swing)
#### Build & Run
On Windows:
```cmd
cd desktop
build.bat
```

On Linux / macOS:
```bash
cd desktop
chmod +x build.sh
./build.sh
```

To run the compiled desktop app:
```bash
java -jar desktop/dist/FileCrypt.jar
```

Or run headless via CLI:
```bash
java -jar desktop/dist/FileCrypt.jar encrypt <file> <password>
java -jar desktop/dist/FileCrypt.jar decrypt <file.fct> <password>
```

### Web App
Open `web/index.html` in your browser or serve statically using any web server:
```bash
npx serve web
```

---

## 🧪 Self-Tests & Interoperability

- **Java Self-Tests**: Run `com.filecrypt.SelfTest` to verify round-trip encryption across varied payloads (0B, 1B, 1KB, 5MB), corruption resistance, and wrong-password rejection.
- **Cross-Platform Interoperability**: Run `web/test/interop.mjs` with Node.js to verify bidirectional compatibility between Java and Web Crypto.

---

## 📄 License
This project is open-source. See the repository for details.
>>>>>>> b1ef0e8 (Initial commit: FileCrypt AES-256-GCM desktop and web application)
