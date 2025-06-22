package client;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientApp extends JFrame {
    private JTextField senderNameField, senderAccField, recipientAccField, amountField;
    private JComboBox<String> typeBox;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public ClientApp() {
        setTitle("Client Transaction Sender");
        setSize(400, 300);
        setLayout(new GridLayout(7, 2));

        add(new JLabel("Sender Name:"));
        senderNameField = new JTextField();
        add(senderNameField);

        add(new JLabel("Sender Acc No:"));
        senderAccField = new JTextField();
        add(senderAccField);

        add(new JLabel("Recipient Acc No:"));
        recipientAccField = new JTextField();
        add(recipientAccField);

        add(new JLabel("Amount:"));
        amountField = new JTextField();
        add(amountField);

        add(new JLabel("Type:"));
        typeBox = new JComboBox<>(new String[]{"Transfer", "Withdraw", "Deposit"});
        add(typeBox);

        JButton sendButton = new JButton("Send Transaction");
        add(sendButton);
        sendButton.addActionListener(e -> executor.submit(this::sendTransaction));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void sendTransaction() {
        String senderName = senderNameField.getText().trim();
        String senderAcc = senderAccField.getText().trim();
        String recipientAcc = recipientAccField.getText().trim();
        String type = (String) typeBox.getSelectedItem();
        String status = "Success";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String threadName = Thread.currentThread().getName();
        double amount;

        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException e) {
            showMessage("Invalid amount format.");
            return;
        }

        if (type.equals("Withdraw")) {
            recipientAcc = "ATM";
        }

        StringBuilder data = new StringBuilder();
        data.append("SenderName: ").append(senderName).append(",\n");
        data.append("SenderAccNo: ").append(senderAcc).append(",\n");
        data.append("ReceiverAccNo: ").append(recipientAcc).append(",\n");
        data.append("Type: ").append(type).append(",\n");
        data.append("Status: ").append(status).append(",\n");
        data.append("DateTime: ").append(timestamp).append(",\n");
        data.append("Amount: ").append(String.format("%.2f", amount)).append(",\n");
        data.append("Thread: ").append(threadName).append("\n\n");

        try (Socket socket = new Socket("172.20.10.2", 9999);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.print(data);
            out.flush();
            showMessage("Transaction Sent Successfully");
        } catch (Exception e) {
            showMessage("Failed to send transaction: " + e.getMessage());
        }
    }

    private void showMessage(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, message));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientApp::new);
    }
}
