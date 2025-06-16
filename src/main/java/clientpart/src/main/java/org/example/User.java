package org.example;

public class User {
    private String accountNo, role, username, password, fullName;
    private double balance;
    public User(String accountNo, String role, String username, String password, String fullName, double balance) {
        this.accountNo = accountNo; this.role = role;
        this.username = username; this.password = password;
        this.fullName = fullName; this.balance = balance;
    }
    public String getAccountNo() { return accountNo; }
    public String getRole() { return role; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
