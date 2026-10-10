package com.filecrypt;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Entry point for FileCrypt.
 *
 * Two modes of operation:
 *   1. GUI mode (no arguments): opens the Swing interface on the EDT.
 *   2. CLI mode: "encrypt <file> <password>" or "decrypt <file> <password>"
 *      for headless operation (useful for testing and scripting).
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            // GUI mode: launch the Swing window on the Event Dispatch Thread
            SwingUtilities.invokeLater(() -> {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            });
        } else if (args.length == 3) {
            // CLI mode: encrypt or decrypt a single file
            runCli(args);
        } else {
            System.err.println("Usage:");
            System.err.println("  GUI mode : java -jar FileCrypt.jar");
            System.err.println("  CLI mode : java -jar FileCrypt.jar encrypt <file> <password>");
            System.err.println("             java -jar FileCrypt.jar decrypt <file> <password>");
            System.exit(1);
        }
    }

    /**
     * Runs encryption or decryption from the command line.
     * Prints progress to stdout and exits with code 0 on success, 1 on failure.
     */
    private static void runCli(String[] args) {
        String command = args[0].toLowerCase();
        Path inputPath = Path.of(args[1]);
        char[] password = args[2].toCharArray();

        if (!command.equals("encrypt") && !command.equals("decrypt")) {
            System.err.println("Unknown command: " + command);
            System.err.println("Use 'encrypt' or 'decrypt'.");
            Arrays.fill(password, '\0');
            System.exit(1);
        }

        if (!Files.exists(inputPath)) {
            System.err.println("File not found: " + inputPath);
            Arrays.fill(password, '\0');
            System.exit(1);
        }

        boolean encrypting = command.equals("encrypt");
        Path outputPath;

        if (encrypting) {
            outputPath = inputPath.resolveSibling(inputPath.getFileName() + ".fct");
        } else {
            String name = inputPath.getFileName().toString();
            if (name.endsWith(".fct")) {
                outputPath = inputPath.resolveSibling(name.substring(0, name.length() - 4));
            } else {
                outputPath = inputPath.resolveSibling(name + ".decrypted");
            }
        }

        CryptoService service = new CryptoService();
        ProgressListener listener = (processed, total) -> {
            if (total > 0) {
                int percent = (int) (processed * 100 / total);
                System.out.printf("\r%s: %d%%", encrypting ? "Encrypting" : "Decrypting", percent);
            }
        };

        try {
            if (encrypting) {
                service.encrypt(inputPath, outputPath, password, listener);
            } else {
                service.decrypt(inputPath, outputPath, password, listener);
            }
            System.out.println();
            System.out.println("Output: " + outputPath);
        } catch (Exception e) {
            System.out.println();
            System.err.println("Error: " + e.getMessage());
            // Clean up partial output
            try { Files.deleteIfExists(outputPath); } catch (Exception ignored) {}
            Arrays.fill(password, '\0');
            System.exit(1);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}