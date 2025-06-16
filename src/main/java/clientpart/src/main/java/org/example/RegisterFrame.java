package org.example;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RegisterFrame extends JFrame {
    public RegisterFrame() {
        setTitle("Register User");
        setSize(320, 240);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new GridLayout(6, 2));

        add(new JLabel("Full Name:"));
        JTextField tfName = new JTextField();
        add(tfName);
        add(new JLabel("Username:"));
        JTextField tfUser = new JTextField();
        add(tfUser);
        add(new JLabel("Password:"));
        JPasswordField tfPass = new JPasswordField();
        add(tfPass);
        add(new JLabel("Initial Balance:"));
        JTextField tfBal = new JTextField();
        add(tfBal);

        add(new JLabel());
        JButton btnReg = new JButton("Register");
        add(btnReg);

        btnReg.addActionListener(e -> {
            try {
                String fn = tfName.getText().trim();
                String u = tfUser.getText().trim();
                String p = new String(tfPass.getPassword());
                double b = Double.parseDouble(tfBal.getText().trim());
                if (fn.isEmpty() || u.isEmpty() || p.isEmpty() || b < 0) throw new Exception();
                List<User> lst = FileHandler.readUsers();
                String acc = FileHandler.generateAccountNo();
                lst.add(new User(acc, "user", u, p, fn, b));
                FileHandler.writeUsers(lst);
                JOptionPane.showMessageDialog(this, "Registered! Your account no: " + acc);
                new LoginFrame();
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid input. Retry.");
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }
}
