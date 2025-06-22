package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class WithdrawDepositFrame extends JFrame {

    public WithdrawDepositFrame(User user, UserMainFrame parent, String op) {
        setTitle(op.equals("withdraw") ? "Withdraw Money" : "Deposit Money");
        setSize(420, 220);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(op.equals("withdraw") ? new Color(255, 99, 71) : new Color(60, 179, 113));
        JLabel titleLabel = new JLabel(op.equals("withdraw") ? "Withdraw Money" : "Deposit Money");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(245, 245, 245));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 10, 12, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblAmount = new JLabel("Amount (RM):");
        lblAmount.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        JTextField tfAmt = new JTextField(18);
        tfAmt.setFont(new Font("Segoe UI", Font.PLAIN, 16));

        JButton btnOK = new JButton("Confirm");
        btnOK.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnOK.setBackground(op.equals("withdraw") ? new Color(255, 99, 71) : new Color(60, 179, 113));
        btnOK.setForeground(Color.WHITE);

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(lblAmount, gbc);

        gbc.gridx = 1;
        panel.add(tfAmt, gbc);

        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(btnOK, gbc);

        add(panel, BorderLayout.CENTER);

        btnOK.addActionListener((ActionEvent e) -> {
            try {
                String input = tfAmt.getText().trim();
                NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
                Number number = format.parse(input);
                double amount = number.doubleValue();

                if (amount <= 0) {
                    throw new Exception("Amount must be greater than 0.");
                }

                if (op.equals("withdraw") && amount > user.getBalance()) {
                    throw new Exception("Insufficient balance.");
                }

                // Build data block to send to server
                StringBuilder block = new StringBuilder();
                block.append("AccountNo: ").append(user.getAccountNo()).append("\n");
                block.append("Amount: ").append(String.format("%.2f", amount)).append("\n");
                block.append("Type: ").append(op.toUpperCase()).append("\n\n");

                boolean success = false;

                try (Socket socket = new Socket("172.20.10.2", 9999);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println("WITHDRAW_DEPOSIT_REQUEST");
                    out.print(block);
                    out.println();

                    String response = in.readLine();
                    success = "UPDATE_SUCCESS".equalsIgnoreCase(response);

                    if (success) {
                        String updatedBal = in.readLine();
                        user.setBalance(Double.parseDouble(updatedBal));
                    }
                }

                if (success) {
                    parent.updateBalance();
                    JOptionPane.showMessageDialog(this, op.substring(0, 1).toUpperCase() + op.substring(1) + " successful!");
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Transaction failed. Server rejected the request.", "Error", JOptionPane.ERROR_MESSAGE);
                }

            } catch (ParseException pe) {
                JOptionPane.showMessageDialog(this, "Invalid number format. Please enter a valid number (e.g. 50, 50.00)", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        setVisible(true);
    }
}

