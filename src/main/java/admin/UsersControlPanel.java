package admin;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.util.Vector;
import java.util.concurrent.*;

public class UsersControlPanel extends JFrame {
    private JTable userTable;
    private DefaultTableModel tableModel;
    private ScheduledExecutorService scheduler;
    private long lastModifiedTime = 0;

    public UsersControlPanel() {
        setTitle("User Control Panel");
        setSize(700, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        String[] columns = {"Account No", "Role", "Username", "Full Name", "Balance"};
        tableModel = new DefaultTableModel(columns, 0);
        userTable = new JTable(tableModel);
        userTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        userTable.setRowHeight(24);

        JScrollPane scrollPane = new JScrollPane(userTable);
        add(scrollPane, BorderLayout.CENTER);

        // Optional: add sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        userTable.setRowSorter(sorter);

        loadUsersFromFile();
        startAutoRefresh();
    }

    private void loadUsersFromFile() {
        File file = new File("src/main/java/users.txt");
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "users.txt not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Update the last modified time for monitoring
        lastModifiedTime = file.lastModified();

        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0); // clear old data
        });

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean skipFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (skipFirstLine) {
                    skipFirstLine = false;
                    continue; // Skip the first line
                }

                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    Vector<String> row = new Vector<>();
                    row.add(parts[0]); // Account No
                    row.add(parts[1]); // Role
                    row.add(parts[2]); // Username
                    row.add(parts[4]); // Full Name
                    row.add("RM" + parts[5]); // Balance

                    SwingUtilities.invokeLater(() -> {
                        tableModel.addRow(row);
                    });
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error reading users.txt", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            File file = new File("src/main/java/users.txt");
            if (file.exists()) {
                long currentModified = file.lastModified();
                if (currentModified > lastModifiedTime) {
                    loadUsersFromFile(); // only reload if modified
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        super.dispose();
    }
}

