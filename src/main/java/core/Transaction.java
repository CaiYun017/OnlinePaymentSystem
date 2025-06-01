package core;

public class Transaction implements Runnable {
    private String clientId;
    private double amount;
    private TransactionManager manager;

    public Transaction(String clientId, double amount, TransactionManager manager) {
        this.clientId = clientId;
        this.amount = amount;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
            boolean success = manager.processTransaction(clientId, amount);
            System.out.println("Transaction " + (success ? "Success" : "Failed") + " for client " + clientId);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
