package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;

public class TransactionViewer extends JFrame {
    private User currentUser;

    public TransactionViewer(User user) {
        this.currentUser = user;

        setTitle("Transaction History");
        setSize(600, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        String[] cols = {"Date Time", "Description"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        table.setRowHeight(24);

        try (BufferedReader br = new BufferedReader(new FileReader("transaction.txt"))) {
            String line;
            while ((line = br.readLine()) != null && line.startsWith("[") && line.contains("] ")) {
                int i = line.indexOf("]");
                String timestamp = line.substring(1, i);
                String desc = line.substring(i + 2);

                // Only show records related to current user
                if (desc.contains(currentUser.getAccountNo())) {
                    model.addRow(new String[]{timestamp, desc});
                }
            }
        } catch (IOException ignored) {}

        add(new JScrollPane(table), BorderLayout.CENTER);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
