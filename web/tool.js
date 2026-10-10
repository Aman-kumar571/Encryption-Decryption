// FileCrypt Browser Tool Controller
import { encrypt, decrypt } from './crypto.js';

const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const fileInfo = document.getElementById('fileInfo');
const fileNameDisplay = document.getElementById('fileName');
const fileSizeDisplay = document.getElementById('fileSize');

const passwordInput = document.getElementById('password');
const confirmPasswordGroup = document.getElementById('confirmPasswordGroup');
const confirmPasswordInput = document.getElementById('confirmPassword');
const showPasswordCheckbox = document.getElementById('showPassword');

const encryptBtn = document.getElementById('encryptBtn');
const decryptBtn = document.getElementById('decryptBtn');
const progressBar = document.getElementById('progressBar');
const statusLog = document.getElementById('statusLog');

let selectedFile = null;
const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

function log(message) {
    statusLog.textContent += message + '\n';
    statusLog.scrollTop = statusLog.scrollHeight;
}

function clearLog() {
    statusLog.textContent = '';
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function updateFileSelection(file) {
    if (!file) {
        selectedFile = null;
        fileInfo.classList.add('hidden');
        return;
    }

    if (file.size > MAX_FILE_SIZE) {
        alert("File exceeds the 100 MB in-browser limit. Please use the FileCrypt desktop app for large files.");
        fileInput.value = '';
        selectedFile = null;
        fileInfo.classList.add('hidden');
        return;
    }

    selectedFile = file;
    fileNameDisplay.textContent = file.name;
    fileSizeDisplay.textContent = formatBytes(file.size);
    fileInfo.classList.remove('hidden');

    if (file.name.endsWith('.fct')) {
        confirmPasswordGroup.classList.add('hidden');
    } else {
        confirmPasswordGroup.classList.remove('hidden');
    }
}

// Drag & drop handlers
dropZone.addEventListener('click', () => fileInput.click());
dropZone.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        fileInput.click();
    }
});

dropZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => {
    dropZone.classList.remove('drag-over');
});

dropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    if (e.dataTransfer.files.length > 0) {
        updateFileSelection(e.dataTransfer.files[0]);
    }
});

fileInput.addEventListener('change', () => {
    if (fileInput.files.length > 0) {
        updateFileSelection(fileInput.files[0]);
    }
});

showPasswordCheckbox.addEventListener('change', () => {
    const type = showPasswordCheckbox.checked ? 'text' : 'password';
    passwordInput.type = type;
    confirmPasswordInput.type = type;
});

function setProcessing(isProcessing) {
    encryptBtn.disabled = isProcessing;
    decryptBtn.disabled = isProcessing;
    fileInput.disabled = isProcessing;
    passwordInput.disabled = isProcessing;
    confirmPasswordInput.disabled = isProcessing;
    if (isProcessing) {
        progressBar.classList.add('active', 'indeterminate');
    } else {
        progressBar.classList.remove('active', 'indeterminate');
    }
}

function triggerDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

async function handleAction(isEncrypt) {
    clearLog();
    if (!selectedFile) {
        log("Please select a file first.");
        return;
    }

    const password = passwordInput.value;
    if (isEncrypt) {
        if (password.length < 8) {
            log("Password must be at least 8 characters.");
            return;
        }
        const confirm = confirmPasswordInput.value;
        if (password !== confirm) {
            log("Passwords do not match.");
            return;
        }
    } else {
        if (password.length === 0) {
            log("Please enter the decryption password.");
            return;
        }
    }

    setProcessing(true);
    const actionLabel = isEncrypt ? "Encrypting" : "Decrypting";
    log(`${actionLabel} ${selectedFile.name}...`);

    try {
        const fileBuffer = await selectedFile.arrayBuffer();
        const fileBytes = new Uint8Array(fileBuffer);

        let resultBytes;
        if (isEncrypt) {
            resultBytes = await encrypt(fileBytes, password);
            const outName = selectedFile.name + ".fct";
            const blob = new Blob([resultBytes], { type: "application/octet-stream" });
            triggerDownload(blob, outName);
            log(`Success. Downloaded ${outName}`);
        } else {
            resultBytes = await decrypt(fileBytes, password);
            let outName;
            if (selectedFile.name.endsWith('.fct')) {
                outName = selectedFile.name.slice(0, -4);
            } else {
                outName = selectedFile.name + ".decrypted";
            }
            const blob = new Blob([resultBytes], { type: "application/octet-stream" });
            triggerDownload(blob, outName);
            log(`Success. Downloaded ${outName}`);
        }
    } catch (err) {
        log(err.message || "An unexpected error occurred.");
    } finally {
        passwordInput.value = '';
        confirmPasswordInput.value = '';
        setProcessing(false);
    }
}

encryptBtn.addEventListener('click', () => {
    confirmPasswordGroup.classList.remove('hidden');
    handleAction(true);
});

decryptBtn.addEventListener('click', () => {
    handleAction(false);
});