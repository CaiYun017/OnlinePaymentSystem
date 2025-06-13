package admin;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class AdminPanel extends JFrame {
    private JTextArea logArea;
    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutor;
    private final Set<String> displayedTransactions = ConcurrentHashMap.newKeySet();
    private int lastLineCount = 0;
    private final Lock fileLock = new ReentrantLock();
    private static final ZoneId MALAYSIA_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AdminPanel() {
        setTitle("Admin Transaction Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        transactionTable = new JTable();
        tableModel = new DefaultTableModel(new String[]{"Timestamp", "Type", "Sender", "Recipient", "Amount", "Status", "Thread"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        transactionTable.setModel(tableModel);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        sorter.setComparator(0, (o1, o2) -> {
            try {
                LocalDateTime dt1 = LocalDateTime.parse(o1.toString(), formatter);
                LocalDateTime dt2 = LocalDateTime.parse(o2.toString(), formatter);
                return dt2.compareTo(dt1); // Descending
            } catch (Exception e) {
                return 0;
            }
        });

        transactionTable.setRowSorter(sorter);
        sorter.toggleSortOrder(0); // sort initially by timestamp descending

        JScrollPane scrollPane = new JScrollPane(transactionTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);

        JButton showTodayButton = new JButton("Show Today's Transactions");
        JButton showAllButton = new JButton("Show All Transactions");

        String[] filterOptions = {"All", "Transfer", "Deposit", "Withdraw"};
        JComboBox<String> filterDropdown = new JComboBox<>(filterOptions);

        showTodayButton.addActionListener(e -> {
            resetTransactionTableHeader();
            filterDropdown.setSelectedItem("All");
            loadTodayTransactions();
        });

        showAllButton.addActionListener(e -> {
            resetTransactionTableHeader();
            filterDropdown.setSelectedItem("All");
            loadTransactions();
        });

        filterDropdown.addActionListener(e -> {
            String selected = (String) filterDropdown.getSelectedItem();
            resetTransactionTableHeader();
            filterByType(selected);
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(showTodayButton);
        topPanel.add(showAllButton);
        topPanel.add(new JLabel("Filter by Type:"));
        topPanel.add(filterDropdown);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        setSize(950, 500);
        setLocationRelativeTo(null);

        executorService = Executors.newCachedThreadPool();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        addLog("Application started.");
        loadTransactions();
        startRealTimeTransactionMonitor();
        startSocketTransactionServer();
    }

    private void loadTransactions() {
        resetTransactionTableHeader();
        displayedTransactions.clear();

        File file = new File("src/main/java/transactions.txt");

        Thread thread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                List<Transaction> transactions = parseMultiLineTransactions(br.lines().collect(Collectors.toList()));
                for (Transaction t : transactions) {
                    SwingUtilities.invokeLater(() -> addTransactionToTable(t));
                }
                lastLineCount = (int) file.length();
            } catch (IOException e) {
                addLog("Error loading transactions: " + e.getMessage());
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            addLog("Thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void loadTodayTransactions() {
        resetTransactionTableHeader();
        displayedTransactions.clear();

        File file = new File("src/main/java/transactions.txt");

        LocalDate today = LocalDate.now(MALAYSIA_ZONE);

        executorService.submit(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                List<Transaction> allTransactions = parseMultiLineTransactions(br.lines().collect(Collectors.toList()));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                List<Transaction> todaysTransactions = allTransactions.parallelStream()
                        .filter(t -> {
                            try {
                                LocalDate txDate = LocalDateTime.parse(t.timestamp, formatter).toLocalDate();
                                return txDate.equals(today);
                            } catch (Exception e) {
                                addLog("Parse error in ShowToday: " + t.timestamp);
                                return false;
                            }
                        })
                        .collect(Collectors.toList());

                SwingUtilities.invokeLater(() -> {
                    displayedTransactions.clear();
                    tableModel.setRowCount(0);
                });

                for (Transaction t : todaysTransactions) {
                    SwingUtilities.invokeLater(() -> addTransactionToTable(t));
                }

                addLog("Displayed only today's transactions. Total: " + todaysTransactions.size());

            } catch (IOException e) {
                addLog("Error loading today's transactions: " + e.getMessage());
            }
        });
    }

    private void filterByType(String type) {
        resetTransactionTableHeader();
        displayedTransactions.clear();

        File file = new File("src/main/java/transactions.txt");

        executorService.submit(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                List<Transaction> allTransactions = parseMultiLineTransactions(br.lines().collect(Collectors.toList()));
                List<Transaction> filtered = allTransactions.parallelStream()
                        .filter(t -> "All".equalsIgnoreCase(type) || t.type.equalsIgnoreCase(type))
                        .collect(Collectors.toList());

                for (Transaction t : filtered) {
                    SwingUtilities.invokeLater(() -> addTransactionToTable(t));
                }

                addLog("Filtered by type: " + type);

            } catch (IOException e) {
                addLog("Filter error: " + e.getMessage());
            }
        });
    }

    private void startRealTimeTransactionMonitor() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            fileLock.lock();
            try {
                File file = new File("src/main/java/transactions.txt");

                long currentLength = file.length();

                if (currentLength > lastLineCount) {
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    List<Transaction> transactions = parseMultiLineTransactions(br.lines().collect(Collectors.toList()));
                    transactions.forEach(t -> SwingUtilities.invokeLater(() -> addTransactionToTable(t)));
                    lastLineCount = (int) currentLength;
                }
            } catch (IOException e) {
                addLog("Monitoring error: " + e.getMessage());
            } finally {
                fileLock.unlock();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private List<Transaction> parseMultiLineTransactions(List<String> lines) {
        return Arrays.stream(String.join("\n", lines).split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(block -> !block.isEmpty())
                .map(this::parseTransactionBlock)
                .filter(t -> t != null && !t.timestamp.isEmpty() && !t.type.isEmpty())
                .collect(Collectors.toList());
    }

    private Transaction parseTransactionBlock(String block) {
        try {
            String[] lines = block.split("\n");

            String senderName = "", senderAccNo = "", receiverAccNo = "", type = "", status = "", timestamp = "", thread = "";
            double amount = 0;

            for (String line : lines) {
                if (!line.contains(":")) continue;

                String[] parts = line.split(":", 2);
                String key = parts[0].trim();
                String value = parts[1].trim().replaceAll(",", "");

                switch (key) {
                    case "SenderName": senderName = value; break;
                    case "SenderAccNo": senderAccNo = value; break;
                    case "ReceiverAccNo": receiverAccNo = value; break;
                    case "Type": type = value; break;
                    case "Status": status = value; break;
                    case "DateTime": timestamp = value; break;
                    case "Amount": amount = Double.parseDouble(value); break;
                    case "Thread": thread = value; break;
                }
            }
            return new Transaction(senderName, senderAccNo, receiverAccNo, type, status, timestamp, amount, thread);
        } catch (Exception e) {
            addLog("Failed to parse transaction block: " + e.getMessage());
            return null;
        }
    }

    private void startSocketTransactionServer() {
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(9999)) {
                addLog("Socket server started on port 9999...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                addLog("Socket error: " + e.getMessage());
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            StringBuilder block = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    String transactionBlock = block.toString().trim();

                    if (!transactionBlock.isEmpty()) {
                        fileLock.lock();
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/main/java/transactions.txt", true))) {
                            writer.write(transactionBlock);
                            writer.newLine();
                            writer.newLine();
                        } finally {
                            fileLock.unlock();
                        }
                        addLog("Transaction block written to file.");
                        block.setLength(0);
                    }
                } else {
                    block.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            addLog("Client handling error: " + e.getMessage());
        }
    }

    private void addTransactionToTable(Transaction t) {
        String uniqueId = t.timestamp + t.senderAccountNo + t.recipientAccountNo + t.amount + t.thread;

        if (!displayedTransactions.contains(uniqueId)) {
            displayedTransactions.add(uniqueId);
            tableModel.addRow(new Object[]{
                    t.timestamp,
                    t.type,
                    t.senderAccountNo,
                    t.recipientAccountNo,
                    "RM" + t.amount,
                    t.status,
                    t.thread
            });

            // Scroll to new row
            SwingUtilities.invokeLater(() -> {
                int lastRow = tableModel.getRowCount() - 1;
                if (lastRow >= 0) {
                    transactionTable.scrollRectToVisible(transactionTable.getCellRect(lastRow, 0, true));
                }

                // 🔥 Force sort by timestamp column DESC after row is added
                TableRowSorter<?> sorter = (TableRowSorter<?>) transactionTable.getRowSorter();
                sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
                sorter.sort();
            });
        }
    }

    private void resetTransactionTableHeader() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            tableModel.setColumnCount(0);
            tableModel.setColumnIdentifiers(new String[]{
                    "Timestamp", "Type", "Sender", "Recipient", "Amount", "Status", "Thread"
            });
            transactionTable.revalidate();
            transactionTable.repaint();
        });
    }

    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            ZonedDateTime malaysiaTime = ZonedDateTime.now(MALAYSIA_ZONE);
            String time = malaysiaTime.format(LOG_TIMESTAMP_FORMAT);
            logArea.append("[" + time + "] " + message + "\n");
        });
    }

    public static class Transaction {
        public final String senderName, senderAccountNo, recipientAccountNo, type, status, timestamp, thread;
        public final double amount;

        public Transaction(String senderName, String senderAccountNo, String recipientAccountNo, String type, String status, String timestamp, double amount, String thread) {
            this.senderName = senderName;
            this.senderAccountNo = senderAccountNo;
            this.recipientAccountNo = recipientAccountNo;
            this.type = type;
            this.status = status;
            this.timestamp = timestamp;
            this.amount = amount;
            this.thread = thread;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminPanel().setVisible(true));
    }
}
