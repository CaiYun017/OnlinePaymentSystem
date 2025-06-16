package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferFrame extends JFrame {
    private JTextField accountField, recipientField, amountField;
    private JTextArea logArea;
    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private JProgressBar progressBar;
    private JLabel balanceLabel;
    private JButton transferButton, batchProcessButton, stopButton, exitButton;

    private ExecutorService executorService;
    private final Lock balanceLock = new ReentrantLock();
    private volatile boolean isProcessing = false;
    private CompletableFuture<Void> currentBatchTask;

    private volatile double currentBalance;
    private User currentUser;
    private final BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>();
    private UserMainFrame userMainFrame;

    public TransferFrame(User user, UserMainFrame userMainFrame) {
        this.currentUser = user;
        this.currentBalance = user.getBalance();
        this.userMainFrame = userMainFrame;
        initializeGUI();
        initializeThreads();
        startTransactionProcessor();
        setVisible(true);
    }

    private void initializeGUI() {
        setTitle("Transfer Frame");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(createMainPanel(), BorderLayout.CENTER);
        topPanel.add(createStatusPanel(), BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        add(createTransactionPanel(), BorderLayout.CENTER);
        add(createLogPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        updateBalanceDisplay();
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        panel.setBorder(BorderFactory.createTitledBorder("Transfer Operations"));

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Sender Account:"), gbc);
        gbc.gridx = 1;
        accountField = new JTextField(currentUser.getAccountNo(), 15);
        accountField.setEditable(false);
        panel.add(accountField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Recipient Account:"), gbc);
        gbc.gridx = 1;
        recipientField = new JTextField(15);
        panel.add(recipientField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Amount:"), gbc);
        gbc.gridx = 1;
        amountField = new JTextField(15);
        panel.add(amountField, gbc);

        JPanel buttonPanel = new JPanel();
        transferButton = new JButton("Transfer");
        batchProcessButton = new JButton("Batch");
        stopButton = new JButton("Stop");
        exitButton = new JButton("Exit");

        stopButton.setEnabled(false);

        transferButton.addActionListener(e -> processSingleTransfer());
        batchProcessButton.addActionListener(e -> startBatchProcessing());
        stopButton.addActionListener(e -> stopProcessing());
        exitButton.addActionListener(e -> dispose());

        buttonPanel.add(transferButton);
        buttonPanel.add(batchProcessButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(exitButton);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        balanceLabel = new JLabel();
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        panel.add(balanceLabel);
        panel.add(Box.createHorizontalStrut(20));
        panel.add(new JLabel("Progress:"));
        panel.add(progressBar);
        return panel;
    }

    private JScrollPane createTransactionPanel() {
        String[] columns = {"Time", "Type", "Sender", "Recipient", "Amount", "Status", "Thread"};
        tableModel = new DefaultTableModel(columns, 0);
        transactionTable = new JTable(tableModel);
        return new JScrollPane(transactionTable);
    }

    private JScrollPane createLogPanel() {
        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        return new JScrollPane(logArea);
    }

    private void initializeThreads() {
        executorService = Executors.newFixedThreadPool(5);
        addLog("Executor initialized");
    }

    private void startTransactionProcessor() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Transaction tr = transactionQueue.poll(1, TimeUnit.SECONDS);
                    if (tr != null) processTransaction(tr);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void processSingleTransfer() {
        try {
            String recipient = recipientField.getText().trim();
            double amount = Double.parseDouble(amountField.getText().trim());

            if (recipient.isEmpty() || amount <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid input");
                return;
            }

            Transaction tx = new Transaction(accountField.getText(), recipient, amount, "Transfer");
            CompletableFuture.supplyAsync(() -> processTransaction(tx), executorService)
                    .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                        if (success) {
                            clearFields();
                            addLog("Transfer success: " + tx);
                            if (userMainFrame != null) userMainFrame.updateBalance();
                        } else {
                            addLog("Transfer failed");
                        }
                    }));

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter valid amount");
        }
    }

    private boolean processTransaction(Transaction tx) {
        balanceLock.lock();
        try {
            if (currentBalance >= tx.getAmount()) {
                Thread.sleep(100 + new Random().nextInt(100));
                currentBalance -= tx.getAmount();
                currentUser.setBalance(currentBalance);
                updateUserBalanceInFile();

                tx.setStatus("Success");
                tx.setThreadId(Thread.currentThread().getName());
                FileHandler.logTransaction("Transferred RM " + String.format("%.2f", tx.getAmount()) + " from " + tx.getSender() + " to " + tx.getRecipient() + " [Success]");

                SwingUtilities.invokeLater(() -> {
                    updateBalanceDisplay();
                    addTransactionToTable(tx);
                });
                return true;
            } else {
                tx.setStatus("Failed");
                tx.setThreadId(Thread.currentThread().getName());
                FileHandler.logTransaction("Attempted transfer of RM " + String.format("%.2f", tx.getAmount()) + " from " + tx.getSender() + " to " + tx.getRecipient() + " [Failed - Insufficient Balance]");

                SwingUtilities.invokeLater(() -> addTransactionToTable(tx));
                return false;
            }
        } catch (Exception e) {
            return false;
        } finally {
            balanceLock.unlock();
        }
    }

    private void startBatchProcessing() {
        if (isProcessing) return;
        isProcessing = true;
        transferButton.setEnabled(false);
        batchProcessButton.setEnabled(false);
        stopButton.setEnabled(true);

        currentBatchTask = CompletableFuture.runAsync(() -> {
            List<Transaction> list = new ArrayList<>();
            String[] recipients = {"ACC002", "ACC003", "ACC004"};
            Random rand = new Random();

            for (int i = 0; i < 10; i++) {
                list.add(new Transaction(accountField.getText(), recipients[rand.nextInt(recipients.length)], 50 + rand.nextDouble() * 200, "Batch"));
            }

            progressBar.setMaximum(list.size());
            progressBar.setValue(0);

            int count = 0;
            for (Transaction tx : list) {
                if (Thread.currentThread().isInterrupted()) break;
                if (processTransaction(tx)) count++;
                int finalCount = count;
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(finalCount);
                    progressBar.setString("Progress: " + finalCount);
                });
            }
            addLog("Batch complete");

            SwingUtilities.invokeLater(() -> {
                isProcessing = false;
                transferButton.setEnabled(true);
                batchProcessButton.setEnabled(true);
                stopButton.setEnabled(false);

                if (userMainFrame != null) {
                    userMainFrame.updateBalance();
                }
            });
            });
    }

    private void stopProcessing() {
        if (currentBatchTask != null) currentBatchTask.cancel(true);
        isProcessing = false;
        transferButton.setEnabled(true);
        batchProcessButton.setEnabled(true);
        stopButton.setEnabled(false);
        addLog("Batch stopped");
    }

    private void updateUserBalanceInFile() {
        try {
            List<User> users = FileHandler.readUsers();
            for (User u : users) {
                if (u.getAccountNo().equals(currentUser.getAccountNo())) {
                    u.setBalance(currentBalance);
                    break;
                }
            }
            FileHandler.writeUsers(users);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTransactionToTable(Transaction tx) {
        tableModel.addRow(new Object[]{
                tx.getTimestamp(), tx.getType(), tx.getSender(), tx.getRecipient(),
                String.format("RM %.2f", tx.getAmount()), tx.getStatus(), tx.getThreadId()
        });
    }

    private void updateBalanceDisplay() {
        balanceLabel.setText(String.format("Current Balance: RM %.2f", currentBalance));
    }

    private void addLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void clearFields() {
        recipientField.setText("");
        amountField.setText("");
    }

    public static class Transaction {
        private final String sender, recipient, type, timestamp;
        private final double amount;
        private String status, threadId;

        public Transaction(String sender, String recipient, double amount, String type) {
            this.sender = sender;
            this.recipient = recipient;
            this.amount = amount;
            this.type = type;
            this.status = "Processing";
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));
        }

        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public double getAmount() { return amount; }
        public String getType() { return type; }
        public String getStatus() { return status; }
        public String getTimestamp() { return timestamp; }
        public String getThreadId() { return threadId; }

        public void setStatus(String status) { this.status = status; }
        public void setThreadId(String threadId) { this.threadId = threadId; }

        @Override
        public String toString() {
            return type + " | RM" + String.format("%.2f", amount) + " | To: " + recipient + " | Status: " + status;
        }
    }
}