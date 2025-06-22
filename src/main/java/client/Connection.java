package client;

import java.io.PrintWriter;
import java.net.Socket;

public class Connection {
    private static final String SERVER_IP = "172.20.10.2";
    private static final int SERVER_PORT = 9999;

    public static boolean sendMessage(String message) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.print(message);
            out.flush();
            return true;

        } catch (Exception e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }
}

