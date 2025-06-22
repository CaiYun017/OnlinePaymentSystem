package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

public class UserMainFrame extends JFrame {
    private User user;
    private JLabel lblBal;
    private Timer autoRefreshTimer;

    private static final String SERVER_IP = "172.20.10.2";
    private static final int SERVER_PORT = 9999;

    public UserMainFrame(User user) {
        this.user = user;
        setTitle("User Dashboard - Online Payment System");
        setSize(600, 450);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header panel
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(30, 144, 255));
        JLabel welcomeLabel = new JLabel("Welcome, " + user.getFullName());
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        welcomeLabel.setForeground(Color.WHITE);
        headerPanel.add(welcomeLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Center content
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(245, 245, 245));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 20, 15, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel accLabel = new JLabel("Account No: " + user.getAccountNo());
        accLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        centerPanel.add(accLabel, gbc);

        lblBal = new JLabel("Balance: RM " + String.format("%.2f", user.getBalance()));
        lblBal.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        gbc.gridy = 1;
        centerPanel.add(lblBal, gbc);

        JButton btnTrans = createStyledButton("Transfer", new Color(70, 130, 180));
        btnTrans.addActionListener(e -> new TransferFrame(user, this));
        gbc.gridy = 2; gbc.gridwidth = 1;
        centerPanel.add(btnTrans, gbc);

        JButton btnW = createStyledButton("Withdraw", new Color(255, 140, 0));
        btnW.addActionListener(e -> new WithdrawDepositFrame(user, this, "withdraw"));
        gbc.gridx = 1; gbc.gridy = 2;
        centerPanel.add(btnW, gbc);

        JButton btnD = createStyledButton("Deposit", new Color(46, 139, 87));
        btnD.addActionListener(e -> new WithdrawDepositFrame(user, this, "deposit"));
        gbc.gridx = 0; gbc.gridy = 3;
        centerPanel.add(btnD, gbc);

        JButton btnView = createStyledButton("View Transactions", new Color(128, 0, 128));
        btnView.addActionListener(e -> new TransactionViewer(user).setVisible(true));
        gbc.gridx = 1; gbc.gridy = 3;
        centerPanel.add(btnView, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // Auto refresh every 10 seconds
        startAutoBalanceRefresh();

        // Stop refresh when window is closed
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
            }
        });

        setVisible(true);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(180, 40));
        return button;
    }

    public void updateBalance() {
        try {
            for (User u : FileHandler.readUsers()) {
                if (u.getAccountNo().equals(user.getAccountNo())) {
                    user.setBalance(u.getBalance());
                    break;
                }
            }
            lblBal.setText("Balance: RM " + String.format("%.2f", user.getBalance()));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to update balance: " + e.getMessage());
        }
    }

    private void startAutoBalanceRefresh() {
        autoRefreshTimer = new Timer(5000, e -> refreshBalanceFromServer());
        autoRefreshTimer.start();
    }

    private void refreshBalanceFromServer() {
        SwingUtilities.invokeLater(() -> {
            try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println("GET_BALANCE");
                out.println(user.getAccountNo());
                out.println();

                String response = in.readLine();
                if (response != null && response.matches("\\d+(\\.\\d+)?")) {
                    double updated = Double.parseDouble(response);
                    user.setBalance(updated);
                    lblBal.setText("Balance: RM " + String.format("%.2f", updated));
                }
            } catch (IOException ex) {
                lblBal.setText("Balance: RM [Error]");
            }
        });
    }
}
