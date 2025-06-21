package org.example;

import javax.swing.*;

public class UserMainFrame extends JFrame {
    private User user;
    private JLabel lblBal;

    public UserMainFrame(User user) {
        this.user = user;
        setTitle("User Dashboard");
        setSize(400, 300);
        setLayout(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        add(new JLabel("Welcome, " + user.getFullName())).setBounds(20, 20, 300, 25);
        add(new JLabel("Account: " + user.getAccountNo())).setBounds(20, 50, 300, 25);

        lblBal = new JLabel("Balance: RM " + String.format("%.2f", user.getBalance()));
        lblBal.setBounds(20, 80, 300, 25);
        add(lblBal);

        JButton btnTrans = new JButton("Transfer");
        btnTrans.setBounds(20, 120, 120, 30);
        btnTrans.addActionListener(e -> new TransferFrame(user, this));
        add(btnTrans);

        JButton btnW = new JButton("Withdraw");
        btnW.setBounds(160, 120, 120, 30);
        btnW.addActionListener(e -> new WithdrawDepositFrame(user, this, "withdraw"));
        add(btnW);

        JButton btnD = new JButton("Deposit");
        btnD.setBounds(20, 160, 120, 30);
        btnD.addActionListener(e -> new WithdrawDepositFrame(user, this, "deposit"));
        add(btnD);

        JButton btnView = new JButton("View Transactions");
        btnView.setBounds(160, 160, 160, 30);
        btnView.addActionListener(e -> new TransactionViewer(user).setVisible(true));
        add(btnView);

        setLocationRelativeTo(null);
        setVisible(true);
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
}
