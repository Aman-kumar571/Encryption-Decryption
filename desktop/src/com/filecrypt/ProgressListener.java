package com.filecrypt;

/**
 * Callback interface for reporting progress during encryption or decryption.
 * Implementations receive the number of bytes processed so far and the total
 * number of bytes, allowing a UI or CLI to display a progress indicator.
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Called periodically as bytes are processed.
     *
     * @param bytesProcessed number of bytes processed so far
     * @param totalBytes     total number of bytes to process
     */
    void onProgress(long bytesProcessed, long totalBytes);
}