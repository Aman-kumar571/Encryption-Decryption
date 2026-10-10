package com.filecrypt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Main GUI window for FileCrypt.
 *
 * Layout:
 *   Top    - File list with Add Files button and drag-and-drop support
 *   Middle - Password fields, show password checkbox, encrypt/decrypt buttons
 *   Bottom - Progress bar and a read-only status log
 *
 * All encryption and decryption work runs in a SwingWorker so the UI
 * stays responsive. Buttons are disabled while an operation is in progress.
 */
public class MainFrame extends JFrame {

    private final DefaultListModel<File> fileListModel;
    private final JList<File> fileList;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;
    private final JLabel confirmLabel;
    private final JCheckBox showPasswordCheckbox;
    private final JButton encryptButton;
    private final JButton decryptButton;
    private final JButton addFilesButton;
    private final JProgressBar progressBar;
    private final JTextArea statusArea;

    private final CryptoService cryptoService = new CryptoService();

    public MainFrame() {
        super("FileCrypt");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(null);

        // Try to use the native system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default look and feel
        }

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // --- Top: File list with Add Files button ---
        JPanel filePanel = new JPanel(new BorderLayout(4, 4));
        filePanel.setBorder(BorderFactory.createTitledBorder("Files"));

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File f) {
                    setText(f.getName() + "  (" + f.getParent() + ")");
                }
                return this;
            }
        });

        // Enable drag-and-drop for adding files
        fileList.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    Transferable t = support.getTransferable();
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (file.isFile() && !containsFile(file)) {
                            fileListModel.addElement(file);
                        }
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(0, 120));
        filePanel.add(fileScroll, BorderLayout.CENTER);

        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addFilesButton = new JButton("Add Files");
        addFilesButton.addActionListener(e -> addFiles());
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> removeSelectedFiles());
        fileButtonPanel.add(addFilesButton);
        fileButtonPanel.add(removeButton);
        filePanel.add(fileButtonPanel, BorderLayout.SOUTH);

        mainPanel.add(filePanel, BorderLayout.NORTH);

        // --- Middle: Password fields and action buttons ---
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Password
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        controlPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        passwordField = new JPasswordField(20);
        controlPanel.add(passwordField, gbc);

        // Confirm password
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        confirmLabel = new JLabel("Confirm:");
        controlPanel.add(confirmLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        confirmPasswordField = new JPasswordField(20);
        controlPanel.add(confirmPasswordField, gbc);

        // Show password checkbox
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        showPasswordCheckbox = new JCheckBox("Show password");
        showPasswordCheckbox.addActionListener(e -> {
            char echoChar = showPasswordCheckbox.isSelected() ? (char) 0 : '\u2022';
            passwordField.setEchoChar(echoChar);
            confirmPasswordField.setEchoChar(echoChar);
        });
        controlPanel.add(showPasswordCheckbox, gbc);

        // Buttons
        gbc.gridy = 3; gbc.gridwidth = 1;
        gbc.gridx = 0;
        encryptButton = new JButton("Encrypt");
        encryptButton.addActionListener(e -> runCrypto(true));
        controlPanel.add(encryptButton, gbc);
        gbc.gridx = 1;
        decryptButton = new JButton("Decrypt");
        decryptButton.addActionListener(e -> runCrypto(false));
        controlPanel.add(decryptButton, gbc);

        mainPanel.add(controlPanel, BorderLayout.CENTER);

        // --- Bottom: Progress bar and status log ---
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        statusArea = new JTextArea(8, 40);
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        bottomPanel.add(statusScroll, BorderLayout.CENTER);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
    }

    /** Checks if the file is already in the list. */
    private boolean containsFile(File file) {
        for (int i = 0; i < fileListModel.size(); i++) {
            if (fileListModel.get(i).equals(file)) {
                return true;
            }
        }
        return false;
    }

    /** Opens a file chooser so the user can pick files to add. */
    private void addFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                if (!containsFile(file)) {
                    fileListModel.addElement(file);
                }
            }
        }
    }

    /** Removes the selected files from the list. */
    private void removeSelectedFiles() {
        int[] indices = fileList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) {
            fileListModel.remove(indices[i]);
        }
    }

    /** Logs a message to the status area. */
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    /** Sets all interactive controls enabled or disabled. */
    private void setControlsEnabled(boolean enabled) {
        encryptButton.setEnabled(enabled);
        decryptButton.setEnabled(enabled);
        addFilesButton.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
        showPasswordCheckbox.setEnabled(enabled);
    }

    /**
     * Starts the encryption or decryption process in a background thread.
     *
     * Validates inputs first (files selected, password length, confirmation
     * match for encryption). Then processes each file in a SwingWorker.
     *
     * @param encrypting true for encryption, false for decryption
     */
    private void runCrypto(boolean encrypting) {
        if (fileListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add at least one file.",
                    "No files", JOptionPane.WARNING_MESSAGE);
            return;
        }

        char[] password = passwordField.getPassword();
        if (encrypting) {
            if (password.length < 8) {
                Arrays.fill(password, '\0');
                JOptionPane.showMessageDialog(this,
                        "Password must be at least 8 characters.",
                        "Password too short", JOptionPane.WARNING_MESSAGE);
                return;
            }
            char[] confirm = confirmPasswordField.getPassword();
            if (!Arrays.equals(password, confirm)) {
                Arrays.fill(password, '\0');
                Arrays.fill(confirm, '\0');
                JOptionPane.showMessageDialog(this,
                        "Passwords do not match.",
                        "Mismatch", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Arrays.fill(confirm, '\0');
        }

        // Collect files to process
        File[] files = new File[fileListModel.size()];
        for (int i = 0; i < fileListModel.size(); i++) {
            files[i] = fileListModel.get(i);
        }

        setControlsEnabled(false);
        progressBar.setValue(0);

        // Run crypto in a background thread
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    for (int i = 0; i < files.length; i++) {
                        File file = files[i];
                        Path inputPath = file.toPath();
                        Path outputPath;

                        if (encrypting) {
                            outputPath = inputPath.resolveSibling(file.getName() + ".fct");
                        } else {
                            String name = file.getName();
                            if (name.endsWith(".fct")) {
                                outputPath = inputPath.resolveSibling(
                                        name.substring(0, name.length() - 4));
                            } else {
                                outputPath = inputPath.resolveSibling(name + ".decrypted");
                            }
                        }

                        // Check if output file exists and ask user
                        if (Files.exists(outputPath)) {
                            int choice = JOptionPane.showConfirmDialog(MainFrame.this,
                                    "File already exists:\n" + outputPath.getFileName()
                                            + "\n\nOverwrite?",
                                    "File exists", JOptionPane.YES_NO_OPTION);
                            if (choice != JOptionPane.YES_OPTION) {
                                publish("Skipped: " + file.getName());
                                continue;
                            }
                        }

                        String action = encrypting ? "Encrypting" : "Decrypting";
                        publish(action + ": " + file.getName());

                        ProgressListener listener = (processed, total) -> {
                            if (total > 0) {
                                int percent = (int) (processed * 100 / total);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(percent));
                            }
                        };

                        try {
                            if (encrypting) {
                                cryptoService.encrypt(inputPath, outputPath, password, listener);
                            } else {
                                cryptoService.decrypt(inputPath, outputPath, password, listener);
                            }
                            publish("Done: " + outputPath.getFileName());
                        } catch (Exception e) {
                            publish("Error: " + e.getMessage());
                            // Clean up partial output
                            try { Files.deleteIfExists(outputPath); } catch (Exception ignored) {}
                        }
                    }
                } finally {
                    // Zero the password array
                    Arrays.fill(password, '\0');
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    log(msg);
                }
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                progressBar.setValue(100);
                log("All operations complete.");
            }
        };

        worker.execute();
    }
}