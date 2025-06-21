package client;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TransferFrame extends JFrame {
    private JTextField accountField, recipientField, recipientNameField, amountField;
    private JLabel balanceLabel;
    private JButton transferButton, exitButton;

    private ExecutorService executorService;
    private ScheduledExecutorService scheduler;
    private final Lock balanceLock = new ReentrantLock();

    private volatile double currentBalance;
    private User currentUser;
    private UserMainFrame userMainFrame;
    private static final String SERVER_IP = "172.20.10.2";
    private static final int SERVER_PORT = 9999;

    public TransferFrame(User user, UserMainFrame userMainFrame) {
        this.currentUser = user;
        this.currentBalance = user.getBalance();
        this.userMainFrame = userMainFrame;
        initializeGUI();
        initializeThreads();
        startAutoBalanceUpdater();
        setVisible(true);
    }

    private void initializeGUI() {
        setTitle("Transfer Money");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(230, 248, 255));
        setLayout(new BorderLayout(15, 10));

        JPanel transferPanel = new JPanel(new GridBagLayout());
        transferPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.BLUE), "Transfer Operations", TitledBorder.LEFT, TitledBorder.TOP));
        transferPanel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        transferPanel.add(new JLabel("Sender Account:"), gbc);
        gbc.gridx = 1;
        accountField = new JTextField(currentUser.getAccountNo(), 20);
        accountField.setEditable(false);
        transferPanel.add(accountField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        transferPanel.add(new JLabel("Recipient Account:"), gbc);
        gbc.gridx = 1;
        recipientField = new JTextField(20);
        recipientField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String acc = recipientField.getText().trim();
                String name = getRecipientNameFromServer(acc);
                recipientNameField.setText(name.isEmpty() ? "[Not Found]" : name);
            }
        });
        transferPanel.add(recipientField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        transferPanel.add(new JLabel("Recipient Name:"), gbc);
        gbc.gridx = 1;
        recipientNameField = new JTextField(20);
        recipientNameField.setEditable(false);
        transferPanel.add(recipientNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        transferPanel.add(new JLabel("Amount (RM):"), gbc);
        gbc.gridx = 1;
        amountField = new JTextField(20);
        transferPanel.add(amountField, gbc);

        gbc.gridx = 1; gbc.gridy = 4;
        JPanel buttonPanel = new JPanel();
        transferButton = new JButton("Transfer");
        exitButton = new JButton("Exit");
        transferButton.setBackground(new Color(70, 130, 180));
        transferButton.setForeground(Color.WHITE);
        exitButton.setBackground(Color.GRAY);
        exitButton.setForeground(Color.WHITE);

        transferButton.addActionListener(e -> processSingleTransfer());
        exitButton.addActionListener(e -> {
            stopAutoBalanceUpdater();
            dispose();
        });
        buttonPanel.add(transferButton);
        buttonPanel.add(exitButton);
        transferPanel.add(buttonPanel, gbc);

        add(transferPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        balanceLabel = new JLabel();
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 16));
        bottomPanel.setBackground(new Color(220, 240, 255));
        bottomPanel.add(balanceLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        updateBalanceDisplay();
    }

    private void initializeThreads() {
        executorService = Executors.newFixedThreadPool(2);
    }

    private void startAutoBalanceUpdater() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            double latest = getBalanceFromServer(currentUser.getAccountNo());
            if (latest >= 0) {
                currentBalance = latest;
                currentUser.setBalance(latest);
                SwingUtilities.invokeLater(this::updateBalanceDisplay);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void stopAutoBalanceUpdater() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void processSingleTransfer() {
        try {
            String recipient = recipientField.getText().trim();
            String recipientName = getRecipientNameFromServer(recipient);
            double amount = Double.parseDouble(amountField.getText().trim());

            if (recipient.isEmpty() || amount <= 0) {
                JOptionPane.showMessageDialog(this, "Please fill all fields correctly.");
                return;
            }
            if (recipientName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No such recipient account exists.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            executorService.submit(() -> {
                balanceLock.lock();
                try {
                    if (currentBalance >= amount) {
                        currentBalance -= amount;
                        currentUser.setBalance(currentBalance);

                        StringBuilder data = new StringBuilder();
                        data.append("SenderAccNo: ").append(currentUser.getAccountNo()).append("\n");
                        data.append("RecipientAccNo: ").append(recipient).append("\n");
                        data.append("Amount: ").append(String.format("%.2f", amount)).append("\n\n");

                        boolean success = sendTransferToServer(data.toString());

                        SwingUtilities.invokeLater(() -> {
                            updateBalanceDisplay();
                            if (success) {
                                JOptionPane.showMessageDialog(this, "Transfer successful.");
                                userMainFrame.updateBalance();
                                stopAutoBalanceUpdater();
                                dispose();
                            } else {
                                JOptionPane.showMessageDialog(this, "Server rejected the transfer.", "Warning", JOptionPane.WARNING_MESSAGE);
                            }
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Insufficient balance."));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    balanceLock.unlock();
                }
            });

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid amount entered.");
        }
    }

    private String getRecipientNameFromServer(String accNo) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("GET_RECIPIENT_NAME");
            out.println(accNo);
            out.println();
            String response = in.readLine();
            return response == null || response.equals("NOT_FOUND") ? "" : response;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private double getBalanceFromServer(String accNo) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("GET_BALANCE");
            out.println(accNo);
            out.println();
            String response = in.readLine();
            return response == null || response.equals("NOT_FOUND") ? -1 : Double.parseDouble(response);
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private boolean sendTransferToServer(String transferBlock) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("TRANSFER_REQUEST");
            out.print(transferBlock);
            out.println();

            String resp = in.readLine();
            if ("TRANSFER_SUCCESS".equalsIgnoreCase(resp)) {
                String newBalanceLine = in.readLine();
                try {
                    double updatedBalance = Double.parseDouble(newBalanceLine);
                    currentBalance = updatedBalance;
                    currentUser.setBalance(updatedBalance);
                } catch (NumberFormatException e) {
                    System.err.println("Failed to parse updated balance from server.");
                }
                return true;
            }
            return false;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateBalanceDisplay() {
        balanceLabel.setText("Current Balance: RM " + String.format("%.2f", currentBalance));
    }
}

