package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class WithdrawDepositFrame extends JFrame {

    public WithdrawDepositFrame(User user, UserMainFrame parent, String op) {
        setTitle(op.equals("withdraw") ? "Withdraw Money" : "Deposit Money");
        setSize(400, 200);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Main panel with padding
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Amount Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Amount (RM):"), gbc);

        // Amount TextField
        JTextField tfAmt = new JTextField();
        gbc.gridx = 1;
        panel.add(tfAmt, gbc);

        // OK Button
        JButton btnOK = new JButton("Confirm");
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(btnOK, gbc);

        // Add panel to frame
        add(panel);

        btnOK.addActionListener(e -> {
            try {
                double am = Double.parseDouble(tfAmt.getText().trim());

                if (am <= 0 || (op.equals("withdraw") && am > user.getBalance())) {
                    throw new Exception("Invalid amount.");
                }

                // Step 1: Update user's balance in file
                List<User> lst = FileHandler.readUsers();
                for (User u : lst) {
                    if (u.getAccountNo().equals(user.getAccountNo())) {
                        if (op.equals("withdraw")) {
                            u.setBalance(u.getBalance() - am);
                        } else {
                            u.setBalance(u.getBalance() + am);
                        }
                        user.setBalance(u.getBalance());
                        break;
                    }
                }
                FileHandler.writeUsers(lst);

                // Step 2: Log transaction
                String msg = op + " RM " + am + " by " + user.getAccountNo();
                FileHandler.logTransaction(msg);

                // Step 3: Try to send transaction message
                try (Socket socket = new Socket("localhost", 9999);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.print(msg);
                    out.flush();

                    JOptionPane.showMessageDialog(this, "Transaction Sent Successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
                    parent.updateBalance();
                    JOptionPane.showMessageDialog(this, op + " successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    dispose();

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Send failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter a valid number.", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        setVisible(true);
    }
}
