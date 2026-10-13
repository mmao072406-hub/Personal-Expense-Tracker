import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * ExpenseManager.java
 * Handles CRUD operations, searching, filtering, sorting for expenses,
 * as well as computing aggregated dashboard statistics for the logged-in user.
 */
public class ExpenseManager {

    /**
     * Adds a new expense for the specified user.
     */
    public boolean addExpense(int userId, double amount, String description, String category, String paymentMethod, String date, String notes) {
        String sql = """
            INSERT INTO expenses (user_id, amount, description, category, payment_method, date, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'));
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, description.trim());
            pstmt.setString(4, category.trim());
            pstmt.setString(5, paymentMethod.trim());
            pstmt.setString(6, date.trim());
            pstmt.setString(7, notes != null ? notes.trim() : "");

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Error adding expense: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves expenses for a given user with optional search, category filter, date filter, and sort order.
     */
    public List<Expense> getExpenses(int userId, String search, String category, String dateFrom, String dateTo, String sortBy) {
        List<Expense> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, user_id, amount, description, category, payment_method, date, notes, created_at FROM expenses WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (description LIKE ? OR notes LIKE ? OR category LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All")) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }

        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            sql.append(" AND date >= ?");
            params.add(dateFrom.trim());
        }

        if (dateTo != null && !dateTo.trim().isEmpty()) {
            sql.append(" AND date <= ?");
            params.add(dateTo.trim());
        }

        // Sorting logic
        if ("amount_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount ASC");
        } else if ("amount_desc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount DESC");
        } else if ("date_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY date ASC, id ASC");
        } else {
            sql.append(" ORDER BY date DESC, id DESC"); // Default newest first
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Expense(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("payment_method"),
                        rs.getString("date"),
                        rs.getString("notes"),
                        rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Error fetching expenses: " + e.getMessage());
        }

        return list;
    }

    /**
     * Gets a single expense by ID, verified against user_id.
     */
    public Expense getExpenseById(int id, int userId) {
        String sql = "SELECT id, user_id, amount, description, category, payment_method, date, notes, created_at FROM expenses WHERE id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Expense(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("payment_method"),
                        rs.getString("date"),
                        rs.getString("notes"),
                        rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Error fetching expense by id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates an existing expense record.
     */
    public boolean updateExpense(int id, int userId, double amount, String description, String category, String paymentMethod, String date, String notes) {
        String sql = """
            UPDATE expenses 
            SET amount = ?, description = ?, category = ?, payment_method = ?, date = ?, notes = ?
            WHERE id = ? AND user_id = ?;
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, amount);
            pstmt.setString(2, description.trim());
            pstmt.setString(3, category.trim());
            pstmt.setString(4, paymentMethod.trim());
            pstmt.setString(5, date.trim());
            pstmt.setString(6, notes != null ? notes.trim() : "");
            pstmt.setInt(7, id);
            pstmt.setInt(8, userId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Error updating expense: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes an expense record owned by userId.
     */
    public boolean deleteExpense(int id, int userId) {
        String sql = "DELETE FROM expenses WHERE id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Error deleting expense: " + e.getMessage());
            return false;
        }
    }

    /**
     * Aggregates key dashboard metrics for the logged in user.
     */
    public Map<String, Object> getDashboardData(int userId) {
        Map<String, Object> data = new HashMap<>();

        double totalIncome = 0.0;
        double totalExpenses = 0.0;
        double thisMonthExpenses = 0.0;
        double highestExpense = 0.0;
        int transactionCount = 0;

        String currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try (Connection conn = Database.getConnection()) {
            // Total Income
            String incSql = "SELECT SUM(amount), COUNT(*) FROM income WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(incSql)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalIncome = rs.getDouble(1);
                        transactionCount += rs.getInt(2);
                    }
                }
            }

            // Total Expenses, Highest Expense, Transaction Count
            String expSql = "SELECT SUM(amount), MAX(amount), COUNT(*) FROM expenses WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(expSql)) {
                pstmt.setInt(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalExpenses = rs.getDouble(1);
                        highestExpense = rs.getDouble(2);
                        transactionCount += rs.getInt(3);
                    }
                }
            }

            // Expenses This Month
            String monthExpSql = "SELECT SUM(amount) FROM expenses WHERE user_id = ? AND strftime('%Y-%m', date) = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(monthExpSql)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, currentMonthPrefix);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        thisMonthExpenses = rs.getDouble(1);
                    }
                }
            }

            // Recent Transactions (top 10 combined or expenses/income)
            List<Expense> recentExpenses = getExpenses(userId, null, null, null, null, "date_desc");
            if (recentExpenses.size() > 10) {
                recentExpenses = recentExpenses.subList(0, 10);
            }

            data.put("totalIncome", totalIncome);
            data.put("totalExpenses", totalExpenses);
            data.put("totalBalance", totalIncome - totalExpenses);
            data.put("thisMonthExpenses", thisMonthExpenses);
            data.put("highestExpense", highestExpense);
            data.put("transactionCount", transactionCount);
            data.put("recentExpenses", recentExpenses);

        } catch (SQLException e) {
            System.err.println("[ExpenseManager] Dashboard query error: " + e.getMessage());
        }

        return data;
    }
}
