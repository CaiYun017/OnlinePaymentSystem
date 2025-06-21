package admin;

import admin.AdminPanel;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class AdminLoginPage extends JFrame {
    private final JTextField adminIdField;
    private final JPasswordField passwordField;
    private final String ADMIN_FILE_PATH = "src/main/java/admins.txt";

    public AdminLoginPage() {
        setTitle("Admin Panel Login");
        setSize(700, 500); // Larger frame
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Title
        JLabel titleLabel = new JLabel("Admin Login", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        // Center Panel: Logo + Form
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 50, 10, 50));

        // Logo
        JLabel logoLabel = new JLabel();
        logoLabel.setHorizontalAlignment(JLabel.CENTER);
        try {
            ImageIcon icon = new ImageIcon("src/main/java/admin/adminPhoto.jpg");
            Image scaled = icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            logoLabel.setText("Logo");
        }
        centerPanel.add(logoLabel, BorderLayout.NORTH);

        // Form Panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 10, 15, 10); // More spacing
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JLabel idLabel = new JLabel("Admin ID:");
        idLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        formPanel.add(idLabel, gbc);

        gbc.gridx = 1;
        adminIdField = new JTextField();
        adminIdField.setPreferredSize(new Dimension(300, 40)); // Wider + taller
        formPanel.add(adminIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        formPanel.add(passLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(300, 40)); // Wider + taller
        formPanel.add(passwordField, gbc);

        centerPanel.add(formPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel();
        JButton loginButton = new JButton("Login");
        loginButton.setFont(new Font("Arial", Font.BOLD, 16));
        loginButton.setPreferredSize(new Dimension(140, 40));
        loginButton.addActionListener(e -> {
            try {
                performLogin();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error reading admin file: " + ex.getMessage(),
                        "File Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        buttonPanel.add(loginButton);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 30, 0));
        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Load admin credentials from admins.txt file
     * Expected format: adminId:password (one per line)
     * Example:
     * admin123:securepass
     * admin456:password123
     */
    private Map<String, String> loadAdminCredentials() throws IOException {
        Map<String, String> credentials = new HashMap<>();

        // Check if file exists
        if (!Files.exists(Paths.get(ADMIN_FILE_PATH))) {
            throw new FileNotFoundException("Admin credentials file not found: " + ADMIN_FILE_PATH);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(ADMIN_FILE_PATH))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip empty lines and comments (lines starting with #)
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Split by colon
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String adminId = parts[0].trim();
                    String password = parts[1].trim();
                    credentials.put(adminId, password);
                } else {
                    System.err.println("Invalid line format in " + ADMIN_FILE_PATH + ": " + line);
                }
            }
        }

        return credentials;
    }

    private void performLogin() throws IOException {
        String adminId = adminIdField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (adminId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter both Admin ID and Password.",
                    "Input Required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Map<String, String> credentials = loadAdminCredentials();

            if (credentials.containsKey(adminId) && credentials.get(adminId).equals(password)) {
                JOptionPane.showMessageDialog(this, "Login successful!");
                new AdminPanel().setVisible(true);
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Invalid Admin ID or Password.",
                        "Login Failed",
                        JOptionPane.ERROR_MESSAGE);

                // Clear password field for security
                passwordField.setText("");
            }
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(this,
                    "Admin credentials file not found.\nPlease contact system administrator.",
                    "Configuration Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error reading admin credentials.\nPlease contact system administrator.",
                    "System Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AdminLoginPage().setVisible(true);
        });
    }
}