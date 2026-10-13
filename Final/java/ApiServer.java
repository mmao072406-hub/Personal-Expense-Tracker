import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * ApiServer.java
 * High-performance embedded HTTP server powering the local REST API and serving frontend static assets.
 */
public class ApiServer {
    private static final int PORT = 8080;
    private final ExpenseManager expenseManager = new ExpenseManager();
    private final IncomeManager incomeManager = new IncomeManager();
    private final BudgetManager budgetManager = new BudgetManager();
    private final ReportManager reportManager = new ReportManager();

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API context router
        server.createContext("/api/", new ApiHandler());

        // Static files router
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("[ApiServer] Server running on http://localhost:" + PORT);
    }

    /*
     * Handles static assets (HTML, CSS, JS) from frontend/ directory.
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.trim().isEmpty()) {
                path = "/login.html";
            }

            File file = new File("frontend" + path);
            if (!file.exists() || file.isDirectory()) {
                // Default fallback to login.html for clean SPA navigation
                file = new File("frontend/login.html");
            }

            if (!file.exists()) {
                sendResponse(exchange, 404, "text/plain", "404 Not Found");
                return;
            }

            String contentType = getMimeType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            try (InputStream is = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /*
     * Central API Router for REST Endpoints.
     */
    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS Headers for local client communication
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                if (path.equals("/api/signup") && "POST".equalsIgnoreCase(method)) {
                    handleSignup(exchange);
                } else if (path.equals("/api/login") && "POST".equalsIgnoreCase(method)) {
                    handleLogin(exchange);
                } else if (path.equals("/api/logout") && "POST".equalsIgnoreCase(method)) {
                    handleLogout(exchange);
                } else {
                    // Protected Routes - Require valid session token
                    Authentication.UserSession session = authenticate(exchange);
                    if (session == null) {
                        sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized. Please log in.\"}");
                        return;
                    }

                    if (path.equals("/api/dashboard") && "GET".equalsIgnoreCase(method)) {
                        handleDashboard(exchange, session);
                    } else if (path.startsWith("/api/expenses")) {
                        handleExpenses(exchange, session);
                    } else if (path.startsWith("/api/income")) {
                        handleIncome(exchange, session);
                    } else if (path.startsWith("/api/budgets")) {
                        handleBudgets(exchange, session);
                    } else if (path.equals("/api/reports") && "GET".equalsIgnoreCase(method)) {
                        handleReports(exchange, session);
                    } else if (path.equals("/api/profile") && "GET".equalsIgnoreCase(method)) {
                        handleGetProfile(exchange, session);
                    } else if (path.equals("/api/profile/change-password") && "POST".equalsIgnoreCase(method)) {
                        handleChangePassword(exchange, session);
                    } else if (path.equals("/api/profile/delete-account") && "POST".equalsIgnoreCase(method)) {
                        handleDeleteAccount(exchange, session);
                    } else {
                        sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Endpoint not found\"}");
                    }
                }
            } catch (Exception e) {
                System.err.println("[ApiServer] Exception handling route " + path + ": " + e.getMessage());
                e.printStackTrace();
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Internal server error occurred.\"}");
            }
        }
    }

    private Authentication.UserSession authenticate(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return Authentication.getSession(token);
        }
        return null;
    }

    // --- Endpoint Handlers ---

    private void handleSignup(HttpExchange exchange) throws IOException {
        System.out.println("[ApiServer] POST /api/signup request received");
        Map<String, String> body = parseJsonBody(exchange);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        String confirmPassword = body.getOrDefault("confirmPassword", "");

        System.out.println("[ApiServer] Signup request parsed for username: " + username);

        if (username.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Username cannot be empty.\"}");
            return;
        }
        if (password.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Password cannot be empty.\"}");
            return;
        }
        if (confirmPassword.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Confirm password cannot be empty.\"}");
            return;
        }
        if (!password.equals(confirmPassword)) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Passwords must match.\"}");
            return;
        }

        // Duplicate username check
        String checkSql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(checkSql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("[ApiServer] Username already exists: " + username);
                    sendJsonResponse(exchange, 409, "{\"success\":false,\"message\":\"Username already exists.\"}");
                    return;
                }
            }

            // Hash password and insert
            String hash = Authentication.hashPassword(password);
            String insertSql = "INSERT INTO users (username, password_hash, created_at) VALUES (?, ?, datetime('now'))";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, username);
                insertStmt.setString(2, hash);
                insertStmt.executeUpdate();
            }

            System.out.println("[ApiServer] User created successfully: " + username);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Account created successfully.\"}");

        } catch (SQLException e) {
            System.err.println("[ApiServer] Signup DB error: " + e.getMessage());
            sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Database error. Please try again.\"}");
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> body = parseJsonBody(exchange);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Incorrect username or password.\"}");
            return;
        }

        String sql = "SELECT id, username, password_hash FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String storedHash = rs.getString("password_hash");

                    if (Authentication.verifyPassword(password, storedHash)) {
                        String token = Authentication.createSession(userId, username);
                        sendJsonResponse(exchange, 200, String.format("{\"success\":true,\"token\":\"%s\",\"username\":\"%s\"}", token, escapeJson(username)));
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApiServer] Login DB error: " + e.getMessage());
        }

        sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Incorrect username or password.\"}");
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        Authentication.UserSession session = authenticate(exchange);
        if (session != null) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                Authentication.destroySession(authHeader.substring(7));
            }
        }
        sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Logged out successfully.\"}");
    }

    private void handleDashboard(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        Map<String, Object> data = expenseManager.getDashboardData(session.getUserId());

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"totalIncome\":").append(data.get("totalIncome")).append(",");
        sb.append("\"totalExpenses\":").append(data.get("totalExpenses")).append(",");
        sb.append("\"totalBalance\":").append(data.get("totalBalance")).append(",");
        sb.append("\"thisMonthExpenses\":").append(data.get("thisMonthExpenses")).append(",");
        sb.append("\"highestExpense\":").append(data.get("highestExpense")).append(",");
        sb.append("\"transactionCount\":").append(data.get("transactionCount")).append(",");

        sb.append("\"recentExpenses\":[");
        @SuppressWarnings("unchecked")
        List<Expense> list = (List<Expense>) data.get("recentExpenses");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                Expense e = list.get(i);
                sb.append(String.format("{\"id\":%d,\"amount\":%.2f,\"description\":\"%s\",\"category\":\"%s\",\"paymentMethod\":\"%s\",\"date\":\"%s\",\"notes\":\"%s\"}",
                    e.getId(), e.getAmount(), escapeJson(e.getDescription()), escapeJson(e.getCategory()), escapeJson(e.getPaymentMethod()), escapeJson(e.getDate()), escapeJson(e.getNotes())));
            }
        }
        sb.append("]}");

        sendJsonResponse(exchange, 200, sb.toString());
    }

    private void handleExpenses(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            String search = query.get("search");
            String category = query.get("category");
            String dateFrom = query.get("dateFrom");
            String dateTo = query.get("dateTo");
            String sortBy = query.get("sortBy");

            List<Expense> expenses = expenseManager.getExpenses(session.getUserId(), search, category, dateFrom, dateTo, sortBy);

            StringBuilder sb = new StringBuilder("{\"success\":true,\"expenses\":[");
            for (int i = 0; i < expenses.size(); i++) {
                if (i > 0) sb.append(",");
                Expense e = expenses.get(i);
                sb.append(String.format("{\"id\":%d,\"amount\":%.2f,\"description\":\"%s\",\"category\":\"%s\",\"paymentMethod\":\"%s\",\"date\":\"%s\",\"notes\":\"%s\"}",
                    e.getId(), e.getAmount(), escapeJson(e.getDescription()), escapeJson(e.getCategory()), escapeJson(e.getPaymentMethod()), escapeJson(e.getDate()), escapeJson(e.getNotes())));
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());

        } else if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> body = parseJsonBody(exchange);

            String amountStr = body.getOrDefault("amount", "");
            String quantityStr = body.getOrDefault("quantity", "1");
            String description = body.getOrDefault("description", "");
            String category = body.getOrDefault("category", "");
            String paymentMethod = body.getOrDefault("paymentMethod", "");
            String date = body.getOrDefault("date", "");
            String notes = body.getOrDefault("notes", "");

            ExpenseCalculator.CalculationResult calc = ExpenseCalculator.calculate(amountStr, quantityStr);
            if (!calc.isValid()) {
                sendJsonResponse(exchange, 400, String.format("{\"success\":false,\"message\":\"%s\"}", escapeJson(calc.getErrorMessage())));
                return;
            }

            if (description.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Description cannot be empty.\"}");
                return;
            }
            if (category.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Category must be selected.\"}");
                return;
            }
            if (date.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Date must be valid.\"}");
                return;
            }

            boolean ok = expenseManager.addExpense(session.getUserId(), calc.getTotal(), description, category, paymentMethod, date, notes);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Expense added successfully.\"}");
            } else {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to save expense.\"}");
            }

        } else if ("PUT".equalsIgnoreCase(method)) {
            Map<String, String> body = parseJsonBody(exchange);
            int id = Integer.parseInt(body.getOrDefault("id", "0"));

            String amountStr = body.getOrDefault("amount", "");
            String quantityStr = body.getOrDefault("quantity", "1");
            String description = body.getOrDefault("description", "");
            String category = body.getOrDefault("category", "");
            String paymentMethod = body.getOrDefault("paymentMethod", "");
            String date = body.getOrDefault("date", "");
            String notes = body.getOrDefault("notes", "");

            ExpenseCalculator.CalculationResult calc = ExpenseCalculator.calculate(amountStr, quantityStr);
            if (!calc.isValid()) {
                sendJsonResponse(exchange, 400, String.format("{\"success\":false,\"message\":\"%s\"}", escapeJson(calc.getErrorMessage())));
                return;
            }

            boolean ok = expenseManager.updateExpense(id, session.getUserId(), calc.getTotal(), description, category, paymentMethod, date, notes);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Expense updated successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update expense.\"}");
            }

        } else if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            int id = Integer.parseInt(query.getOrDefault("id", "0"));

            boolean ok = expenseManager.deleteExpense(id, session.getUserId());
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Expense deleted successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to delete expense.\"}");
            }
        }
    }

    private void handleIncome(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            String search = query.get("search");
            String category = query.get("category");
            String dateFrom = query.get("dateFrom");
            String dateTo = query.get("dateTo");
            String sortBy = query.get("sortBy");

            List<Income> incomeList = incomeManager.getIncome(session.getUserId(), search, category, dateFrom, dateTo, sortBy);

            StringBuilder sb = new StringBuilder("{\"success\":true,\"income\":[");
            for (int i = 0; i < incomeList.size(); i++) {
                if (i > 0) sb.append(",");
                Income inc = incomeList.get(i);
                sb.append(String.format("{\"id\":%d,\"amount\":%.2f,\"source\":\"%s\",\"category\":\"%s\",\"date\":\"%s\",\"notes\":\"%s\"}",
                    inc.getId(), inc.getAmount(), escapeJson(inc.getSource()), escapeJson(inc.getCategory()), escapeJson(inc.getDate()), escapeJson(inc.getNotes())));
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());

        } else if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> body = parseJsonBody(exchange);

            double amount;
            try {
                amount = Double.parseDouble(body.getOrDefault("amount", "0"));
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid amount. Please enter a valid positive number.\"}");
                return;
            }

            if (amount <= 0) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Income amount must be greater than zero.\"}");
                return;
            }

            String source = body.getOrDefault("source", "");
            String category = body.getOrDefault("category", "");
            String date = body.getOrDefault("date", "");
            String notes = body.getOrDefault("notes", "");

            if (source.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Source cannot be empty.\"}");
                return;
            }
            if (date.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Date must be valid.\"}");
                return;
            }

            boolean ok = incomeManager.addIncome(session.getUserId(), amount, source, category, date, notes);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Income record added successfully.\"}");
            } else {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to save income record.\"}");
            }

        } else if ("PUT".equalsIgnoreCase(method)) {
            Map<String, String> body = parseJsonBody(exchange);
            int id = Integer.parseInt(body.getOrDefault("id", "0"));
            double amount = Double.parseDouble(body.getOrDefault("amount", "0"));
            String source = body.getOrDefault("source", "");
            String category = body.getOrDefault("category", "");
            String date = body.getOrDefault("date", "");
            String notes = body.getOrDefault("notes", "");

            if (amount <= 0) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Income amount must be greater than zero.\"}");
                return;
            }

            boolean ok = incomeManager.updateIncome(id, session.getUserId(), amount, source, category, date, notes);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Income record updated successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to update income record.\"}");
            }

        } else if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            int id = Integer.parseInt(query.getOrDefault("id", "0"));

            boolean ok = incomeManager.deleteIncome(id, session.getUserId());
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Income record deleted successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to delete income record.\"}");
            }
        }
    }

    private void handleBudgets(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            int month = Integer.parseInt(query.getOrDefault("month", String.valueOf(LocalDate.now().getMonthValue())));
            int year = Integer.parseInt(query.getOrDefault("year", String.valueOf(LocalDate.now().getYear())));

            List<BudgetManager.BudgetStatus> statuses = budgetManager.getBudgetStatuses(session.getUserId(), month, year);

            StringBuilder sb = new StringBuilder("{\"success\":true,\"budgets\":[");
            for (int i = 0; i < statuses.size(); i++) {
                if (i > 0) sb.append(",");
                BudgetManager.BudgetStatus bs = statuses.get(i);
                Budget b = bs.getBudget();
                sb.append(String.format("{\"id\":%d,\"category\":\"%s\",\"amount\":%.2f,\"spent\":%.2f,\"remaining\":%.2f,\"percentageUsed\":%.1f,\"warning\":%s}",
                    b.getId(), escapeJson(b.getCategory()), b.getAmount(), bs.getSpent(), bs.getRemaining(), bs.getPercentageUsed(),
                    bs.getWarning() != null ? "\"" + escapeJson(bs.getWarning()) + "\"" : "null"));
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());

        } else if ("POST".equalsIgnoreCase(method)) {
            Map<String, String> body = parseJsonBody(exchange);
            String category = body.getOrDefault("category", "");
            double amount = Double.parseDouble(body.getOrDefault("amount", "0"));
            int month = Integer.parseInt(body.getOrDefault("month", String.valueOf(LocalDate.now().getMonthValue())));
            int year = Integer.parseInt(body.getOrDefault("year", String.valueOf(LocalDate.now().getYear())));

            if (category.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Category cannot be empty.\"}");
                return;
            }
            if (amount <= 0) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Budget amount must be a positive number.\"}");
                return;
            }

            boolean ok = budgetManager.setBudget(session.getUserId(), category, amount, month, year);
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Budget set successfully.\"}");
            } else {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to save budget.\"}");
            }

        } else if ("DELETE".equalsIgnoreCase(method)) {
            Map<String, String> query = parseQueryString(exchange.getRequestURI().getQuery());
            int id = Integer.parseInt(query.getOrDefault("id", "0"));

            boolean ok = budgetManager.deleteBudget(id, session.getUserId());
            if (ok) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Budget deleted successfully.\"}");
            } else {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Failed to delete budget.\"}");
            }
        }
    }

    private void handleReports(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        String json = reportManager.getReportJson(session.getUserId());
        sendJsonResponse(exchange, 200, json);
    }

    private void handleGetProfile(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        String sql = "SELECT username, created_at FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, session.getUserId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String username = rs.getString("username");
                    String createdAt = rs.getString("created_at");

                    // Count total transactions
                    Map<String, Object> data = expenseManager.getDashboardData(session.getUserId());
                    int totalTransactions = (int) data.getOrDefault("transactionCount", 0);

                    sendJsonResponse(exchange, 200, String.format(
                        "{\"success\":true,\"username\":\"%s\",\"createdAt\":\"%s\",\"totalTransactions\":%d}",
                        escapeJson(username), escapeJson(createdAt), totalTransactions
                    ));
                    return;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApiServer] Get Profile DB error: " + e.getMessage());
        }

        sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load profile data.\"}");
    }

    private void handleChangePassword(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        Map<String, String> body = parseJsonBody(exchange);
        String currentPassword = body.getOrDefault("currentPassword", "");
        String newPassword = body.getOrDefault("newPassword", "");
        String confirmNewPassword = body.getOrDefault("confirmNewPassword", "");

        if (currentPassword.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Current password cannot be empty.\"}");
            return;
        }
        if (newPassword.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"New password cannot be empty.\"}");
            return;
        }
        if (!newPassword.equals(confirmNewPassword)) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"New passwords must match.\"}");
            return;
        }

        // Verify current password
        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, session.getUserId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (!Authentication.verifyPassword(currentPassword, storedHash)) {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Incorrect current password.\"}");
                        return;
                    }

                    // Hash and update new password
                    String newHash = Authentication.hashPassword(newPassword);
                    String updateSql = "UPDATE users SET password_hash = ? WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, newHash);
                        updateStmt.setInt(2, session.getUserId());
                        updateStmt.executeUpdate();
                    }

                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Password changed successfully.\"}");
                    return;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApiServer] Change password DB error: " + e.getMessage());
        }

        sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to change password due to database error.\"}");
    }

    private void handleDeleteAccount(HttpExchange exchange, Authentication.UserSession session) throws IOException {
        Map<String, String> body = parseJsonBody(exchange);
        String password = body.getOrDefault("password", "");

        if (password.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Please enter your password to confirm account deletion.\"}");
            return;
        }

        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, session.getUserId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (!Authentication.verifyPassword(password, storedHash)) {
                        sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Incorrect password. Account deletion cancelled.\"}");
                        return;
                    }

                    // Execute cascade delete
                    String deleteSql = "DELETE FROM users WHERE id = ?";
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setInt(1, session.getUserId());
                        deleteStmt.executeUpdate();
                    }

                    // Destroy all active user sessions
                    Authentication.destroySessionsForUser(session.getUserId());

                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Account deleted successfully.\"}");
                    return;
                }
            }
        } catch (SQLException e) {
            System.err.println("[ApiServer] Delete account DB error: " + e.getMessage());
        }

        sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to delete account due to database error.\"}");
    }

    // --- Helper Utility Methods ---

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        sendResponse(exchange, statusCode, "application/json", jsonResponse);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseJsonBody(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        InputStream is = exchange.getRequestBody();
        String text = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        if (text.isEmpty() || !text.startsWith("{")) return map;

        // Simple JSON object key-value parser for simple strings & numbers
        text = text.substring(1, text.length() - 1); // remove outer braces
        String[] pairs = text.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (kv.length == 2) {
                String key = unquote(kv[0].trim());
                String val = unquote(kv[1].trim());
                map.put(key, val);
            }
        }
        return map;
    }

    private String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                map.put(kv[0], "");
            }
        }
        return map;
    }

    private String getMimeType(String filename) {
        if (filename.endsWith(".html")) return "text/html";
        if (filename.endsWith(".css")) return "text/css";
        if (filename.endsWith(".js")) return "application/javascript";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
