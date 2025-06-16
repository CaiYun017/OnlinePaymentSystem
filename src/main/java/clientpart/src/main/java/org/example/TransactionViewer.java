package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionViewer extends JFrame {
    public TransactionViewer() {
        setTitle("Transaction History");
        setSize(600, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        String[] cols = {"Date Time", "Description"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        table.setRowHeight(24);

        DecimalFormat df = new DecimalFormat("0.00");
        Pattern pattern = Pattern.compile("RM\\s*(\\d+(\\.\\d+)?)");

        try (BufferedReader br = new BufferedReader(new FileReader("transactions.txt"))) {
            String line;
            while ((line = br.readLine()) != null && line.startsWith("[") && line.contains("] ")) {
                int i = line.indexOf("]");
                String date = line.substring(1, i);
                String desc = line.substring(i + 2);

                Matcher m = pattern.matcher(desc);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    double amt = Double.parseDouble(m.group(1));
                    m.appendReplacement(sb, "RM " + df.format(amt));
                }
                m.appendTail(sb);

                model.addRow(new String[]{date, sb.toString()});
            }
        } catch (IOException ignored) {}

        add(new JScrollPane(table), BorderLayout.CENTER);
        setLocationRelativeTo(null);
        setVisible(true);
    }
}
