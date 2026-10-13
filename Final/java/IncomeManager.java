import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/*
 * IncomeManager.java
 * Handles CRUD operations, searching, and filtering for user income records.
 */
public class IncomeManager {

    /**
     * Adds an income record for the specified user.
     */
    public boolean addIncome(int userId, double amount, String source, String category, String date, String notes) {
        String sql = """
            INSERT INTO income (user_id, amount, source, category, date, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'));
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, source.trim());
            pstmt.setString(4, category.trim());
            pstmt.setString(5, date.trim());
            pstmt.setString(6, notes != null ? notes.trim() : "");

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[IncomeManager] Error adding income: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves income records for a given user with optional search, category filter, and sorting.
     */
    public List<Income> getIncome(int userId, String search, String category, String dateFrom, String dateTo, String sortBy) {
        List<Income> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, user_id, amount, source, category, date, notes, created_at FROM income WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (source LIKE ? OR notes LIKE ? OR category LIKE ?)");
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

        if ("amount_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount ASC");
        } else if ("amount_desc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY amount DESC");
        } else if ("date_asc".equalsIgnoreCase(sortBy)) {
            sql.append(" ORDER BY date ASC, id ASC");
        } else {
            sql.append(" ORDER BY date DESC, id DESC");
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Income(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("amount"),
                        rs.getString("source"),
                        rs.getString("category"),
                        rs.getString("date"),
                        rs.getString("notes"),
                        rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[IncomeManager] Error fetching income: " + e.getMessage());
        }

        return list;
    }

    /**
     * Gets a single income record by ID for a user.
     */
    public Income getIncomeById(int id, int userId) {
        String sql = "SELECT id, user_id, amount, source, category, date, notes, created_at FROM income WHERE id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Income(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getDouble("amount"),
                        rs.getString("source"),
                        rs.getString("category"),
                        rs.getString("date"),
                        rs.getString("notes"),
                        rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[IncomeManager] Error fetching income by id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates an income record.
     */
    public boolean updateIncome(int id, int userId, double amount, String source, String category, String date, String notes) {
        String sql = """
            UPDATE income 
            SET amount = ?, source = ?, category = ?, date = ?, notes = ?
            WHERE id = ? AND user_id = ?;
        """;

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, amount);
            pstmt.setString(2, source.trim());
            pstmt.setString(3, category.trim());
            pstmt.setString(4, date.trim());
            pstmt.setString(5, notes != null ? notes.trim() : "");
            pstmt.setInt(6, id);
            pstmt.setInt(7, userId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[IncomeManager] Error updating income: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes an income record owned by userId.
     */
    public boolean deleteIncome(int id, int userId) {
        String sql = "DELETE FROM income WHERE id = ? AND user_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[IncomeManager] Error deleting income: " + e.getMessage());
            return false;
        }
    }
}
