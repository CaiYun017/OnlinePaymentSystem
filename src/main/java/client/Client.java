package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class Client extends JFrame {
    private JTextField amountField;
    private JButton payButton;
    private JTextArea statusArea;

    public Client() {
        setTitle("User Payment Client");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        amountField = new JTextField(10);
        payButton = new JButton("Pay");
        statusArea = new JTextArea();
        statusArea.setEditable(false);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Amount:"));
        panel.add(amountField);
        panel.add(payButton);

        add(panel, BorderLayout.NORTH);
        add(new JScrollPane(statusArea), BorderLayout.CENTER);

        payButton.addActionListener(this::handlePayment);
    }

    private void handlePayment(ActionEvent e) {
        String amount = amountField.getText();
        statusArea.append("Initiating payment of $" + amount + "\n");
    }
}
