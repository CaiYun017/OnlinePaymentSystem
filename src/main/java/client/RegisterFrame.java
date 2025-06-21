package client;

import javax.swing.*;
import java.awt.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RegisterFrame extends JFrame {
    private float opacity = 0f;
    private Timer fadeInTimer;

    public RegisterFrame() {
        setTitle("Online Payment System - Register");
        setSize(500, 350);
        setUndecorated(true);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(30, 144, 255));
        JLabel titleLabel = new JLabel("Register New Account");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(245, 245, 245));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 12, 8, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblName = new JLabel("Full Name:");
        JTextField tfName = new JTextField(18);
        JLabel lblUser = new JLabel("Username:");
        JTextField tfUser = new JTextField(18);
        JLabel lblPass = new JLabel("Password:");
        JPasswordField tfPass = new JPasswordField(18);
        JLabel lblBal = new JLabel("Initial Balance:");
        JTextField tfBal = new JTextField(18);

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
        lblName.setFont(labelFont);
        lblUser.setFont(labelFont);
        lblPass.setFont(labelFont);
        lblBal.setFont(labelFont);
        tfName.setFont(labelFont);
        tfUser.setFont(labelFont);
        tfPass.setFont(labelFont);
        tfBal.setFont(labelFont);

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(lblName, gbc);
        gbc.gridx = 1; gbc.gridy = 0; formPanel.add(tfName, gbc);
        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(lblUser, gbc);
        gbc.gridx = 1; gbc.gridy = 1; formPanel.add(tfUser, gbc);
        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(lblPass, gbc);
        gbc.gridx = 1; gbc.gridy = 2; formPanel.add(tfPass, gbc);
        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(lblBal, gbc);
        gbc.gridx = 1; gbc.gridy = 3; formPanel.add(tfBal, gbc);

        JButton btnRegister = new JButton("Register");
        JButton btnBack = new JButton("Back");

        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setBackground(new Color(60, 179, 113));
        btnRegister.setForeground(Color.WHITE);

        btnBack.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnBack.setBackground(new Color(255, 99, 71));
        btnBack.setForeground(Color.WHITE);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(btnRegister);
        buttonPanel.add(btnBack);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        formPanel.add(buttonPanel, gbc);
        add(formPanel, BorderLayout.CENTER);

        setOpacity(0f);
        setVisible(true);
        fadeInTimer = new Timer(20, e -> {
            opacity += 0.05f;
            if (opacity >= 1f) {
                opacity = 1f;
                fadeInTimer.stop();
            }
            setOpacity(opacity);
        });
        fadeInTimer.start();

        // Register button action
        btnRegister.addActionListener(e -> {
            try {
                String fullName = tfName.getText().trim();
                String username = tfUser.getText().trim();
                String password = new String(tfPass.getPassword()).trim();
                String balanceStr = tfBal.getText().trim();

                if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || balanceStr.isEmpty()) {
                    throw new Exception("Please fill in all fields.");
                }

                double balance = Double.parseDouble(balanceStr);
                String acc = FileHandler.generateAccountNo();
                String hashedPassword = hashPassword(password);

                // Step 1: Check for duplicate username or fullname
                try (Socket socket = new Socket("172.20.10.2", 9999);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println("CHECK_DUPLICATE");
                    out.println(username);
                    out.println();

                    String response = in.readLine();
                    if ("DUPLICATE_FOUND".equals(response)) {
                        JOptionPane.showMessageDialog(this, "Username already exists.");
                        return;
                    }
                }

                // Step 2: Send registration request
                StringBuilder message = new StringBuilder();
                message.append("REGISTER_USER\n");
                message.append(acc).append(",user,")
                        .append(username).append(",")
                        .append(hashedPassword).append(",")
                        .append(fullName).append(",")
                        .append(balance).append("\n\n");

                boolean success = Connection.sendMessage(message.toString());
                if (success) {
                    JOptionPane.showMessageDialog(this, "Registration successful.");
                    dispose();
                    new LoginFrame();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to contact server.");
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid balance format.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        });

        // Back button action
        btnBack.addActionListener(e -> {
            dispose();
            new LoginFrame();
        });
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}
