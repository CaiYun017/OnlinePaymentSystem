package client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class TransactionViewer extends JFrame {
    private User currentUser;
    private static final String SERVER_IP = "172.20.10.2";
    private static final int SERVER_PORT = 9999;

    public TransactionViewer(User user) {
        this.currentUser = user;

        setTitle("Transaction History");
        setSize(600, 400);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        String[] cols = {"Date Time", "Description"};
        DefaultTableModel model = new DefaultTableModel(cols, 0);
        JTable table = new JTable(model);

        // Disable auto-resize to allow horizontal scroll
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Optionally set preferred column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(150); // DateTime
        table.getColumnModel().getColumn(1).setPreferredWidth(800); // Description

        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 14));
        table.setRowHeight(24);

        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        fetchTransactionsFromServer(model);

        add(scrollPane, BorderLayout.CENTER);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void fetchTransactionsFromServer(DefaultTableModel model) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println("GET_TRANSACTIONS");
                    out.println(currentUser.getAccountNo());
                    out.println(); // End of block

                    StringBuilder block = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.trim().isEmpty()) {
                            if (block.length() > 0) {
                                final String blockText = block.toString();
                                SwingUtilities.invokeLater(() -> parseAndAddTransaction(blockText, model));
                                block.setLength(0);
                            }
                        } else {
                            block.append(line).append("\n");
                        }
                    }

                    // Handle final block if no empty line at EOF
                    if (block.length() > 0) {
                        final String blockText = block.toString();
                        SwingUtilities.invokeLater(() -> parseAndAddTransaction(blockText, model));
                    }

                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(TransactionViewer.this,
                                "Failed to retrieve transactions.",
                                "Connection Error", JOptionPane.ERROR_MESSAGE);
                    });
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    private void parseAndAddTransaction(String block, DefaultTableModel model) {
        String[] lines = block.split("\n");
        String timestamp = "";
        StringBuilder description = new StringBuilder();

        for (String line : lines) {
            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;

            String key = parts[0].trim();
            String value = parts[1].trim().replaceAll(",", "");

            if (key.equals("DateTime")) {
                timestamp = value;
            } else {
                description.append(key).append(": ").append(value).append(", ");
            }
        }

        model.addRow(new Object[]{timestamp, description.toString()});
    }
}
