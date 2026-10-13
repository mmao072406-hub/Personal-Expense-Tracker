/*
 * Income.java
 * Entity class representing an income record.
 */
public class Income {
    private int id;
    private int userId;
    private double amount;
    private String source;
    private String category;
    private String date;
    private String notes;
    private String createdAt;

    public Income() {}

    public Income(int id, int userId, double amount, String source, String category, String date, String notes, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.source = source;
        this.category = category;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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
