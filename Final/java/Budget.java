/*
 * Budget.java
 * Entity class representing a category budget record.
 */
public class Budget {
    private int id;
    private int userId;
    private String category;
    private double amount;
    private int month;
    private int year;
    private String createdAt;

    public Budget() {}

    public Budget(int id, int userId, String category, double amount, int month, int year, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.category = category;
        this.amount = amount;
        this.month = month;
        this.year = year;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
