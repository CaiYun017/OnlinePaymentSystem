package client;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LoginFrame extends JFrame {
    private static final String SERVER_IP = "172.20.10.2";
    private static final int SERVER_PORT = 9999;

    public LoginFrame() {
        setTitle("Online Payment System - Login");
        setSize(450, 300);
        setUndecorated(true);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(30, 144, 255));
        JLabel titleLabel = new JLabel("Online Payment System");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(new Color(245, 245, 245));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblUsername = new JLabel("Username:");
        JTextField tfUser = new JTextField();
        JLabel lblPassword = new JLabel("Password:");
        JPasswordField tfPass = new JPasswordField();

        lblUsername.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tfUser.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblPassword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tfPass.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(lblUsername, gbc);
        gbc.gridx = 1; gbc.gridy = 0; formPanel.add(tfUser, gbc);
        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(lblPassword, gbc);
        gbc.gridx = 1; gbc.gridy = 1; formPanel.add(tfPass, gbc);

        JButton btnLogin = new JButton("Login");
        JButton btnRegister = new JButton("Register");

        btnLogin.setBackground(new Color(60, 179, 113));
        btnLogin.setForeground(Color.WHITE);
        btnRegister.setBackground(new Color(70, 130, 180));
        btnRegister.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        formPanel.add(buttonPanel, gbc);

        add(formPanel, BorderLayout.CENTER);

        setOpacity(0f);
        setVisible(true);

        Timer fadeInTimer = new Timer(20, e -> {
            float opacity = getOpacity();
            opacity += 0.05f;
            if (opacity >= 1f) {
                setOpacity(1f);
                ((Timer) e.getSource()).stop();
            } else {
                setOpacity(opacity);
            }
        });
        fadeInTimer.start();

        btnLogin.addActionListener((ActionEvent e) -> {
            String username = tfUser.getText().trim();
            String password = new String(tfPass.getPassword()).trim();
            String hashedPassword = hashPassword(password);

            User user = authenticateUser(username, hashedPassword);
            if (user != null) {
                JOptionPane.showMessageDialog(this, "Login successful! Welcome " + user.fullName);
                new UserMainFrame(user).setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Login failed.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRegister.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                new RegisterFrame().setVisible(true);
                dispose();
            });
        });

    }

    private User authenticateUser(String username, String hashedPassword) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("AUTHENTICATE_USER");
            out.println(username);
            out.println(hashedPassword);
            out.println();

            String response = in.readLine();
            if ("AUTH_SUCCESS".equalsIgnoreCase(response)) {
                String accNo = in.readLine();
                String role = in.readLine();
                String uname = in.readLine();
                String pass = in.readLine();
                String fullName = in.readLine();
                String balance = in.readLine();

                return new User(accNo, role, uname, pass, fullName, Double.parseDouble(balance));
            }
        } catch (IOException e) {
            System.err.println("Login error: " + e.getMessage());
        }
        return null;
    }

    public static String hashPassword(String password) {
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


