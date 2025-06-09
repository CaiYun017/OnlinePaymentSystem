package GUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OnlinePaymentSystemGUI extends JFrame {
    // GUI Components
    private JTextField accountField;
    private JTextField amountField;
    private JTextField recipientField;
    private JTextArea logArea;
    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private JProgressBar progressBar;
    private JLabel balanceLabel;
    private JButton transferButton;
    private JButton batchProcessButton;
    private JButton stopButton;

    // Multi-threading and Concurrency
    private ExecutorService executorService;
    private final Lock balanceLock = new ReentrantLock();
    private volatile boolean isProcessing = false;
    private CompletableFuture<Void> currentBatchTask;

    // Data
    private volatile double currentBalance = 10000.0;
    private final BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>();
    private final List<Transaction> completedTransactions = new ArrayList<>();

    public OnlinePaymentSystemGUI() {
        initializeGUI();
        initializeThreads();
        startTransactionProcessor();
    }

    private void initializeGUI() {
        setTitle("Online Payment System - Concurrent Processing Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create and organize panels
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
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        mainPanel.setBorder(BorderFactory.createTitledBorder("Payment Operations"));

        // Sender
        gbc.gridx = 0; gbc.gridy = 0;
        mainPanel.add(new JLabel("Sender Account:"), gbc);
        gbc.gridx = 1;
        accountField = new JTextField("ACC001", 15);
        mainPanel.add(accountField, gbc);

        // Recipient
        gbc.gridx = 0; gbc.gridy = 1;
        mainPanel.add(new JLabel("Recipient Account:"), gbc);
        gbc.gridx = 1;
        recipientField = new JTextField(15);
        mainPanel.add(recipientField, gbc);

        // Amount
        gbc.gridx = 0; gbc.gridy = 2;
        mainPanel.add(new JLabel("Transfer Amount:"), gbc);
        gbc.gridx = 1;
        amountField = new JTextField(15);
        mainPanel.add(amountField, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        transferButton = new JButton("Single Transfer");
        batchProcessButton = new JButton("Batch Processing");
        stopButton = new JButton("Stop Processing");
        stopButton.setEnabled(false);

        transferButton.addActionListener(e -> processSingleTransfer());
        batchProcessButton.addActionListener(e -> startBatchProcessing());
        stopButton.addActionListener(e -> stopProcessing());

        buttonPanel.add(transferButton);
        buttonPanel.add(batchProcessButton);
        buttonPanel.add(stopButton);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        mainPanel.add(buttonPanel, gbc);

        return mainPanel;
    }

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        balanceLabel = new JLabel("Current Balance: $0.00");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");

        statusPanel.add(balanceLabel);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(new JLabel("Progress:"));
        statusPanel.add(progressBar);

        return statusPanel;
    }

    private JScrollPane createTransactionPanel() {
        JPanel transactionPanel = new JPanel(new BorderLayout());
        transactionPanel.setBorder(BorderFactory.createTitledBorder("Transaction History"));

        String[] columns = {"Time", "Type", "Sender", "Recipient", "Amount", "Status", "Thread ID"};
        tableModel = new DefaultTableModel(columns, 0);
        transactionTable = new JTable(tableModel);
        transactionTable.setPreferredScrollableViewportSize(new Dimension(600, 200));

        JScrollPane scrollPane = new JScrollPane(transactionTable);
        transactionPanel.add(scrollPane, BorderLayout.CENTER);
        return scrollPane;
    }

    private JScrollPane createLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("System Log"));

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);

        logPanel.add(logScrollPane, BorderLayout.CENTER);
        return logScrollPane;
    }

    private void initializeThreads() {
        executorService = Executors.newFixedThreadPool(5);
        addLog("Thread pool initialized with size: 5");
    }

    private void startTransactionProcessor() {
        Thread processorThread = new Thread(new TransactionProcessor());
        processorThread.setName("TransactionProcessor");
        processorThread.setDaemon(true);
        processorThread.start();
        addLog("Transaction processor thread started");
    }

    private void processSingleTransfer() {
        try {
            String recipient = recipientField.getText().trim();
            String amountStr = amountField.getText().trim();

            if (recipient.isEmpty() || amountStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all information");
                return;
            }

            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "Amount must be greater than 0");
                return;
            }

            Transaction transaction = new Transaction(
                    accountField.getText(),
                    recipient,
                    amount,
                    "Transfer"
            );

            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                    processTransaction(transaction), executorService);

            future.thenAccept(success -> SwingUtilities.invokeLater(() -> {
                if (success) {
                    clearFields();
                    addLog("Transfer successful: " + transaction.toString());
                } else {
                    addLog("Transfer failed: Insufficient funds");
                }
            }));

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid amount");
        }
    }

    private void startBatchProcessing() {
        if (isProcessing) {
            JOptionPane.showMessageDialog(this, "Batch processing is already in progress");
            return;
        }

        isProcessing = true;
        transferButton.setEnabled(false);
        batchProcessButton.setEnabled(false);
        stopButton.setEnabled(true);

        currentBatchTask = CompletableFuture.runAsync(() -> {
            try {
                addLog("Starting batch processing...");
                List<Transaction> batchTransactions = generateBatchTransactions(20);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setMaximum(batchTransactions.size());
                    progressBar.setValue(0);
                });

                int successCount = batchTransactions.parallelStream()
                        .filter(t -> t.getAmount() > 0)
                        .mapToInt(this::processTransactionWithDelay)
                        .reduce(0, (count, success) -> {
                            if (success == 1) {
                                SwingUtilities.invokeLater(() -> {
                                    progressBar.setValue(progressBar.getValue() + 1);
                                    progressBar.setString("Processing... " +
                                            (progressBar.getValue() * 100 / progressBar.getMaximum()) + "%");
                                });
                                return count + 1;
                            }
                            return count;
                        });

                addLog("Successfully processed transactions: " + successCount);
                addLog("Batch processing completed");

            } catch (Exception e) {
                addLog("Batch processing error: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    isProcessing = false;
                    transferButton.setEnabled(true);
                    batchProcessButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    progressBar.setString("Completed");
                });
            }
        }, executorService);
    }

    private void stopProcessing() {
        if (currentBatchTask != null && !currentBatchTask.isDone()) {
            currentBatchTask.cancel(true);
            addLog("Batch processing stopped");
        }
        isProcessing = false;
        transferButton.setEnabled(true);
        batchProcessButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private boolean processTransaction(Transaction transaction) {
        balanceLock.lock();
        try {
            if (currentBalance >= transaction.getAmount()) {
                Thread.sleep(100 + new Random().nextInt(200));

                currentBalance -= transaction.getAmount();
                transaction.setStatus("Success");
                transaction.setThreadId(Thread.currentThread().getName());

                SwingUtilities.invokeLater(() -> {
                    addTransactionToTable(transaction);
                    updateBalanceDisplay();
                });

                completedTransactions.add(transaction);
                return true;
            } else {
                transaction.setStatus("Failed - Insufficient Funds");
                transaction.setThreadId(Thread.currentThread().getName());

                SwingUtilities.invokeLater(() -> addTransactionToTable(transaction));
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            balanceLock.unlock();
        }
    }

    private int processTransactionWithDelay(Transaction transaction) {
        try {
            Thread.sleep(50);
            return processTransaction(transaction) ? 1 : 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private List<Transaction> generateBatchTransactions(int count) {
        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random();
        String[] recipients = {"ACC002", "ACC003", "ACC004", "ACC005", "ACC006"};

        for (int i = 0; i < count; i++) {
            transactions.add(new Transaction(
                    accountField.getText(),
                    recipients[random.nextInt(recipients.length)],
                    50 + random.nextDouble() * 500,
                    "Batch Transfer"
            ));
        }
        return transactions;
    }

    private void addTransactionToTable(Transaction transaction) {
        Object[] row = {
                transaction.getTimestamp(),
                transaction.getType(),
                transaction.getSender(),
                transaction.getRecipient(),
                String.format("$%.2f", transaction.getAmount()),
                transaction.getStatus(),
                transaction.getThreadId()
        };
        tableModel.addRow(row);

        int lastRow = transactionTable.getRowCount() - 1;
        transactionTable.scrollRectToVisible(transactionTable.getCellRect(lastRow, 0, true));
    }

    private void updateBalanceDisplay() {
        balanceLabel.setText(String.format("Current Balance: $%.2f", currentBalance));
    }

    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append(String.format("[%s] %s\n", timestamp, message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void clearFields() {
        recipientField.setText("");
        amountField.setText("");
    }

    private class TransactionProcessor implements Runnable {
        @Override
        public void run() {
            addLog("Transaction processor started on thread: " + Thread.currentThread().getName());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Transaction transaction = transactionQueue.poll(1, TimeUnit.SECONDS);
                    if (transaction != null) {
                        processTransaction(transaction);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            addLog("Transaction processor thread ended");
        }
    }

    // Inner Transaction class
    public static class Transaction {
        private final String sender;
        private final String recipient;
        private final double amount;
        private final String type;
        private String status;
        private final String timestamp;
        private String threadId;

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
            return type + " | $" + amount + " | To: " + recipient + " | Status: " + status;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OnlinePaymentSystemGUI().setVisible(true));
    }
}
