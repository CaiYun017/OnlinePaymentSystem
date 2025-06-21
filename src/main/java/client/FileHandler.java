package client;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FileHandler {
    public static final String USER_FILE = "users.txt";
    public static final String TRANS_FILE = "transactions.txt";

    public static List<User> readUsers() {
        List<User> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(USER_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] a = line.split(",");
                if (a.length == 6) {
                    list.add(new User(a[0], a[1], a[2], a[3], a[4], Double.parseDouble(a[5])));
                }
            }
        } catch (IOException ignored) {}
        return list;
    }

    public static void writeUsers(List<User> users) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(USER_FILE))) {
            for (User u : users) {
                bw.write(String.join(",", u.getAccountNo(), u.getRole(), u.getUsername(),
                        u.getPassword(), u.getFullName(), String.valueOf(u.getBalance())));
                bw.newLine();
            }
        }
    }

    public static void logTransaction(String msg) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRANS_FILE, true))) {
            String dt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            bw.write("[" + dt + "] " + msg);
            bw.newLine();
        } catch (IOException ignored) {}
    }

    public static String generateAccountNo() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }

    public static Optional<User> findByAccount(String acc) {
        return readUsers().stream()
                .filter(x -> x.getAccountNo().equals(acc))
                .findFirst();
    }

    public static Optional<User> findUser(String username, String password) {
        return readUsers().stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst();
    }
}
