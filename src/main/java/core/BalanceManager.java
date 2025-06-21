package core;

import java.util.concurrent.locks.ReentrantLock;

public class BalanceManager {
    private double balance = 1000.0;
    private ReentrantLock lock = new ReentrantLock();

    public boolean deduct(double amount) {
        lock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                System.out.println("Balance deducted: " + amount + ", new balance: " + balance);
                return true;
            } else {
                System.out.println("Insufficient balance for " + amount);
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public double getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }
}
