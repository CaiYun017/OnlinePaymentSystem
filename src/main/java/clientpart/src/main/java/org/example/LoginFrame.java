package org.example;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {
    public LoginFrame() {
        setTitle("User Login");
        setSize(350, 180);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 2));

        add(new JLabel("Username:"));
        JTextField tfUser = new JTextField();
        add(tfUser);

        add(new JLabel("Password:"));
        JPasswordField tfPass = new JPasswordField();
        add(tfPass);

        JButton btnLogin = new JButton("Login");
        JButton btnRegister = new JButton("Register");
        add(btnLogin);
        add(btnRegister);

        // Login button action
        btnLogin.addActionListener(e -> {
            String username = tfUser.getText().trim();
            String password = new String(tfPass.getPassword());

            FileHandler.findUser(username, password).ifPresentOrElse(user -> {
                dispose();
                new UserMainFrame(user);
            }, () -> JOptionPane.showMessageDialog(this, "Login failed. Invalid credentials."));
        });

        // Register button action
        btnRegister.addActionListener(e -> {
            new RegisterFrame();
            dispose();
        });

        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }
}
