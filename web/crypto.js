// FileCrypt Web Crypto Module
// Shared between tool.js and tests

export const MAGIC = new Uint8Array([0x46, 0x43, 0x54, 0x31]); // "FCT1"
export const SALT_LENGTH = 16;
export const IV_LENGTH = 12;
export const HEADER_LENGTH = 32;
export const PBKDF2_ITERATIONS = 600000;

export async function deriveKey(password, salt) {
    const enc = new TextEncoder();
    const passKey = await crypto.subtle.importKey(
        "raw",
        enc.encode(password),
        { name: "PBKDF2" },
        false,
        ["deriveKey"]
    );

    return await crypto.subtle.deriveKey(
        {
            name: "PBKDF2",
            salt: salt,
            iterations: PBKDF2_ITERATIONS,
            hash: "SHA-256"
        },
        passKey,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt", "decrypt"]
    );
}

export async function encrypt(fileBytes, password) {
    const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
    const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
    const key = await deriveKey(password, salt);

    const ciphertextBuffer = await crypto.subtle.encrypt(
        {
            name: "AES-GCM",
            iv: iv
        },
        key,
        fileBytes
    );

    const ciphertext = new Uint8Array(ciphertextBuffer);
    const output = new Uint8Array(HEADER_LENGTH + ciphertext.length);

    output.set(MAGIC, 0);
    output.set(salt, 4);
    output.set(iv, 20);
    output.set(ciphertext, HEADER_LENGTH);

    return output;
}

export async function decrypt(fileBytes, password) {
    if (fileBytes.length < HEADER_LENGTH) {
        throw new Error("Wrong password, or the file is corrupted.");
    }

    for (let i = 0; i < 4; i++) {
        if (fileBytes[i] !== MAGIC[i]) {
            throw new Error("Wrong password, or the file is corrupted.");
        }
    }

    const salt = fileBytes.subarray(4, 20);
    const iv = fileBytes.subarray(20, 32);
    const ciphertext = fileBytes.subarray(HEADER_LENGTH);

    let key;
    try {
        key = await deriveKey(password, salt);
    } catch (e) {
        throw new Error("Wrong password, or the file is corrupted.");
    }

    try {
        const decryptedBuffer = await crypto.subtle.decrypt(
            {
                name: "AES-GCM",
                iv: iv
            },
            key,
            ciphertext
        );
        return new Uint8Array(decryptedBuffer);
    } catch (e) {
        throw new Error("Wrong password, or the file is corrupted.");
    }
}