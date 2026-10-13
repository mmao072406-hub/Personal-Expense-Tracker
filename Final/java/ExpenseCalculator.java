/*
 * ExpenseCalculator.java
 * Performs calculation of total expense amount = unit price * quantity
 * and input validation with user-friendly error messages.
 */
public class ExpenseCalculator {

    public static class CalculationResult {
        private final double total;
        private final String errorMessage;
        private final boolean valid;

        public CalculationResult(double total) {
            this.total = total;
            this.errorMessage = null;
            this.valid = true;
        }

        public CalculationResult(String errorMessage) {
            this.total = 0.0;
            this.errorMessage = errorMessage;
            this.valid = false;
        }

        public double getTotal() {
            return total;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isValid() {
            return valid;
        }
    }

    /**
     * Calculates total expense amount (Amount * Quantity).
     */
    public static CalculationResult calculate(String amountStr, String quantityStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return new CalculationResult("Invalid amount. Please enter a valid positive number.");
        }
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            return new CalculationResult("Invalid quantity. Please enter a positive number.");
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr.trim());
        } catch (NumberFormatException e) {
            return new CalculationResult("Invalid amount. Please enter a valid positive number.");
        }

        if (amount <= 0) {
            return new CalculationResult("Amount must be greater than zero.");
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityStr.trim());
        } catch (NumberFormatException e) {
            return new CalculationResult("Invalid quantity. Please enter a valid positive number.");
        }

        if (quantity <= 0) {
            return new CalculationResult("Quantity must be greater than zero.");
        }

        double total = amount * quantity;
        return new CalculationResult(total);
    }

    /**
     * Directly calculates total for numeric values.
     */
    public static double computeTotal(double unitPrice, double quantity) {
        if (unitPrice < 0 || quantity < 0) return 0.0;
        return unitPrice * quantity;
    }
}
