package core;

import java.util.concurrent.locks.ReentrantLock;

public class TransactionManager {
    private BalanceManager balanceManager = new BalanceManager();

    public boolean processTransaction(String clientId, double amount) {
        boolean result = balanceManager.deduct(amount);
        return result;
    }
}
