import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/*
 * ReportManager.java
 * Executes Python analytics engine (python/reports.py) via ProcessBuilder to generate
 * financial summaries and analysis JSON. Includes pure Java fallback calculation.
 */
public class ReportManager {

    /**
     * Obtains report JSON string by invoking python/reports.py for given user ID.
     */
    public String getReportJson(int userId) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "python/reports.py", "--user-id", String.valueOf(userId));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && output.toString().trim().startsWith("{")) {
                return output.toString().trim();
            } else {
                System.err.println("[ReportManager] Python script returned exit code " + exitCode + ": " + output);
            }
        } catch (Exception e) {
            System.err.println("[ReportManager] Failed to invoke Python analytics: " + e.getMessage());
        }

        // Fallback: Generate structured JSON directly in Java if Python is missing or failed
        return generateJavaFallbackReport(userId);
    }

    private String generateJavaFallbackReport(int userId) {
        ExpenseManager expManager = new ExpenseManager();
        IncomeManager incManager = new IncomeManager();

        List<Expense> expenses = expManager.getExpenses(userId, null, null, null, null, "date_desc");
        List<Income> incomeList = incManager.getIncome(userId, null, null, null, null, "date_desc");

        double totalIncome = incomeList.stream().mapToDouble(Income::getAmount).sum();
        double totalExpenses = expenses.stream().mapToDouble(Expense::getAmount).sum();
        double netSavings = totalIncome - totalExpenses;

        double highestExpense = expenses.stream().mapToDouble(Expense::getAmount).max().orElse(0.0);

        // Highest spending category calculation
        java.util.Map<String, Double> categoryTotals = new java.util.HashMap<>();
        for (Expense e : expenses) {
            categoryTotals.put(e.getCategory(), categoryTotals.getOrDefault(e.getCategory(), 0.0) + e.getAmount());
        }

        String highestCategory = "None";
        double highestCategoryAmount = 0.0;
        for (java.util.Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            if (entry.getValue() > highestCategoryAmount) {
                highestCategoryAmount = entry.getValue();
                highestCategory = entry.getKey();
            }
        }

        double avgDailySpending = expenses.isEmpty() ? 0.0 : totalExpenses / Math.max(1, expenses.size());

        StringBuilder catJson = new StringBuilder("{");
        int count = 0;
        for (java.util.Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            if (count > 0) catJson.append(",");
            catJson.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            count++;
        }
        catJson.append("}");

        return String.format(
            "{\"status\":\"success\",\"totalIncome\":%.2f,\"totalExpenses\":%.2f,\"netSavings\":%.2f,\"highestExpense\":%.2f,\"highestCategory\":\"%s\",\"highestCategoryAmount\":%.2f,\"averageDailySpending\":%.2f,\"categoryTotals\":%s}",
            totalIncome, totalExpenses, netSavings, highestExpense, escapeJson(highestCategory), highestCategoryAmount, avgDailySpending, catJson.toString()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
