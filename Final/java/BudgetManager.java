import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * BudgetManager.java
 * Manages monthly category budgets, computes spent amounts, calculates usage percentages,
 * and generates budget threshold warning messages.
 */
public class BudgetManager {

    public static class BudgetStatus {
        private final Budget budget;
        private final double spent;
        private final double remaining;
        private final double percentageUsed;
        private final String warning;

        public BudgetStatus(Budget budget, double spent) {
            this.budget = budget;
            this.spent = spent;
            this.remaining = budget.getAmount() - spent;
            this.percentageUsed = budget.getAmount() > 0 ? (spent / budget.getAmount()) * 100.0 : 0.0;

            if (spent > budget.getAmount()) {
                this.warning = "Warning: You have exceeded your " + budget.getCategory() + " budget.";
            } else if (Math.abs(spent - budget.getAmount()) < 0.01 || spent == budget.getAmount()) {
                this.warning = "Warning: You have reached your " + budget.getCategory() + " budget.";
            } else if (this.percentageUsed >= 90.0) {
                this.warning = "Caution: You have used " + String.format("%.1f", this.percentageUsed) + "% of your " + budget.getCategory() + " budget.";
            } else {
                this.warning = null;
            }
        }

        public Budget getBudget() { return budget; }
        public double getSpent() { return spent; }
        public double getRemaining() { return remaining; }
        public double getPercentageUsed() { return percentageUsed; }
        public String getWarning() { return warning; }
    }

    /**
     * Saves or updates a budget for a category, month, and year.
     */
    public boolean setBudget(int userId, String category, double amount, int month, int year) {
        String existingSql = "SELECT id FROM budgets WHERE user_id = ? AND category = ? AND month = ? AND year = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(existingSql)) {

            checkStmt.setInt(1, userId);
            checkStmt.setString(2, category.trim());
            checkStmt.setInt(3, month);
            checkStmt.setInt(4, year);

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String updateSql = "UPDATE budgets SET amount = ? WHERE id = ? AND user_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setDouble(1, amount);
                        updateStmt.setInt(2, id);
                        updateStmt.setInt(3, userId);
                        return updateStmt.executeUpdate() > 0;
                    }
                } else {
                    String insertSql = """
                        INSERT INTO budgets (user_id, category, amount, month, year, created_at)
                        VALUES (?, ?, ?, ?, ?, datetime('now'));
                    """;
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setString(2, category.trim());
                        insertStmt.setDouble(3, amount);
                        insertStmt.setInt(4, month);
                        insertStmt.setInt(5, year);
                        return insertStmt.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[BudgetManager] Error setting budget: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves all budget statuses (with spent & remaining amounts) for a given user, month, and year.
     */
    public List<BudgetStatus> getBudgetStatuses(int userId, int month, int year) {
        List<BudgetStatus> list = new ArrayList<>();
        String monthFormatted = String.format("%04d-%02d", year, month);

        String sql = "SELECT id, user_id, category, amount, month, year, created_at FROM budgets WHERE user_id = ? AND month = ? AND year = ? ORDER BY category ASC";
        
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, month);
            pstmt.setInt(3, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Budget b = new Budget(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("category"),
                        rs.getDouble("amount"),
                        rs.getInt("month"),
                        rs.getInt("year"),
                        rs.getString("created_at")
                    );

                    double spent = getSpentForCategory(conn, userId, b.getCategory(), monthFormatted);
                    list.add(new BudgetStatus(b, spent));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BudgetManager] Error getting budget statuses: " + e.getMessage());
        }

        return list;
    }

    private double getSpentForCategory(Connection conn, int userId, String category, String yearMonth) throws SQLException {
        String sql = "SELECT SUM(amount) FROM expenses WHERE user_id = ? AND category = ? AND strftime('%Y-%m', date) = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, category);
            pstmt.setString(3, yearMonth);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    /**
     * Deletes a budget by ID for a user.
     */
    public boolean deleteBudget(int id, int userId) {
        String sql = "DELETE FROM budgets WHERE id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[BudgetManager] Error deleting budget: " + e.getMessage());
            return false;
        }
    }
}
