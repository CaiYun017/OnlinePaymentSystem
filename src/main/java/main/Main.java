package main;

import client.Client;
import admin.AdminMonitor;

public class Main {
    public static void main(String[] args) {
        // 启动用户界面
        javax.swing.SwingUtilities.invokeLater(() -> {
            new Client().setVisible(true);
        });

        // 启动管理员界面
        javax.swing.SwingUtilities.invokeLater(() -> {
            new AdminMonitor().setVisible(true);
        });
    }
}
