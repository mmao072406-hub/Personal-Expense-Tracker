/*
 * Expense.java
 * Entity class representing an expense record.
 */
public class Expense {
    private int id;
    private int userId;
    private double amount;
    private String description;
    private String category;
    private String paymentMethod;
    private String date;
    private String notes;
    private String createdAt;

    public Expense() {}

    public Expense(int id, int userId, double amount, String description, String category, String paymentMethod, String date, String notes, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.date = date;
        this.notes = notes;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
