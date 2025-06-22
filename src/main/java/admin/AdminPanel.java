package admin;

import client.User;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
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
    private static final ReentrantLock userFileLock = new ReentrantLock();
    private static final ReentrantLock transactionFileLock = new ReentrantLock();

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

        JButton openUserPanelButton = new JButton("Open User Control Panel");
        openUserPanelButton.addActionListener(this::openUserPanel);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(showTodayButton);
        topPanel.add(showAllButton);
        topPanel.add(new JLabel("Filter by Type:"));
        topPanel.add(filterDropdown);
        topPanel.add(openUserPanelButton);

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

    private void openUserPanel(ActionEvent e) {
        SwingUtilities.invokeLater(() -> new UsersControlPanel().setVisible(true));
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
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            StringBuilder block = new StringBuilder();
            String line;
            boolean isAuth = false;
            boolean isRegister = false;
            boolean isGetName = false;
            boolean isTransfer = false;
            boolean isGetBalance = false;
            boolean isWithdrawDeposit = false;
            boolean isGetTransactions = false;

            while ((line = in.readLine()) != null) {
                System.out.println("Received line: " + line);

                if (line.equals("AUTHENTICATE_USER")) {
                    isAuth = true;
                    continue;
                }
                if (line.equals("REGISTER_USER")) {
                    isRegister = true;
                    continue;
                }
                if (line.equals("GET_RECIPIENT_NAME")) {
                    isGetName = true;
                    continue;
                }
                if (line.equals("TRANSFER_REQUEST")) {
                    isTransfer = true;
                    continue;
                }
                if (line.equals("GET_BALANCE")) {
                    isGetBalance = true;
                    continue;
                }
                if (line.equals("WITHDRAW_DEPOSIT_REQUEST")) {
                    isWithdrawDeposit = true;
                    block.setLength(0);
                    continue;
                }
                if (line.equals("GET_TRANSACTIONS")) {
                    isGetTransactions = true;
                    block.setLength(0);
                    continue;
                }

                if (line.equals("CHECK_DUPLICATE")) {
                    String user = in.readLine().trim();   // username or fullname
                    in.readLine(); // consume empty line

                    boolean duplicateFound = checkDuplicateUser(user);
                    out.println(duplicateFound ? "DUPLICATE_FOUND" : "NO_DUPLICATE");
                    continue;
                }

                if (line.trim().isEmpty()) {
                    if (isAuth) {
                        String[] data = block.toString().split("\n");
                        if (data.length >= 2) {
                            String username = data[0].trim();
                            String password = data[1].trim();
                            System.out.println("Auth request for: " + username);
                            User user = authenticateUser(username, password);
                            if (user != null) {
                                out.println("AUTH_SUCCESS");
                                out.println(user.accountNo);
                                out.println(user.role);
                                out.println(user.username);
                                out.println(user.password);
                                out.println(user.fullName);
                                out.println(user.balance);
                            } else {
                                out.println("AUTH_FAILED");
                            }
                        }
                        isAuth = false;
                        block.setLength(0);

                    } else if (isRegister) {
                        String[] userData = block.toString().trim().split("\n");
                        if (userData.length >= 1) {
                            String userLine = userData[0].trim();
                            File userFile = new File("src/main/java/users.txt");

                            fileLock.lock();
                            try {
                                boolean fileExists = userFile.exists();
                                boolean fileEmpty = !fileExists || userFile.length() == 0;

                                try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile, true))) {
                                    if (fileEmpty) {
                                        writer.write("AccountNo,Role,Username,Password,FullName,Balance");
                                        writer.newLine();
                                    }

                                    writer.write(userLine);
                                    writer.newLine();
                                    out.println("REGISTER_SUCCESS");
                                    addLog("User registered: " + userLine);
                                }
                            } catch (IOException ex) {
                                out.println("REGISTER_FAILED");
                                addLog("Failed to register user: " + ex.getMessage());
                            } finally {
                                fileLock.unlock();
                            }
                        } else {
                            out.println("REGISTER_FAILED");
                        }
                        isRegister = false;
                        block.setLength(0);

                    } else if (isGetName) {
                        String acc = block.toString().trim();
                        System.out.println("GET_RECIPIENT_NAME block: " + acc);
                        String name = lookupName(acc);
                        out.println(name.isEmpty() ? "NOT_FOUND" : name);
                        isGetName = false;
                        block.setLength(0);

                    } else if (isTransfer) {
                        System.out.println("TRANSFER_REQUEST block:\n" + block);
                        processTransfer(block.toString(), out);
                        isTransfer = false;
                        block.setLength(0);

                    } else if (isGetBalance) {
                        String accNo = block.toString().trim();
                        double bal = lookupBalance(accNo);
                        out.println(bal >= 0 ? String.valueOf(bal) : "NOT_FOUND");
                        isGetBalance = false;
                        block.setLength(0);

                    } else if (isWithdrawDeposit) {
                        System.out.println("WITHDRAW_DEPOSIT_REQUEST block:\n" + block);
                        processWithdrawDeposit(block.toString(), out);
                        isWithdrawDeposit = false;
                        block.setLength(0);

                    } else if (isGetTransactions) {
                        String accNo = block.toString().trim();
                        File transFile = new File("src/main/java/transactions.txt");

                        fileLock.lock();
                        try (BufferedReader br = new BufferedReader(new FileReader(transFile))) {
                            StringBuilder transactionBlock = new StringBuilder();
                            String transLine;

                            while ((transLine = br.readLine()) != null) {
                                if (transLine.trim().isEmpty()) {
                                    String blockText = transactionBlock.toString();
                                    if (blockText.matches("(?s).*\\b" + Pattern.quote(accNo) + "\\b.*")) {
                                        out.print(blockText);
                                        out.println(); // block separator
                                    }
                                    transactionBlock.setLength(0);
                                } else {
                                    transactionBlock.append(transLine).append("\n");
                                }
                            }

                            // Final block
                            if (transactionBlock.length() > 0) {
                                String blockText = transactionBlock.toString();
                                if (blockText.matches("(?s).*\\b" + Pattern.quote(accNo) + "\\b.*")) {
                                    out.print(blockText);
                                    out.println();
                                }
                            }

                            out.flush();
                        } catch (IOException e) {
                            out.println("ERROR_READING_TRANSACTIONS");
                            e.printStackTrace();
                        } finally {
                            fileLock.unlock();
                        }

                        isGetTransactions = false;
                        block.setLength(0);
                    }

                } else {
                    block.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private User authenticateUser(String username, String hashedPassword) {
        File file = new File("src/main/java/users.txt");
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean skip = true;
            while ((line = reader.readLine()) != null) {
                if (skip) { skip = false; continue; }

                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String storedUsername = parts[2].trim();
                    String storedPassword = parts[3].trim(); // This should already be hashed!

                    System.out.println("Checking: " + storedUsername);

                    if (storedUsername.equals(username) && storedPassword.equals(hashedPassword)) {
                        return new User(
                                parts[0].trim(),  // account number
                                parts[1].trim(),  // role
                                parts[2].trim(),  // username
                                parts[3].trim(),  // password (hashed)
                                parts[4].trim(),  // full name
                                Double.parseDouble(parts[5].trim())  // balance
                        );
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean checkDuplicateUser(String username) {
        File userFile = new File("src/main/java/users.txt");

        fileLock.lock();
        try (BufferedReader br = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("AccountNo")) continue;
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    if (parts[2].equalsIgnoreCase(username)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fileLock.unlock();
        }
        return false;
    }

    private double lookupBalance(String accNo) {
        File userFile = new File("src/main/java/users.txt");

        if (!userFile.exists()) return -1;

        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) { // Skip header
                    firstLine = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String accountNo = parts[0].trim();
                    if (accountNo.equals(accNo)) {
                        return Double.parseDouble(parts[5].trim()); // Balance is the 6th field
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        return -1;
    }

    private void processWithdrawDeposit(String block, PrintWriter out) {
        String accNo = "", type = "";
        double amount = 0;
        String name = "", status = "Failed";
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String threadName = Thread.currentThread().getName();

        for (String line : block.split("\n")) {
            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;
            switch (parts[0].trim()) {
                case "AccountNo": accNo = parts[1].trim(); break;
                case "Amount": amount = Double.parseDouble(parts[1].trim()); break;
                case "Type": type = parts[1].trim().toUpperCase(); break;
            }
        }

        File userFile = new File("src/main/java/users.txt");
        File transFile = new File("src/main/java/transactions.txt");

        boolean updated = false;
        List<String> lines = new ArrayList<>();

        userFileLock.lock();
        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(accNo)) {
                    double bal = Double.parseDouble(parts[5]);
                    name = parts[2];

                    if ("WITHDRAW".equals(type) && bal >= amount) {
                        bal -= amount;
                        parts[5] = String.format("%.2f", bal);
                        updated = true;
                    } else if ("DEPOSIT".equals(type)) {
                        bal += amount;
                        parts[5] = String.format("%.2f", bal);
                        updated = true;
                    }
                }
                lines.add(String.join(",", parts));
            }
        } catch (IOException e) {
            out.println("UPDATE_FAILED");
            e.printStackTrace();
            userFileLock.unlock();
            return;
        }

        if (updated) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile))) {
                for (String l : lines) {
                    writer.write(l);
                    writer.newLine();
                }
            } catch (IOException e) {
                out.println("UPDATE_FAILED");
                userFileLock.unlock();
                return;
            } finally {
                userFileLock.unlock();
            }

            // ✅ Write transaction record
            transactionFileLock.lock();
            try (BufferedWriter log = new BufferedWriter(new FileWriter(transFile, true))) {
                log.write("SenderName: " + name + ",\n");
                log.write("SenderAccNo: " + ("WITHDRAW".equals(type) ? "ATM" : accNo) + ",\n");
                log.write("ReceiverAccNo: " + ("WITHDRAW".equals(type) ? accNo : "ATM") + ",\n");
                log.write("Type: " + type + ",\n");
                log.write("Status: Success,\n");
                log.write("DateTime: " + timestamp + ",\n");
                log.write("Amount: " + String.format("%.2f", amount) + ",\n");
                log.write("Thread: " + threadName + ",\n\n");
            } catch (IOException e) {
                System.err.println("Failed to write transaction log: " + e.getMessage());
            } finally {
                transactionFileLock.unlock();
            }

            out.println("UPDATE_SUCCESS");
            out.println(String.format("%.2f", getBalanceOfAccount(accNo)));
        } else {
            userFileLock.unlock();
            out.println("UPDATE_FAILED");
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

    private String lookupName(String accNo) {
        File file = new File("src/main/java/users.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5 && parts[0].equals(accNo)) {
                    return parts[4];
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void processTransfer(String block, PrintWriter out) {
        String sender = "", recipient = "";
        double amount = 0;
        String senderName = "";
        String threadName = Thread.currentThread().getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        for (String line : block.split("\n")) {
            String[] parts = line.split(":", 2);
            if (parts.length < 2) continue;
            switch (parts[0].trim()) {
                case "SenderAccNo": sender = parts[1].trim(); break;
                case "RecipientAccNo": recipient = parts[1].trim(); break;
                case "Amount": amount = Double.parseDouble(parts[1].trim()); break;
            }
        }

        File userFile = new File("src/main/java/users.txt");
        File transFile = new File("src/main/java/transactions.txt");
        List<String> lines = new ArrayList<>();
        boolean updatedSender = false, updatedRecipient = false;

        // Lock for reading/updating user file
        userFileLock.lock();
        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(sender)) {
                    double bal = Double.parseDouble(parts[5]);
                    if (bal < amount) continue; // Not enough balance
                    bal -= amount;
                    parts[5] = String.format("%.2f", bal);
                    senderName = parts[2]; // full name
                    updatedSender = true;
                } else if (parts[0].equals(recipient)) {
                    double bal = Double.parseDouble(parts[5]);
                    bal += amount;
                    parts[5] = String.format("%.2f", bal);
                    updatedRecipient = true;
                }
                lines.add(String.join(",", parts));
            }
        } catch (IOException e) {
            e.printStackTrace();
            out.println("TRANSFER_FAILED");
            userFileLock.unlock();
            return;
        }

        if (updatedSender && updatedRecipient) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile))) {
                for (String ln : lines) {
                    writer.write(ln);
                    writer.newLine();
                }
            } catch (IOException e) {
                out.println("TRANSFER_FAILED");
                userFileLock.unlock();
                return;
            } finally {
                userFileLock.unlock(); // release after write
            }

            // ✅ Append transaction log
            transactionFileLock.lock();
            try (BufferedWriter transWriter = new BufferedWriter(new FileWriter(transFile, true))) {
                transWriter.write("SenderName: " + senderName + ",\n");
                transWriter.write("SenderAccNo: " + sender + ",\n");
                transWriter.write("ReceiverAccNo: " + recipient + ",\n");
                transWriter.write("Type: TRANSFER,\n");
                transWriter.write("Status: Success,\n");
                transWriter.write("DateTime: " + timestamp + ",\n");
                transWriter.write("Amount: " + String.format("%.2f", amount) + ",\n");
                transWriter.write("Thread: " + threadName + ",\n\n");
            } catch (IOException e) {
                System.err.println("Failed to write to transactions.txt: " + e.getMessage());
            } finally {
                transactionFileLock.unlock();
            }

            // ✅ Send response to client
            out.println("TRANSFER_SUCCESS");
            out.println(String.format("%.2f", getBalanceOfAccount(sender)));
        } else {
            userFileLock.unlock(); // ensure unlocked even if failed
            out.println("TRANSFER_FAILED");
        }
    }


    private double getBalanceOfAccount(String accountNo) {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/java/users.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(accountNo)) {
                    return Double.parseDouble(parts[5]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
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
}
