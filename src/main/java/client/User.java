package client;

public class User {
    public String accountNo;
    public String role;
    public String username;
    public String password;
    public String fullName;
    public double balance;

    public User(String accountNo, String role, String username, String password, String fullName, double balance) {
        this.accountNo = accountNo;
        this.role = role;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.balance = balance;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getRole() {
        return role;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public double getBalance() {
        return balance;
    }

    public double setBalance(double v) {
        return balance;
    }
}

