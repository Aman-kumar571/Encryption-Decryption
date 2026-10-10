// Interoperability Test between Web Crypto (crypto.js) and Java (desktop)
import { encrypt as webEncrypt, decrypt as webDecrypt } from '../crypto.js';
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '../../');
const desktopDir = path.join(rootDir, 'desktop');

const testPassword = "InteropPassword123#";
const testMessage = "FileCrypt Interoperability Test Payload: 1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()_+";

async function run() {
    console.log("=== Testing Web -> Java Interoperability ===");
    const textEncoder = new TextEncoder();
    const textDecoder = new TextDecoder();
    const originalBytes = textEncoder.encode(testMessage);

    // 1. Web Encrypt
    const webEncrypted = await webEncrypt(originalBytes, testPassword);
    const webEncFile = path.join(desktopDir, 'web_encrypted.txt.fct');
    fs.writeFileSync(webEncFile, webEncrypted);

    // 2. Java Decrypt
    try {
        execSync(`java -cp "${path.join(desktopDir, 'build')}" com.filecrypt.Main decrypt "${webEncFile}" "${testPassword}"`, {
            cwd: rootDir,
            stdio: 'pipe'
        });
        const javaDecFile = path.join(desktopDir, 'web_encrypted.txt');
        const javaDecryptedBytes = fs.readFileSync(javaDecFile);
        const javaDecryptedText = textDecoder.decode(javaDecryptedBytes);

        if (javaDecryptedText === testMessage) {
            console.log("PASS: Web Encrypt -> Java Decrypt matched byte-for-byte!");
        } else {
            console.error("FAIL: Plaintext mismatch from Java decrypt!");
            process.exit(1);
        }

        // Cleanup
        if (fs.existsSync(webEncFile)) fs.unlinkSync(webEncFile);
        if (fs.existsSync(javaDecFile)) fs.unlinkSync(javaDecFile);
    } catch (err) {
        console.error("FAIL: Error running Java decrypt:", err.message);
        process.exit(1);
    }

    console.log("=== Testing Java -> Web Interoperability ===");
    // 3. Java Encrypt
    const javaPlainFile = path.join(desktopDir, 'java_plain.txt');
    fs.writeFileSync(javaPlainFile, testMessage, 'utf8');

    try {
        execSync(`java -cp "${path.join(desktopDir, 'build')}" com.filecrypt.Main encrypt "${javaPlainFile}" "${testPassword}"`, {
            cwd: rootDir,
            stdio: 'pipe'
        });

        const javaEncFile = path.join(desktopDir, 'java_plain.txt.fct');
        const javaEncBytes = new Uint8Array(fs.readFileSync(javaEncFile));

        // 4. Web Decrypt
        const webDecryptedBytes = await webDecrypt(javaEncBytes, testPassword);
        const webDecryptedText = textDecoder.decode(webDecryptedBytes);

        if (webDecryptedText === testMessage) {
            console.log("PASS: Java Encrypt -> Web Decrypt matched byte-for-byte!");
        } else {
            console.error("FAIL: Plaintext mismatch from Web decrypt!");
            process.exit(1);
        }

        // Cleanup
        if (fs.existsSync(javaPlainFile)) fs.unlinkSync(javaPlainFile);
        if (fs.existsSync(javaEncFile)) fs.unlinkSync(javaEncFile);
    } catch (err) {
        console.error("FAIL: Error running Web decrypt:", err.message);
        process.exit(1);
    }

    console.log("ALL INTEROPERABILITY TESTS PASSED.");
}

run().catch(err => {
    console.error(err);
    process.exit(1);
});