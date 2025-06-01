package admin;

import javax.swing.*;
import java.awt.*;

public class AdminMonitor extends JFrame {
    private JTextArea logArea;

    public AdminMonitor() {
        setTitle("Admin Monitor");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);

        add(new JScrollPane(logArea), BorderLayout.CENTER);

    }

    public void appendLog(String log) {
        logArea.append(log + "\n");
    }
}

